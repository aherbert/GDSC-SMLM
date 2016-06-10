package gdsc.smlm.ij.plugins;

/*----------------------------------------------------------------------------- 
 * GDSC SMLM Software
 * 
 * Copyright (C) 2014 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import gdsc.smlm.engine.FitEngineConfiguration;
import gdsc.smlm.engine.FitParameters;
import gdsc.smlm.engine.FitWorker;
import gdsc.smlm.engine.ParameterisedFitJob;
import gdsc.smlm.filters.MaximaSpotFilter;
import gdsc.smlm.filters.Spot;
import gdsc.smlm.fitting.FitConfiguration;
import gdsc.smlm.fitting.FitFunction;
import gdsc.smlm.fitting.FitResult;
import gdsc.smlm.fitting.FitSolver;
import gdsc.smlm.fitting.FitStatus;
import gdsc.smlm.function.gaussian.Gaussian2DFunction;
import gdsc.smlm.ij.plugins.BenchmarkSpotFilter.FilterResult;
import gdsc.smlm.ij.plugins.BenchmarkSpotFilter.ScoredSpot;
import gdsc.smlm.ij.plugins.ResultsMatchCalculator.PeakResultPoint;
import gdsc.smlm.ij.settings.GlobalSettings;
import gdsc.smlm.ij.settings.SettingsManager;
import gdsc.smlm.ij.utils.ImageConverter;
import gdsc.core.ij.Utils;
import gdsc.core.match.BasePoint;
import gdsc.core.match.Coordinate;
import gdsc.core.match.FractionClassificationResult;
import gdsc.core.match.MatchCalculator;
import gdsc.core.match.PointPair;
import gdsc.smlm.results.Calibration;
import gdsc.smlm.results.MemoryPeakResults;
import gdsc.smlm.results.NullPeakResults;
import gdsc.smlm.results.PeakResult;
import gdsc.core.utils.RampedScore;
import gdsc.core.utils.StoredDataStatistics;
import gdsc.core.utils.NoiseEstimator.Method;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.plugin.WindowOrganiser;
import ij.text.TextWindow;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Fits all the candidate spots identified by the benchmark spot filter plugin.
 */
public class BenchmarkSpotFit implements PlugIn
{
	private static final String TITLE = "Fit Spot Data";

