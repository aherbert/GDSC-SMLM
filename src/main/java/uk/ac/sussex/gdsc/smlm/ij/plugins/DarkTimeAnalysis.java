/*-
 * #%L
 * Genome Damage and Stability Centre SMLM ImageJ Plugins
 *
 * Software for single molecule localisation microscopy (SMLM)
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package uk.ac.sussex.gdsc.smlm.ij.plugins;

import uk.ac.sussex.gdsc.core.clustering.Cluster;
import uk.ac.sussex.gdsc.core.clustering.ClusteringAlgorithm;
import uk.ac.sussex.gdsc.core.clustering.ClusteringEngine;
import uk.ac.sussex.gdsc.core.ij.HistogramPlot.HistogramPlotBuilder;
import uk.ac.sussex.gdsc.core.ij.ImageJTrackProgress;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.ij.gui.Plot2;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.StoredData;
import uk.ac.sussex.gdsc.core.utils.StoredDataStatistics;
import uk.ac.sussex.gdsc.smlm.data.config.UnitProtos.DistanceUnit;
import uk.ac.sussex.gdsc.smlm.ij.plugins.ResultsManager.InputSource;
import uk.ac.sussex.gdsc.smlm.results.MemoryPeakResults;
import uk.ac.sussex.gdsc.smlm.results.Trace;
import uk.ac.sussex.gdsc.smlm.results.TraceManager;

import ij.IJ;
import ij.Prefs;
import ij.plugin.PlugIn;

import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Computes a graph of the dark time and estimates the time threshold for the specified point in the
 * cumulative histogram.
 */
public class DarkTimeAnalysis implements PlugIn {
  private static String TITLE = "Dark-time Analysis";

  private static String[] METHOD;
  private static ClusteringAlgorithm[] algorithms =
      new ClusteringAlgorithm[] {ClusteringAlgorithm.CENTROID_LINKAGE_TIME_PRIORITY,
          ClusteringAlgorithm.CENTROID_LINKAGE_DISTANCE_PRIORITY,
          ClusteringAlgorithm.PARTICLE_CENTROID_LINKAGE_TIME_PRIORITY,
          ClusteringAlgorithm.PARTICLE_CENTROID_LINKAGE_DISTANCE_PRIORITY};

  static {
    final ArrayList<String> methods = new ArrayList<>();
    methods.add("Tracing");
    for (final ClusteringAlgorithm c : algorithms) {
      methods.add("Clustering (" + c.toString() + ")");
    }
    METHOD = methods.toArray(new String[methods.size()]);
  }

  private static String inputOption = "";
  private static int method;
  private double msPerFrame;
  private static double searchDistance = 100;
  private static double maxDarkTime;
  private static double percentile = 99;
  private static int nBins;

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    SMLMUsageTracker.recordPlugin(this.getClass(), arg);

    // Require some fit results and selected regions
    if (MemoryPeakResults.isMemoryEmpty()) {
      IJ.error(TITLE, "There are no fitting results in memory");
      return;
    }

    if (!showDialog()) {
      return;
    }

    // Assume pixels for now
    final MemoryPeakResults results =
        ResultsManager.loadInputResults(inputOption, true, DistanceUnit.PIXEL);
    IJ.showStatus("");
    if (results == null || results.size() == 0) {
      IJ.error(TITLE, "No results could be loaded");
      return;
    }
    if (!results.hasCalibration()) {
      IJ.error(TITLE, "Results are not calibrated");
      return;
    }
    msPerFrame = results.getCalibrationReader().getExposureTime();
    if (!(msPerFrame > 0)) {
      IJ.error(TITLE, "ms/frame must be strictly positive: " + msPerFrame);
      return;
    }
    ImageJUtils.log("%s: %d localisations", TITLE, results.size());

