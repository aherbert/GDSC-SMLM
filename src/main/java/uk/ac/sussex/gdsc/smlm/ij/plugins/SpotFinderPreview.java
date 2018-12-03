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

import uk.ac.sussex.gdsc.core.ij.HistogramPlot.HistogramPlotBuilder;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog.OptionCollectedEvent;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog.OptionCollectedListener;
import uk.ac.sussex.gdsc.core.ij.gui.NonBlockingExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.ij.gui.Plot2;
import uk.ac.sussex.gdsc.core.ij.plugin.WindowOrganiser;
import uk.ac.sussex.gdsc.core.ij.process.LutHelper;
import uk.ac.sussex.gdsc.core.ij.process.LutHelper.LutColour;
import uk.ac.sussex.gdsc.core.match.AucCalculator;
import uk.ac.sussex.gdsc.core.match.BasePoint;
import uk.ac.sussex.gdsc.core.match.ClassificationResult;
import uk.ac.sussex.gdsc.core.match.Coordinate;
import uk.ac.sussex.gdsc.core.match.FractionalAssignment;
import uk.ac.sussex.gdsc.core.match.ImmutableFractionalAssignment;
import uk.ac.sussex.gdsc.core.match.RankedScoreCalculator;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.RampedScore;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.StoredData;
import uk.ac.sussex.gdsc.core.utils.TurboList;
import uk.ac.sussex.gdsc.smlm.data.config.CalibrationProtos.Calibration;
import uk.ac.sussex.gdsc.smlm.data.config.CalibrationProtos.CameraType;
import uk.ac.sussex.gdsc.smlm.data.config.FitProtos.DataFilterMethod;
import uk.ac.sussex.gdsc.smlm.data.config.FitProtos.DataFilterType;
import uk.ac.sussex.gdsc.smlm.data.config.FitProtos.FitEngineSettings;
import uk.ac.sussex.gdsc.smlm.data.config.PSFProtos.PSF;
import uk.ac.sussex.gdsc.smlm.data.config.PSFProtosHelper;
import uk.ac.sussex.gdsc.smlm.data.config.TemplateProtos.TemplateSettings;
import uk.ac.sussex.gdsc.smlm.engine.FitConfiguration;
import uk.ac.sussex.gdsc.smlm.engine.FitEngineConfiguration;
import uk.ac.sussex.gdsc.smlm.filters.MaximaSpotFilter;
import uk.ac.sussex.gdsc.smlm.filters.Spot;
import uk.ac.sussex.gdsc.smlm.filters.SpotFilterHelper;
import uk.ac.sussex.gdsc.smlm.ij.IJImageSource;
import uk.ac.sussex.gdsc.smlm.ij.plugins.PeakFit.FitConfigurationProvider;
import uk.ac.sussex.gdsc.smlm.ij.plugins.PeakFit.RelativeParameterProvider;
import uk.ac.sussex.gdsc.smlm.ij.settings.SettingsManager;
import uk.ac.sussex.gdsc.smlm.model.camera.CameraModel;
import uk.ac.sussex.gdsc.smlm.model.camera.FakePerPixelCameraModel;
import uk.ac.sussex.gdsc.smlm.results.MemoryPeakResults;

import gnu.trove.map.hash.TIntObjectHashMap;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.Blitter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Label;
import java.awt.Rectangle;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Vector;

/**
 * Runs the candidate maxima identification on the image and provides a preview using an overlay.
 */