	static FitConfiguration fitConfig;
	private static FitEngineConfiguration config;
	private static Calibration cal;
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
	}

	private static double fractionPositives = 100;
	private static double fractionNegativesAfterAllPositives = 50;
	private static int negativesAfterAllPositives = 10;
	private static double distance = 1.5;
	private static double lowerDistance = 1.5;
	private static double matchSignal = 2;

	private boolean extraOptions = false;

	private static TextWindow summaryTable = null;

	private ImagePlus imp;
	private MemoryPeakResults results;
	private CreateData.SimulationParameters simulationParameters;
	private MaximaSpotFilter spotFilter;

	private static HashMap<Integer, ArrayList<Coordinate>> actualCoordinates = null;
	private static HashMap<Integer, FilterCandidates> filterCandidates;
	private static double nP, nN;

	static int lastId = -1, lastFilterId = -1;
	private static double lastFractionPositives = -1;
	private static double lastFractionNegativesAfterAllPositives = -1;
	private static int lastNegativesAfterAllPositives = -1;

	// Allow other plugins to access the results
	static int fitResultsId = 0;
	static HashMap<Integer, FilterCandidates> fitResults;
	static double distanceInPixels;
	static double lowerDistanceInPixels;
	static double signalFactor;
	static double candidateTN, candidateFN;

	public static String tablePrefix, resultPrefix;

	/**
	 * Store details of spot candidates that match actual spots
	 */
	public abstract class SpotMatch
	{
		/**
		 * The index for the spot candidate
		 */
		final int i;
		/**
		 * The distance to the spot
		 */
		final double d;
		/**
		 * The depth of the actual spot
		 */
		final double z;

		public SpotMatch(int i, double d, double z)
		{
			this.i = i;
			this.d = d;
			this.z = z;
		}

		/**
		 * @return True if the spot candidate was successfully fitted
		 */
		public abstract boolean isFitResult();

		/**
		 * Return a score for the difference between the fitted and actual signal. Zero is no difference. Negative is
		 * the fitted is below the actual. Positive means the fitted is above the actual.
		 * 
		 * @return The factor difference between the successfully fitted signal and the actual signal.
		 */
		public abstract double getSignalFactor();

		/**
		 * Return a score for the difference between the fitted and actual signal. Zero is no difference.
		 * Positive means the fitted is difference from the actual.
		 * 
		 * @return The factor difference between the successfully fitted signal and the actual signal.
		 */
		public double getAbsoluteSignalFactor()
		{
			return Math.abs(getSignalFactor());
		}
	}

	/**
	 * Store details of a fitted spot candidate that matches an actual spot
	 */
	public class FitMatch extends SpotMatch
	{
		final double sf;

		public FitMatch(int i, double d, double z, double rsf)
		{
			super(i, d, z);
			// The relative signal factor is 1 for a perfect fit, less than 1 for below and above 1 for above.
			// Reset the signal factor from 0
			this.sf = (rsf < 1) ? 1 - 1 / rsf : rsf - 1;
		}

		@Override
		public boolean isFitResult()
		{
			return true;
		}

		@Override
		public double getSignalFactor()
		{
			return sf;
		}
	}

	/**
	 * Store details of a spot candidate that matches an actual spot
	 */
	public class CandidateMatch extends SpotMatch
	{
		public CandidateMatch(int i, double d, double z)
		{
			super(i, d, z);
		}

		@Override
		public boolean isFitResult()
		{
			return false;
		}

		@Override
		public double getSignalFactor()
		{
			return 0;
		}
	}

	public class FilterCandidates implements Cloneable
	{
		final int p, n;
		final ScoredSpot[] spots;
		double tp, fp, tn, fn;
		FitResult[] fitResult;
		float noise;

		/** Store if the candidates can be fitted and match a position. Size is the number of scored spots */
		boolean[] fitMatch;
		/** Store the z-position of the actual spots for later analysis. Size is the number of actual spots */
		double[] zPosition;

		/**
		 * Store details about the actual spots that were matched by spot candidates or fitted spot candidates
		 */
		SpotMatch[] match;

		public FilterCandidates(int p, int n, ScoredSpot[] spots)
		{
			this.p = p;
			this.n = n;
			this.spots = spots;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#clone()
		 */
		public FilterCandidates clone()
		{
			try
			{
				return (FilterCandidates) super.clone();
			}
			catch (CloneNotSupportedException e)
			{
				return null;
			}
		}
	}

	/**
	 * Used to allow multi-threading of the fitting method
	 */
	private class Worker implements Runnable
	{
		volatile boolean finished = false;
		final BlockingQueue<Integer> jobs;
		final ImageStack stack;
		final FitWorker fitWorker;
		final HashMap<Integer, ArrayList<Coordinate>> actualCoordinates;
		final HashMap<Integer, FilterCandidates> filterCandidates;
		final HashMap<Integer, FilterCandidates> results;
		final Rectangle bounds;

		float[] data = null;
		List<PointPair> matches = new ArrayList<PointPair>();

		public Worker(BlockingQueue<Integer> jobs, ImageStack stack,
				HashMap<Integer, ArrayList<Coordinate>> actualCoordinates,
				HashMap<Integer, FilterCandidates> filterCandidates)
		{
			this.jobs = jobs;
			this.stack = stack;
			this.fitWorker = new FitWorker(config.clone(), new NullPeakResults(), null);

			final int fitting = config.getRelativeFitting();
			fitWorker.setSearchParameters(spotFilter, fitting);
			fitWorker.setUpdateInitialParameters(true);

			this.actualCoordinates = actualCoordinates;
			this.filterCandidates = filterCandidates;
			this.results = new HashMap<Integer, FilterCandidates>();
			bounds = new Rectangle(0, 0, stack.getWidth(), stack.getHeight());
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
				throw new RuntimeException(e);
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

			// Extract the data
			data = ImageConverter.getData(stack.getPixels(frame), stack.getWidth(), stack.getHeight(), null, data);

			FilterCandidates candidates = filterCandidates.get(frame);
			FitResult[] fitResult = new FitResult[candidates.spots.length];

			// Fit the candidates and store the results
			FitParameters parameters = new FitParameters();
			Spot[] spots = new Spot[candidates.spots.length];
			for (int i = 0; i < spots.length; i++)
			{
				spots[i] = candidates.spots[i].spot;
				//System.out.printf("Fit %d [%d,%d = %.1f]\n", i+1, spots[i].x, spots[i].y, spots[i].intensity);
			}
			parameters.spots = spots;

			ParameterisedFitJob job = new ParameterisedFitJob(parameters, frame, data, bounds);
			fitWorker.run(job); // Results will be stored in the fit job 

			@SuppressWarnings("unused")
			int fittedSpots = 0;
			for (int i = 0; i < spots.length; i++)
			{
				fitResult[i] = job.getFitResult(i);
				if (fitResult[i].getStatus() == FitStatus.OK)
					fittedSpots++;
			}

			// Compute the matches of the fitted spots to the simulated positions
			Coordinate[] actual = ResultsMatchCalculator.getCoordinates(actualCoordinates, frame);
			final double[] zPosition = new double[actual.length];
			final boolean[] fitMatch = new boolean[spots.length];
			SpotMatch[] match = new SpotMatch[spots.length];
			int matchCount = 0;
			if (actual.length > 0)
			{
				// Build a list of the coordinates z-depth using the PeakResultPoint
				for (int i = 0; i < actual.length; i++)
				{
					PeakResultPoint p = (PeakResultPoint) actual[i];
					zPosition[i] = p.peakResult.error;
				}

				BasePoint[] predicted = new BasePoint[spots.length];
				matches.clear();

				int count = 0;
				for (int i = 0; i < spots.length; i++)
				{
					if (fitResult[i].getStatus() == FitStatus.OK)
					{
						final double[] params = job.getFitResult(i).getParameters();
						predicted[count++] = new BasePoint((float) params[Gaussian2DFunction.X_POSITION],
								(float) params[Gaussian2DFunction.Y_POSITION], i);
					}
					else
					{
						// Use the candidate position instead
						predicted[count++] = new BasePoint(spots[i].x, spots[i].y, -1 - i);
					}
				}
				// If we made any fits then score them
				if (count > 0)
				{
					predicted = Arrays.copyOf(predicted, count);
					// Pass in a list to get the point pairs so we have access to the distance
					MatchCalculator.analyseResults2D(actual, predicted, distanceInPixels, null, null, null, matches);

					// Store the fits that match and get the squared distance
					for (PointPair pair : matches)
					{
						final BasePoint p2 = (BasePoint) pair.getPoint2();
						int i = (int) p2.getZ();
						final PeakResultPoint p1 = (PeakResultPoint) pair.getPoint1();

						final double d = pair.getXYDistance();

						if (i >= 0)
						{
							// This is a fitted candidate

							final double a = p1.peakResult.getSignal();
							final double p = job.getFitResult(i).getParameters()[Gaussian2DFunction.SIGNAL];

							fitMatch[i] = true;
							match[matchCount++] = new FitMatch(i, d, p1.peakResult.error, p / a);
						}
						else
						{
							// This is a candidate that could not be fitted

							// Get the index
							i = -1 - i;
							match[matchCount++] = new CandidateMatch(i, d, p1.peakResult.error);
						}
					}
				}
			}
			match = Arrays.copyOf(match, matchCount);

			// Mark the results 
			RampedScore score = new RampedScore(lowerDistanceInPixels, distanceInPixels);
			double tp = 0;
			double fp = 0;
			double tn = 0;
			double fn = 0;

			for (int i = 0; i < match.length; i++)
			{
				final double s = RampedScore.flatten(score.score(match[i].d), 256);

				if (match[i].isFitResult())
				{
					// This is a fitted result so is a positive

					// Check the fitted signal is approximately correct
					if (signalFactor != 0)
					{
						if (match[i].getAbsoluteSignalFactor() > signalFactor)
						{
							// Treat as an unfitted result
							fn += s;
							tn += 1 - s;
							continue;
						}
					}
					tp += s;
					fp += 1 - s;
				}
				else
				{
					// This is an unfitted result that matches so is a negative
					fn += s;
					tn += 1 - s;
				}
			}

			// Make the totals sum to the correct numbers.
			// *** Do not do this. We only want to score the matches to allow reporting of a perfect filter.

			// TP + FP = fitted spots
			//fp = fittedSpots - tp;
			// TN + FN = unfitted candidate spots
			//tn = (spots.length - fittedSpots) - fn;

			// Store the results using a copy of the original (to preserve the candidates for repeat analysis)
			candidates = candidates.clone();
			candidates.tp = tp;
			candidates.fp = fp;
			candidates.tn = tn;
			candidates.fn = fn;
			candidates.fitResult = fitResult;
			candidates.fitMatch = fitMatch;
			candidates.match = match;
			candidates.zPosition = zPosition;
			// Noise should be the same for all results
			if (!job.getResults().isEmpty())
				candidates.noise = job.getResults().get(0).noise;
			results.put(frame, candidates);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(String arg)
	{
		SMLMUsageTracker.recordPlugin(this.getClass(), arg);
		
		extraOptions = Utils.isExtraOptions();

		simulationParameters = CreateData.simulationParameters;
		if (simulationParameters == null)
		{
			IJ.error(TITLE, "No benchmark spot parameters in memory");
			return;
		}
		// This is required to initialise the FitWorker
		spotFilter = BenchmarkSpotFilter.spotFilter;

		imp = WindowManager.getImage(CreateData.CREATE_DATA_IMAGE_TITLE);
		if (imp == null)
		{
			IJ.error(TITLE, "No benchmark image");
			return;
		}
		results = MemoryPeakResults.getResults(CreateData.CREATE_DATA_IMAGE_TITLE + " (Create Data)");
		if (results == null)
		{
			IJ.error(TITLE, "No benchmark results in memory");
			return;
		}
		if (BenchmarkSpotFilter.filterResults == null)
		{
			IJ.error(TITLE, "No benchmark spot candidates in memory");
			return;
		}
		if (BenchmarkSpotFilter.simulationId != simulationParameters.id)
		{
			IJ.error(TITLE, "Update the benchmark spot candidates for the latest simulation");
			return;
		}

		if (!showDialog())
			return;

		run();
	}

	private boolean showDialog()
	{
		GenericDialog gd = new GenericDialog(TITLE);
		gd.addHelp(About.HELP_URL);

		gd.addMessage(String.format("Fit candidate spots in the benchmark image created by " + CreateData.TITLE +
				" plugin\nand identified by the " + BenchmarkSpotFilter.TITLE +
				" plugin.\nPSF width = %s nm (Square pixel adjustment = %s nm)\n \nConfigure the fitting:",
				Utils.rounded(simulationParameters.s), Utils.rounded(getSa())));

		gd.addSlider("Fraction_positives", 50, 100, fractionPositives);
		gd.addSlider("Fraction_negatives_after_positives", 0, 100, fractionNegativesAfterAllPositives);
		gd.addSlider("Min_negatives_after_positives", 0, 10, negativesAfterAllPositives);
		gd.addSlider("Match_distance", 0.2, 3, distance);
		gd.addSlider("Lower_distance", 0.2, 3, lowerDistance);
		gd.addSlider("Match_signal", 0, 3.5, matchSignal);

		// Collect options for fitting
		gd.addNumericField("Initial_StdDev", getSa() / simulationParameters.a, 3);
		gd.addSlider("Fitting_width", 2, 4.5, config.getFitting());
		String[] solverNames = SettingsManager.getNames((Object[]) FitSolver.values());
		gd.addChoice("Fit_solver", solverNames, solverNames[fitConfig.getFitSolver().ordinal()]);
		String[] functionNames = SettingsManager.getNames((Object[]) FitFunction.values());
		gd.addChoice("Fit_function", functionNames, functionNames[fitConfig.getFitFunction().ordinal()]);
		gd.addCheckbox("Include_neighbours", config.isIncludeNeighbours());
		gd.addSlider("Neighbour_height", 0.01, 1, config.getNeighbourHeightThreshold());
		gd.addSlider("Residuals_threshold", 0.01, 1, config.getResidualsThreshold());
		gd.addSlider("Duplicate_distance", 0, 1.5, fitConfig.getDuplicateDistance());

		if (extraOptions)
		{
		}

		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		fractionPositives = Math.abs(gd.getNextNumber());
		fractionNegativesAfterAllPositives = Math.abs(gd.getNextNumber());
		negativesAfterAllPositives = (int) Math.abs(gd.getNextNumber());
		distance = Math.abs(gd.getNextNumber());
		lowerDistance = Math.abs(gd.getNextNumber());
		signalFactor = matchSignal = Math.abs(gd.getNextNumber());

		fitConfig.setInitialPeakStdDev(gd.getNextNumber());
		config.setFitting(gd.getNextNumber());
		fitConfig.setFitSolver(gd.getNextChoiceIndex());
		fitConfig.setFitFunction(gd.getNextChoiceIndex());
		config.setIncludeNeighbours(gd.getNextBoolean());
		config.setNeighbourHeightThreshold(gd.getNextNumber());
		config.setResidualsThreshold(gd.getNextNumber());
		fitConfig.setDuplicateDistance(gd.getNextNumber());

		if (extraOptions)
		{
		}

		if (gd.invalidNumber())
			return false;

		if (lowerDistance > distance)
			lowerDistance = distance;

		distanceInPixels = distance * simulationParameters.s / simulationParameters.a;
		lowerDistanceInPixels = lowerDistance * simulationParameters.s / simulationParameters.a;

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
		if (!PeakFit.configureFitSolver(settings, null, extraOptions))
			return false;

		return true;
	}

	private void run()
	{
		// Extract all the results in memory into a list per frame. This can be cached
		boolean refresh = false;
		if (lastId != simulationParameters.id)
		{
			// Do not get integer coordinates
			// The Coordinate objects will be PeakResultPoint objects that store the original PeakResult
			// from the MemoryPeakResults
			actualCoordinates = ResultsMatchCalculator.getCoordinates(results.getResults(), false);
			lastId = simulationParameters.id;
			refresh = true;
		}

		// Extract all the candidates into a list per frame. This can be cached if the settings have not changed
		if (refresh || lastFilterId != BenchmarkSpotFilter.filterResultsId ||
				lastFractionPositives != fractionPositives ||
				lastFractionNegativesAfterAllPositives != fractionNegativesAfterAllPositives ||
				lastNegativesAfterAllPositives != negativesAfterAllPositives)
		{
			filterCandidates = subsetFilterResults(BenchmarkSpotFilter.filterResults);

			lastFilterId = BenchmarkSpotFilter.filterResultsId;
			lastFractionPositives = fractionPositives;
			lastFractionNegativesAfterAllPositives = fractionNegativesAfterAllPositives;
			lastNegativesAfterAllPositives = negativesAfterAllPositives;
		}

		final ImageStack stack = imp.getImageStack();

		// Clear old results to free memory
		if (fitResults != null)
			fitResults.clear();
		fitResults = null;

		// Create a pool of workers
		final int nThreads = Prefs.getThreads();
		BlockingQueue<Integer> jobs = new ArrayBlockingQueue<Integer>(nThreads * 2);
		List<Worker> workers = new LinkedList<Worker>();
		List<Thread> threads = new LinkedList<Thread>();
		for (int i = 0; i < nThreads; i++)
		{
			Worker worker = new Worker(jobs, stack, actualCoordinates, filterCandidates);
			Thread t = new Thread(worker);
			workers.add(worker);
			threads.add(t);
			t.start();
		}

		final int totalFrames = stack.getSize();

		// Fit the frames
		final int step = Utils.getProgressInterval(totalFrames);
		for (int i = 1; i <= totalFrames; i++)
		{
			put(jobs, i);
			if (i % step == 0)
			{
				if (Utils.showStatus("Frame: " + i + " / " + totalFrames))
					IJ.showProgress(i, totalFrames);
			}
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

		IJ.showProgress(1);

		if (Utils.isInterrupted())
		{
			IJ.showProgress(1);
			IJ.showStatus("Aborted");
			return;
		}

		IJ.showStatus("Collecting results ...");

		fitResultsId++;
		fitResults = new HashMap<Integer, FilterCandidates>();
		for (Worker w : workers)
		{
			fitResults.putAll(w.results);
		}

		summariseResults(fitResults);

		IJ.showStatus("");
	}

	/**
	 * Extract all the filter candidates in order until the desired number of positives have been reached and the number
	 * of negatives matches the configured parameters.
	 * 
	 * @param filterResults
	 * @return The filter candidates
	 */
	private HashMap<Integer, FilterCandidates> subsetFilterResults(HashMap<Integer, FilterResult> filterResults)
	{
		// Convert fractions from percent 
		final double f1 = Math.min(1, fractionPositives / 100.0);
		final double f2 = fractionNegativesAfterAllPositives / 100.0;

		HashMap<Integer, FilterCandidates> subset = new HashMap<Integer, FilterCandidates>();
		nP = nN = 0;
		for (Entry<Integer, FilterResult> result : filterResults.entrySet())
		{
			FilterResult r = result.getValue();

			// Determine the number of positives to find
			nP += r.result.getTP();
			nN += r.result.getFP();
			final int targetP = (int) Math.round(r.result.getTP() * f1);
			// Count the number of positive & negatives
			int p = 0, n = 0;
			boolean reachedTarget = false;
			int nAfter = 0;

			int count = 0;
			for (ScoredSpot spot : r.spots)
			{
				if (spot.match)
				{
					p++;
					if (!reachedTarget)
					{
						reachedTarget = p >= targetP;
					}
				}
				else
				{
					n++;
					if (reachedTarget)
					{
						nAfter++;
					}
				}

				if (reachedTarget)
				{
					// Check if we have reached both the limits
					if (nAfter >= negativesAfterAllPositives && (double) n / (n + p) >= f2)
						break;
				}

				count++;
			}

			// Debug
			//System.out.printf("Frame %d : %d / (%d + %d). p=%d, n=%d, after=%d, f=%.1f\n", result.getKey().intValue(),
			//		r.result.getTruePositives(), r.result.getTruePositives(), r.result.getFalsePositives(), p, n,
			//		nAfter, (double) n / (n + p));

			subset.put(result.getKey(), new FilterCandidates(p, n, Arrays.copyOf(r.spots, count)));
		}
		return subset;
	}

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

	private void summariseResults(HashMap<Integer, FilterCandidates> filterCandidates)
	{
		createTable();

		// Summarise the fitting results. N fits, N failures. 
		// Optimal match statistics if filtering is perfect (since fitting is not perfect).
		StoredDataStatistics distanceStats = new StoredDataStatistics();
		StoredDataStatistics depthStats = new StoredDataStatistics();
		StoredDataStatistics precisionStats = new StoredDataStatistics();
		final double nmPerPixel = simulationParameters.a;
		final boolean mle = fitConfig.getFitSolver() == FitSolver.MLE;
		double tp = 0, fp = 0;
		int failcTP = 0, failcFP = 0;
		int cTP = 0, cFP = 0;
		for (FilterCandidates result : filterCandidates.values())
		{
			tp += result.tp;
			fp += result.fp;
			for (int i = 0; i < result.fitResult.length; i++)
			{
				if (result.spots[i].match)
					cTP++;
				else
					cFP++;
				if (result.fitResult[i].getStatus() != FitStatus.OK)
				{
					if (result.spots[i].match)
						failcTP++;
					else
						failcFP++;
				}
			}
			for (int i = 0; i < result.match.length; i++)
			{
				if (!result.match[i].isFitResult())
					// For now just ignore the candidates that matched
					continue;

				distanceStats.add(result.match[i].d * nmPerPixel);
				depthStats.add(result.match[i].z * nmPerPixel);
				final double[] p = result.fitResult[result.match[i].i].getParameters();
				final double s = (p[Gaussian2DFunction.X_SD] + p[Gaussian2DFunction.Y_SD]) * 0.5 * nmPerPixel;
				final double N = p[Gaussian2DFunction.SIGNAL] / simulationParameters.gain;
				final double b2 = Math.max(0, (p[Gaussian2DFunction.BACKGROUND] - simulationParameters.bias) /
						simulationParameters.gain);
				if (mle)
					precisionStats.add(PeakResult.getMLPrecisionX(nmPerPixel, s, N, b2, simulationParameters.emCCD));
				else
					precisionStats.add(PeakResult.getPrecisionX(nmPerPixel, s, N, b2, simulationParameters.emCCD));
			}
		}

		StringBuilder sb = new StringBuilder();

		// Add information about the simulation
		final double signal = simulationParameters.signalPerFrame; //(simulationParameters.minSignal + simulationParameters.maxSignal) * 0.5;
		final int n = results.size();
		sb.append(imp.getStackSize()).append("\t");
		final int w = imp.getWidth();
		final int h = imp.getHeight();
		sb.append(w).append("\t");
		sb.append(h).append("\t");
		sb.append(n).append("\t");
		double density = ((double) n / imp.getStackSize()) / (w * h) /
				(simulationParameters.a * simulationParameters.a / 1e6);
		sb.append(Utils.rounded(density)).append("\t");
		sb.append(Utils.rounded(signal)).append("\t");
		sb.append(Utils.rounded(simulationParameters.s)).append("\t");
		sb.append(Utils.rounded(simulationParameters.a)).append("\t");
		sb.append(Utils.rounded(simulationParameters.depth)).append("\t");
		sb.append(simulationParameters.fixedDepth).append("\t");
		sb.append(Utils.rounded(simulationParameters.gain)).append("\t");
		sb.append(Utils.rounded(simulationParameters.readNoise)).append("\t");
		sb.append(Utils.rounded(simulationParameters.b)).append("\t");
		sb.append(Utils.rounded(simulationParameters.b2)).append("\t");

		// Compute the noise
		double noise = simulationParameters.b2;
		if (simulationParameters.emCCD)
		{
			// The b2 parameter was computed without application of the EM-CCD noise factor of 2.
			//final double b2 = backgroundVariance + readVariance
			//                = simulationParameters.b + readVariance
			// This should be applied only to the background variance.
			final double readVariance = noise - simulationParameters.b;
			noise = simulationParameters.b * 2 + readVariance;
		}

		if (simulationParameters.fullSimulation)
		{
			// The total signal is spread over frames
		}

		sb.append(Utils.rounded(signal / Math.sqrt(noise))).append("\t");
		sb.append(Utils.rounded(simulationParameters.s / simulationParameters.a)).append("\t");

		sb.append(spotFilter.getDescription());

		addCount(sb, nP + nN);
		addCount(sb, nP);
		addCount(sb, nN);
		String name = PeakFit.getSolverName(fitConfig);
		if (fitConfig.getFitSolver() == FitSolver.MLE && fitConfig.isModelCamera())
			name += " Camera";
		add(sb, name);
		add(sb, config.getFitting());

		resultPrefix = sb.toString();

		// Q. Should I add other fit configuration here?

		// The fraction of positive and negative candidates that were included
		add(sb, (100.0 * cTP) / nP);
		add(sb, (100.0 * cFP) / nN);

		// Score the fitting results compared to the original simulation.

		// Score the candidate selection:
		add(sb, cTP + cFP);
		add(sb, cTP);
		add(sb, cFP);
		// TP are all candidates that can be matched to a spot
		// FP are all candidates that cannot be matched to a spot
		// FN = The number of missed spots
		FractionClassificationResult m = new FractionClassificationResult(cTP, cFP, 0, simulationParameters.molecules -
				cTP);
		add(sb, m.getRecall());
		add(sb, m.getPrecision());
		add(sb, m.getF1Score());
		add(sb, m.getJaccard());

		// Score the fitting results:
		add(sb, failcTP);
		add(sb, failcFP);
		// TP are all fit results that can be matched to a spot
		// FP are all fit results that cannot be matched to a spot
		// FN = The number of missed spots
		add(sb, tp);
		add(sb, fp);
		m = new FractionClassificationResult(tp, fp, 0, simulationParameters.molecules - tp);
		add(sb, m.getRecall());
		add(sb, m.getPrecision());
		add(sb, m.getF1Score());
		add(sb, m.getJaccard());

		// The mean may be subject to extreme outliers so use the median
		double median = distanceStats.getMedian();
		add(sb, median);

		int[] idList = new int[3];
		int idCount = 0;

		String label = String.format("Recall = %s. n = %d. Median = %s nm. SD = %s nm", Utils.rounded(m.getRecall()),
				distanceStats.getN(), Utils.rounded(median), Utils.rounded(distanceStats.getStandardDeviation()));
		int id = Utils.showHistogram(TITLE, distanceStats, "Match Distance (nm)", 0, 0, 100, label);
		if (Utils.isNewWindow())
			idList[idCount++] = id;

		median = depthStats.getMedian();
		add(sb, median);

		label = String.format("n = %d. Median = %s nm", depthStats.getN(), Utils.rounded(median));
		id = Utils.showHistogram(TITLE, depthStats, "Match Depth (nm)", 0, 1, 100, label);
		if (Utils.isNewWindow())
			idList[idCount++] = id;

		median = precisionStats.getMedian();
		add(sb, median);

		label = String.format("n = %d. Median = %s nm", precisionStats.getN(), Utils.rounded(median));
		id = Utils.showHistogram(TITLE, precisionStats, "Precision (nm)", 0, 1, 100, label);
		if (Utils.isNewWindow())
			idList[idCount++] = id;

		if (idCount != 0)
		{
			idList = Arrays.copyOf(idList, idCount);
			WindowOrganiser wo = new WindowOrganiser();
			wo.tileWindows(idList);
		}

		summaryTable.append(sb.toString());
	}

	private static void add(StringBuilder sb, String value)
	{
		sb.append("\t").append(value);
	}

	private static void add(StringBuilder sb, int value)
	{
		sb.append("\t").append(value);
	}

	private static void add(StringBuilder sb, double value)
	{
		add(sb, Utils.rounded(value));
	}

	private static void addCount(StringBuilder sb, double value)
	{
		// Check if the double holds an integer count
		if ((int) value == value)
		{
			sb.append("\t").append((int) value);
		}
		else
		{
			// Otherwise add the counts using at least 2 dp
			if (value > 100)
				sb.append("\t").append(IJ.d2s(value));
			else
				add(sb, Utils.rounded(value));
		}
	}

	private void createTable()
	{
		if (summaryTable == null || !summaryTable.isVisible())
		{
			summaryTable = new TextWindow(TITLE, createHeader(false), "", 1000, 300);
			summaryTable.setVisible(true);
		}
	}

	private String createHeader(boolean extraRecall)
	{
		StringBuilder sb = new StringBuilder(
				"Frames\tW\tH\tMolecules\tDensity (um^-2)\tN\ts (nm)\ta (nm)\tDepth (nm)\tFixed\tGain\tReadNoise (ADUs)\tB (photons)\tb2 (photons)\tSNR\ts (px)\t");
		sb.append("Filter\t");
		sb.append("Spots\t");
		sb.append("nP\t");
		sb.append("nN\t");
		sb.append("Solver\t");
		sb.append("Fitting");

		tablePrefix = sb.toString();

		sb.append("\t");
		sb.append("% nP\t");
		sb.append("% nN\t");
		sb.append("Total\t");
		sb.append("cTP\t");
		sb.append("cFP\t");

		sb.append("cRecall\t");
		sb.append("cPrecision\t");
		sb.append("cF1\t");
		sb.append("cJaccard\t");

		sb.append("Fail cTP\t");
		sb.append("Fail cFP\t");
		sb.append("TP\t");
		sb.append("FP\t");
		sb.append("Recall\t");
		sb.append("Precision\t");
		sb.append("F1\t");
		sb.append("Jaccard\t");
		sb.append("Med.Distance (nm)\t");
		sb.append("Med.Depth (nm)\t");
		sb.append("Med.Precision (nm)\t");

		return sb.toString();
	}

	private double getSa()
	{
		final double sa = PSFCalculator.squarePixelAdjustment(simulationParameters.s, simulationParameters.a);
		return sa;
	}
}