    analyse(results);
  }

  private static boolean showDialog() {
    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);
    gd.addHelp(About.HELP_URL);

    gd.addMessage("Compute the cumulative dark-time histogram");
    ResultsManager.addInput(gd, inputOption, InputSource.MEMORY);

    gd.addChoice("Method", METHOD, METHOD[method]);
    gd.addSlider("Search_distance (nm)", 5, 150, searchDistance);
    gd.addNumericField("Max_dark_time (seconds)", maxDarkTime, 2);
    gd.addSlider("Percentile", 0, 100, percentile);
    gd.addSlider("Histogram_bins", -1, 100, nBins);
    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    inputOption = gd.getNextChoice();
    method = gd.getNextChoiceIndex();
    searchDistance = gd.getNextNumber();
    maxDarkTime = gd.getNextNumber();
    percentile = gd.getNextNumber();
    nBins = (int) gd.getNextNumber();

    // Check arguments
    try {
      Parameters.isAboveZero("Search distance", searchDistance);
      Parameters.isPositive("Percentile", percentile);
    } catch (final IllegalArgumentException ex) {
      IJ.error(TITLE, ex.getMessage());
      return false;
    }

    return true;
  }

  private void analyse(MemoryPeakResults results) {
    // Find min and max time frames
    results.sort();
    final int min = results.getFirstFrame();
    final int max = results.getLastFrame();

    // Trace results:
    // TODO - The search distance could have units to avoid assuming the results are in pixels
    final double d = searchDistance / results.getCalibrationReader().getNmPerPixel();
    int range = max - min + 1;
    if (maxDarkTime > 0) {
      range = FastMath.max(1, (int) Math.round(maxDarkTime * 1000 / msPerFrame));
    }

    final ImageJTrackProgress tracker = new ImageJTrackProgress();
    tracker.status("Analysing ...");
    tracker.log("Analysing (d=%s nm (%s px) t=%s s (%d frames)) ...",
        MathUtils.rounded(searchDistance), MathUtils.rounded(d),
        MathUtils.rounded(range * msPerFrame / 1000.0), range);

    Trace[] traces;
    if (method == 0) {
      final TraceManager tm = new TraceManager(results);
      tm.setTracker(tracker);
      tm.traceMolecules(d, range);
      traces = tm.getTraces();
    } else {
      final ClusteringEngine engine =
          new ClusteringEngine(Prefs.getThreads(), algorithms[method - 1], tracker);
      final List<Cluster> clusters =
          engine.findClusters(TraceMolecules.convertToClusterPoints(results), d, range);
      traces = TraceMolecules.convertToTraces(results, clusters);
    }

    tracker.status("Computing histogram ...");

    // Build dark-time histogram
    final int[] times = new int[range];
    final StoredData stats = new StoredData();
    for (final Trace trace : traces) {
      if (trace.getNBlinks() > 1) {
        for (final int t : trace.getOffTimes()) {
          times[t]++;
        }
        stats.add(trace.getOffTimes());
      }
    }

    plotDarkTimeHistogram(stats);

    // Cumulative histogram
    for (int i = 1; i < times.length; i++) {
      times[i] += times[i - 1];
    }
    final int total = times[times.length - 1];

    // Plot dark-time up to 100%
    double[] x = new double[range];
    double[] y = new double[range];
    int truncate = 0;
    for (int i = 0; i < x.length; i++) {
      x[i] = i * msPerFrame;
      if (times[i] == total) {
        // Final value at 100%
        y[i] = 100.0;
        truncate = i + 1;
        break;
      }
      y[i] = (100.0 * times[i]) / total;
    }
    if (truncate > 0) {
      x = Arrays.copyOf(x, truncate);
      y = Arrays.copyOf(y, truncate);
    }

    final String title = "Cumulative Dark-time";
    final Plot2 plot = new Plot2(title, "Time (ms)", "Percentile", x, y);
    ImageJUtils.display(title, plot);

    // Report percentile
    for (int i = 0; i < y.length; i++) {
      if (y[i] >= percentile) {
        ImageJUtils.log("Dark-time Percentile %.1f @ %s ms = %s s", percentile,
            MathUtils.rounded(x[i]), MathUtils.rounded(x[i] / 1000));
        break;
      }
    }

    tracker.status("");
  }

  private void plotDarkTimeHistogram(StoredData stats) {
    if (nBins >= 0) {
      // Convert the X-axis to milliseconds
      final double[] xValues = stats.getValues();
      for (int i = 0; i < xValues.length; i++) {
        xValues[i] *= msPerFrame;
      }

      // Ensure the bin width is never less than 1
      new HistogramPlotBuilder("Dark-time", StoredDataStatistics.create(xValues), "Time (ms)")
          .setIntegerBins(true).setNumberOfBins(nBins).show();
    }
  }
}