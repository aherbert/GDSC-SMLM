package gdsc.smlm.ij.plugins;

/*----------------------------------------------------------------------------- 
 * GDSC SMLM Software
 * 
 * Copyright (C) 2016 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.apache.commons.math3.analysis.integration.TrapezoidIntegrator;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.commons.math3.util.FastMath;

import gdsc.core.ij.Utils;
import gdsc.core.match.BasePoint;
import gdsc.core.match.Coordinate;
import gdsc.core.match.MatchCalculator;
import gdsc.core.match.PointPair;
import gdsc.core.utils.ImageExtractor;
import gdsc.core.utils.Maths;
import gdsc.core.utils.NoiseEstimator.Method;
import gdsc.core.utils.StoredDataStatistics;
import gdsc.smlm.engine.DataFilter;
import gdsc.smlm.engine.DataFilterType;
import gdsc.smlm.engine.FitEngineConfiguration;
import gdsc.smlm.engine.FitWorker;
import gdsc.smlm.engine.QuadrantAnalysis;
import gdsc.smlm.filters.MaximaSpotFilter;
import gdsc.smlm.filters.Spot;
import gdsc.smlm.fitting.FitConfiguration;
import gdsc.smlm.fitting.FitFunction;
import gdsc.smlm.fitting.FitResult;
import gdsc.smlm.fitting.FitSolver;
import gdsc.smlm.fitting.Gaussian2DFitter;
import gdsc.smlm.function.gaussian.Gaussian2DFunction;
import gdsc.smlm.ij.settings.GlobalSettings;
import gdsc.smlm.ij.settings.SettingsManager;
import gdsc.smlm.ij.utils.ImageConverter;
import gdsc.smlm.results.Calibration;
import gdsc.smlm.results.MemoryPeakResults;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.Plot2;
import ij.gui.PlotWindow;
import ij.gui.PointRoi;
import ij.plugin.PlugIn;
import ij.plugin.WindowOrganiser;
import ij.text.TextWindow;

/**
 * Fits spots created by CreateData plugin.
 * <p>
 * Assigns results to filter candidates to determine if spots are either single or doublets or larger clusters. Outputs
 * a table of the single and double fit for each spot with metrics. This can be used to determine the best settings for
 * optimum doublet fitting and filtering.
 */
public class DoubletAnalysis implements PlugIn
{
	private static final String TITLE = "Doublet Analysis";
	static FitConfiguration fitConfig;
	private static FitEngineConfiguration config;
	private static Calibration cal;
	private static int lastId = 0;
	static
	{
		cal = new Calibration();
		fitConfig = new FitConfiguration();
		config = new FitEngineConfiguration(fitConfig);
		// Set some default fit settings here ...
		// Ensure all candidates are fitted
		config.setFailuresLimit(-1);
		fitConfig.setFitValidation(true);
		fitConfig.setMinPhotons(0); // Do not allow negative photons 
		fitConfig.setCoordinateShiftFactor(0); // Disable
		fitConfig.setPrecisionThreshold(0);
		fitConfig.setWidthFactor(0);

		fitConfig.setBackgroundFitting(true);
		fitConfig.setMinIterations(0);
		fitConfig.setNoise(0);
		config.setNoiseMethod(Method.QUICK_RESIDUALS_LEAST_MEAN_OF_SQUARES);

		fitConfig.setBackgroundFitting(true);
		fitConfig.setNotSignalFitting(false);
		fitConfig.setComputeDeviations(false);
		fitConfig.setComputeResiduals(true);
	}

	private static boolean showOverlay = false;
	private static boolean showHistograms = false;
	private static boolean showResults = true;
	private static boolean analysisShowResults = false;
	private static boolean showJaccardPlot = true;
	private static double dThreshold = 1;

	private static TextWindow summaryTable = null, resultsTable = null, analysisTable = null;
	private static ArrayList<DoubletResult> doubletResults;
	private static double[] _residuals, _jaccard;
	private static int _maxJaccardIndex, numberOfMolecules;
	private static String analysisPrefix;
	private ImagePlus imp;
	private MemoryPeakResults results;
	private CreateData.SimulationParameters simulationParameters;

	private static final String[] NAMES = new String[] { "Candidate:N results in candidate",
			"Assigned Result:N results in assigned spot", "Singles:Neighbours", "Doublets:Neighbours",
			"Multiples:Neighbours", "Singles:Almost", "Doublets:Almost", "Multiples:Almost"

	};
	private static final String[] NAMES2 = { "Score n=1", "Score n=2", "Score n=N", "Iter n=1", "Eval n=1", "Iter n>1",
			"Eval n>1" };

	private static boolean[] displayHistograms = new boolean[NAMES.length + NAMES2.length];
	static
	{
		for (int i = 0; i < displayHistograms.length; i++)
			displayHistograms[i] = true;
	}

	private WindowOrganiser windowOrganiser = new WindowOrganiser();

	/**
	 * Allows plotting the bonus from fitting all spots at a given residuals threshold
	 */
	public class DoubletBonus implements Comparable<DoubletBonus>
	{
		final double r;
		final int tp, fp;

		/**
		 * Instantiates a new doublet bonus.
		 *
		 * @param r
		 *            the r
		 * @param tp
		 *            the additional true positives if this was accepted as a doublet
		 * @param fp
		 *            the additional false positives if this was accepted as a doublet
		 */
		public DoubletBonus(double r, int tp, int fp)
		{
			this.r = r;
			this.tp = tp;
			this.fp = fp;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(DoubletBonus that)
		{
			if (this.r < that.r)
				return -1;
			if (this.r > that.r)
				return 1;
			return 0;
		}
	}

	private class ResultCoordinate extends BasePoint
	{
		final DoubletResult result;

		public ResultCoordinate(DoubletResult result, double x, double y)
		{
			// Add the 0.5 pixel offset
			super((float) (x + 0.5), (float) (y + 0.5));
			this.result = result;
		}
	}

	/**
	 * Stores results from single and doublet fitting.
	 */
	public class DoubletResult implements Comparable<DoubletResult>
	{
		final int frame;
		final Spot spot;
		final int n, c, neighbours, almostNeighbours;
		FitResult fitResult1 = null;
		FitResult fitResult2 = null;
		double sumOfSquares1, sumOfSquares2;
		double r1, r2;
		double value1, value2;
		double score1, score2;
		double aic1, aic2, bic1, bic2;
		double maic1, maic2, mbic1, mbic2;
		double[] xshift = new double[2];
		double[] yshift = new double[2];
		double[] a = new double[2];
		double gap;
		int iter1, iter2, eval1, eval2;
		boolean good1, good2, valid, valid2;
		int tp1, fp1, tp2, fp2;

		public DoubletResult(int frame, Spot spot, int n, int neighbours, int almostNeighbours)
		{
			this.frame = frame;
			this.spot = spot;
			this.n = n;
			this.c = DoubletAnalysis.getClass(n);
			this.neighbours = neighbours;
			this.almostNeighbours = almostNeighbours;
			this.tp1 = 0;
			this.fp1 = 1;
			this.tp2 = 0;
			this.fp2 = 2;
		}

		public void addTP1()
		{
			tp1++;
			fp1--;
		}

		public void addTP2()
		{
			tp2++;
			fp2--;
		}

		public double getMaxScore()
		{
			return (score1 > score2) ? score1 : score2;
		}

		public double getAvScore()
		{
			return (score1 + score2) * 0.5;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(DoubletResult that)
		{
			// Makes the resutls easy to find in the table
			int r = this.frame - that.frame;
			if (r != 0)
				return r;
			r = this.spot.x - that.spot.x;
			if (r != 0)
				return r;
			return this.spot.y - that.spot.y;
			//if (this.spot.intensity > that.spot.intensity)
			//	return -1;
			//if (this.spot.intensity < that.spot.intensity)
			//	return 1;
			//return 0;
		}
	}