public class SpotFinderPreview implements ExtendedPlugInFilter, DialogListener, ImageListener,
    ItemListener, FitConfigurationProvider, OptionCollectedListener {
  private static final String TITLE = "Spot Finder Preview";

  private static DataFilterMethod defaultDataFilterMethod;
  private static double defaultSmooth;
  static {
    final FitEngineConfiguration c = new FitEngineConfiguration();
    defaultDataFilterMethod = c.getDataFilterMethod(0);
    defaultSmooth = c.getDataFilterParameterValue(0);
  }

  private final int flags = DOES_16 | DOES_8G | DOES_32 | NO_CHANGES;
  private FitEngineConfiguration config = null;
  private FitConfiguration fitConfig = null;
  private Overlay o = null;
  private ImagePlus imp = null;
  private boolean preview = false;
  private Label label = null;
  private TIntObjectHashMap<ArrayList<Coordinate>> actualCoordinates = null;
  private static double distance = 1.5;
  private static double lowerDistance = 50;
  private static boolean multipleMatches = false;
  private static boolean showTP = true;
  private static boolean showFP = true;
  private static int topN = 100;
  private static int select = 1;
  private static int neighbourRadius = 4;

  private int currentSlice = 0;
  private MaximaSpotFilter filter = null;

  // All the fields that will be updated when reloading the configuration file
  private Choice textCameraModelName;
  private Choice textPSF;
  private Choice textDataFilterType;
  private Choice textDataFilterMethod;
  private TextField textSmooth;
  private Choice textDataFilterMethod2;
  private TextField textSmooth2;
  private TextField textSearch;
  private TextField textBorder;

  // For adjusting the selction sliders
  private Scrollbar topNScrollBar, selectScrollBar;

  private boolean refreshing = false;
  private NonBlockingExtendedGenericDialog gd;

  private final SpotFilterHelper spotFilterHelper = new SpotFilterHelper();

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    SMLMUsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      IJ.noImage();
      return DONE;
    }

    final Roi roi = imp.getRoi();
    if (roi != null && roi.getType() != Roi.RECTANGLE) {
      IJ.error("Rectangular ROI required");
      return DONE;
    }

    return flags;
  }

  /** {@inheritDoc} */
  @Override
  public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
    this.o = imp.getOverlay();
    this.imp = imp;

    // The image is locked by the PlugInFilterRunner so unlock it to allow scroll.
    // This should be OK as the image data is not modified and only the overlay is
    // adjusted. If another plugin changes the image then the preview should update
    // the overlay and it will be obvious to the user to turn this plugin off.
    imp.unlock();

    config = SettingsManager.readFitEngineConfiguration(0);
    fitConfig = config.getFitConfiguration();

    gd = new NonBlockingExtendedGenericDialog(TITLE);
    gd.addHelp(About.HELP_URL);
    gd.addMessage("Preview candidate maxima");

    final String[] templates = ConfigurationTemplate.getTemplateNames(true);
    gd.addChoice("Template", templates, templates[0]);

    final String[] models = CameraModelManager.listCameraModels(true);
    gd.addChoice("Camera_model_name", models, fitConfig.getCameraModelName());

    PeakFit.addPSFOptions(gd, this);
    final PeakFit.SimpleFitEngineConfigurationProvider provider =
        new PeakFit.SimpleFitEngineConfigurationProvider(config);
    PeakFit.addDataFilterOptions(gd, provider);
    gd.addChoice("Spot_filter_2", SettingsManager.getDataFilterMethodNames(),
        config.getDataFilterMethod(1, defaultDataFilterMethod).ordinal());
    // gd.addSlider("Smoothing_2", 2.5, 4.5, config.getDataFilterParameterValue(1, defaultSmooth));
    PeakFit.addRelativeParameterOptions(gd,
        new RelativeParameterProvider(2.5, 4.5, "Smoothing_2", provider) {
          @Override
          void setAbsolute(boolean absolute) {
            final FitEngineConfiguration c =
                fitEngineConfigurationProvider.getFitEngineConfiguration();
            final DataFilterMethod m = c.getDataFilterMethod(1, defaultDataFilterMethod);
            final double smooth = c.getDataFilterParameterValue(1, defaultSmooth);
            c.setDataFilter(m, smooth, absolute, 1);
          }

          @Override
          boolean isAbsolute() {
            return fitEngineConfigurationProvider.getFitEngineConfiguration()
                .getDataFilterParameterAbsolute(1, false);
          }

          @Override
          double getValue() {
            return fitEngineConfigurationProvider.getFitEngineConfiguration()
                .getDataFilterParameterValue(1, defaultSmooth);
          }
        });

    PeakFit.addSearchOptions(gd, provider);
    PeakFit.addBorderOptions(gd, provider);
    // gd.addNumericField("Top_N", topN, 0);
    gd.addSlider("Top_N", 0, 100, topN);
    topNScrollBar = gd.getLastScrollbar();
    gd.addSlider("Select", 0, 100, select);
    selectScrollBar = gd.getLastScrollbar();
    gd.addSlider("Neigbour_radius", 0, 10, neighbourRadius);

    // Find if this image was created with ground truth data
    if (imp.getID() == CreateData.getImageId()) {
      final MemoryPeakResults results = CreateData.getResults();
      if (results != null) {
        gd.addSlider("Match_distance", 0, 2.5, distance);
        gd.addSlider("Lower_match_distance (%)", 0, 100, lowerDistance);
        gd.addCheckbox("Multiple_matches", multipleMatches);
        gd.addCheckbox("Show_TP", showTP);
        gd.addCheckbox("Show_FP", showFP);
        gd.addMessage("");
        label = (Label) gd.getMessage();
        final boolean integerCoords = false;
        actualCoordinates = ResultsMatchCalculator.getCoordinates(results, integerCoords);
      }
    }

    if (ImageJUtils.isShowGenericDialog()) {
      // Listen for changes in the dialog options
      gd.addOptionCollectedListener(this);
      // Listen for changes to an image
      ImagePlus.addImageListener(this);

      // Support template settings
      final Vector<TextField> numerics = gd.getNumericFields();
      final Vector<Choice> choices = gd.getChoices();

      int n = 0;
      int ch = 0;

      final Choice textTemplate = choices.get(ch++);
      textTemplate.removeItemListener(gd);
      textTemplate.removeKeyListener(gd);
      textTemplate.addItemListener(this);

      textCameraModelName = choices.get(ch++);
      textPSF = choices.get(ch++);
      textDataFilterType = choices.get(ch++);
      textDataFilterMethod = choices.get(ch++);
      textSmooth = numerics.get(n++);
      textDataFilterMethod2 = choices.get(ch++);
      textSmooth2 = numerics.get(n++);
      textSearch = numerics.get(n++);
      textBorder = numerics.get(n++);
    }

    gd.addPreviewCheckbox(pfr);
    gd.addDialogListener(this);
    gd.setOKLabel("Save");
    gd.setCancelLabel("Close");
    gd.showDialog();

    if (!(IJ.isMacro() || java.awt.GraphicsEnvironment.isHeadless())) {
      ImagePlus.removeImageListener(this);
    }

    if (!gd.wasCanceled()) {
      if (!SettingsManager.writeSettings(config, SettingsManager.FLAG_SILENT)) {
        IJ.error(TITLE, "Failed to save settings");
      }
    }

    // Reset
    imp.setOverlay(o);

    return DONE;
  }

  /** {@inheritDoc} */
  @Override
  public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
    if (refreshing) {
      return false;
    }

    gd.getNextChoice(); // Ignore template

    // Set a camera model
    fitConfig.setCameraModelName(gd.getNextChoice());
    // CameraModel model = CameraModelManager.load(fitConfig.getCameraModelName());
    // if (model == null)
    // model = new FakePerPixelCameraModel(0, 1, 1);
    // fitConfig.setCameraModel(model);

    fitConfig.setPSFType(PeakFit.getPSFTypeValues()[gd.getNextChoiceIndex()]);

    config.setDataFilterType(gd.getNextChoiceIndex());
    config.setDataFilter(gd.getNextChoiceIndex(), Math.abs(gd.getNextNumber()), 0);
    config.setDataFilter(gd.getNextChoiceIndex(), Math.abs(gd.getNextNumber()), 1);
    config.setSearch(gd.getNextNumber());
    config.setBorder(gd.getNextNumber());
    topN = (int) gd.getNextNumber();
    select = (int) gd.getNextNumber();
    neighbourRadius = (int) gd.getNextNumber();

    if (label != null) {
      distance = gd.getNextNumber();
      lowerDistance = gd.getNextNumber();
      multipleMatches = gd.getNextBoolean();
      showTP = gd.getNextBoolean();
      showFP = gd.getNextBoolean();
    }
    preview = gd.getNextBoolean();

    ((ExtendedGenericDialog) gd).collectOptions();

    final boolean result = !gd.invalidNumber();
    if (!preview) {
      setLabel("");
      this.imp.setOverlay(o);
    }
    // For astigmatism PSF.
    // TODO - See if this is slowing the preview down. If so only do if the PSF type changes.
    if (!PeakFit.configurePSFModel(config, PeakFit.FLAG_NO_SAVE)) {
      return false;
    }
    return result;
  }

  private void setLabel(String message) {
    if (label == null) {
      return;
    }
    label.setText(message);
  }

  private Calibration lastCalibration = null;
  private FitEngineSettings lastFitEngineSettings = null;
  private PSF lastPSF = null;

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor ip) {
    if (refreshing) {
      return;
    }

    final Rectangle bounds = ip.getRoi();

    // Only do this if the settings changed
    final Calibration calibration = fitConfig.getCalibration();
    final FitEngineSettings fitEngineSettings = config.getFitEngineSettings();
    final PSF psf = fitConfig.getPSF();

    boolean newCameraModel = filter == null;
    if (!calibration.equals(lastCalibration)) {
      newCameraModel = true;
      // Set a camera model.
      // We have to set the camera type too to avoid configuration errors.
      CameraModel cameraModel = CameraModelManager.load(fitConfig.getCameraModelName());
      if (cameraModel == null) {
        cameraModel = new FakePerPixelCameraModel(0, 1, 1);
        fitConfig.setCameraType(CameraType.EMCCD);
      } else {
        fitConfig.setCameraType(CameraType.SCMOS);

        // Support cropped origin selection.
        final Rectangle sourceBounds = IJImageSource.getBounds(imp);
        cameraModel = PeakFit.cropCameraModel(cameraModel, sourceBounds, null, true);
        if (cameraModel == null) {
          gd.getPreviewCheckbox().setState(false);
          return;
        }
      }
      fitConfig.setCameraModel(cameraModel);
    }

    if (newCameraModel || !fitEngineSettings.equals(lastFitEngineSettings)
        || !psf.equals(lastPSF)) {
      // Configure a jury filter
      if (config.getDataFilterType() == DataFilterType.JURY) {
        if (!PeakFit.configureDataFilter(config, PeakFit.FLAG_NO_SAVE)) {
          gd.getPreviewCheckbox().setState(false);
          return;
        }
      }

      try {
        filter = config.createSpotFilter();
      } catch (final Exception ex) {
        filter = null;
        this.imp.setOverlay(o);
        throw new RuntimeException(ex); // Required for ImageJ to disable the preview
        // Utils.log("ERROR: " + ex.getMessage());
        // return;
      }
      ImageJUtils.log(filter.getDescription());
    }

    lastCalibration = calibration;
    lastFitEngineSettings = fitEngineSettings;
    lastPSF = psf;

    // This code can probably be removed since the crop is done above.
    if (fitConfig.getCameraTypeValue() == CameraType.SCMOS_VALUE) {
      // Instead just warn if the roi cannot be extracted from the selected model
      // or there is a mismatch
      final Rectangle modelBounds = fitConfig.getCameraModel().getBounds();
      if (modelBounds != null) {
        if (!modelBounds.contains(bounds)) {
          //@formatter:off
                ImageJUtils.log("WARNING: Camera model bounds [x=%d,y=%d,width=%d,height=%d] does not contain image target bounds [x=%d,y=%d,width=%d,height=%d]",
                    modelBounds.x, modelBounds.y, modelBounds.width, modelBounds.height,
                    bounds.x, bounds.y, bounds.width, bounds.height
                    );
                //@formatter:on
        } else
        // Warn if the model bounds are mismatched than the image as this may be an incorrect
        // selection for the camera model
        if (modelBounds.x != 0 || modelBounds.y != 0 || modelBounds.width > ip.getWidth()
            || modelBounds.height > ip.getHeight()) {
          //@formatter:off
                ImageJUtils.log("WARNING: Probably an incorrect camera model!\nModel bounds [x=%d,y=%d,width=%d,height=%d]\ndo not match the image target bounds [width=%d,height=%d].",
                    modelBounds.x, modelBounds.y, modelBounds.width, modelBounds.height,
                    ip.getWidth(),  ip.getHeight()
                    );
                //@formatter:on
        }
      }
    }

    run(ip, filter);
  }

  private void run(ImageProcessor ip, MaximaSpotFilter filter) {
    if (refreshing) {
      return;
    }

    currentSlice = imp.getCurrentSlice();

    final Rectangle bounds = ip.getRoi();

    // Crop to the ROI
    FloatProcessor fp = ip.crop().toFloat(0, null);

    float[] data = (float[]) fp.getPixels();

    final int width = fp.getWidth();
    final int height = fp.getHeight();

    // Store the mean bias and gain of the region data.
    // This is used to correctly overlay the filtered data on the original image.
    double bias = 0;
    double gain = 1;
    boolean adjust = false;

    // Set weights
    final CameraModel cameraModel = fitConfig.getCameraModel();
    if (!(cameraModel instanceof FakePerPixelCameraModel)) {
      // This should be done on the normalised data
      final float[] w = cameraModel.getNormalisedWeights(bounds);
      filter.setWeights(w, width, height);
      data = data.clone();
      if (data.length < ip.getPixelCount()) {
        adjust = true;
        bias = MathUtils.sum(cameraModel.getBias(bounds)) / data.length;
        gain = MathUtils.sum(cameraModel.getGain(bounds)) / data.length;
      }
      cameraModel.removeBiasAndGain(bounds, data);
    }

    final Spot[] spots = filter.rank(data, width, height);
    data = filter.getPreprocessedData();

    final int size = spots.length;
    topNScrollBar.setMaximum(size);
    selectScrollBar.setMaximum(size);

    fp = new FloatProcessor(width, height, data);
    final FloatProcessor out = new FloatProcessor(ip.getWidth(), ip.getHeight());
    out.copyBits(ip, 0, 0, Blitter.COPY);
    if (adjust) {
      fp.multiply(gain);
      fp.add(bias);
    }
    out.insert(fp, bounds.x, bounds.y);
    // ip.resetMinAndMax();
    final double min = fp.getMin();
    final double max = fp.getMax();
    out.setMinAndMax(min, max);

    final Overlay o = new Overlay();
    o.add(new ImageRoi(0, 0, out));

    if (label != null) {
      // Get results for frame
      final Coordinate[] actual =
          ResultsMatchCalculator.getCoordinates(actualCoordinates, imp.getCurrentSlice());

      final Coordinate[] predicted = new Coordinate[size];
      for (int i = 0; i < size; i++) {
        predicted[i] = new BasePoint(spots[i].x + bounds.x, spots[i].y + bounds.y);
      }

      // Compute assignments
      final TurboList<FractionalAssignment> fractionalAssignments =
          new TurboList<>(3 * predicted.length);
      final double matchDistance = distance * fitConfig.getInitialPeakStdDev();
      final RampedScore score = new RampedScore(matchDistance * lowerDistance / 100, matchDistance);
      final double dmin = matchDistance * matchDistance;
      final int nActual = actual.length;
      final int nPredicted = predicted.length;
      for (int j = 0; j < nPredicted; j++) {
        // Centre in the middle of the pixel
        final float x = predicted[j].getX() + 0.5f;
        final float y = predicted[j].getY() + 0.5f;
        // Any spots that match
        for (int i = 0; i < nActual; i++) {
          final double dx = (x - actual[i].getX());
          final double dy = (y - actual[i].getY());
          final double d2 = dx * dx + dy * dy;
          if (d2 <= dmin) {
            final double d = Math.sqrt(d2);
            final double s = score.score(d);

            if (s == 0) {
              continue;
            }

            double distance = 1 - s;
            if (distance == 0) {
              // In the case of a match below the distance thresholds
              // the distance will be 0. To distinguish between candidates all below
              // the thresholds just take the closest.
              // We know d2 is below dmin so we subtract the delta.
              distance -= (dmin - d2);
            }

            // Store the match
            fractionalAssignments.add(new ImmutableFractionalAssignment(i, j, distance, s));
          }
        }
      }

      final FractionalAssignment[] assignments =
          fractionalAssignments.toArray(new FractionalAssignment[fractionalAssignments.size()]);

      // Compute matches
      final RankedScoreCalculator calc =
          RankedScoreCalculator.create(assignments, nActual - 1, nPredicted - 1);
      final boolean save = showTP || showFP;
      final double[] calcScore = calc.score(nPredicted, multipleMatches, save);
      final ClassificationResult result =
          RankedScoreCalculator.toClassificationResult(calcScore, nActual);

      // Compute AUC and max jaccard (and plot)
      final double[][] curve =
          RankedScoreCalculator.getPrecisionRecallCurve(assignments, nActual, nPredicted);
      final double[] precision = curve[0];
      final double[] recall = curve[1];
      final double[] jaccard = curve[2];
      final double auc = AucCalculator.auc(precision, recall);

      // Show scores
      final String label = String.format("Slice=%d, AUC=%s, R=%s, Max J=%s", imp.getCurrentSlice(),
          MathUtils.rounded(auc), MathUtils.rounded(result.getRecall()),
          MathUtils.rounded(MathUtils.maxDefault(0, jaccard)));
      setLabel(label);

      // Plot
      String title = TITLE + " Performance";
      Plot2 plot = new Plot2(title, "Spot Rank", "");
      final double[] rank = SimpleArrayUtils.newArray(precision.length, 0, 1.0);
      plot.setLimits(0, nPredicted, 0, 1.05);
      plot.setColor(Color.blue);
      plot.addPoints(rank, precision, Plot.LINE);
      plot.setColor(Color.red);
      plot.addPoints(rank, recall, Plot.LINE);
      plot.setColor(Color.black);
      plot.addPoints(rank, jaccard, Plot.LINE);
      plot.setColor(Color.black);
      plot.addLabel(0, 0, label);

      final WindowOrganiser windowOrganiser = new WindowOrganiser();
      ImageJUtils.display(title, plot, 0, windowOrganiser);

      title = TITLE + " Precision-Recall";
      plot = new Plot2(title, "Recall", "Precision");
      plot.setLimits(0, 1, 0, 1.05);
      plot.setColor(Color.red);
      plot.addPoints(recall, precision, Plot.LINE);
      plot.drawLine(recall[recall.length - 1], precision[recall.length - 1],
          recall[recall.length - 1], 0);
      plot.setColor(Color.black);
      plot.addLabel(0, 0, label);
      ImageJUtils.display(title, plot, 0, windowOrganiser);

      windowOrganiser.tile();

      // Create Rois for TP and FP
      if (save) {
        final double[] matchScore =
            RankedScoreCalculator.getMatchScore(calc.getScoredAssignments(), nPredicted);
        int matches = 0;
        for (int i = 0; i < matchScore.length; i++) {
          if (matchScore[i] != 0) {
            matches++;
          }
        }
        if (showTP) {
          final float[] x = new float[matches];
          final float[] y = new float[x.length];
          int n = 0;
          for (int i = 0; i < matchScore.length; i++) {
            if (matchScore[i] != 0) {
              final BasePoint p = (BasePoint) predicted[i];
              x[n] = p.getX() + 0.5f;
              y[n] = p.getY() + 0.5f;
              n++;
            }
          }
          addRoi(0, o, x, y, n, Color.green);
        }
        if (showFP) {
          final float[] x = new float[nPredicted - matches];
          final float[] y = new float[x.length];
          int n = 0;
          for (int i = 0; i < matchScore.length; i++) {
            if (matchScore[i] == 0) {
              final BasePoint p = (BasePoint) predicted[i];
              x[n] = p.getX() + 0.5f;
              y[n] = p.getY() + 0.5f;
              n++;
            }
          }
          addRoi(0, o, x, y, n, Color.red);
        }
      }
    } else {
      // float[] x = new float[size];
      // float[] y = new float[x.length];
      // for (int i = 0; i < size; i++)
      // {
      // x[i] = spots[i].x + bounds.x + 0.5f;
      // y[i] = spots[i].y + bounds.y + 0.5f;
      // }
      // PointRoi roi = new PointRoi(x, y);
      //// Add options to configure colour and labels
      // o.add(roi);

      final WindowOrganiser wo = new WindowOrganiser();

      // Option to show the number of neighbours within a set pixel box radius
      final int[] count = spotFilterHelper.countNeighbours(spots, width, height, neighbourRadius);

      // Show as histogram the totals...
      new HistogramPlotBuilder(TITLE, StoredData.create(count), "Neighbours").setIntegerBins(true)
          .setPlotLabel("Radius = " + neighbourRadius).show(wo);

      // TODO - Draw n=0, n=1 on the image overlay

      final LUT lut = LutHelper.createLut(LutColour.FIRE_LIGHT);
      // These are copied by the ROI
      final float[] x = new float[1];
      final float[] y = new float[1];
      // Plot the intensity
      final double[] intensity = new double[size];
      final double[] rank = SimpleArrayUtils.newArray(size, 1, 1.0);
      final int top = (topN > 0) ? topN : size;
      final int size_1 = size - 1;
      for (int i = 0; i < size; i++) {
        intensity[i] = spots[i].intensity;
        if (i < top) {
          x[0] = spots[i].x + bounds.x + 0.5f;
          y[0] = spots[i].y + bounds.y + 0.5f;
          final Color c = LutHelper.getColour(lut, size_1 - i, size);
          addRoi(0, o, x, y, 1, c, 2, 1);
        }
      }

      final String title = TITLE + " Intensity";
      final Plot plot = new Plot(title, "Rank", "Intensity");
      plot.setColor(Color.blue);
      plot.addPoints(rank, intensity, Plot.LINE);
      if (topN > 0 && topN < size) {
        plot.setColor(Color.magenta);
        plot.drawLine(topN, 0, topN, intensity[topN - 1]);
      }
      if (select > 0 && select < size) {
        plot.setColor(Color.yellow);
        final double in = intensity[select - 1];
        plot.drawLine(select, 0, select, in);
        x[0] = spots[select].x + bounds.x + 0.5f;
        y[0] = spots[select].y + bounds.y + 0.5f;
        final Color c = LutHelper.getColour(lut, size_1 - select, size);
        addRoi(0, o, x, y, 1, c, 3, 3);
        plot.setColor(Color.black);
        plot.addLabel(0, 0, "Selected spot intensity = " + MathUtils.rounded(in));
      }

      ImageJUtils.display(title, plot, 0, wo);

      wo.tile();
    }

    imp.setOverlay(o);
  }

  /**
   * Adds the roi to the overlay as a circle.
   *
   * @param frame the frame
   * @param o the overlay
   * @param x the x coordinates
   * @param y the y coordinates
   * @param n the number of points
   * @param colour the colour
   * @see PointRoi#setPosition(int)
   * @see PointRoi#setFillColor(Color)
   * @see PointRoi#setStrokeColor(Color)
   */
  public static void addRoi(int frame, Overlay o, float[] x, float[] y, int n, Color colour) {
    // Add as a circle
    addRoi(frame, o, x, y, n, colour, 3);
  }

  /**
   * Adds the roi to the overlay.
   *
   * @param frame the frame
   * @param o the overlay
   * @param x the x coordinates
   * @param y the y coordinates
   * @param n the number of points
   * @param colour the colour
   * @param pointType the point type
   * @see PointRoi#setPosition(int)
   * @see PointRoi#setPointType(int)
   * @see PointRoi#setFillColor(Color)
   * @see PointRoi#setStrokeColor(Color)
   */
  public static void addRoi(int frame, Overlay o, float[] x, float[] y, int n, Color colour,
      int pointType) {
    addRoi(frame, o, x, y, n, colour, pointType, 0);
  }

  /**
   * Adds the roi to the overlay.
   *
   * @param frame the frame
   * @param o the overlay
   * @param x the x coordinates
   * @param y the y coordinates
   * @param n the number of points
   * @param colour the colour
   * @param pointType the point type
   * @param size the size
   * @see PointRoi#setPosition(int)
   * @see PointRoi#setPointType(int)
   * @see PointRoi#setFillColor(Color)
   * @see PointRoi#setStrokeColor(Color)
   * @see PointRoi#setSize(int)
   */
  public static void addRoi(int frame, Overlay o, float[] x, float[] y, int n, Color colour,
      int pointType, int size) {
    if (n == 0) {
      return;
    }
    final PointRoi roi = new PointRoi(x, y, n);
    roi.setPointType(pointType);
    roi.setFillColor(colour);
    roi.setStrokeColor(colour);
    if (frame != 0) {
      roi.setPosition(frame);
    }
    if (size != 0) {
      roi.setSize(size);
    }
    o.add(roi);
  }

  /** {@inheritDoc} */
  @Override
  public void setNPasses(int nPasses) {
    // Nothing to do
  }

  @Override
  public void imageOpened(ImagePlus imp) {
    // Ignore
  }

  @Override
  public void imageClosed(ImagePlus imp) {
    // Ignore
  }

  @Override
  public void imageUpdated(ImagePlus imp) {
    if (this.imp.getID() == imp.getID() && preview) {
      if (imp.getCurrentSlice() != currentSlice && filter != null) {
        run(imp.getProcessor(), filter);
      }
    }
  }

  @Override
  public void itemStateChanged(ItemEvent e) {
    if (e.getSource() instanceof Choice) {
      // Update the settings from the template
      final Choice choice = (Choice) e.getSource();
      final String templateName = choice.getSelectedItem();
      // System.out.println("Update to " + templateName);

      // Get the configuration template
      final TemplateSettings template = ConfigurationTemplate.getTemplate(templateName);

      if (template != null) {
        refreshing = true;

        IJ.log("Applying template: " + templateName);

        for (final String note : template.getNotesList()) {
          IJ.log(note);
        }

        final boolean custom = ConfigurationTemplate.isCustomTemplate(templateName);
        if (template.hasPsf()) {
          refreshSettings(template.getPsf(), custom);
        }
        if (template.hasFitEngineSettings()) {
          refreshSettings(template.getFitEngineSettings(), custom);
        }

        refreshing = false;
        // dialogItemChanged(gd, null);
      }
    }
  }

  private void refreshSettings(PSF psf, boolean isCustomTemplate) {
    if (!isCustomTemplate || psf == null) {
      return;
    }

    // Do not use set() as we support merging a partial PSF
    fitConfig.mergePSF(psf);

    textPSF.select(PSFProtosHelper.getName(fitConfig.getPSFType()));
  }

  /**
   * Refresh settings. <p> If this is a custom template then use all the settings. If a default
   * template then leave some existing spot settings untouched as the user may have updated them
   * (e.g. PSF width).
   *
   * @param fitEngineSettings the config
   * @param isCustomTemplate True if a custom template.
   */
  private void refreshSettings(FitEngineSettings fitEngineSettings, boolean isCustomTemplate) {
    // Set the configuration
    // This will clear everything and merge the configuration so
    // remove the fit settings (as we do not care about those).

    this.config.setFitEngineSettings(fitEngineSettings.toBuilder().clearFitSettings().build());
    fitConfig = this.config.getFitConfiguration();

    textCameraModelName.select(fitConfig.getCameraModelName());
    textDataFilterType
        .select(SettingsManager.getDataFilterTypeNames()[config.getDataFilterType().ordinal()]);
    textDataFilterMethod.select(
        SettingsManager.getDataFilterMethodNames()[config.getDataFilterMethod(0).ordinal()]);
    textSmooth.setText("" + config.getDataFilterParameterValue(0));
    if (config.getDataFiltersCount() > 1) {
      textDataFilterMethod2.select(SettingsManager.getDataFilterMethodNames()[config
          .getDataFilterMethod(1, defaultDataFilterMethod).ordinal()]);
      textSmooth2.setText("" + config.getDataFilterParameterValue(1, defaultSmooth));
      // XXX - What about the Abolute/Relative flag?
    }
    textSearch.setText("" + config.getSearch());
    textBorder.setText("" + config.getBorder());
  }

  /** {@inheritDoc} */
  @Override
  public FitConfiguration getFitConfiguration() {
    return fitConfig;
  }

  /** {@inheritDoc} */
  @Override
  public void optionCollected(OptionCollectedEvent e) {
    // Just run on the current processor
    if (preview) {
      run(imp.getProcessor());
    }
  }
}