	/**
	 * Used to allow multi-threading of the fitting method.
	 */
	private class Worker implements Runnable
	{
		volatile boolean finished = false;
		final BlockingQueue<Integer> jobs;
		final ImageStack stack;
		final HashMap<Integer, ArrayList<Coordinate>> actualCoordinates;
		final int fitting;
		final FitConfiguration fitConfig;
		final MaximaSpotFilter spotFilter;
		final Gaussian2DFitter gf;
		final boolean relativeIntensity;
		final double limit;
		final int[] spotHistogram, resultHistogram;
		final int[][] neighbourHistogram;
		final int[][] almostNeighbourHistogram;
		final Overlay o;
		double[] region = null;
		float[] data = null;
		ArrayList<DoubletResult> results = new ArrayList<DoubletResult>();
		int daic = 0, dbic = 0, cic = 0;

		/**
		 * Instantiates a new worker.
		 *
		 * @param jobs
		 *            the jobs
		 * @param stack
		 *            the stack
		 * @param actualCoordinates
		 *            the actual coordinates
		 * @param fitConfig
		 *            the fit config
		 * @param maxCount
		 *            the max count
		 * @param o
		 *            the o
		 */
		public Worker(BlockingQueue<Integer> jobs, ImageStack stack,
				HashMap<Integer, ArrayList<Coordinate>> actualCoordinates, FitConfiguration fitConfig, int maxCount,
				Overlay o)
		{
			this.jobs = jobs;
			this.stack = stack;
			this.actualCoordinates = actualCoordinates;
			this.fitConfig = fitConfig.clone();
			this.gf = new Gaussian2DFitter(this.fitConfig);
			gf.setMaximumWidthFactor(20);
			this.spotFilter = config.createSpotFilter(true);
			this.relativeIntensity = !spotFilter.isAbsoluteIntensity();

			fitting = config.getRelativeFitting();
			// Fit window is 2*fitting+1. The distance limit is thus 0.5 pixel higher than fitting. 
			limit = fitting + 0.5;
			spotHistogram = new int[maxCount + 1];
			resultHistogram = new int[spotHistogram.length];
			neighbourHistogram = new int[3][spotHistogram.length];
			almostNeighbourHistogram = new int[3][spotHistogram.length];
			this.o = o;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Runnable#run()
		 */
		public void run()
		{
			try
			{
				while (!finished)
				{
					Integer job = jobs.take();
					if (job == null || job.intValue() < 0 || finished)
						break;
					run(job.intValue());
				}
			}
			catch (InterruptedException e)
			{
				System.out.println(e.toString());
				//throw new RuntimeException(e);
			}
			finally
			{
				finished = true;
			}
		}

		private void run(int frame)
		{
			if (Utils.isInterrupted())
			{
				finished = true;
				return;
			}

			showProgress();

			Coordinate[] actual = ResultsMatchCalculator.getCoordinates(actualCoordinates, frame);

			// Extract the data
			final int maxx = stack.getWidth();
			final int maxy = stack.getHeight();
			data = ImageConverter.getData(stack.getPixels(frame), maxx, maxy, null, data);

			// Smooth the image and identify spots with a filter
			Spot[] spots = spotFilter.rank(data, maxx, maxy);

			// Match the each actual result to the closest filter candidate.
			// The match must be within the fit window used during fitting, i.e. could the actual
			// result be fit using this candidate.
			int[] matches = new int[actual.length];
			for (int i = 0; i < actual.length; i++)
			{
				double dmin = Double.POSITIVE_INFINITY;
				int match = -1;
				// Get the coordinates, offset to allow for 0.5 to be the centre of the pixel
				double x = actual[i].getX() - 0.5;
				double y = actual[i].getY() - 0.5;
				for (int j = 0; j < spots.length; j++)
				{
					double dx = Math.abs(x - spots[j].x);
					double dy = Math.abs(y - spots[j].y);
					if (dx < limit && dy < limit)
					{
						final double d2 = dx * dx + dy * dy;
						if (dmin > d2)
						{
							dmin = d2;
							match = j;
						}
					}
				}
				matches[i] = match;
			}

			ImageExtractor ie = null;
			float estimatedBackground = 0;

			// Identify single and doublets (and other)
			int singles = 0, doublets = 0, multiples = 0, total = 0;
			int[] spotMatchCount = new int[spots.length];
			int[] neighbourIndices = new int[spots.length];
			for (int i = 0; i < actual.length; i++)
			{
				if (matches[i] != -1)
				{
					// Count all matches
					int n = 0;
					final int j = matches[i];
					for (int ii = i; ii < matches.length; ii++)
					{
						if (matches[ii] == j)
						{
							n++;
							// Reset to avoid double counting
							matches[ii] = -1;
						}
					}
					switch (n)
					{
						//@formatter:off
						case 1: singles++; break;
						case 2: doublets++; break;
						default: multiples++;
						//@formatter:on
					}
					// Store the number of actual results that match to a spot
					spotHistogram[n]++;
					// Store the number of results that match to a spot with n results
					resultHistogram[n] += n;
					total += n;
					spotMatchCount[j] = n;

					// Initialise for fitting on first match 
					if (ie == null)
					{
						ie = new ImageExtractor(data, maxx, maxy);
						estimatedBackground = estimateBackground(maxx, maxy);
					}

					final Spot spot = spots[j];
					final Rectangle regionBounds = ie.getBoxRegionBounds(spot.x, spot.y, fitting);

					// Count the number of candidates within the fitting window
					// that are potential neighbours,
					// i.e. will fit neighbours be used? 
					// It does not matter if the neighbours have a match to a result
					// or not, just that they are present for multiple peak fitting

					final int xmin = regionBounds.x;
					final int xmax = xmin + regionBounds.width - 1;
					final int ymin = regionBounds.y;
					final int ymax = ymin + regionBounds.height - 1;
					final int xmin2 = xmin - fitting;
					final int xmax2 = xmax + fitting;
					final int ymin2 = ymin - fitting;
					final int ymax2 = ymax + fitting;

					final float heightThreshold;
					float background = estimatedBackground;

					if (spot.intensity < background)
						heightThreshold = spot.intensity;
					else
						heightThreshold = (float) ((spot.intensity - background) *
								config.getNeighbourHeightThreshold() + background);

					int neighbourCount = 0;
					int almostNeighbourCount = 0;
					for (int jj = 0; jj < spots.length; jj++)
					{
						if (j == jj)
							continue;
						if (spots[jj].x < xmin2 || spots[jj].x > xmax2 || spots[jj].y < ymin2 || spots[jj].y > ymax2)
							continue;
						if (spots[jj].x < xmin || spots[jj].x > xmax || spots[jj].y < ymin || spots[jj].y > ymax ||
								spots[jj].intensity < heightThreshold)
						{
							almostNeighbourCount++;
						}
						else
						{
							neighbourIndices[neighbourCount++] = jj;
						}
					}
					final int c = DoubletAnalysis.getClass(spotMatchCount[j]);
					neighbourHistogram[c][neighbourCount]++;
					almostNeighbourHistogram[c][almostNeighbourCount]++;

					// TODO - Fit with neighbours?

					// Currently this will only explore how to benchmark the fitting of medium
					// density data with singles and a few doublets with no neighbours. This
					// is fine for low density PALM data but not for high density STORM data.

					// Fit the candidates (as per the FitWorker logic)
					// (Fit even multiple since this is what the FitWorker will do) 
					region = ie.crop(regionBounds, region);

					boolean amplitudeEstimate = false;
					float signal = 0;
					double sum = 0;
					final int width = regionBounds.width;
					final int height = regionBounds.height;
					final int size = width * height;
					for (int k = size; k-- > 0;)
						sum += region[k];
					signal = (float) (sum - background * size);
					if (signal <= 0)
					{
						amplitudeEstimate = true;
						signal = spot.intensity - ((relativeIntensity) ? 0 : background);
						if (signal < 0)
						{
							signal += background;
							background = 0;
						}
					}

					final double[] params = new double[] { background, signal, 0, spot.x - regionBounds.x,
							spot.y - regionBounds.y, 0, 0 };

					final DoubletResult result = new DoubletResult(frame, spot, n, neighbourCount,
							almostNeighbourCount);
					result.fitResult1 = gf.fit(region, width, height, 1, params, amplitudeEstimate);
					result.iter1 = gf.getIterations();
					result.eval1 = gf.getEvaluations();

					// For now only process downstream if the fit was reasonable. This allows a good attempt at doublet fitting.
					result.good1 = goodFit(result.fitResult1, width, height) == 2;

					if (result.good1)
					{
						result.sumOfSquares1 = gf.getFinalResidualSumOfSquares();
						result.value1 = gf.getValue();

						// Compute residuals and fit as a doublet
						final double[] fitParams = result.fitResult1.getParameters();
						final int cx = (int) Math.round(fitParams[Gaussian2DFunction.X_POSITION]);
						final int cy = (int) Math.round(fitParams[Gaussian2DFunction.Y_POSITION]);
						final double[] residuals = gf.getResiduals();

						QuadrantAnalysis qa = new QuadrantAnalysis();

						// TODO - Also perform quadrant analysis on a new region centred around 
						// the fit centre...

						if (qa.quadrantAnalysis(residuals, width, height, cx, cy) && qa.computeDoubletCentres(width,
								height, cx, cy, fitParams[Gaussian2DFunction.X_SD], fitParams[Gaussian2DFunction.Y_SD]))
						{
							result.score1 = qa.score1;
							result.score2 = qa.score2;

							// -+-+-
							// Estimate params using the single fitted peak
							// -+-+-
							final double[] doubletParams = new double[1 + 2 * 6];

							doubletParams[Gaussian2DFunction.BACKGROUND] = fitParams[Gaussian2DFunction.BACKGROUND];
							doubletParams[Gaussian2DFunction.SIGNAL] = fitParams[Gaussian2DFunction.SIGNAL] * 0.5;
							doubletParams[Gaussian2DFunction.X_POSITION] = (float) (qa.x1 - 0.5);
							doubletParams[Gaussian2DFunction.Y_POSITION] = (float) (qa.y1 - 0.5);
							doubletParams[6 + Gaussian2DFunction.SIGNAL] = params[Gaussian2DFunction.SIGNAL] * 0.5;
							doubletParams[6 + Gaussian2DFunction.X_POSITION] = (float) (qa.x2 - 0.5);
							doubletParams[6 + Gaussian2DFunction.Y_POSITION] = (float) (qa.y2 - 0.5);
							// -+-+-

							// Increase the iterations level then reset afterwards.
							final int maxIterations = fitConfig.getMaxIterations();
							final int maxEvaluations = fitConfig.getMaxFunctionEvaluations();
							fitConfig.setMaxIterations(maxIterations * FitWorker.ITERATION_INCREASE_FOR_DOUBLETS * 2);
							fitConfig.setMaxFunctionEvaluations(
									maxEvaluations * FitWorker.EVALUATION_INCREASE_FOR_DOUBLETS * 2);
							gf.setComputeResiduals(false);
							result.fitResult2 = gf.fit(region, width, height, 2, doubletParams, false);
							gf.setComputeResiduals(true);
							fitConfig.setMaxIterations(maxIterations);
							fitConfig.setMaxFunctionEvaluations(maxEvaluations);
							result.iter2 = gf.getIterations();
							result.eval2 = gf.getEvaluations();
							int r2 = goodFit2(result.fitResult2, width, height);

							// Store all results if we made a fit, even if the fit was not good
							if (r2 != 0)
							{
								result.good2 = r2 == 2;
								result.sumOfSquares2 = gf.getFinalResidualSumOfSquares();
								result.value2 = gf.getValue();

								final int length = width * height;
								result.aic1 = Maths.getAkaikeInformationCriterionFromResiduals(result.sumOfSquares1,
										length, result.fitResult1.getNumberOfFittedParameters());
								result.aic2 = Maths.getAkaikeInformationCriterionFromResiduals(result.sumOfSquares2,
										length, result.fitResult2.getNumberOfFittedParameters());
								result.bic1 = Maths.getBayesianInformationCriterionFromResiduals(result.sumOfSquares1,
										length, result.fitResult1.getNumberOfFittedParameters());
								result.bic2 = Maths.getBayesianInformationCriterionFromResiduals(result.sumOfSquares2,
										length, result.fitResult2.getNumberOfFittedParameters());
								if (fitConfig.getFitSolver() == FitSolver.MLE)
								{
									result.maic1 = Maths.getAkaikeInformationCriterion(result.value1, length,
											result.fitResult1.getNumberOfFittedParameters());
									result.maic2 = Maths.getAkaikeInformationCriterion(result.value2, length,
											result.fitResult2.getNumberOfFittedParameters());
									result.mbic1 = Maths.getBayesianInformationCriterion(result.value1, length,
											result.fitResult1.getNumberOfFittedParameters());
									result.mbic2 = Maths.getBayesianInformationCriterion(result.value2, length,
											result.fitResult2.getNumberOfFittedParameters());

									// XXX - Debugging: see if the IC computed from the residuals would make a different choice
									// Disable by setting to 1
									if (result.getMaxScore() > 1)
									{
										cic++;
										if (Math.signum(result.aic1 - result.aic2) != Math
												.signum(result.maic1 - result.maic2))
										{
											daic++;
											System.out.printf(
													"AIC difference with residuals [%d] %d,%d : %d  %f vs %f (%.2f)\n",
													frame, spot.x, spot.y, n, Math.signum(result.aic1 - result.aic2),
													Math.signum(result.maic1 - result.maic2), result.getMaxScore());
										}
										if (Math.signum(result.bic1 - result.bic2) != Math
												.signum(result.mbic1 - result.mbic2))
										{
											dbic++;
											System.out.printf(
													"BIC difference with residuals [%d] %d,%d : %d  %f vs %f (%.2f)\n",
													frame, spot.x, spot.y, n, Math.signum(result.bic1 - result.bic2),
													Math.signum(result.mbic1 - result.mbic2), result.getMaxScore());
										}
										if (Double.isInfinite(result.value1) || Double.isInfinite(result.value2))
											System.out.printf("oops\n", result.value1, result.value2);
									}
								}
								else
								{
									result.maic1 = result.aic1;
									result.maic2 = result.aic2;
									result.mbic1 = result.bic1;
									result.mbic2 = result.bic2;
								}
								result.r1 = Maths.getAdjustedCoefficientOfDetermination(result.sumOfSquares1,
										gf.getTotalSumOfSquares(), length,
										result.fitResult1.getNumberOfFittedParameters());
								result.r2 = Maths.getAdjustedCoefficientOfDetermination(result.sumOfSquares2,
										gf.getTotalSumOfSquares(), length,
										result.fitResult2.getNumberOfFittedParameters());

								// Debugging: see if the AIC or BIC ever differ								
								//if (Math.signum(result.aic1 - result.aic2) != Math.signum(result.bic1 - result.bic2))
								//	System.out.printf("BIC difference [%d] %d,%d : %d  %f vs %f (%.2f)\n", frame,
								//			spot.x, spot.y, n, Math.signum(result.aic1 - result.aic2),
								//			Math.signum(result.bic1 - result.bic2), result.getMaxScore());

								final double[] newParams = result.fitResult2.getParameters();
								for (int p = 0; p < 2; p++)
								{
									final double xShift = newParams[Gaussian2DFunction.X_POSITION + p * 6] -
											params[Gaussian2DFunction.X_POSITION];
									final double yShift = newParams[Gaussian2DFunction.Y_POSITION + p * 6] -
											params[Gaussian2DFunction.Y_POSITION];
									result.a[p] = 57.29577951 *
											QuadrantAnalysis.getAngle(qa.vector, new double[] { xShift, yShift });
									result.xshift[p] = xShift / limit;
									result.yshift[p] = yShift / limit;
								}

								// Store the distance between the spots
								final double dx = newParams[Gaussian2DFunction.X_POSITION] -
										newParams[Gaussian2DFunction.X_POSITION + 6];
								final double dy = newParams[Gaussian2DFunction.Y_POSITION] -
										newParams[Gaussian2DFunction.Y_POSITION + 6];
								result.gap = Math.sqrt(dx * dx + dy * dy);
							}
						}
					}

					// True results, i.e. where there was a choice between selecting fit results of single or doublet
					if (result.good1 && result.good2)
					{
						if (result.neighbours == 0)
						{
							result.valid = true;
							if (result.almostNeighbours == 0)
								result.valid2 = true;
						}
					}

					results.add(result);
				}
			}
			//System.out.printf("Frame %d, singles=%d, doublets=%d, multi=%d\n", frame, singles, doublets, multiples);
			resultHistogram[0] += actual.length - total;

			addToOverlay(frame, spots, singles, doublets, multiples, spotMatchCount);

			// At the end of all the fitting, assign results as true or false positive.
			ArrayList<Coordinate> f1 = new ArrayList<Coordinate>();
			ArrayList<Coordinate> f2 = new ArrayList<Coordinate>();
			for (DoubletResult result : results)
			{
				if (result.good1)
				{
					final Rectangle regionBounds = ie.getBoxRegionBounds(result.spot.x, result.spot.y, fitting);
					double x = result.fitResult1.getParameters()[Gaussian2DFunction.X_POSITION] + regionBounds.x;
					double y = result.fitResult1.getParameters()[Gaussian2DFunction.Y_POSITION] + regionBounds.y;
					f1.add(new ResultCoordinate(result, x, y));
					if (result.good2)
					{
						x = result.fitResult2.getParameters()[Gaussian2DFunction.X_POSITION] + regionBounds.x;
						y = result.fitResult2.getParameters()[Gaussian2DFunction.Y_POSITION] + regionBounds.y;
						f2.add(new ResultCoordinate(result, x, y));
						x = result.fitResult2.getParameters()[Gaussian2DFunction.X_POSITION + 6] + regionBounds.x;
						y = result.fitResult2.getParameters()[Gaussian2DFunction.Y_POSITION + 6] + regionBounds.y;
						f2.add(new ResultCoordinate(result, x, y));
					}
				}
			}
			List<PointPair> pairs = new ArrayList<PointPair>();
			MatchCalculator.analyseResults2D(actual, f1.toArray(new Coordinate[f1.size()]), dThreshold, null, null,
					null, pairs);
			for (PointPair pair : pairs)
			{
				ResultCoordinate coord = (ResultCoordinate) pair.getPoint2();
				coord.result.addTP1();
			}
			MatchCalculator.analyseResults2D(actual, f2.toArray(new Coordinate[f2.size()]), dThreshold, null, null,
					null, pairs);
			for (PointPair pair : pairs)
			{
				ResultCoordinate coord = (ResultCoordinate) pair.getPoint2();
				coord.result.addTP2();
			}
		}

		private int goodFit(FitResult fitResult, final int width, final int height)
		{
			if (fitResult == null)
				return 0;
			final double[] params = fitResult.getParameters();
			if (params == null)
				return 0;
			switch (fitResult.getStatus())
			{
				case OK:
					// The following happen when we are doing validation, which we are not
				case WIDTH_DIVERGED:
				case INSUFFICIENT_SIGNAL:
				case INSUFFICIENT_PRECISION:
				case COORDINATES_MOVED:
					break;

				default:
					return 0;
			}

			// Check if centre is within the region
			final double border = FastMath.min(width, height) / 4.0;
			if ((params[Gaussian2DFunction.X_POSITION] < border ||
					params[Gaussian2DFunction.X_POSITION] > width - border) ||
					params[Gaussian2DFunction.Y_POSITION] < border ||
					params[Gaussian2DFunction.Y_POSITION] > height - border)
				return 1;

			// Check the width is reasonable
			final double regionSize = FastMath.max(width, height) * 0.5;
			if (params[Gaussian2DFunction.X_SD] < 0 || params[Gaussian2DFunction.X_SD] > regionSize ||
					params[Gaussian2DFunction.Y_SD] < 0 || params[Gaussian2DFunction.Y_SD] > regionSize)
				return 1;
			return 2;
		}

		private int goodFit2(FitResult fitResult, final int width, final int height)
		{
			if (fitResult == null)
				return 0;
			final double[] params = fitResult.getParameters();
			if (params == null)
				return 0;
			switch (fitResult.getStatus())
			{
				case OK:
					// The following happen when we are doing validation, which we are not
				case WIDTH_DIVERGED:
				case INSUFFICIENT_SIGNAL:
				case INSUFFICIENT_PRECISION:
				case COORDINATES_MOVED:
					break;

				default:
					return 0;
			}

			final double regionSize = FastMath.max(width, height) * 0.5;
			for (int n = 0; n < 2; n++)
			{
				// Check the width is reasonable
				if (params[n * 6 + Gaussian2DFunction.X_SD] < 0 ||
						params[n * 6 + Gaussian2DFunction.X_SD] > regionSize ||
						params[n * 6 + Gaussian2DFunction.Y_SD] < 0 ||
						params[n * 6 + Gaussian2DFunction.Y_SD] > regionSize)
					return 1;

				// Check if centre is within the region - Border allowing fit slightly outside
				final double borderx = Gaussian2DFunction.SD_TO_HWHM_FACTOR * fitConfig.getInitialPeakStdDev0();
				final double bordery = Gaussian2DFunction.SD_TO_HWHM_FACTOR * fitConfig.getInitialPeakStdDev1();
				if ((params[n * 6 + Gaussian2DFunction.X_POSITION] < -borderx ||
						params[n * 6 + Gaussian2DFunction.X_POSITION] > width + borderx) ||
						params[n * 6 + Gaussian2DFunction.Y_POSITION] < -bordery ||
						params[n * 6 + Gaussian2DFunction.Y_POSITION] > height + bordery)
				{
					// Perhaps do a check on the quadrant?					
					return 1;
				}
			}

			return 2;
		}

		/**
		 * Get an estimate of the background level using the mean of image.
		 *
		 * @param width
		 *            the width
		 * @param height
		 *            the height
		 * @return the float
		 */
		private float estimateBackground(int width, int height)
		{
			// Compute average of the entire image
			double sum = 0;
			for (int i = width * height; i-- > 0;)
				sum += data[i];
			return (float) sum / (width * height);
		}

		/**
		 * Adds the to overlay.
		 *
		 * @param frame
		 *            the frame
		 * @param spots
		 *            the spots
		 * @param singles
		 *            the singles
		 * @param doublets
		 *            the doublets
		 * @param multiples
		 *            the multiples
		 * @param spotMatchCount
		 *            the spot match count
		 */
		private void addToOverlay(int frame, Spot[] spots, int singles, int doublets, int multiples,
				int[] spotMatchCount)
		{
			if (o != null)
			{
				// Create an output stack with coloured ROI overlay for each n=1, n=2, n=other
				// to check that the doublets are correctly identified.
				final int[] sx = new int[singles];
				final int[] sy = new int[singles];
				final int[] dx = new int[doublets];
				final int[] dy = new int[doublets];
				final int[] mx = new int[multiples];
				final int[] my = new int[multiples];
				final int[] count = new int[3];
				final int[][] coords = new int[][] { sx, dx, mx, sy, dy, my };
				final Color[] color = new Color[] { Color.red, Color.green, Color.blue };
				for (int j = 0; j < spotMatchCount.length; j++)
				{
					final int c = DoubletAnalysis.getClass(spotMatchCount[j]);
					if (c < 0)
						continue;
					coords[c][count[c]] = spots[j].x;
					coords[c + 3][count[c]] = spots[j].y;
					count[c]++;
				}
				for (int c = 0; c < 3; c++)
				{
					final PointRoi roi = new PointRoi(coords[c], coords[c + 3], count[c]);
					roi.setPosition(frame);
					roi.setHideLabels(true);
					roi.setFillColor(color[c]);
					roi.setStrokeColor(color[c]);
					// Overlay uses a vector which is synchronized already
					o.add(roi);
				}
			}
		}
	}

	/**
	 * Gets the class.
	 *
	 * @param n
	 *            the number of results that match to the spot
	 * @return the class (none = -1, single = 0, double = 1, multiple = 2)
	 */
	private static int getClass(int n)
	{
		if (n == 0)
			return -1;
		if (n == 1)
			return 0;
		if (n == 2)
			return 1;
		return 2;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(String arg)
	{
		SMLMUsageTracker.recordPlugin(this.getClass(), arg);

		if ("analysis".equals(arg))
		{
			runAnalysis();
		}
		else
		{
			simulationParameters = CreateData.simulationParameters;
			if (simulationParameters == null)
			{
				IJ.error(TITLE, "No simulation parameters in memory");
				return;
			}
			imp = CreateData.getImage();
			if (imp == null)
			{
				IJ.error(TITLE, "No simulation image");
				return;
			}
			results = MemoryPeakResults.getResults(CreateData.CREATE_DATA_IMAGE_TITLE + " (Create Data)");
			if (results == null)
			{
				IJ.error(TITLE, "No simulation results in memory");
				return;
			}

			if (!showDialog())
				return;

			run();
		}
	}

	/**
	 * Show dialog.
	 *
	 * @return true, if successful
	 */
	private boolean showDialog()
	{
		GenericDialog gd = new GenericDialog(TITLE);
		gd.addHelp(About.HELP_URL);

		final double sa = getSa();
		gd.addMessage(
				String.format("Fits the benchmark image created by CreateData plugin.\nPSF width = %s, adjusted = %s",
						Utils.rounded(simulationParameters.s / simulationParameters.a), Utils.rounded(sa)));

		// For each new benchmark width, reset the PSF width to the square pixel adjustment
		if (lastId != simulationParameters.id)
		{
			double w = sa;
			dThreshold = w * Gaussian2DFunction.SD_TO_HWHM_FACTOR;
			fitConfig.setInitialPeakStdDev(w);
		}

		// Collect options for fitting
		gd.addNumericField("Initial_StdDev", fitConfig.getInitialPeakStdDev0(), 3);
		String[] filterTypes = SettingsManager.getNames((Object[]) DataFilterType.values());
		gd.addChoice("Spot_filter_type", filterTypes, filterTypes[config.getDataFilterType().ordinal()]);
		String[] filterNames = SettingsManager.getNames((Object[]) DataFilter.values());
		gd.addChoice("Spot_filter", filterNames, filterNames[config.getDataFilter(0).ordinal()]);
		gd.addSlider("Smoothing", 0, 2.5, config.getSmooth(0));
		gd.addSlider("Search_width", 0.5, 2.5, config.getSearch());
		gd.addSlider("Border", 0.5, 2.5, config.getBorder());
		gd.addSlider("Fitting_width", 2, 4.5, config.getFitting());
		String[] solverNames = SettingsManager.getNames((Object[]) FitSolver.values());
		gd.addChoice("Fit_solver", solverNames, solverNames[fitConfig.getFitSolver().ordinal()]);
		String[] functionNames = SettingsManager.getNames((Object[]) FitFunction.values());
		gd.addChoice("Fit_function", functionNames, functionNames[fitConfig.getFitFunction().ordinal()]);

		gd.addCheckbox("Show_overlay", showOverlay);
		gd.addCheckbox("Show_histograms", showHistograms);
		gd.addCheckbox("Show_results", showResults);
		gd.addCheckbox("Show_Jaccard_Plot", showJaccardPlot);
		gd.addNumericField("Match_distance", dThreshold, 2);

		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		fitConfig.setInitialPeakStdDev(gd.getNextNumber());
		config.setDataFilterType(gd.getNextChoiceIndex());
		config.setDataFilter(gd.getNextChoiceIndex(), Math.abs(gd.getNextNumber()), 0);
		config.setSearch(gd.getNextNumber());
		config.setBorder(gd.getNextNumber());
		config.setFitting(gd.getNextNumber());
		fitConfig.setFitSolver(gd.getNextChoiceIndex());
		fitConfig.setFitFunction(gd.getNextChoiceIndex());

		showOverlay = gd.getNextBoolean();
		showHistograms = gd.getNextBoolean();
		showResults = gd.getNextBoolean();
		showJaccardPlot = gd.getNextBoolean();
		dThreshold = gd.getNextNumber();

		if (gd.invalidNumber())
			return false;
		GlobalSettings settings = new GlobalSettings();
		settings.setFitEngineConfiguration(config);
		settings.setCalibration(cal);

		// Copy simulation defaults if a new simulation
		if (lastId != simulationParameters.id)
		{
			cal.nmPerPixel = simulationParameters.a;
			cal.gain = simulationParameters.gain;
			cal.amplification = simulationParameters.amplification;
			cal.exposureTime = 100;
			cal.readNoise = simulationParameters.readNoise;
			cal.bias = simulationParameters.bias;
			cal.emCCD = simulationParameters.emCCD;
		}
		if (!PeakFit.configureFitSolver(settings, null, false))
			return false;

		lastId = simulationParameters.id;

		if (showHistograms)
		{
			gd = new GenericDialog(TITLE);
			gd.addMessage("Select the histograms to display");

			for (int i = 0; i < NAMES.length; i++)
				gd.addCheckbox(NAMES[i].replace(' ', '_'), displayHistograms[i]);
			for (int i = 0; i < NAMES2.length; i++)
				gd.addCheckbox(NAMES2[i].replace(' ', '_'), displayHistograms[i + NAMES.length]);
			gd.showDialog();
			if (gd.wasCanceled())
				return false;
			for (int i = 0; i < displayHistograms.length; i++)
				displayHistograms[i] = gd.getNextBoolean();
		}

		return true;
	}

	/**
	 * Gets the sa.
	 *
	 * @return the sa
	 */
	private double getSa()
	{
		final double sa = PSFCalculator.squarePixelAdjustment(simulationParameters.s, simulationParameters.a) /
				simulationParameters.a;
		return sa;
	}

	/** The total progress. */
	int progress, stepProgress, totalProgress;

	/**
	 * Show progress.
	 */
	private synchronized void showProgress()
	{
		if (++progress % stepProgress == 0)
		{
			if (Utils.showStatus("Frame: " + progress + " / " + totalProgress))
				IJ.showProgress(progress, totalProgress);
		}
	}

	/**
	 * Run.
	 */
	private void run()
	{
		doubletResults = null;

		final ImageStack stack = imp.getImageStack();

		// Get the coordinates per frame
		HashMap<Integer, ArrayList<Coordinate>> actualCoordinates = ResultsMatchCalculator
				.getCoordinates(results.getResults(), false);

		int maxCount = 0;
		for (ArrayList<Coordinate> list : actualCoordinates.values())
			if (maxCount < list.size())
				maxCount = list.size();

		// Create a pool of workers
		final int nThreads = Prefs.getThreads();
		BlockingQueue<Integer> jobs = new ArrayBlockingQueue<Integer>(nThreads * 2);
		List<Worker> workers = new LinkedList<Worker>();
		List<Thread> threads = new LinkedList<Thread>();
		Overlay overlay = (showOverlay) ? new Overlay() : null;
		for (int i = 0; i < nThreads; i++)
		{
			Worker worker = new Worker(jobs, stack, actualCoordinates, fitConfig, maxCount, overlay);
			Thread t = new Thread(worker);
			workers.add(worker);
			threads.add(t);
			t.start();
		}

		// Fit the frames
		totalProgress = actualCoordinates.size();
		stepProgress = Utils.getProgressInterval(totalProgress);
		progress = 0;
		for (int frame : actualCoordinates.keySet())
		{
			put(jobs, frame);
		}

		// Finish all the worker threads by passing in a null job
		for (int i = 0; i < threads.size(); i++)
		{
			put(jobs, -1);
		}

		// Wait for all to finish
		for (int i = 0; i < threads.size(); i++)
		{
			try
			{
				threads.get(i).join();
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		threads.clear();
		threads = null;

		IJ.showProgress(1);
		IJ.showStatus("Collecting results ...");

		// Collect the results
		int cic = 0, daic = 0, dbic = 0;
		ArrayList<DoubletResult> results = null;
		for (Worker worker : workers)
		{
			if (results == null)
				results = worker.results;
			else
				results.addAll(worker.results);
			cic += worker.cic;
			daic += worker.daic;
			dbic += worker.dbic;
		}
		if (cic > 0)
			System.out.printf("Difference AIC %d, BIC %d, Total %d\n", daic, dbic, cic);
		if (showHistograms)
		{
			double[] spotHistogram = new double[maxCount];
			double[] resultHistogram = new double[maxCount];
			double[][] neighbourHistogram = new double[3][maxCount];
			double[][] almostNeighbourHistogram = new double[3][maxCount];
			for (Worker worker : workers)
			{
				final int[] h1 = worker.spotHistogram;
				final int[] h2 = worker.resultHistogram;
				final int[][] h3 = worker.neighbourHistogram;
				final int[][] h4 = worker.almostNeighbourHistogram;
				for (int j = 0; j < spotHistogram.length; j++)
				{
					spotHistogram[j] += h1[j];
					resultHistogram[j] += h2[j];
					for (int k = 0; k < 3; k++)
					{
						neighbourHistogram[k][j] += h3[k][j];
						almostNeighbourHistogram[k][j] += h4[k][j];
					}
				}
			}

			showHistogram(0, spotHistogram);
			showHistogram(1, resultHistogram);
			showHistogram(2, neighbourHistogram[0]);
			showHistogram(3, neighbourHistogram[1]);
			showHistogram(4, neighbourHistogram[2]);
			showHistogram(5, almostNeighbourHistogram[0]);
			showHistogram(6, almostNeighbourHistogram[1]);
			showHistogram(7, almostNeighbourHistogram[2]);
		}
		workers.clear();
		workers = null;

		if (overlay != null)
			imp.setOverlay(overlay);

		MemoryPeakResults.freeMemory();

		Collections.sort(results);
		summariseResults(results);

		windowOrganiser.tile();

		IJ.showStatus("");
	}

	/**
	 * Show histogram.
	 *
	 * @param i
	 *            the i
	 * @param spotHistogram
	 *            the spot histogram
	 */
	private void showHistogram(int i, double[] spotHistogram)
	{
		if (!displayHistograms[i])
			return;
		String[] labels = NAMES[i].split(":");
		Plot2 plot = new Plot2(labels[0], labels[1], "Count");
		double max = Maths.max(spotHistogram);
		plot.setLimits(0, spotHistogram.length, 0, max * 1.05);
		plot.addPoints(Utils.newArray(spotHistogram.length, 0, 1.0), spotHistogram, Plot2.BAR);
		PlotWindow pw = Utils.display(labels[0], plot);
		if (Utils.isNewWindow())
			windowOrganiser.add(pw.getImagePlus().getID());
	}

	/**
	 * Put.
	 *
	 * @param jobs
	 *            the jobs
	 * @param i
	 *            the i
	 */
	private void put(BlockingQueue<Integer> jobs, int i)
	{
		try
		{
			jobs.put(i);
		}
		catch (InterruptedException e)
		{
			throw new RuntimeException("Unexpected interruption", e);
		}
	}

	/**
	 * Summarise results.
	 *
	 * @param results
	 *            the results
	 */
	private void summariseResults(ArrayList<DoubletResult> results)
	{
		// Store results in memory for later analysis
		doubletResults = results;
		numberOfMolecules = this.results.size();

		// Store details we want in the analysis table
		StringBuilder sb = new StringBuilder();
		sb.append(Utils.rounded(getSa() * simulationParameters.a)).append("\t");
		sb.append(config.getRelativeFitting()).append("\t");
		sb.append(fitConfig.getFitFunction().toString());
		sb.append(":").append(PeakFit.getSolverName(fitConfig));
		if (fitConfig.getFitSolver() == FitSolver.MLE && fitConfig.isModelCamera())
		{
			sb.append(":Camera\t");

			// Add details of the noise model for the MLE
			sb.append("EM=").append(fitConfig.isEmCCD());
			sb.append(":A=").append(Utils.rounded(fitConfig.getAmplification()));
			sb.append(":N=").append(Utils.rounded(fitConfig.getReadNoise()));
			sb.append("\t");
		}
		else
			sb.append("\t\t");
		analysisPrefix = sb.toString();

		// -=-=-=-=-

		showResults(results, showResults);

		createSummaryTable();

		sb.setLength(0);

		final int n = countN(results);

		// Create the benchmark settings and the fitting settings
		sb.append(numberOfMolecules).append("\t");
		sb.append(n).append("\t");
		sb.append(Utils.rounded(simulationParameters.minSignal)).append("\t");
		sb.append(Utils.rounded(simulationParameters.maxSignal)).append("\t");
		sb.append(Utils.rounded(simulationParameters.signalPerFrame)).append("\t");
		sb.append(Utils.rounded(simulationParameters.s)).append("\t");
		sb.append(Utils.rounded(simulationParameters.a)).append("\t");
		sb.append(Utils.rounded(getSa() * simulationParameters.a)).append("\t");
		sb.append(Utils.rounded(simulationParameters.gain)).append("\t");
		sb.append(Utils.rounded(simulationParameters.readNoise)).append("\t");
		sb.append(Utils.rounded(simulationParameters.b)).append("\t");

		// Compute the noise
		double noise = Math
				.sqrt((simulationParameters.b * ((simulationParameters.emCCD) ? 2 : 1)) / simulationParameters.gain +
						simulationParameters.readNoise * simulationParameters.readNoise);
		sb.append(Utils.rounded(noise)).append("\t");
		sb.append(Utils.rounded(simulationParameters.signalPerFrame / noise)).append("\t");
		sb.append(config.getRelativeFitting()).append("\t");
		sb.append(fitConfig.getFitFunction().toString());
		sb.append(":").append(PeakFit.getSolverName(fitConfig));
		if (fitConfig.getFitSolver() == FitSolver.MLE && fitConfig.isModelCamera())
		{
			sb.append(":Camera\t");

			// Add details of the noise model for the MLE
			sb.append("EM=").append(fitConfig.isEmCCD());
			sb.append(":A=").append(Utils.rounded(fitConfig.getAmplification()));
			sb.append(":N=").append(Utils.rounded(fitConfig.getReadNoise()));
			sb.append("\t");
		}
		else
			sb.append("\t\t");

		// Now output the actual results ...

		// Show histograms as cumulative to avoid problems with bin width
		// Residuals scores 
		// Iterations and evaluations where fit was OK

		StoredDataStatistics[] stats = new StoredDataStatistics[NAMES2.length];
		for (int i = 0; i < stats.length; i++)
			stats[i] = new StoredDataStatistics();

		// For Jaccard scoring we need to count the score with no residuals threshold,
		// i.e. Accumulate the score accepting all doublets that were fit 
		int tp = 0;
		int fp = 0;
		ArrayList<DoubletBonus> data = new ArrayList<DoubletBonus>(results.size());
		for (DoubletResult result : results)
		{
			final double score = result.getMaxScore();

			// Filter the singles that would be accepted
			if (result.good1)
			{
				// Filter the doublets that would be accepted
				if (result.good2)
				{
					tp += result.tp2;
					fp += result.fp2;
					// Store this as a doublet bonus
					data.add(new DoubletBonus(score, result.tp2 - result.tp1, result.fp2 - result.fp1));
				}
				else
				{
					// No doublet fit so this will always be the single fit result
					tp += result.tp1;
					fp += result.fp1;
				}
			}

			// Build statistics
			final int c = result.c;

			// True results, i.e. where there was a choice between single or doublet
			if (result.valid)
			{
				stats[c].add(score);
			}

			// Of those where the fit was good, summarise the iterations and evalulations
			if (result.good1)
			{
				stats[3].add(result.iter1);
				stats[4].add(result.eval1);
				// Summarise only those which are a valid doublet. We do not really care
				// about the iteration increase for singles that are not doublets.
				if (c != 0 && result.good2)
				{
					stats[5].add(result.iter2);
					stats[6].add(result.eval2);
				}
			}
		}

		// Summarise score for true results
		Percentile p = new Percentile(99);
		for (int c = 0; c < stats.length; c++)
		{
			double[] values = stats[c].getValues();
			// Sorting is need for the percentile and the cumulative histogram so do it once 
			Arrays.sort(values);
			sb.append(Utils.rounded(stats[c].getMean())).append("+/-")
					.append(Utils.rounded(stats[c].getStandardDeviation())).append(" (").append(stats[c].getN())
					.append(") ").append(Utils.rounded(p.evaluate(values))).append('\t');

			if (showHistograms && displayHistograms[c + NAMES.length])
				showHistogram(values, NAMES2[c]);
		}

		// Plot a graph of the additional results we would fit at all score thresholds.
		// This assumes we just pick the the doublet if we fit it (NO FILTERING at all!)

		// Initialise the score for residuals 0
		computeJaccard(data, tp, fp, numberOfMolecules);

		// Store this as it serves as a baseline for the filtering analysis
		_jaccard = jaccard;
		_residuals = residuals;
		_maxJaccardIndex = maxJaccardIndex;

		if (showJaccardPlot)
			plotJaccard(residuals, jaccard, maxJaccardIndex, null, null, 0);

		addJaccardScores(sb);

		summaryTable.append(sb.toString());
	}

	/**
	 * Compute the total number of true results that all the candidates could have fit, including singles, doublets and
	 * multiples.
	 * 
	 * @param results
	 *            the doublet fitting results
	 * @return the total localisation results we could have fit
	 */
	private int countN(ArrayList<DoubletResult> results)
	{
		int n = 0;
		for (DoubletResult r : results)
			n += r.n;
		return n;
	}

	private double[] residuals, jaccard;
	private int maxJaccardIndex;

	/**
	 * Compute maximum jaccard for all the residuals thresholds.
	 *
	 * @param data
	 *            the data
	 * @param tp
	 *            the true positives at residuals = 0
	 * @param fp
	 *            the false positives at residuals = 0
	 * @param n
	 *            the number of true positives at residuals = 0
	 * @return the double[]
	 */
	private void computeJaccard(ArrayList<DoubletBonus> data, double tp, int fp, int n)
	{
		// Add data at ends to complete the residuals scale from 0 to 1
		data.add(new DoubletBonus(0, 0, 0));
		data.add(new DoubletBonus(1, 0, 0));
		Collections.sort(data);

		residuals = new double[data.size() + 2];
		jaccard = new double[residuals.length];
		maxJaccardIndex = 0;

		int count = 0;
		double last = 0;
		for (DoubletBonus b : data)
		{
			if (last != b.r)
			{
				residuals[count] = last;
				jaccard[count] = tp / (n + fp);
				if (jaccard[maxJaccardIndex] < jaccard[count])
					maxJaccardIndex = count;
				count++;
			}
			tp -= b.tp;
			fp -= b.fp;
			last = b.r;
		}
		residuals[count] = last;
		jaccard[count] = tp / (n + fp);
		if (jaccard[maxJaccardIndex] < jaccard[count])
			maxJaccardIndex = count;
		count++;
		residuals = Arrays.copyOf(residuals, count);
		jaccard = Arrays.copyOf(jaccard, count);
	}

	private void plotJaccard(double[] residuals, double[] jaccard, int maxJaccardIndex, double[] residuals2,
			double[] jaccard2, int maxJaccardIndex2)
	{
		String title = TITLE + " Jaccard";
		Plot plot = new Plot(title, "Score", "Jaccard");
		double max = Math.max(0.01, jaccard[maxJaccardIndex]);
		if (jaccard2 != null)
			max = Math.max(max, jaccard2[maxJaccardIndex2]);
		plot.setLimits(0, 1, 0, max * 1.05);
		plot.addPoints(residuals, jaccard, Plot.LINE);
		if (jaccard2 != null)
		{
			plot.setColor(Color.red);
			plot.addPoints(residuals2, jaccard2, Plot.LINE);
		}
		display(title, plot);
	}

	/**
	 * Show a cumulative histogram of the data.
	 *
	 * @param values
	 *            the values
	 * @param xTitle
	 *            The name of plotted statistic
	 */
	public void showHistogram(double[] values, String xTitle)
	{
		double[][] h = Maths.cumulativeHistogram(values, false);

		String title = TITLE + " " + xTitle + " Cumulative";
		Plot2 plot = new Plot2(title, xTitle, "Frequency");
		double xMax = h[0][h[0].length - 1];
		double yMax = h[1][h[1].length - 1];
		plot.setLimits(0, xMax, 0, 1.05 * yMax);
		plot.setColor(Color.blue);
		plot.addPoints(h[0], h[1], Plot2.BAR);
		display(title, plot);
	}

	private void display(String title, Plot plot)
	{
		PlotWindow pw = Utils.display(title, plot);
		if (Utils.isNewWindow())
			windowOrganiser.add(pw.getImagePlus().getID());
	}

	/**
	 * Creates the summary table.
	 */
	private void createSummaryTable()
	{
		if (summaryTable == null || !summaryTable.isVisible())
		{
			summaryTable = new TextWindow(TITLE + " Summary", createSummaryHeader(), "", 1000, 300);
			summaryTable.setVisible(true);
		}
	}

	/**
	 * Creates the summary header.
	 *
	 * @return the string
	 */
	private String createSummaryHeader()
	{
		StringBuilder sb = new StringBuilder();
		sb.append(
				"Molecules\tMatched\tminN\tmaxN\tN\ts (nm)\ta (nm)\tsa (nm)\tGain\tReadNoise (ADUs)\tB (photons)\t\tnoise (ADUs)\tSNR\tWidth\tMethod\tOptions\t");
		for (String name : NAMES2)
			sb.append(name).append('\t');
		sb.append("\tMax J\t@score\tArea 0.15\tArea 0.3\tArea");
		return sb.toString();
	}

	/**
	 * Show results.
	 *
	 * @param results
	 *            the results
	 */
	private void showResults(ArrayList<DoubletResult> results, boolean show)
	{
		if (!show)
			return;

		createResultsTable();

		ArrayList<String> list = new ArrayList<String>(results.size());
		int flush = 9;
		StringBuilder sb = new StringBuilder();
		for (DoubletResult result : results)
		{
			sb.append(result.frame).append('\t');
			sb.append(result.spot.x).append('\t');
			sb.append(result.spot.y).append('\t');
			sb.append(IJ.d2s(result.spot.intensity, 1)).append('\t');
			sb.append(result.n).append('\t');
			sb.append(result.neighbours).append('\t');
			sb.append(result.almostNeighbours).append('\t');
			sb.append(Utils.rounded(result.score1)).append('\t');
			sb.append(Utils.rounded(result.score2)).append('\t');
			add(sb, result.fitResult1);
			add(sb, result.fitResult2);
			sb.append(IJ.d2s(result.sumOfSquares1, 1)).append("\t");
			sb.append(IJ.d2s(result.sumOfSquares2, 1)).append("\t");
			sb.append(IJ.d2s(result.value1, 1)).append("\t");
			sb.append(IJ.d2s(result.value2, 1)).append("\t");
			sb.append(Utils.rounded(result.r1)).append("\t");
			sb.append(Utils.rounded(result.r2)).append("\t");
			sb.append(Utils.rounded(result.aic1)).append('\t');
			sb.append(Utils.rounded(result.aic2)).append('\t');
			sb.append(Utils.rounded(result.bic1)).append('\t');
			sb.append(Utils.rounded(result.bic2)).append('\t');
			sb.append(Utils.rounded(result.maic1)).append('\t');
			sb.append(Utils.rounded(result.maic2)).append('\t');
			sb.append(Utils.rounded(result.mbic1)).append('\t');
			sb.append(Utils.rounded(result.mbic2)).append('\t');
			sb.append(Utils.rounded(result.a[0])).append('\t');
			sb.append(Utils.rounded(result.a[1])).append('\t');
			sb.append(Utils.rounded(result.gap)).append('\t');
			sb.append(Utils.rounded(result.xshift[0])).append('\t');
			sb.append(Utils.rounded(result.yshift[0])).append('\t');
			sb.append(Utils.rounded(result.xshift[1])).append('\t');
			sb.append(Utils.rounded(result.yshift[1])).append('\t');
			sb.append(result.iter1).append('\t');
			sb.append(result.iter2).append('\t');
			sb.append(result.eval1).append('\t');
			sb.append(result.eval2).append('\t');
			addParams(sb, result.fitResult1);
			addParams(sb, result.fitResult2);

			list.add(sb.toString());
			sb.setLength(0);
			// Flush below 10 lines so ImageJ will layout the columns
			if (--flush == 0)
			{
				resultsTable.getTextPanel().append(list);
				list.clear();
			}
		}

		resultsTable.getTextPanel().append(list);
	}

	/**
	 * Adds the.
	 *
	 * @param sb
	 *            the sb
	 * @param fitResult
	 *            the fit result
	 */
	private void add(StringBuilder sb, FitResult fitResult)
	{
		if (fitResult != null)
		{
			sb.append(fitResult.getStatus()).append("\t");
		}
		else
		{
			sb.append("\t");
		}
	}

	/**
	 * Adds the params.
	 *
	 * @param sb
	 *            the sb
	 * @param fitResult
	 *            the fit result
	 */
	private void addParams(StringBuilder sb, FitResult fitResult)
	{
		if (fitResult != null)
		{
			sb.append(Arrays.toString(fitResult.getParameters())).append("\t");
		}
		else
		{
			sb.append("\t");
		}
	}

	/**
	 * Creates the results table.
	 */
	private void createResultsTable()
	{
		if (resultsTable == null || !resultsTable.isVisible())
		{
			resultsTable = new TextWindow(TITLE + " Results", createResultsHeader(), "", 1000, 300);
			resultsTable.setVisible(true);
		}
	}

	/**
	 * Creates the results header.
	 *
	 * @return the string
	 */
	private String createResultsHeader()
	{
		return "Frame\tx\ty\tI\tn\tneighbours\talmost\tscore1\tscore2\tR1\tR2\tss1\tss2\tv1\tv2\tr1\tr2\taic1\taic2\tbic1\tbic2\tmaic1\tmaic2\tmbic1\tmbic2\ta1\ta2\tgap\tx1\ty1\tx2\ty2\ti1\ti2\te1\te2\tparams1\tparams2";
	}

	/**
	 * Run analysis.
	 */
	private void runAnalysis()
	{
		if (doubletResults == null)
		{
			IJ.error(TITLE, "No doublet results in memory");
			return;
		}

		// Ask the user to set filters
		if (!showAnalysisDialog())
			return;

		showResults(doubletResults, analysisShowResults);

		// Store the effect of fitting as a doublet
		ArrayList<DoubletBonus> data = new ArrayList<DoubletBonus>(doubletResults.size());
		// True positive and False positives at residuals = 0
		int tp = 0;
		int fp = 0;

		// TODO - Get filters for the single and double fits

		// Process all the results
		for (DoubletResult result : doubletResults)
		{
			final double score = result.getMaxScore();

			// Filter the singles that would be accepted
			if (result.good1)
			{
				// Filter the doublets that would be accepted
				if (result.good2 && result.mbic2 < result.mbic1)
				{
					tp += result.tp2;
					fp += result.fp2;
					// Store this as a doublet bonus
					data.add(new DoubletBonus(score, result.tp2 - result.tp1, result.fp2 - result.fp1));
				}
				else
				{
					// No doublet fit so this will always be the single fit result
					tp += result.tp1;
					fp += result.fp1;
				}
			}
		}

		// Compute the max Jaccard
		computeJaccard(data, tp, fp, numberOfMolecules);

		if (showJaccardPlot)
			plotJaccard(_residuals, _jaccard, _maxJaccardIndex, residuals, jaccard, maxJaccardIndex);

		createAnalysisTable();

		StringBuilder sb = new StringBuilder(analysisPrefix);
		sb.append("Filter settings\t");

		addJaccardScores(sb);

		analysisTable.append(sb.toString());
	}

	private void addJaccardScores(StringBuilder sb)
	{
		sb.append(Utils.rounded(jaccard[maxJaccardIndex])).append('\t')
				.append(Utils.rounded(residuals[maxJaccardIndex]));
		sb.append('\t').append(Utils.rounded(getArea(residuals, jaccard, maxJaccardIndex, 0.15)));
		sb.append('\t').append(Utils.rounded(getArea(residuals, jaccard, maxJaccardIndex, 0.3)));
		sb.append('\t').append(Utils.rounded(getArea(residuals, jaccard, maxJaccardIndex, 1)));
	}

	private double getArea(double[] residuals, double[] jaccard, int maxJaccardIndex, double window)
	{
		double lower = Math.max(0, residuals[maxJaccardIndex] - window);
		double upper = Math.min(1, residuals[maxJaccardIndex] + window);
		LinearInterpolator li = new LinearInterpolator();
		PolynomialSplineFunction fun = li.interpolate(residuals, jaccard);
		TrapezoidIntegrator in = new TrapezoidIntegrator();
		double sum = 0;

		try
		{
			sum = in.integrate(1000, fun, lower, upper);
		}
		catch (TooManyEvaluationsException e)
		{
			int j = maxJaccardIndex;
			for (int i = j; i-- > 0;)
			{
				if (residuals[i] < lower)
				{
					lower = residuals[j];
					break;
				}
				sum += (jaccard[j] + jaccard[i]) * 0.5 * (residuals[j] - residuals[i]);
				j = i;
			}
			j = maxJaccardIndex;
			for (int i = j; ++i < jaccard.length;)
			{
				if (residuals[i] > upper)
				{
					upper = residuals[j];
					break;
				}
				sum += (jaccard[j] + jaccard[i]) * 0.5 * (residuals[i] - residuals[j]);
				j = i;
			}
		}
		return sum / (upper - lower);
	}

	/**
	 * Show dialog.
	 *
	 * @return true, if successful
	 */
	private boolean showAnalysisDialog()
	{
		GenericDialog gd = new GenericDialog(TITLE);
		gd.addHelp(About.HELP_URL);

		gd.addMessage("Filters the doublet fits and reports the performance increase");

		// Show the fitting settings that will effect filters, i.e. fit standard deviation, fit width

		// Collect options for filtering

		// Collect display options
		gd.addCheckbox("Show_results", analysisShowResults);
		gd.addCheckbox("Show_Jaccard_Plot", showJaccardPlot);

		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		analysisShowResults = gd.getNextBoolean();
		showJaccardPlot = gd.getNextBoolean();

		if (gd.invalidNumber())
			return false;

		return true;
	}

	/**
	 * Creates the analysis table.
	 */
	private void createAnalysisTable()
	{
		if (analysisTable == null || !analysisTable.isVisible())
		{
			analysisTable = new TextWindow(TITLE + " Analysis", createAnalysisHeader(), "", 1000, 300);
			analysisTable.setVisible(true);
		}
	}

	/**
	 * Creates the analysis header.
	 *
	 * @return the string
	 */
	private String createAnalysisHeader()
	{
		return "s\tWidth\tMethod\tOptions\tFilters...\tMax J\t@score\tArea 0.15\tArea 0.3\tArea";
	}
}
