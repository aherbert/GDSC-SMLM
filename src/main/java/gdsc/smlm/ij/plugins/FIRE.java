package gdsc.smlm.ij.plugins;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.Rectangle;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.analysis.ParametricUnivariateFunction;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.function.Gaussian;
import org.apache.commons.math3.analysis.interpolation.LoessInterpolator;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.fitting.GaussianCurveFitter;
import org.apache.commons.math3.fitting.SimpleCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
import org.apache.commons.math3.optim.univariate.BracketFinder;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariateOptimizer;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;
import org.apache.commons.math3.random.Well19937c;
//import org.apache.commons.math3.special.Erf;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MathArrays;

import gdsc.core.data.DataException;
import gdsc.core.data.utils.ConversionException;
import gdsc.core.ij.IJTrackProgress;
import gdsc.core.ij.Utils;
import gdsc.core.logging.NullTrackProgress;
import gdsc.core.logging.TrackProgress;
import gdsc.core.utils.Maths;
import gdsc.core.utils.MedianWindow;
import gdsc.core.utils.Random;
import gdsc.core.utils.Statistics;
import gdsc.core.utils.StoredDataStatistics;
import gdsc.core.utils.TextUtils;
import gdsc.core.utils.TurboList;
import gdsc.smlm.data.config.CalibrationReader;
import gdsc.smlm.data.config.ResultsProtos.ResultsImageMode;
import gdsc.smlm.data.config.ResultsProtos.ResultsImageType;
import gdsc.smlm.data.config.UnitProtos.DistanceUnit;
import gdsc.smlm.data.config.UnitHelper;
import gdsc.smlm.function.Erf;

/*----------------------------------------------------------------------------- 
 * GDSC SMLM Software
 * 
 * Copyright (C) 2017 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import gdsc.smlm.ij.frc.FRC;
import gdsc.smlm.ij.frc.FRC.FIREResult;
import gdsc.smlm.ij.frc.FRC.FRCCurve;
import gdsc.smlm.ij.frc.FRC.FRCCurveResult;
import gdsc.smlm.ij.frc.FRC.FourierMethod;
import gdsc.smlm.ij.frc.FRC.SamplingMethod;
import gdsc.smlm.ij.frc.FRC.ThresholdMethod;
import gdsc.smlm.ij.plugins.ResultsManager.InputSource;
import gdsc.smlm.ij.results.IJImagePeakResults;
import gdsc.smlm.ij.results.ImagePeakResultsFactory;
import gdsc.smlm.ij.settings.SettingsManager;
import gdsc.smlm.results.Counter;
import gdsc.smlm.results.MemoryPeakResults;
import gdsc.smlm.results.PeakResult;
import gdsc.smlm.results.procedures.PeakResultProcedure;
import gdsc.smlm.results.procedures.PrecisionResultProcedure;
import gdsc.smlm.results.procedures.XYRResultProcedure;
import gnu.trove.list.array.TDoubleArrayList;
import ij.IJ;
import ij.ImagePlus;
import ij.Macro;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.ExtendedGenericDialog;
import ij.gui.GenericDialog;
import ij.gui.NonBlockingExtendedGenericDialog;
import ij.gui.Plot;
import ij.gui.Plot2;
import ij.gui.PlotWindow;
import ij.plugin.PlugIn;
import ij.plugin.WindowOrganiser;
import ij.plugin.frame.Recorder;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.LUTHelper;
import ij.process.LUTHelper.LutColour;

/**
 * Computes the Fourier Image Resolution of an image
 * <p>
 * Implements the FIRE (Fourier Image REsolution) method described in:<br>
 * Niewenhuizen, et al (2013). Measuring image resolution in optical nanoscopy. Nature Methods, 10, 557<br>
 * http://www.nature.com/nmeth/journal/v10/n6/full/nmeth.2448.html
 * <p>
 * A second plugin allows estimation of the spurious correlation component contributed by the same molecule being
 * present in both super-resolution images due to splitting of repeat localisations. The correction Q factor is the
 * number of times a molecule is repeat localised (i.e. average blinks per molecule). This code was developed using the
 * Matlab examples provided by Bernd Reiger.
 */
public class FIRE implements PlugIn
{
	private String TITLE = "Fourier Image REsolution (FIRE)";
	private static String inputOption = "";
	private static String inputOption2 = "";

	private static int repeats = 1;
	private static boolean useSignal = false;
	private boolean myUseSignal = false;
	private static int maxPerBin = 0; // 5 in the Niewenhuizen paper
	private static boolean randomSplit = true;
	private static int blockSize = 50;
	private static String[] SCALE_ITEMS;
	private static int[] SCALE_VALUES = new int[] { 0, 1, 2, 4, 8, 16, 32, 64, 128 };
	private static String[] IMAGE_SIZE_ITEMS;
	private static int[] IMAGE_SIZE_VALUES;
	private static int imageScaleIndex = 0;
	private static int imageSizeIndex;

	// The Q value and the mean and sigma for spurious correlation correction
	private static boolean spuriousCorrelationCorrection = false;
	private static double qValue, mean, sigma;

	static
	{
		SCALE_ITEMS = new String[SCALE_VALUES.length];
		SCALE_ITEMS[0] = "Auto";
		for (int i = 1; i < SCALE_VALUES.length; i++)
			SCALE_ITEMS[i] = Integer.toString(SCALE_VALUES[i]);

		// Create size for Fourier transforms. Must be power of 2.
		IMAGE_SIZE_VALUES = new int[32];
		IMAGE_SIZE_ITEMS = new String[IMAGE_SIZE_VALUES.length];
		int size = 512; // Start at a reasonable size. Too small does not work.
		int count = 0;
		while (size <= 16384)
		{
			if (size == 2048)
				imageSizeIndex = count;

			// Image sizes are 1 smaller so that rounding error when scaling does not create an image too large for the power of 2
			IMAGE_SIZE_VALUES[count] = size - 1;
			IMAGE_SIZE_ITEMS[count] = Integer.toString(size);
			size *= 2;
			count++;
		}
		IMAGE_SIZE_VALUES = Arrays.copyOf(IMAGE_SIZE_VALUES, count);
		IMAGE_SIZE_ITEMS = Arrays.copyOf(IMAGE_SIZE_ITEMS, count);
	}

	/**
	 * Specify the method to use to determine the parameters for the distribution of the localisation precision (assumed
	 * to be Gaussian)
	 */
	private enum PrecisionMethod
	{
		//@formatter:off
		/**
		 * Use a fixed value for the precision distribution mean and standard deviation
		 */
		FIXED{ public String getName() { return "Fixed"; }},
		/**
		 * Use the precision value that is stored in the results, e.g. from loaded data. 
		 * The values can then be used to fit the entire distribution using a Gaussian or 
		 * sampled to construct a decay curve from which the parameters are estimated. 
		 */
		STORED{ public String getName() { return "Stored"; }},
		/**
		 * Calculate the precision of each localisation using the formula of Mortensen. 
		 * The values can then be used to fit the entire distribution using a Gaussian or 
		 * sampled to construct a decay curve from which the parameters are estimated.
		 */
		CALCULATE{ public String getName() { return "Calculate"; }};
		//@formatter:on

		@Override
		public String toString()
		{
			return getName();
		}

		/**
		 * Gets the name.
		 *
		 * @return the name
		 */
		abstract public String getName();
	}

	private static double perimeterSamplingFactor = 1;
	private static int fourierMethodIndex = FourierMethod.JTRANSFORMS.ordinal();
	private FourierMethod fourierMethod;
	private static int samplingMethodIndex = SamplingMethod.RADIAL_SUM.ordinal();
	private SamplingMethod samplingMethod;
	private static int thresholdMethodIndex = ThresholdMethod.FIXED_1_OVER_7.ordinal();
	private ThresholdMethod thresholdMethod;
	private static boolean showFRCCurve = true;
	private static boolean showFRCCurveRepeats = false;
	private static boolean showFRCTimeEvolution = false;
	private static int precisionMethodIndex = PrecisionMethod.CALCULATE.ordinal();
	private PrecisionMethod precisionMethod;
	private static boolean sampleDecay = false;
	private static boolean loessSmoothing = false;
	private static boolean fitPrecision = false;
	private static double minQ = 0.2;
	private static double maxQ = 0.45;

	private static boolean chooseRoi = false;
	private static String roiImage = "";

	private boolean extraOptions;
	private Rectangle roiBounds;
	private int roiImageWidth, roiImageHeight;

	// Stored in initialisation
	MemoryPeakResults results, results2;
	Rectangle2D dataBounds;
	String units;
	double nmPerUnit = 1;

	// Stored in setCorrectionParameters
	private double correctionQValue, correctionMean, correctionSigma;

	public class FireImages
	{
		/** The first super-resolution image. */
		final ImageProcessor ip1;
		/** The second super-resolution image. */
		final ImageProcessor ip2;
		/** The nm per pixel in the super-resolution images */
		final double nmPerPixel;

		FireImages(ImageProcessor ip1, ImageProcessor ip2, double nmPerPixel)
		{
			this.ip1 = ip1;
			this.ip2 = ip2;
			this.nmPerPixel = nmPerPixel;
		}
	}

	/**
	 * Contains the Fourier Image REsolution (FIRE) result.
	 */
	public class FireResult
	{
		/** The fire number (in nm). */
		final double fireNumber;

		/** The correlation at the given resolution. */
		final double correlation;

		/** The FRC curve used to compute the resolution. */
		final FRCCurve frcCurve;

		/** The original correlation curve, i.e. the raw curve before smoothing. */
		final double[] originalCorrelationCurve;

		FireResult(double fireNumber, double correlation, FRCCurve frcCurve, double[] originalCorrelationCurve)
		{
			this.fireNumber = fireNumber;
			this.correlation = correlation;
			this.frcCurve = frcCurve;
			this.originalCorrelationCurve = originalCorrelationCurve;
		}

		/**
		 * Gets the nm per pixel for the super-resolution images used to construct the FRC curve.
		 *
		 * @return the nm per pixel
		 */
		double getNmPerPixel()
		{
			return frcCurve.nmPerPixel;
		}
	}

	private class FIREWorker implements Runnable
	{
		final double fourierImageScale;
		final int imageSize;

		String name;
		FireResult result;
		Plot2 plot;
		/**
		 * Flag to denote that an out-of-memory error occurred. This is probably due to using too many threads to
		 * compute large Fourier transforms.
		 */
		boolean oom = false;

		public FIREWorker(int id, double fourierImageScale, int imageSize)
		{
			this.fourierImageScale = fourierImageScale;
			this.imageSize = imageSize;
			name = results.getName() + " [" + id + "]";
		}

		public void run()
		{
			try
			{
				result = calculateFireNumber(fourierMethod, samplingMethod, thresholdMethod, fourierImageScale,
						imageSize);
				if (showFRCCurve)
				{
					plot = createFrcCurve(name, result, thresholdMethod);
					if (showFRCCurveRepeats)
						// Do this on the thread
						plot.draw();
				}
			}
			catch (OutOfMemoryError e)
			{
				oom = true;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(String arg)
	{
		extraOptions = Utils.isExtraOptions();
		SMLMUsageTracker.recordPlugin(this.getClass(), arg);

		// Require some fit results and selected regions
		int size = MemoryPeakResults.countMemorySize();
		if (size == 0)
		{
			IJ.error(TITLE, "There are no fitting results in memory");
			return;
		}

		if ("q".equals(arg))
		{
			TITLE += " Q estimation";
			runQEstimation();
			return;
		}

		IJ.showStatus(TITLE + " ...");

		if (!showInputDialog())
			return;

		MemoryPeakResults results = ResultsManager.loadInputResults(inputOption, false);
		if (results == null || results.size() == 0)
		{
			IJ.error(TITLE, "No results could be loaded");
			return;
		}
		MemoryPeakResults results2 = ResultsManager.loadInputResults(inputOption2, false);

		results = cropToRoi(results);
		if (results.size() < 2)
		{
			IJ.error(TITLE, "No results within the crop region");
			return;
		}
		if (results2 != null)
		{
			results2 = cropToRoi(results2);
			if (results2.size() < 2)
			{
				IJ.error(TITLE, "No results2 within the crop region");
				return;
			}
		}

		initialise(results, results2);

		if (!showDialog())
			return;

		long start = System.currentTimeMillis();

		// Compute FIRE

		String name = results.getName();
		double fourierImageScale = SCALE_VALUES[imageScaleIndex];
		int imageSize = IMAGE_SIZE_VALUES[imageSizeIndex];

		if (this.results2 != null)
		{
			name += " vs " + results2.getName();

			FireResult result = calculateFireNumber(fourierMethod, samplingMethod, thresholdMethod, fourierImageScale,
					imageSize);

			if (result != null)
			{
				logResult(name, result);

				if (showFRCCurve)
					showFrcCurve(name, result, thresholdMethod);
			}
		}
		else
		{
			FireResult result = null;

			int repeats = (randomSplit) ? Math.max(1, FIRE.repeats) : 1;
			if (repeats == 1)
			{
				result = calculateFireNumber(fourierMethod, samplingMethod, thresholdMethod, fourierImageScale,
						imageSize);

				if (result != null)
				{
					logResult(name, result);

					if (showFRCCurve)
						showFrcCurve(name, result, thresholdMethod);
				}
			}
			else
			{
				// Multi-thread this ... 			
				int nThreads = Maths.min(repeats, getThreads());
				ExecutorService executor = Executors.newFixedThreadPool(nThreads);
				TurboList<Future<?>> futures = new TurboList<Future<?>>(repeats);
				TurboList<FIREWorker> workers = new TurboList<FIREWorker>(repeats);
				setProgress(repeats);
				IJ.showProgress(0);
				IJ.showStatus(TITLE + " computing ...");
				for (int i = 1; i <= repeats; i++)
				{
					FIREWorker w = new FIREWorker(i, fourierImageScale, imageSize);
					workers.add(w);
					futures.add(executor.submit(w));
				}

				// Wait for all to finish
				for (int t = futures.size(); t-- > 0;)
				{
					try
					{
						// The future .get() method will block until completed
						futures.get(t).get();
					}
					catch (Exception e)
					{
						// This should not happen. 
						// Ignore it and allow processing to continue (the number of neighbour samples will just be smaller).  
						e.printStackTrace();
					}
				}
				IJ.showProgress(1);

				executor.shutdown();

				// Show a combined FRC curve plot of all the smoothed curves if we have multiples.
				LUT valuesLUT = LUTHelper.createLUT(LutColour.FIRE_GLOW);
				@SuppressWarnings("unused")
				LUT noSmoothLUT = LUTHelper.createLUT(LutColour.GRAYS).createInvertedLut(); // Black at max value
				LUTHelper.DefaultLUTMapper mapper = new LUTHelper.DefaultLUTMapper(0, repeats);
				FrcCurve curve = new FrcCurve();

				Statistics stats = new Statistics();
				WindowOrganiser wo = new WindowOrganiser();
				boolean oom = false;
				for (int i = 0; i < repeats; i++)
				{
					FIREWorker w = workers.get(i);
					if (w.oom)
						oom = true;
					if (w.result == null)
						continue;
					result = w.result;
					if (!Double.isNaN(result.fireNumber))
						stats.add(result.fireNumber);

					if (showFRCCurveRepeats)
					{
						// Output each FRC curve using a suffix.
						logResult(w.name, result);
						wo.add(Utils.display(w.plot.getTitle(), w.plot));
					}
					if (showFRCCurve)
					{
						int index = mapper.map(i + 1);
						//@formatter:off
						curve.add(name, result, thresholdMethod, 
								LUTHelper.getColour(valuesLUT, index),
								Color.blue, 
								null //LUTHelper.getColour(noSmoothLUT, index)
								);
						//@formatter:on
					}
				}

				if (result != null)
				{
					wo.cascade();
					double mean = stats.getMean();
					logResult(name, result, mean, stats);
					if (showFRCCurve)
					{
						curve.addResolution(mean);
						Plot2 plot = curve.getPlot();
						Utils.display(plot.getTitle(), plot);
					}
				}

				if (oom)
				{
					//@formatter:off
					IJ.error(TITLE,
							"ERROR - Parallel computation out-of-memory.\n \n" + 
					TextUtils.wrap("The number of results will be reduced. " +
									"Please reduce the size of the Fourier image " +
									"or change the number of threads " +
									"using the extra options (hold down the 'Shift' " +
									"key when running the plugin).",
									80));
					//@formatter:on
				}
			}

			// Only do this once
			if (showFRCTimeEvolution && result != null && !Double.isNaN(result.fireNumber))
				showFrcTimeEvolution(name, result.fireNumber, thresholdMethod, nmPerUnit / result.getNmPerPixel(),
						imageSize);
		}

		IJ.showStatus(TITLE + " complete : " + Utils.timeToString(System.currentTimeMillis() - start));
	}

	private void logResult(String name, FireResult result)
	{
		IJ.log(String.format("%s : FIRE number = %s %s (Fourier scale = %s)", name, Utils.rounded(result.fireNumber, 4),
				units, Utils.rounded(nmPerUnit / result.getNmPerPixel(), 3)));
		if (Double.isNaN(result.fireNumber))
		{
			Utils.log(
					"%s Warning: NaN result possible if the resolution is below the pixel size of the input Fourier image (%s %s).",
					TITLE, Utils.rounded(result.getNmPerPixel()), units);
		}
	}

	private void logResult(String name, FireResult result, double mean, Statistics stats)
	{
		IJ.log(String.format("%s : FIRE number = %s +/- %s %s [95%% CI, n=%d] (Fourier scale = %s)", name,
				Utils.rounded(mean, 4), Utils.rounded(stats.getConfidenceInterval(0.95), 4), units, stats.getN(),
				Utils.rounded(nmPerUnit / result.getNmPerPixel(), 3)));
	}

	private MemoryPeakResults cropToRoi(MemoryPeakResults results)
	{
		if (roiBounds == null)
			return results;

		// Adjust bounds relative to input results image
		//Rectangle2D.Float bounds = results.getDataBounds();
		Rectangle bounds = results.getBounds(true);
		double xscale = roiImageWidth / bounds.width;
		double yscale = roiImageHeight / bounds.height;

		final float minX = (float) (bounds.x + roiBounds.x / xscale);
		final float maxX = (float) (minX + roiBounds.width / xscale);
		final float minY = (float) (bounds.y + (roiBounds.y / yscale));
		final float maxY = (float) (minY + roiBounds.height / yscale);

		// Create a new set of results within the bounds
		final MemoryPeakResults newResults = new MemoryPeakResults();
		newResults.begin();
		results.forEach(DistanceUnit.PIXEL, new XYRResultProcedure()
		{
			public void executeXYR(float x, float y, PeakResult result)
			{
				if (x < minX || x > maxX || y < minY || y > maxY)
					return;
				newResults.add(result);
			}
		});
		newResults.end();
		newResults.copySettings(results);
		newResults.setBounds(new Rectangle((int) minX, (int) minY, (int) (maxX - minX), (int) (maxY - minY)));
		return newResults;
	}

	private boolean showInputDialog()
	{
		ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);
		gd.addMessage("Compute the resolution using Fourier Ring Correlation");
		gd.addHelp(About.HELP_URL);

		// Build a list of all images with a region ROI
		List<String> titles = new LinkedList<String>();
		if (WindowManager.getWindowCount() > 0)
		{
			for (int imageID : WindowManager.getIDList())
			{
				ImagePlus imp = WindowManager.getImage(imageID);
				if (imp != null && imp.getRoi() != null && imp.getRoi().isArea())
					titles.add(imp.getTitle());
			}
		}

		ResultsManager.addInput(gd, inputOption, InputSource.MEMORY);
		ResultsManager.addInput(gd, "Input2", inputOption2, InputSource.NONE, InputSource.MEMORY);

		if (!titles.isEmpty())
			gd.addCheckbox((titles.size() == 1) ? "Use_ROI" : "Choose_ROI", chooseRoi);

		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		inputOption = ResultsManager.getInputSource(gd);
		inputOption2 = ResultsManager.getInputSource(gd);

		if (!titles.isEmpty())
			chooseRoi = gd.getNextBoolean();

		if (!titles.isEmpty() && chooseRoi)
		{
			if (titles.size() == 1)
			{
				roiImage = titles.get(0);
				Recorder.recordOption("Image", roiImage);
			}
			else
			{
				String[] items = titles.toArray(new String[titles.size()]);
				gd = new ExtendedGenericDialog(TITLE);
				gd.addMessage("Select the source image for the ROI");
				gd.addChoice("Image", items, roiImage);
				gd.showDialog();
				if (gd.wasCanceled())
					return false;
				roiImage = gd.getNextChoice();
			}
			ImagePlus imp = WindowManager.getImage(roiImage);

			roiBounds = imp.getRoi().getBounds();
			roiImageWidth = imp.getWidth();
			roiImageHeight = imp.getHeight();
		}
		else
		{
			roiBounds = null;
		}

		return true;
	}

	private boolean showDialog()
	{
		ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);
		gd.addMessage("Compute the resolution using Fourier Ring Correlation");
		gd.addHelp(About.HELP_URL);

		boolean single = results2 == null;

		gd.addMessage("Image construction options:");
		gd.addChoice("Image_scale", SCALE_ITEMS, SCALE_ITEMS[imageScaleIndex]);
		gd.addChoice("Auto_image_size", IMAGE_SIZE_ITEMS, IMAGE_SIZE_ITEMS[imageSizeIndex]);
		if (extraOptions)
			gd.addCheckbox("Use_signal (if present)", useSignal);
		gd.addNumericField("Max_per_bin", maxPerBin, 0);

		gd.addMessage("Fourier options:");
		String[] fourierMethodNames = SettingsManager.getNames((Object[]) FRC.FourierMethod.values());
		gd.addChoice("Fourier_method", fourierMethodNames, fourierMethodNames[fourierMethodIndex]);
		String[] samplingMethodNames = SettingsManager.getNames((Object[]) FRC.SamplingMethod.values());
		gd.addChoice("Sampling_method", samplingMethodNames, samplingMethodNames[samplingMethodIndex]);
		gd.addSlider("Sampling_factor", 0.2, 4, perimeterSamplingFactor);

		gd.addMessage("FIRE options:");
		String[] thresholdMethodNames = SettingsManager.getNames((Object[]) FRC.ThresholdMethod.values());
		gd.addChoice("Threshold_method", thresholdMethodNames, thresholdMethodNames[thresholdMethodIndex]);
		gd.addCheckbox("Show_FRC_curve", showFRCCurve);

		if (single)
		{
			gd.addMessage("For single datasets:");
			Label l = (Label) gd.getMessage();
			gd.addNumericField("Block_size", blockSize, 0);
			gd.addCheckbox("Random_split", randomSplit);
			gd.addNumericField("Repeats", repeats, 0);
			gd.addCheckbox("Show_FRC_curve_repeats", showFRCCurveRepeats);
			gd.addCheckbox("Show_FRC_time_evolution", showFRCTimeEvolution);
			gd.addCheckbox("Spurious correlation correction", spuriousCorrelationCorrection);
			gd.addNumericField("Q-value", qValue, 3);
			gd.addNumericField("Precision_Mean", mean, 2, 6, "nm");
			gd.addNumericField("Precision_Sigma", sigma, 2, 6, "nm");
			if (extraOptions)
				gd.addNumericField("Threads", getLastNThreads(), 0);

			// Rearrange the dialog
			if (gd.getLayout() != null)
			{
				GridBagLayout grid = (GridBagLayout) gd.getLayout();

				int xOffset = 0, yOffset = 0;
				int lastY = -1, rowCount = 0;
				for (Component comp : gd.getComponents())
				{
					// Check if this should be the second major column
					if (comp == l)
					{
						xOffset += 2;
						yOffset = yOffset - rowCount + 1; // Skip title row
					}
					// Reposition the field
					GridBagConstraints c = grid.getConstraints(comp);
					if (lastY != c.gridy)
						rowCount++;
					lastY = c.gridy;
					c.gridx = c.gridx + xOffset;
					c.gridy = c.gridy + yOffset;
					c.insets.left = c.insets.left + 10 * xOffset;
					c.insets.top = 0;
					c.insets.bottom = 0;
					grid.setConstraints(comp, c);
				}

				if (IJ.isLinux())
					gd.setBackground(new Color(238, 238, 238));
			}
		}

		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		imageScaleIndex = gd.getNextChoiceIndex();
		imageSizeIndex = gd.getNextChoiceIndex();
		if (extraOptions)
			myUseSignal = useSignal = gd.getNextBoolean();
		maxPerBin = Math.abs((int) gd.getNextNumber());

		fourierMethodIndex = gd.getNextChoiceIndex();
		fourierMethod = FourierMethod.values()[fourierMethodIndex];
		samplingMethodIndex = gd.getNextChoiceIndex();
		samplingMethod = SamplingMethod.values()[samplingMethodIndex];
		perimeterSamplingFactor = gd.getNextNumber();

		thresholdMethodIndex = gd.getNextChoiceIndex();
		thresholdMethod = FRC.ThresholdMethod.values()[thresholdMethodIndex];
		showFRCCurve = gd.getNextBoolean();

		if (single)
		{
			blockSize = Math.max(1, (int) gd.getNextNumber());
			randomSplit = gd.getNextBoolean();
			repeats = Math.max(1, (int) gd.getNextNumber());
			showFRCCurveRepeats = gd.getNextBoolean();
			showFRCTimeEvolution = gd.getNextBoolean();
			spuriousCorrelationCorrection = gd.getNextBoolean();
			qValue = Math.abs(gd.getNextNumber());
			mean = Math.abs(gd.getNextNumber());
			sigma = Math.abs(gd.getNextNumber());
			if (extraOptions)
			{
				setThreads((int) gd.getNextNumber());
				lastNThreads = this.nThreads;
			}
		}

		// Check arguments
		try
		{
			Parameters.isAboveZero("Perimeter sampling factor", perimeterSamplingFactor);
			if (single && spuriousCorrelationCorrection)
			{
				Parameters.isAboveZero("Q-value", qValue);
				Parameters.isAboveZero("Precision Mean", mean);
				Parameters.isAboveZero("Precision Sigma", sigma);
				// Set these for use in FIRE computation 
				setCorrectionParameters(qValue, mean, sigma);
			}
		}
		catch (IllegalArgumentException e)
		{
			IJ.error(TITLE, e.getMessage());
			return false;
		}

		return true;
	}

	/**
	 * Initialise this instance with localisation results for the FIRE computation.
	 *
	 * @param results
	 *            the results
	 * @param results2
	 *            the second set of results (can be null)
	 */
	public void initialise(MemoryPeakResults results, MemoryPeakResults results2)
	{
		this.results = verify(results);
		this.results2 = verify(results2);

		if (this.results == null)
			return;

		nmPerUnit = 1;
		DistanceUnit unit = null;
		units = "unknown";

		CalibrationReader cal = results.getCalibrationReader();
		if (cal != null)
		{
			try
			{
				nmPerUnit = cal.getDistanceConverter(DistanceUnit.NM).convert(1);
				units = UnitHelper.getShortName(DistanceUnit.NM);
				unit = DistanceUnit.NM;
			}
			catch (ConversionException e)
			{
				IJ.log(TITLE + " Warning: Ignoring invalid distance calibration for primary results");
			}
		}
		else
		{
			IJ.log(TITLE + " Warning: No calibration exists for primary results");
		}

		// Calibration must match between datasets
		if (this.results2 != null)
		{
			CalibrationReader cal2 = results.getCalibrationReader();
			if (unit == null)
			{
				if (cal2 != null)
				{
					IJ.log(TITLE +
							" Warning: Ignoring calibration for secondary results since no calibration exists for primary results");
				}
			}
			else
			{
				// The calibration must match
				try
				{
					// Try to create a converter and check it is the same conversion
					if (cal2 != null && cal2.getDistanceConverter(DistanceUnit.NM).convert(1) != nmPerUnit)
					{
						// Set to null to mark invalid
						cal2 = null;
					}
				}
				catch (ConversionException e)
				{
					// Set to null to mark invalid
					cal2 = null;
				}
				finally
				{
					if (cal2 == null)
					{
						this.results = null;
						IJ.error(TITLE, "Error: Calibration between the two input datasets does not match");
						return;
					}
				}
			}
		}

		// Use the float data bounds. This prevents problems if the data is far from the origin.
		dataBounds = results.getDataBounds(null);

		if (this.results2 != null)

		{
			Rectangle2D dataBounds2 = results.getDataBounds(null);
			dataBounds = dataBounds.createUnion(dataBounds2);
		}
	}

	/**
	 * Sets the correction parameters for spurious correlation correction. Only relevant for single images.
	 *
	 * @param qValue
	 *            the q value
	 * @param mean
	 *            the mean of the localisation precision
	 * @param sigma
	 *            the standard deviation of the localisation precision
	 */
	public void setCorrectionParameters(double qValue, double mean, double sigma)
	{
		if (qValue > 0 && mean > 0 && sigma > 0)
		{
			correctionQValue = qValue;
			correctionMean = mean;
			correctionSigma = sigma;
		}
		else
		{
			correctionQValue = correctionMean = correctionSigma = 0;
		}
	}

	/**
	 * Shallow copy this instance so skipping initialisation. Only variables required for the FIRE calculation are
	 * copied.
	 *
	 * @return the new FIRE instance
	 */
	private FIRE copy()
	{
		FIRE f = new FIRE();
		f.results = results;
		f.results2 = results2;
		f.nmPerUnit = nmPerUnit;
		f.units = units;
		f.dataBounds = dataBounds;
		f.correctionQValue = correctionQValue;
		f.correctionMean = correctionMean;
		f.correctionSigma = correctionSigma;
		return f;
	}

	/**
	 * Verify the results can be used for FIRE. Results are sorted in time order if the block size is above 1.
	 *
	 * @param results
	 *            the results
	 * @return the memory peak results
	 */
	private MemoryPeakResults verify(MemoryPeakResults results)
	{
		if (results == null || results.size() < 2)
			return null;
		if (blockSize > 1)
			// Results must be in time order when processing blocks
			results.sort();
		return results;
	}

	/**
	 * Creates the images to use for the FIRE calculation. This must be called after
	 * {@link #initialise(MemoryPeakResults, MemoryPeakResults)}.
	 *
	 * @param fourierImageScale
	 *            the fourier image scale (set to zero to auto compute)
	 * @param imageSize
	 *            the image size
	 * @return the fire images
	 */
	public FireImages createImages(double fourierImageScale, int imageSize)
	{
		return createImages(fourierImageScale, imageSize, myUseSignal);
	}

	private interface SignalProvider
	{
		float getSignal(PeakResult p);
	}

	private static class FixedSignalProvider implements SignalProvider
	{
		public float getSignal(PeakResult p)
		{
			return 1f;
		}
	}

	private static class PeakSignalProvider implements SignalProvider
	{
		public float getSignal(PeakResult p)
		{
			return p.getSignal();
		}
	}

	/**
	 * Creates the images to use for the FIRE calculation. This must be called after
	 * {@link #initialise(MemoryPeakResults, MemoryPeakResults)}.
	 *
	 * @param fourierImageScale
	 *            the fourier image scale (set to zero to auto compute)
	 * @param imageSize
	 *            the image size
	 * @param useSignal
	 *            Use the localisation signal to weight the intensity. The default uses a value of 1 per localisation.
	 * @return the fire images
	 */
	public FireImages createImages(double fourierImageScale, int imageSize, boolean useSignal)
	{
		if (results == null)
			return null;

		final SignalProvider signalProvider = (useSignal && (results.hasIntensity())) ? new PeakSignalProvider()
				: new FixedSignalProvider();

		// Draw images using the existing IJ routines.
		Rectangle bounds = new Rectangle(0, 0, (int) Math.ceil(dataBounds.getWidth()),
				(int) Math.ceil(dataBounds.getHeight()));

		boolean weighted = true;
		boolean equalised = false;
		double imageScale;
		if (fourierImageScale <= 0)
		{
			double size = FastMath.max(bounds.width, bounds.height);
			if (size <= 0)
				size = 1;
			imageScale = imageSize / size;
		}
		else
			imageScale = fourierImageScale;

		IJImagePeakResults image1 = ImagePeakResultsFactory.createPeakResultsImage(ResultsImageType.DRAW_NONE, weighted,
				equalised, "IP1", bounds, 1, 1, imageScale, 0, ResultsImageMode.IMAGE_ADD);
		image1.setDisplayImage(false);
		image1.setUncalibrated(true);
		image1.begin();

		IJImagePeakResults image2 = ImagePeakResultsFactory.createPeakResultsImage(ResultsImageType.DRAW_NONE, weighted,
				equalised, "IP2", bounds, 1, 1, imageScale, 0, ResultsImageMode.IMAGE_ADD);
		image2.setDisplayImage(false);
		image1.setUncalibrated(true);
		image2.begin();

		final float minx = (float) dataBounds.getX();
		final float miny = (float) dataBounds.getY();

		if (this.results2 != null)
		{
			// Two image comparison
			final IJImagePeakResults i1 = image1;
			results.forEach(new PeakResultProcedure()
			{
				public void execute(PeakResult p)
				{
					float x = p.getXPosition() - minx;
					float y = p.getYPosition() - miny;
					i1.add(x, y, signalProvider.getSignal(p));
				}
			});
			final IJImagePeakResults i2 = image2;
			results2.forEach(new PeakResultProcedure()
			{
				public void execute(PeakResult p)
				{
					float x = p.getXPosition() - minx;
					float y = p.getYPosition() - miny;
					i2.add(x, y, signalProvider.getSignal(p));
				}
			});
		}
		else
		{
			// Block sampling.
			// Ensure we have at least 2 even sized blocks.
			int blockSize = Math.min(results.size() / 2, Math.max(1, FIRE.blockSize));
			int nBlocks = (int) Math.ceil((double) results.size() / blockSize);
			while (nBlocks <= 1 && blockSize > 1)
			{
				blockSize /= 2;
				nBlocks = (int) Math.ceil((double) results.size() / blockSize);
			}
			if (nBlocks <= 1)
				// This should not happen since the results should contain at least 2 localisations
				return null;
			if (blockSize != FIRE.blockSize)
				IJ.log(TITLE + " Warning: Changed block size to " + blockSize);

			final Counter i = new Counter();
			final Counter block = new Counter();
			final int finalBlockSize = blockSize;
			final PeakResult[][] blocks = new PeakResult[nBlocks][blockSize];
			results.forEach(new PeakResultProcedure()
			{
				public void execute(PeakResult p)
				{
					if (i.getCount() == finalBlockSize)
					{
						block.increment();
						i.reset();
					}
					blocks[block.getCount()][i.getAndIncrement()] = p;
				}
			});
			// Truncate last block
			blocks[block.getCount()] = Arrays.copyOf(blocks[block.getCount()], i.getCount());

			final int[] indices = Utils.newArray(nBlocks, 0, 1);
			if (randomSplit)
				MathArrays.shuffle(indices);

			for (int index : indices)
			{
				// Split alternating so just rotate
				IJImagePeakResults image = image1;
				image1 = image2;
				image2 = image;
				for (PeakResult p : blocks[index])
				{
					float x = p.getXPosition() - minx;
					float y = p.getYPosition() - miny;
					image.add(x, y, signalProvider.getSignal(p));
				}
			}
		}

		image1.end();
		ImageProcessor ip1 = image1.getImagePlus().getProcessor();

		image2.end();
		ImageProcessor ip2 = image2.getImagePlus().getProcessor();

		if (maxPerBin > 0 && signalProvider instanceof FixedSignalProvider)
		{
			// We can eliminate over-sampled pixels
			for (int i = ip1.getPixelCount(); i-- > 0;)
			{
				if (ip1.getf(i) > maxPerBin)
					ip1.setf(i, maxPerBin);
				if (ip2.getf(i) > maxPerBin)
					ip2.setf(i, maxPerBin);
			}
		}

		return new FireImages(ip1, ip2, nmPerUnit / imageScale);
	}

	/**
	 * Encapsulate plotting the FRC curve to allow multiple curves to be plotted together
	 */
	private class FrcCurve
	{
		double[] xValues = null;
		double[] threshold = null;
		Plot2 plot = null;

		void add(String name, FireResult result, ThresholdMethod thresholdMethod, Color colorValues,
				Color colorThreshold, Color colorNoSmooth)
		{
			FRCCurve frcCurve = result.frcCurve;

			double[] yValues = new double[frcCurve.getSize()];

			if (plot == null)
			{
				String title = name + " FRC Curve";
				plot = new Plot2(title, String.format("Spatial Frequency (%s^-1)", units), "FRC");

				xValues = new double[frcCurve.getSize()];
				final double L = frcCurve.fieldOfView;
				final double conversion = 1.0 / (L * result.getNmPerPixel());
				for (int i = 0; i < xValues.length; i++)
				{
					xValues[i] = frcCurve.get(i).getRadius() * conversion;
				}

				// The threshold curve is the same
				threshold = FRC.calculateThresholdCurve(frcCurve, thresholdMethod);
				add(colorThreshold, threshold);
			}

			for (int i = 0; i < xValues.length; i++)
			{
				yValues[i] = frcCurve.get(i).getCorrelation();
			}

			add(colorValues, yValues);
			add(colorNoSmooth, result.originalCorrelationCurve);
		}

		public void addResolution(double resolution)
		{
			// Convert back to nm^-1
			double x = 1 / resolution;

			// Find the intersection with the threshold line
			for (int i = 1; i < xValues.length; i++)
			{
				if (x < xValues[i])
				{
					double correlation;
					// Interpolate
					double upper = xValues[i], lower = xValues[i - 1];
					double xx = (x - lower) / (upper - lower);
					correlation = threshold[i - 1] + xx * (threshold[i] - threshold[i - 1]);
					addResolution(resolution, correlation);
					return;
				}
			}
		}

		public void addResolution(double resolution, double correlation)
		{
			addResolution(resolution, Double.NaN, correlation);
		}

		public void addResolution(double resolution, double originalResolution, double correlation)
		{
			// Convert back to nm^-1
			double x = 1 / resolution;
			plot.setColor(Color.MAGENTA);
			plot.drawLine(x, 0, x, correlation);
			plot.setColor(Color.BLACK);
			if (Double.isNaN(originalResolution))
				plot.addLabel(0, 0, String.format("Resolution = %s %s", Utils.rounded(resolution), units));
			else
				plot.addLabel(0, 0, String.format("Resolution = %s %s (Original = %s %s)", Utils.rounded(resolution),
						units, Utils.rounded(originalResolution), units));
		}

		private void add(Color color, double[] y)
		{
			if (color == null)
				return;
			plot.setColor(color);
			plot.addPoints(xValues, y, Plot2.LINE);
		}

		Plot2 getPlot()
		{
			plot.setLimitsToFit(false);
			// Q. For some reason the limits calculated are ignored,
			// so set them as the defaults.
			double[] limits = plot.getCurrentMinAndMax();
			// The FRC should not go above 1 so limit Y
			plot.setLimits(limits[0], limits[1], Math.min(0, limits[2]), 1.05);
			return plot;
		}
	}

	private Plot2 createFrcCurve(String name, FireResult result, ThresholdMethod thresholdMethod)
	{
		FrcCurve curve = new FrcCurve();
		curve.add(name, result, thresholdMethod, Color.red, Color.blue, Color.black);
		curve.addResolution(result.fireNumber, result.correlation);
		return curve.getPlot();
	}

	private PlotWindow showFrcCurve(String name, FireResult result, ThresholdMethod thresholdMethod)
	{
		return showFrcCurve(name, result, thresholdMethod, 0);
	}

	private PlotWindow showFrcCurve(String name, FireResult result, ThresholdMethod thresholdMethod, int flags)
	{
		Plot2 plot = createFrcCurve(name, result, thresholdMethod);
		return Utils.display(plot.getTitle(), plot, flags);
	}

	private void showFrcTimeEvolution(String name, double fireNumber, ThresholdMethod thresholdMethod,
			double fourierImageScale, int imageSize)
	{
		IJ.showStatus("Calculating FRC time evolution curve...");

		// Sort by time
		results.sort();

		int nSteps = 10;
		int maxT = results.getLastFrame();
		if (maxT == 0)
			maxT = results.size();
		int step = maxT / nSteps;

		TDoubleArrayList x = new TDoubleArrayList();
		TDoubleArrayList y = new TDoubleArrayList();

		double yMin = fireNumber;
		double yMax = fireNumber;

		MemoryPeakResults newResults = new MemoryPeakResults();
		newResults.copySettings(results);
		int i = 0;

		PeakResult[] list = results.toArray();
		for (int t = step; t <= maxT - step; t += step)
		{
			while (i < list.length)
			{
				PeakResult r = list[i];
				if (r.getFrame() <= t)
				{
					newResults.add(r);
					i++;
				}
				else
					break;
			}

			x.add((double) t);

			FIRE f = this.copy();
			FireResult result = f.calculateFireNumber(fourierMethod, samplingMethod, thresholdMethod, fourierImageScale,
					imageSize);
			double fire = (result == null) ? 0 : result.fireNumber;
			y.add(fire);

			yMin = FastMath.min(yMin, fire);
			yMax = FastMath.max(yMax, fire);
		}

		// Add the final fire number
		x.add((double) maxT);
		y.add(fireNumber);

		double[] xValues = x.toArray();
		double[] yValues = y.toArray();

		String units = "px";
		if (results.getCalibration() != null)
		{
			nmPerUnit = results.getNmPerPixel();
			units = "nm";
		}

		String title = name + " FRC Time Evolution";
		Plot2 plot = new Plot2(title, "Frames", "Resolution (" + units + ")", (float[]) null, (float[]) null);
		double range = Math.max(1, yMax - yMin) * 0.05;
		plot.setLimits(xValues[0], xValues[xValues.length - 1], yMin - range, yMax + range);
		plot.setColor(Color.red);
		plot.addPoints(xValues, yValues, Plot.CONNECTED_CIRCLES);

		Utils.display(title, plot);
	}

	/**
	 * Calculate the Fourier Image REsolution (FIRE) number using the chosen threshold method. Should be called after
	 * {@link #initialise(MemoryPeakResults)}
	 *
	 * @param fourierMethod
	 *            the fourier method
	 * @param samplingMethod
	 *            the sampling method
	 * @param thresholdMethod
	 *            the threshold method
	 * @param fourierImageScale
	 *            The scale to use when reconstructing the super-resolution images (0 for auto)
	 * @param imageSize
	 *            The width of the super resolution images when using auto scale (should be a power of two minus 1 for
	 *            optimum memory usage)
	 * @return The FIRE number
	 */
	public FireResult calculateFireNumber(FourierMethod fourierMethod, SamplingMethod samplingMethod,
			ThresholdMethod thresholdMethod, double fourierImageScale, int imageSize)
	{
		FireImages images = createImages(fourierImageScale, imageSize);
		return calculateFireNumber(fourierMethod, samplingMethod, thresholdMethod, images);
	}

	private TrackProgress progress = new IJTrackProgress();

	private void setProgress(int repeats)
	{
		if (repeats > 1)
			progress = new ParallelTrackProgress(repeats);
		else
			progress = new IJTrackProgress();
	}

	/**
	 * Dumb implementation of the track progress interface for parallel threads. Used simple synchronisation to
	 * increment total progress.
	 */
	private static class ParallelTrackProgress extends NullTrackProgress
	{
		double done = 0;
		final int total;

		ParallelTrackProgress(int repeats)
		{
			total = repeats;
		}

		@Override
		public void incrementProgress(double fraction)
		{
			// Avoid synchronisation for nothing
			if (fraction == 0)
				return;
			double done = add(fraction);
			IJ.showProgress(done / this.total);
		}

		synchronized double add(double d)
		{
			done += d;
			return done;
		}
	}

	/**
	 * Calculate the Fourier Image REsolution (FIRE) number using the chosen threshold method. Should be called after
	 * {@link #initialise(MemoryPeakResults)}
	 *
	 * @param fourierMethod
	 *            the fourier method
	 * @param samplingMethod
	 *            the sampling method
	 * @param thresholdMethod
	 *            the threshold method
	 * @param images
	 *            the images
	 * @return The FIRE number
	 */
	public FireResult calculateFireNumber(FourierMethod fourierMethod, SamplingMethod samplingMethod,
			ThresholdMethod thresholdMethod, FireImages images)
	{
		if (images == null)
			return null;

		FRC frc = new FRC();
		// Allow a progress tracker to be input.
		// This should be setup for the total number of repeats. 
		// If parallelised then do not output the text status messages as they conflict. 
		frc.progress = progress;
		frc.setFourierMethod(fourierMethod);
		frc.setSamplingMethod(samplingMethod);
		frc.setPerimeterSamplingFactor(perimeterSamplingFactor);
		FRCCurve frcCurve = frc.calculateFrcCurve(images.ip1, images.ip2, images.nmPerPixel);
		if (frcCurve == null)
			return null;
		if (correctionQValue > 0)
			FRC.applyQCorrection(frcCurve, correctionQValue, correctionMean, correctionSigma);
		double[] originalCorrelationCurve = frcCurve.getCorrelationValues();
		FRC.getSmoothedCurve(frcCurve, true);

		// Resolution in pixels
		FIREResult result = FRC.calculateFire(frcCurve, thresholdMethod);
		if (result == null)
			return new FireResult(Double.NaN, Double.NaN, frcCurve, originalCorrelationCurve);
		double fireNumber = result.fireNumber;

		// The FRC paper states that the super-resolution pixel size should be smaller
		// than 1/4 of R (the resolution).
		if (fireNumber > 0 && (images.nmPerPixel > fireNumber / 4))
		{
			// Q. Should this be output somewhere else?
			Utils.log(
					"%s Warning: The super-resolution pixel size (%s) should be smaller than 1/4 of R (the resolution %s)",
					TITLE, Utils.rounded(images.nmPerPixel), Utils.rounded(fireNumber));
		}

		return new FireResult(fireNumber, result.correlation, frcCurve, originalCorrelationCurve);
	}

	private void runQEstimation()
	{
		IJ.showStatus(TITLE + " ...");

		if (!showQEstimationInputDialog())
			return;

		MemoryPeakResults results = ResultsManager.loadInputResults(inputOption, false);
		if (results == null || results.size() == 0)
		{
			IJ.error(TITLE, "No results could be loaded");
			return;
		}
		if (results.getCalibration() == null)
		{
			IJ.error(TITLE, "The results are not calibrated");
			return;
		}

		results = cropToRoi(results);
		if (results.size() < 2)
		{
			IJ.error(TITLE, "No results within the crop region");
			return;
		}

		initialise(results, null);

		// We need localisation precision.
		// Build a histogram of the localisation precision.
		// Get the initial mean and SD and plot as a Gaussian.
		PrecisionHistogram histogram = calculatePrecisionHistogram();
		if (histogram == null)
		{
			IJ.error(TITLE, "No localisation precision available.\n \nPlease choose " + PrecisionMethod.FIXED +
					" and enter a precision mean and SD.");
			return;
		}
		StoredDataStatistics precision = histogram.precision;

		//String name = results.getName();
		double fourierImageScale = SCALE_VALUES[imageScaleIndex];
		int imageSize = IMAGE_SIZE_VALUES[imageSizeIndex];

		// Create the image and compute the numerator of FRC. 
		// Do not use the signal so results.size() is the number of localisations.
		IJ.showStatus("Computing FRC curve ...");
		FireImages images = createImages(fourierImageScale, imageSize, false);

		// DEBUGGING - Save the two images to disk. Load the images into the Matlab 
		// code that calculates the Q-estimation and make this plugin match the functionality.
		//IJ.save(new ImagePlus("i1", images.ip1), "/scratch/i1.tif");
		//IJ.save(new ImagePlus("i2", images.ip2), "/scratch/i2.tif");

		FRC frc = new FRC();
		frc.progress = progress;
		frc.setFourierMethod(fourierMethod);
		frc.setSamplingMethod(samplingMethod);
		frc.setPerimeterSamplingFactor(perimeterSamplingFactor);
		FRCCurve frcCurve = frc.calculateFrcCurve(images.ip1, images.ip2, images.nmPerPixel);
		if (frcCurve == null)
		{
			IJ.error(TITLE, "Failed to compute FRC curve");
			return;
		}

		IJ.showStatus("Running Q-estimation ...");

		// Note:
		// The method implemented here is based on Matlab code provided by Bernd Rieger.
		// The idea is to compute the spurious correlation component of the FRC Numerator
		// using an initial estimate of distribution of the localisation precision (assumed 
		// to be Gaussian). This component is the contribution of repeat localisations of 
		// the same molecule to the numerator and is modelled as an exponential decay
		// (exp_decay). The component is scaled by the Q-value which
		// is the average number of times a molecule is seen in addition to the first time.
		// At large spatial frequencies the scaled component should match the numerator,
		// i.e. at high resolution (low FIRE number) the numerator is made up of repeat 
		// localisations of the same molecule and not actual structure in the image.
		// The best fit is where the numerator equals the scaled component, i.e. num / (q*exp_decay) == 1.
		// The FRC Numerator is plotted and Q can be determined by
		// adjusting Q and the precision mean and SD to maximise the cost function.
		// This can be done interactively by the user with the effect on the FRC curve
		// dynamically updated and displayed.

		// Compute the scaled FRC numerator
		double qNorm = (1 / frcCurve.mean1 + 1 / frcCurve.mean2);
		double[] frcnum = new double[frcCurve.getSize()];
		for (int i = 0; i < frcnum.length; i++)
		{
			FRCCurveResult r = frcCurve.get(i);
			frcnum[i] = qNorm * r.getNumerator() / r.getNumberOfSamples();
		}

		// Compute the spatial frequency and the region for curve fitting
		double[] q = FRC.computeQ(frcCurve, false);
		int low = 0, high = q.length;
		while (high > 0 && q[high - 1] > maxQ)
			high--;
		while (low < q.length && q[low] < minQ)
			low++;
		// Require we fit at least 10% of the curve
		if (high - low < q.length * 0.1)
		{
			IJ.error(TITLE, "Not enough points for Q estimation");
			return;
		}

		// Obtain initial estimate of Q plateau height and decay.
		// This can be done by fitting the precision histogram and then fixing the mean and sigma.
		// Or it can be done by allowing the precision to be sampled and the mean and sigma
		// become parameters for fitting.

		// Check if we can sample precision values
		boolean sampleDecay = precision != null && FIRE.sampleDecay;

		double[] exp_decay;
		if (sampleDecay)
		{
			// Random sample of precision values from the distribution is used to 
			// construct the decay curve
			int[] sample = Random.sample(10000, precision.getN(), new Well19937c());

			final double four_pi2 = 4 * Math.PI * Math.PI;
			double[] pre = new double[q.length];
			for (int i = 1; i < q.length; i++)
				pre[i] = -four_pi2 * q[i] * q[i];

			// Sample
			final int n = sample.length;
			double[] hq = new double[n];
			for (int j = 0; j < n; j++)
			{
				// Scale to SR pixels
				double s2 = precision.getValue(sample[j]) / images.nmPerPixel;
				s2 *= s2;
				for (int i = 1; i < q.length; i++)
					hq[i] += FastMath.exp(pre[i] * s2);
			}
			for (int i = 1; i < q.length; i++)
				hq[i] /= n;

			exp_decay = new double[q.length];
			exp_decay[0] = 1;
			for (int i = 1; i < q.length; i++)
			{
				double sinc_q = sinc(Math.PI * q[i]);
				exp_decay[i] = sinc_q * sinc_q * hq[i];
			}
		}
		else
		{
			// Note: The sigma mean and std should be in the units of super-resolution 
			// pixels so scale to SR pixels
			exp_decay = computeExpDecay(histogram.mean / images.nmPerPixel, histogram.sigma / images.nmPerPixel, q);
		}

		// Smoothing
		double[] smooth;
		if (loessSmoothing)
		{
			// Note: This computes the log then smooths it 
			double bandwidth = 0.1;
			int robustness = 0;
			double[] l = new double[exp_decay.length];
			for (int i = 0; i < l.length; i++)
			{
				// Original Matlab code computes the log for each array.
				// This is equivalent to a single log on the fraction of the two.
				// Perhaps the two log method is more numerically stable.
				//l[i] = Math.log(Math.abs(frcnum[i])) - Math.log(exp_decay[i]);
				l[i] = Math.log(Math.abs(frcnum[i] / exp_decay[i]));
			}
			try
			{
				LoessInterpolator loess = new LoessInterpolator(bandwidth, robustness);
				smooth = loess.smooth(q, l);
			}
			catch (Exception e)
			{
				IJ.error(TITLE, "LOESS smoothing failed");
				return;
			}
		}
		else
		{
			// Note: This smooths the curve before computing the log 

			double[] norm = new double[exp_decay.length];
			for (int i = 0; i < norm.length; i++)
			{
				norm[i] = frcnum[i] / exp_decay[i];
			}
			// Median window of 5 == radius of 2
			MedianWindow mw = new MedianWindow(norm, 2);
			smooth = new double[exp_decay.length];
			for (int i = 0; i < norm.length; i++)
			{
				smooth[i] = Math.log(Math.abs(mw.getMedian()));
				mw.increment();
			}
		}

		// Fit with quadratic to find the initial guess.
		// Note: example Matlab code frc_Qcorrection7.m identifies regions of the 
		// smoothed log curve with low derivative and only fits those. The fit is 
		// used for the final estimate. Fitting a subset with low derivative is not 
		// implemented here since the initial estimate is subsequently optimised 
		// to maximise a cost function. 
		Quadratic curve = new Quadratic();
		SimpleCurveFitter fit = SimpleCurveFitter.create(curve, new double[2]);
		WeightedObservedPoints points = new WeightedObservedPoints();
		for (int i = low; i < high; i++)
			points.add(q[i], smooth[i]);
		double[] estimate = fit.fit(points.toList());
		double qValue = FastMath.exp(estimate[0]);

		//System.out.printf("Initial q-estimate = %s => %.3f\n", Arrays.toString(estimate), qValue);

		// This could be made an option. Just use for debugging
		boolean debug = false;
		if (debug)
		{
			// Plot the initial fit and the fit curve
			double[] qScaled = FRC.computeQ(frcCurve, true);
			double[] line = new double[q.length];
			for (int i = 0; i < q.length; i++)
				line[i] = curve.value(q[i], estimate);
			String title = TITLE + " Initial fit";
			Plot2 plot = new Plot2(title, "Spatial Frequency (nm^-1)", "FRC Numerator");
			String label = String.format("Q = %.3f", qValue);
			plot.addPoints(qScaled, smooth, Plot.LINE);
			plot.setColor(Color.red);
			plot.addPoints(qScaled, line, Plot.LINE);
			plot.setColor(Color.black);
			plot.addLabel(0, 0, label);
			Utils.display(title, plot, Utils.NO_TO_FRONT);
		}

		if (fitPrecision)
		{
			// Q - Should this be optional?
			if (sampleDecay)
			{
				// If a sample of the precision was used to construct the data for the initial fit 
				// then update the estimate using the fit result since it will be a better start point. 

				histogram.sigma = precision.getStandardDeviation();
				// Normalise sum-of-squares to the SR pixel size
				double meanSumOfSquares = (precision.getSumOfSquares() / (images.nmPerPixel * images.nmPerPixel)) /
						precision.getN();
				histogram.mean = images.nmPerPixel *
						Math.sqrt(meanSumOfSquares - estimate[1] / (4 * Math.PI * Math.PI));
			}

			// Do a multivariate fit ...
			SimplexOptimizer opt = new SimplexOptimizer(1e-6, 1e-10);
			PointValuePair p = null;
			MultiPlateauness f = new MultiPlateauness(frcnum, q, low, high);
			double[] initial = new double[] { histogram.mean / images.nmPerPixel, histogram.sigma / images.nmPerPixel,
					qValue };
			p = findMin(p, opt, f, scale(initial, 0.1));
			p = findMin(p, opt, f, scale(initial, 0.5));
			p = findMin(p, opt, f, initial);
			p = findMin(p, opt, f, scale(initial, 2));
			p = findMin(p, opt, f, scale(initial, 10));

			if (p != null)
			{
				double[] point = p.getPointRef();
				histogram.mean = point[0] * images.nmPerPixel;
				histogram.sigma = point[1] * images.nmPerPixel;
				qValue = point[2];
			}
		}
		else
		{
			// Reset to theoretical curve. This is what will be used to compute the final correction.
			// TODO - check if the Matlab code uses a sampled curve to compute the correction. 
			// If so then this should be optional.
			if (sampleDecay)
			{
				// If a sample of the precision was used to construct the data for the initial fit 
				// then update the estimate using the fit result since it will be a better start point. 

				if (precisionMethod != PrecisionMethod.FIXED)
				{
					histogram.sigma = precision.getStandardDeviation();
					// Normalise sum-of-squares to the SR pixel size
					double meanSumOfSquares = (precision.getSumOfSquares() / (images.nmPerPixel * images.nmPerPixel)) /
							precision.getN();
					histogram.mean = images.nmPerPixel *
							Math.sqrt(meanSumOfSquares - estimate[1] / (4 * Math.PI * Math.PI));
				}

				exp_decay = computeExpDecay(histogram.mean / images.nmPerPixel, histogram.sigma / images.nmPerPixel, q);
			}

			// Estimate spurious component by promoting plateauness.
			// The Matlab code used random initial points for a Simplex optimiser.

			// A Brent line search should be pretty deterministic so do simple repeats.
			// However it will proceed downhill so if the initial point is wrong then 
			// it will find a sub-optimal result.
			UnivariateOptimizer o = new BrentOptimizer(1e-3, 1e-6);
			Plateauness f = new Plateauness(frcnum, exp_decay, low, high);
			UnivariatePointValuePair p = null;
			p = findMin(p, o, f, qValue, 0.1);
			p = findMin(p, o, f, qValue, 0.2);
			p = findMin(p, o, f, qValue, 0.333);
			p = findMin(p, o, f, qValue, 0.5);

			// Do some Simplex repeats as well
			SimplexOptimizer opt = new SimplexOptimizer(1e-6, 1e-10);
			p = findMin(p, opt, f, qValue * 0.1);
			p = findMin(p, opt, f, qValue * 0.5);
			p = findMin(p, opt, f, qValue);
			p = findMin(p, opt, f, qValue * 2);
			p = findMin(p, opt, f, qValue * 10);

			if (p != null)
				qValue = p.getPoint();
		}

		QPlot qplot = new QPlot(frcCurve, qValue, low, high);

		// Interactive dialog to estimate Q (blinking events per flourophore) using 
		// sliders for the mean and standard deviation of the localisation precision.
		showQEstimationDialog(histogram, qplot, frcCurve, images.nmPerPixel);

		IJ.showStatus(TITLE + " complete");
	}

	private double[] scale(double[] a, double f)
	{
		a = a.clone();
		for (int i = 0; i < a.length; i++)
			a[i] *= f;
		return a;
	}

	private double[] computeExpDecay(double mean, double sigma, double[] q)
	{
		double[] hq = FRC.computeHq(q, mean, sigma);
		double[] exp_decay = new double[q.length];
		exp_decay[0] = 1;
		for (int i = 1; i < q.length; i++)
		{
			double sinc_q = sinc(Math.PI * q[i]);
			exp_decay[i] = sinc_q * sinc_q * hq[i];
		}
		return exp_decay;
	}

	/**
	 * Compute the Sinc function.
	 *
	 * @param d
	 *            the d
	 * @return the sinc value
	 */
	private static double sinc(double d)
	{
		return Math.sin(d) / d;
	}

	private class Quadratic implements ParametricUnivariateFunction
	{
		public double value(double x, double... parameters)
		{
			return parameters[0] + parameters[1] * x * x;
		}

		public double[] gradient(double x, double... parameters)
		{
			return new double[] { 1, x * x };
		}
	}

	private UnivariatePointValuePair findMin(UnivariatePointValuePair current, UnivariateOptimizer o,
			UnivariateFunction f, double qValue, double factor)
	{
		try
		{
			BracketFinder bracket = new BracketFinder();
			bracket.search(f, GoalType.MINIMIZE, qValue * factor, qValue / factor);
			UnivariatePointValuePair next = o.optimize(GoalType.MINIMIZE, new MaxEval(3000),
					new SearchInterval(bracket.getLo(), bracket.getHi(), bracket.getMid()),
					new UnivariateObjectiveFunction(f));
			if (next == null)
				return current;
			//System.out.printf("LineMin [%.1f]  %f = %f\n", factor, next.getPoint(), next.getValue());
			if (current != null)
				return (next.getValue() < current.getValue()) ? next : current;
			return next;
		}
		catch (Exception e)
		{
			return current;
		}
	}

	private UnivariatePointValuePair findMin(UnivariatePointValuePair current, SimplexOptimizer o,
			MultivariateFunction f, double qValue)
	{
		try
		{
			NelderMeadSimplex simplex = new NelderMeadSimplex(1);
			double[] initialSolution = { qValue };
			PointValuePair solution = o.optimize(new MaxEval(1000), new InitialGuess(initialSolution), simplex,
					new ObjectiveFunction(f), GoalType.MINIMIZE);
			UnivariatePointValuePair next = (solution == null) ? null
					: new UnivariatePointValuePair(solution.getPointRef()[0], solution.getValue());
			if (next == null)
				return current;
			//System.out.printf("Simplex [%f]  %f = %f\n", qValue, next.getPoint(), next.getValue());
			if (current != null)
				return (next.getValue() < current.getValue()) ? next : current;
			return next;
		}
		catch (Exception e)
		{
			return current;
		}
	}

	private PointValuePair findMin(PointValuePair current, SimplexOptimizer o, MultivariateFunction f,
			double[] initialSolution)
	{
		try
		{
			NelderMeadSimplex simplex = new NelderMeadSimplex(initialSolution.length);
			PointValuePair next = o.optimize(new MaxEval(1000), new InitialGuess(initialSolution), simplex,
					new ObjectiveFunction(f), GoalType.MINIMIZE);
			if (next == null)
				return current;
			//System.out.printf("MultiSimplex [%s]  %s = %f\n", Arrays.toString(initialSolution),
			//		Arrays.toString(next.getPointRef()), next.getValue());
			if (current != null)
				return (next.getValue() < current.getValue()) ? next : current;
			return next;
		}
		catch (Exception e)
		{
			return current;
		}
	}

	private class Plateauness implements UnivariateFunction, MultivariateFunction
	{
		final double frcnum_noisevar = 0.1;
		final double[] pre;
		final double n2;

		/**
		 * Instantiates a new plateauness.
		 *
		 * @param frcnum
		 *            the scaled FRC numerator
		 * @param exp_decay
		 *            the precomputed exponential decay (hq)
		 * @param low
		 *            the lower bound of the array for optimisation
		 * @param high
		 *            the higher bound of the array for optimisation
		 */
		Plateauness(double[] frcnum, double[] exp_decay, int low, int high)
		{
			// Precompute
			pre = new double[high - low];
			for (int i = 0; i < pre.length; i++)
			{
				int index = i + low;
				pre[i] = frcnum[index] / exp_decay[index];
			}
			n2 = frcnum_noisevar * frcnum_noisevar;
		}

		public double value(double qValue)
		{
			if (qValue < 1e-16)
				qValue = 1e-16;
			double v = 0;
			for (int i = 0; i < pre.length; i++)
			{
				// Original cost function. Note that each observation has a 
				// contribution of 0 to 1.
				double diff = (pre[i] / qValue) - 1;
				v += 1 - FastMath.exp(-diff * diff / n2);

				// Modified cost function so that the magnitude of difference over or 
				// under 1 is penalised the same. This has a problem if FRC numerator 
				// is negative. Also the range is unchecked so observation can have 
				// unequal contributions.
				//double diff = Math.abs(pre[i]) / qValue;
				//v += Math.abs(Math.log(diff));
			}
			return v;
		}

		public double value(double[] point) throws IllegalArgumentException
		{
			return value(point[0]);
		}
	}

	private class MultiPlateauness implements MultivariateFunction
	{
		final double frcnum_noisevar = 0.1;
		final double[] pre, q2;
		final double n2;
		final double four_pi2 = 4 * Math.PI * Math.PI;

		@SuppressWarnings("unused")
		final double[] q;
		@SuppressWarnings("unused")
		final int low;

		/**
		 * Instantiates a new plateauness.
		 *
		 * @param frcnum
		 *            the scaled FRC numerator
		 * @param exp_decay
		 *            the precomputed exponential decay (hq)
		 * @param low
		 *            the lower bound of the array for optimisation
		 * @param high
		 *            the higher bound of the array for optimisation
		 */
		MultiPlateauness(double[] frcnum, double[] q, int low, int high)
		{
			this.q = q;
			this.low = low;

			q2 = new double[q.length];

			// Precompute
			pre = new double[high - low];
			for (int i = 0; i < pre.length; i++)
			{
				int index = i + low;
				double sinc_q = (index == 0) ? 1 : sinc(Math.PI * q[index]);
				pre[i] = frcnum[index] / (sinc_q * sinc_q);
				q2[i] = q[index] * q[index];
			}
			n2 = frcnum_noisevar * frcnum_noisevar;
		}

		public double value(double[] point) throws IllegalArgumentException
		{
			double mean = point[0];
			double sigma = point[1];
			double qValue = point[2];

			if (qValue < 1e-16)
				qValue = 1e-16;

			// Fast computation of a subset of hq
			double eight_pi2_s2 = 2 * four_pi2 * sigma * sigma;
			double factor = -four_pi2 * mean * mean;

			// Check 
			//double[] hq2 = FRC.computeHq(q, mean, sigma);

			double v = 0;
			for (int i = 0; i < pre.length; i++)
			{
				double d = 1 + eight_pi2_s2 * q2[i];
				double hq = FastMath.exp((factor * q2[i]) / d) / Math.sqrt(d);

				// Check
				//if (hq != hq2[i + low])
				//	System.out.printf("hq error: %f != %f\n", hq, hq2[i + low]);

				// Original cost function. Note that each observation has a 
				// contribution of 0 to 1.
				double diff = (pre[i] / (qValue * hq)) - 1;
				v += 1 - FastMath.exp(-diff * diff / n2);
			}
			return v;
		}
	}

	private boolean showQEstimationInputDialog()
	{
		ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);
		gd.addHelp(About.HELP_URL);

		// Build a list of all images with a region ROI
		List<String> titles = new LinkedList<String>();
		if (WindowManager.getWindowCount() > 0)
		{
			for (int imageID : WindowManager.getIDList())
			{
				ImagePlus imp = WindowManager.getImage(imageID);
				if (imp != null && imp.getRoi() != null && imp.getRoi().isArea())
					titles.add(imp.getTitle());
			}
		}

		gd.addMessage("Estimate the blinking correction parameter Q for Fourier Ring Correlation");

		ResultsManager.addInput(gd, inputOption, InputSource.MEMORY);
		if (!titles.isEmpty())
			gd.addCheckbox((titles.size() == 1) ? "Use_ROI" : "Choose_ROI", chooseRoi);

		gd.addMessage("Image construction options:");
		//gd.addCheckbox("Use_signal (if present)", useSignal);
		gd.addChoice("Image_scale", SCALE_ITEMS, SCALE_ITEMS[imageScaleIndex]);
		gd.addChoice("Auto_image_size", IMAGE_SIZE_ITEMS, IMAGE_SIZE_ITEMS[imageSizeIndex]);
		gd.addNumericField("Block_size", blockSize, 0);
		gd.addCheckbox("Random_split", randomSplit);
		gd.addNumericField("Max_per_bin", maxPerBin, 0);

		gd.addMessage("Fourier options:");
		String[] fourierMethodNames = SettingsManager.getNames((Object[]) FRC.FourierMethod.values());
		gd.addChoice("Fourier_method", fourierMethodNames, fourierMethodNames[fourierMethodIndex]);
		String[] samplingMethodNames = SettingsManager.getNames((Object[]) FRC.SamplingMethod.values());
		gd.addChoice("Sampling_method", samplingMethodNames, samplingMethodNames[samplingMethodIndex]);
		gd.addSlider("Sampling_factor", 0.2, 4, perimeterSamplingFactor);

		gd.addMessage("Estimation options:");
		String[] thresholdMethodNames = SettingsManager.getNames((Object[]) FRC.ThresholdMethod.values());
		gd.addChoice("Threshold_method", thresholdMethodNames, thresholdMethodNames[thresholdMethodIndex]);
		String[] precisionMethodNames = SettingsManager.getNames((Object[]) PrecisionMethod.values());
		gd.addChoice("Precision_method", precisionMethodNames, precisionMethodNames[precisionMethodIndex]);
		gd.addNumericField("Precision_Mean", mean, 2, 6, "nm");
		gd.addNumericField("Precision_Sigma", sigma, 2, 6, "nm");
		gd.addCheckbox("Sample_decay", sampleDecay);
		gd.addCheckbox("LOESS_smoothing", loessSmoothing);
		gd.addCheckbox("Fit_precision", fitPrecision);
		gd.addSlider("MinQ", 0, 0.4, minQ);
		gd.addSlider("MaxQ", 0.1, 0.5, maxQ);

		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		inputOption = ResultsManager.getInputSource(gd);
		if (!titles.isEmpty())
			chooseRoi = gd.getNextBoolean();

		//useSignal = gd.getNextBoolean();
		imageScaleIndex = gd.getNextChoiceIndex();
		imageSizeIndex = gd.getNextChoiceIndex();
		blockSize = Math.max(1, (int) gd.getNextNumber());
		randomSplit = gd.getNextBoolean();
		maxPerBin = Math.abs((int) gd.getNextNumber());

		fourierMethodIndex = gd.getNextChoiceIndex();
		fourierMethod = FourierMethod.values()[fourierMethodIndex];
		samplingMethodIndex = gd.getNextChoiceIndex();
		samplingMethod = SamplingMethod.values()[samplingMethodIndex];
		perimeterSamplingFactor = gd.getNextNumber();

		thresholdMethodIndex = gd.getNextChoiceIndex();
		thresholdMethod = FRC.ThresholdMethod.values()[thresholdMethodIndex];
		precisionMethodIndex = gd.getNextChoiceIndex();
		precisionMethod = PrecisionMethod.values()[precisionMethodIndex];
		mean = Math.abs(gd.getNextNumber());
		sigma = Math.abs(gd.getNextNumber());
		sampleDecay = gd.getNextBoolean();
		loessSmoothing = gd.getNextBoolean();
		fitPrecision = gd.getNextBoolean();
		minQ = Maths.clip(0, 0.5, gd.getNextNumber());
		maxQ = Maths.clip(0, 0.5, gd.getNextNumber());

		// Check arguments
		try
		{
			Parameters.isAboveZero("Perimeter sampling factor", perimeterSamplingFactor);
			if (precisionMethod == PrecisionMethod.FIXED)
			{
				Parameters.isAboveZero("Precision Mean", mean);
				Parameters.isAboveZero("Precision Sigma", sigma);
			}
			Parameters.isAbove("MaxQ", maxQ, minQ);
		}
		catch (IllegalArgumentException e)
		{
			IJ.error(TITLE, e.getMessage());
			return false;
		}

		if (!titles.isEmpty() && chooseRoi)
		{
			if (titles.size() == 1)
			{
				roiImage = titles.get(0);
				Recorder.recordOption("Image", roiImage);
			}
			else
			{
				String[] items = titles.toArray(new String[titles.size()]);
				gd = new ExtendedGenericDialog(TITLE);
				gd.addMessage("Select the source image for the ROI");
				gd.addChoice("Image", items, roiImage);
				gd.showDialog();
				if (gd.wasCanceled())
					return false;
				roiImage = gd.getNextChoice();
			}
			ImagePlus imp = WindowManager.getImage(roiImage);

			roiBounds = imp.getRoi().getBounds();
			roiImageWidth = imp.getWidth();
			roiImageHeight = imp.getHeight();
		}
		else
		{
			roiBounds = null;
		}

		return true;
	}

	public class QPlot
	{
		final FRCCurve frcCurve;
		final double nmPerPixel, qNorm;
		final double[] vq, sinc2, q, qScaled;
		final int low, high;
		String title, title2;
		FireResult originalFireResult;
		double originalFireNumber = Double.NaN;

		// Store the last plotted value
		double mean, sigma, qValue;

		QPlot(FRCCurve frcCurve, double qValue, int low, int high)
		{
			this.nmPerPixel = frcCurve.nmPerPixel;
			this.frcCurve = frcCurve;
			this.qValue = qValue;
			this.low = low;
			this.high = high;

			// For normalisation
			qNorm = (1 / frcCurve.mean1 + 1 / frcCurve.mean2);

			// Compute v(q) - The numerator of the FRC divided by the number of pixels 
			// in the Fourier circle (2*pi*q*L)
			vq = new double[frcCurve.getSize()];
			for (int i = 0; i < vq.length; i++)
			{
				vq[i] = qNorm * frcCurve.get(i).getNumerator() / frcCurve.get(i).getNumberOfSamples();
			}

			q = FRC.computeQ(frcCurve, false);
			// For the plot
			qScaled = FRC.computeQ(frcCurve, true);

			// Compute sinc factor
			sinc2 = new double[frcCurve.getSize()];
			sinc2[0] = 1; // By definition
			for (int i = 1; i < sinc2.length; i++)
			{
				// Note the original equation given in the paper: sinc(pi*q*L)^2 is a typo.
				// Matlab code provided by Bernd Rieger removes L to compute: sinc(pi*q)^2
				// with q == 1/L, 2/L, ... (i.e. no unit conversion to nm). This means that 
				// the function will start at 1 and drop off to zero at q=L.

				// sinc(pi*q)^2
				sinc2[i] = sinc(Math.PI * q[i]);
				sinc2[i] *= sinc2[i];
			}

			// For the plot
			title = results.getName() + " FRC Numerator Curve";
			title2 = results.getName() + " FRC Numerator/Correction Ratio";

			// Reset
			FRC.applyQCorrection(frcCurve, 0, 0, 0);
			FRCCurve smoothedFrcCurve = FRC.getSmoothedCurve(frcCurve, false);
			originalFireResult = new FireResult(0, 0, smoothedFrcCurve, frcCurve.getCorrelationValues());
			FIREResult result = FRC.calculateFire(smoothedFrcCurve, thresholdMethod);
			if (result != null)
			{
				originalFireNumber = result.fireNumber;
			}
		}

		private double sinc(double x)
		{
			return Math.sin(x) / x;
		}

		PlotWindow[] plot(double mean, double sigma, double qValue)
		{
			this.mean = mean;
			this.sigma = sigma;
			this.qValue = qValue;

			double mu = mean / nmPerPixel;
			double sd = sigma / nmPerPixel;

			double[] hq = FRC.computeHq(q, mu, sd);
			double[] correction = new double[hq.length];
			double[] vq_corr = new double[vq.length];
			double[] ratio = new double[vq.length];

			for (int i = 0; i < hq.length; i++)
			{
				// Note: vq already has the qNorm factor applied so we do not 
				// divide qValue by qNorm.
				correction[i] = qValue * sinc2[i] * hq[i];
				// This is not actually the corrected numerator since it is made absolute
				//vq_corr[i] = Math.abs(vq[i] - correction[i]);
				vq_corr[i] = vq[i] - correction[i];
				ratio[i] = vq[i] / correction[i];
			}

			// Add this to aid is manual adjustment
			double plateauness = computePlateauness(qValue, mu, sd);

			Plot2 plot = new Plot2(title, "Spatial Frequency (nm^-1)", "FRC Numerator");

			String label = String.format("Q = %.3f (Precision = %.3f +/- %.3f)", qValue, mean, sigma);
			plot.setColor(Color.red);
			double[] vq = makeStrictlyPositive(this.vq, Double.POSITIVE_INFINITY);
			plot.addPoints(qScaled, vq, Plot.LINE);
			double min = Maths.min(vq);
			if (qValue > 0)
			{
				label += String.format(". Cost = %.3f", plateauness);
				plot.setColor(Color.darkGray);
				plot.addPoints(qScaled, correction, Plot.DOT);
				plot.setColor(Color.blue);
				plot.addPoints(qScaled, makeStrictlyPositive(vq_corr, min), Plot.LINE);
				plot.setColor(Color.black);
				plot.addLegend("Numerator\nCorrection\nCorrected Numerator", "top-right");
			}
			plot.setColor(Color.magenta);
			plot.drawLine(qScaled[low], min, qScaled[low], vq[0]);
			plot.drawLine(qScaled[high], min, qScaled[high], vq[0]);
			plot.setColor(Color.black);
			plot.addLabel(0, 0, label);

			plot.setAxisYLog(true);
			PlotWindow pw1 = Utils.display(title, plot, Utils.NO_TO_FRONT);
			plot.setLimitsToFit(true); // For the log scale this seems to only work after drawing

			// Show how the resolution changes

			FRC.applyQCorrection(frcCurve, qValue, mean, sigma);
			FRCCurve smoothedFrcCurve = FRC.getSmoothedCurve(frcCurve, false);

			// Resolution in pixels
			FIREResult result = FRC.calculateFire(smoothedFrcCurve, thresholdMethod);
			PlotWindow pw2 = null;
			if (result != null)
			{
				double fireNumber = result.fireNumber;

				FrcCurve curve = new FrcCurve();
				FireResult fireResult = new FireResult(fireNumber, result.correlation, smoothedFrcCurve,
						frcCurve.getCorrelationValues());
				double orig = Double.NaN;
				if (qValue > 0)
				{
					curve.add(results.getName(), originalFireResult, thresholdMethod, Color.orange, Color.blue,
							Color.lightGray);
					orig = originalFireNumber;
				}
				curve.add(results.getName(), fireResult, thresholdMethod, Color.red, Color.blue, Color.black);
				curve.addResolution(fireNumber, orig, result.correlation);
				plot = curve.getPlot();

				pw2 = Utils.display(plot.getTitle(), plot, Utils.NO_TO_FRONT);
			}

			// Produce a ratio plot. Plateauness is designed to achieve a value of 1 for this ratio. 
			plot = new Plot2(title2, "Spatial Frequency (nm^-1)", "FRC Numerator / Spurious component");
			double xMax = qScaled[qScaled.length - 1];
			if (qValue > 0)
			{
				plot.addLabel(0, 0, String.format("Cost = %.3f", plateauness));
				plot.setColor(Color.blue);
				plot.addPoints(qScaled, ratio, Plot.LINE);
			}
			plot.setColor(Color.black);
			plot.drawLine(0, 1, xMax, 1);
			plot.setColor(Color.magenta);
			plot.drawLine(qScaled[low], 0, qScaled[low], 2);
			plot.drawLine(qScaled[high], 0, qScaled[high], 2);
			plot.setLimits(0, xMax, 0, 2);
			PlotWindow pw3 = Utils.display(title2, plot, Utils.NO_TO_FRONT);

			return new PlotWindow[] { pw1, pw2, pw3 };
		}

		private double computePlateauness(double qValue, double mu, double sd)
		{
			double[] exp_decay = computeExpDecay(mu, sd, q);
			Plateauness p = new Plateauness(vq, exp_decay, low, high);
			double plateauness = p.value(qValue);
			return plateauness;
		}

		private double[] makeStrictlyPositive(double[] data, double min)
		{
			data = data.clone();
			if (min == Double.POSITIVE_INFINITY)
			{
				// Find min positive value
				for (int i = 0; i < data.length; i++)
					if (data[i] > 0 && data[i] < min)
						min = data[i];
			}
			for (int i = 0; i < data.length; i++)
				if (data[i] < min)
					data[i] = min;
			return data;
		}
	}

	public class PrecisionHistogram
	{
		final float[] x, y;
		final String title;
		final double standardAmplitude;
		final float[] x2;
		final StoredDataStatistics precision;

		/**
		 * The mean of the localisation precision distribution (in nm). This value can be updated by the
		 * {@link #plot(double, double)} method.
		 */
		double mean;

		/**
		 * The standard deviation of the localisation precision distribution (in nm). This value can be updated by the
		 * {@link #plot(double, double)} method.
		 */
		double sigma;

		PrecisionHistogram(float[][] hist, StoredDataStatistics precision, String title)
		{
			this.title = title;
			x = Utils.createHistogramAxis(hist[0]);
			y = Utils.createHistogramValues(hist[1]);
			this.precision = precision;

			// Sum the area under the histogram to use for normalisation.
			// Amplitude = volume / (sigma * sqrt(2*pi)) 
			// Precompute the correct amplitude for a standard width Gaussian
			double dx = (hist[0][1] - hist[0][0]);
			standardAmplitude = precision.getN() * dx / Math.sqrt(2 * Math.PI);

			// Set up for drawing the Gaussian curve
			double min = x[0];
			double max = x[x.length - 1];
			int n = 100;
			dx = (max - min) / n;
			x2 = new float[n + 1];
			for (int i = 0; i <= n; i++)
				x2[i] = (float) (min + i * dx);
		}

		public PrecisionHistogram(String title)
		{
			this.title = title;
			// Set some defaults
			this.mean = 20;
			this.sigma = 2;
			x = y = x2 = null;
			precision = null;
			standardAmplitude = 0;
		}

		PlotWindow plot(double mean, double sigma)
		{
			this.mean = mean;
			this.sigma = sigma;
			return plot();
		}

		PlotWindow plot()
		{
			Plot2 plot = new Plot2(title, "Precision (nm)", "Frequency");
			if (x != null)
			{
				plot.setColor(Color.black);
				plot.addPoints(x, y, Plot.LINE);
				plot.addLabel(0, 0, String.format("Precision = %.3f +/- %.3f", mean, sigma));
				// Add the Gaussian line
				// Compute the integral of the standard gaussian between the min and max
				final double denom0 = 1.0 / (Math.sqrt(2.0) * sigma);
				double integral = 0.5 * Erf.erf((x2[0] - mean) * denom0, (x2[x2.length - 1] - mean) * denom0);
				// Normalise so the integral has the same volume as the histogram
				Gaussian g = new Gaussian(this.standardAmplitude / (sigma * integral), mean, sigma);
				float[] y2 = new float[x2.length];
				for (int i = 0; i < y2.length; i++)
				{
					y2[i] = (float) g.value(x2[i]);
				}
				// Normalise
				plot.setColor(Color.red);
				plot.addPoints(x2, y2, Plot.LINE);
				float max = Maths.max(y2);
				max = Maths.maxDefault(max, y);
				double rangex = 0; //(x2[x2.length - 1] - x2[0]) * 0.025;
				plot.setLimits(x2[0] - rangex, x2[x2.length - 1] + rangex, 0, max * 1.05);
			}
			else
			{
				// There is no base histogram.
				// Just plot a Gaussian +/- 4 SD.
				plot.addLabel(0, 0, String.format("Precision = %.3f +/- %.3f", mean, sigma));
				double min = Math.max(0, mean - 4 * sigma);
				double max = mean + 4 * sigma;
				int n = 100;
				double dx = (max - min) / n;
				float[] x2 = new float[n + 1];
				Gaussian g = new Gaussian(1, mean, sigma);
				float[] y2 = new float[x2.length];
				for (int i = 0; i <= n; i++)
				{
					x2[i] = (float) (min + i * dx);
					y2[i] = (float) g.value(x2[i]);
				}
				plot.setColor(Color.red);
				plot.addPoints(x2, y2, Plot.LINE);

				// Always put min = 0 otherwise the plot does not change.
				plot.setLimits(0, max, 0, 1.05);
			}
			return Utils.display(title, plot, Utils.NO_TO_FRONT);
		}
	}

	/**
	 * Calculate a histogram of the precision. The precision can be either stored in the results or calculated using the
	 * Mortensen formula. If the precision method for Q estimation is not fixed then the histogram is fitted with a
	 * Gaussian to create an initial estimate.
	 *
	 * @param precisionMethod
	 *            the precision method
	 * @return The precision histogram
	 */
	private PrecisionHistogram calculatePrecisionHistogram()
	{
		boolean logFitParameters = false;
		String title = results.getName() + " Precision Histogram";

		// Check if the results has the precision already or if it can be computed.
		boolean canUseStored = canUseStoredPrecision(results);
		boolean canCalculatePrecision = canCalculatePrecision(results);

		// Set the method to compute a histogram. Default to the user selected option.
		PrecisionMethod m = null;
		if (canUseStored && precisionMethod == PrecisionMethod.STORED)
			m = precisionMethod;
		else if (canCalculatePrecision && precisionMethod == PrecisionMethod.CALCULATE)
			m = precisionMethod;

		if (m == null)
		{
			// We get here if the choice of the user is not available.
			// We only have two choices so if one is available then select it.
			if (canUseStored)
				m = PrecisionMethod.STORED;
			else if (canCalculatePrecision)
				m = PrecisionMethod.CALCULATE;
			// If the user selected a method not available then log a warning
			if (m != null && precisionMethod != PrecisionMethod.FIXED)
			{
				IJ.log(String.format("%s : Selected precision method '%s' not available, switching to '%s'", TITLE,
						precisionMethod, m.getName()));
			}

			if (m == null)
			{
				// We cannot compute a precision histogram. 
				// This does not matter if the user has provide a fixed input.
				if (precisionMethod == PrecisionMethod.FIXED)
				{
					PrecisionHistogram histogram = new PrecisionHistogram(title);
					histogram.mean = mean;
					histogram.sigma = sigma;
					return histogram;
				}
				// No precision
				return null;
			}
		}

		// We get here if we can compute precision.
		// Build the histogram 
		StoredDataStatistics precision = new StoredDataStatistics(results.size());
		if (m == PrecisionMethod.STORED)
		{
			final StoredDataStatistics p = precision;
			results.forEach(new PeakResultProcedure()
			{
				public void execute(PeakResult r)
				{
					p.add(r.getPrecision());
				}
			});
		}
		else
		{
			precision.add(pp.precision);
		}
		//System.out.printf("Raw p = %f\n", precision.getMean());

		double yMin = Double.NEGATIVE_INFINITY, yMax = 0;

		// Set the min and max y-values using 1.5 x IQR 
		DescriptiveStatistics stats = precision.getStatistics();
		double lower = stats.getPercentile(25);
		double upper = stats.getPercentile(75);
		if (Double.isNaN(lower) || Double.isNaN(upper))
		{
			if (logFitParameters)
				Utils.log("Error computing IQR: %f - %f", lower, upper);
		}
		else
		{
			double iqr = upper - lower;

			yMin = FastMath.max(lower - iqr, stats.getMin());
			yMax = FastMath.min(upper + iqr, stats.getMax());

			if (logFitParameters)
				Utils.log("  Data range: %f - %f. Plotting 1.5x IQR: %f - %f", stats.getMin(), stats.getMax(), yMin,
						yMax);
		}

		if (yMin == Double.NEGATIVE_INFINITY)
		{
			int n = 5;
			yMin = Math.max(stats.getMin(), stats.getMean() - n * stats.getStandardDeviation());
			yMax = Math.min(stats.getMax(), stats.getMean() + n * stats.getStandardDeviation());

			if (logFitParameters)
				Utils.log("  Data range: %f - %f. Plotting mean +/- %dxSD: %f - %f", stats.getMin(), stats.getMax(), n,
						yMin, yMax);
		}

		// Get the data within the range
		double[] data = precision.getValues();
		precision = new StoredDataStatistics(data.length);
		for (double d : data)
		{
			if (d < yMin || d > yMax)
				continue;
			precision.add(d);
		}

		int histogramBins = Utils.getBins(precision, Utils.BinMethod.SCOTT);
		float[][] hist = Utils.calcHistogram(precision.getFloatValues(), yMin, yMax, histogramBins);
		PrecisionHistogram histogram = new PrecisionHistogram(hist, precision, title);

		if (precisionMethod == PrecisionMethod.FIXED)
		{
			histogram.mean = mean;
			histogram.sigma = sigma;
			return histogram;
		}

		// Fitting of the histogram to produce the initial estimate

		// Extract non-zero data
		float[] x = Arrays.copyOf(hist[0], hist[0].length);
		float[] y = hist[1];
		int count = 0;
		float dx = (x[1] - x[0]) * 0.5f;
		for (int i = 0; i < y.length; i++)
			if (y[i] > 0)
			{
				x[count] = x[i] + dx;
				y[count] = y[i];
				count++;
			}
		x = Arrays.copyOf(x, count);
		y = Arrays.copyOf(y, count);

		// Sense check to fitted data. Get mean and SD of histogram
		double[] stats2 = Utils.getHistogramStatistics(x, y);
		if (logFitParameters)
			Utils.log("  Initial Statistics: %f +/- %f", stats2[0], stats2[1]);
		histogram.mean = stats2[0];
		histogram.sigma = stats2[1];

		// Standard Gaussian fit
		double[] parameters = fitGaussian(x, y);
		if (parameters == null)
		{
			Utils.log("  Failed to fit initial Gaussian");
			return histogram;
		}
		double newMean = parameters[1];
		double error = Math.abs(stats2[0] - newMean) / stats2[1];
		if (error > 3)
		{
			Utils.log("  Failed to fit Gaussian: %f standard deviations from histogram mean", error);
			return histogram;
		}
		if (newMean < yMin || newMean > yMax)
		{
			Utils.log("  Failed to fit Gaussian: %f outside data range %f - %f", newMean, yMin, yMax);
			return histogram;
		}

		if (logFitParameters)
			Utils.log("  Initial Gaussian: %f @ %f +/- %f", parameters[0], parameters[1], parameters[2]);

		histogram.mean = parameters[1];
		histogram.sigma = parameters[2];

		return histogram;
	}

	private PrecisionResultProcedure pp;

	private boolean canCalculatePrecision(MemoryPeakResults results)
	{
		try
		{
			pp = new PrecisionResultProcedure(results);
			pp.getPrecision();
		}
		catch (DataException e)
		{
			return false;
		}

		// Check they are different
		for (int i = 0; i < pp.size(); i++)
		{
			// Check this is valid
			if (Maths.isFinite(pp.precision[i]))
			{
				final double p1 = pp.precision[i];
				for (int j = i + 1; j < pp.size(); j++)
				{
					if (Maths.isFinite(pp.precision[j]) && pp.precision[j] != p1)
						return true;
				}
				// All the results are the same, this is not valid
				break;
			}
		}

		return false;
	}

	private boolean canUseStoredPrecision(MemoryPeakResults results)
	{
		return results.hasPrecision();
	}

	/**
	 * Fit gaussian.
	 *
	 * @param x
	 *            the x
	 * @param y
	 *            the y
	 * @return new double[] { norm, mean, sigma }
	 */
	private double[] fitGaussian(float[] x, float[] y)
	{
		WeightedObservedPoints obs = new WeightedObservedPoints();
		for (int i = 0; i < x.length; i++)
			obs.add(x[i], y[i]);

		Collection<WeightedObservedPoint> observations = obs.toList();
		GaussianCurveFitter fitter = GaussianCurveFitter.create().withMaxIterations(2000);
		GaussianCurveFitter.ParameterGuesser guess = new GaussianCurveFitter.ParameterGuesser(observations);
		double[] initialGuess = null;
		try
		{
			initialGuess = guess.guess();
			return fitter.withStartPoint(initialGuess).fit(observations);
		}
		catch (TooManyEvaluationsException e)
		{
			// Use the initial estimate
			return initialGuess;
		}
		catch (Exception e)
		{
			// Just in case there is another exception type, or the initial estimate failed
			return null;
		}
	}

	/**
	 * Used to tile the windows from the worker threads on the first plot
	 */
	private class MyWindowOrganiser
	{
		final WindowOrganiser wo = new WindowOrganiser();
		int expected;
		int size = 0;
		boolean ignore = false;

		public void add(PlotWindow plot)
		{
			if (ignore)
				return;

			// This is not perfect since multiple threads may reset the same new-window flag  
			if (Utils.isNewWindow())
				wo.add(plot);

			// Layout the windows if we reached the expected size.
			if (++size == expected)
			{
				wo.tile();
				ignore = true; // No further need to track the windows
			}
		}
	}

	private class WorkSettings implements Cloneable
	{
		double mean, sigma, qValue = 0;

		WorkSettings(double mean, double sigma, double qValue)
		{
			this.mean = mean;
			this.sigma = sigma;
			this.qValue = qValue;
		}

		@Override
		public WorkSettings clone()
		{
			try
			{
				return (WorkSettings) super.clone();
			}
			catch (CloneNotSupportedException e)
			{
				return null; // Shouldn't happen
			}
		}
	}

	private class BaseWorker extends WorkflowWorker<WorkSettings, Object>
	{
		final MyWindowOrganiser wo;

		BaseWorker(MyWindowOrganiser wo)
		{
			this.wo = wo;
		}

		@Override
		public boolean equalSettings(WorkSettings current, WorkSettings previous)
		{
			if (current.mean != previous.mean)
				return false;
			if (current.sigma != previous.sigma)
				return false;
			return true;
		}

		@Override
		public boolean equalResults(Object current, Object previous)
		{
			// We never create any results so ignore this
			return true;
		}

		@Override
		public Pair<WorkSettings, Object> doWork(Pair<WorkSettings, Object> work)
		{
			return work;
		}
	}

	private class HistogramWorker extends BaseWorker
	{
		final PrecisionHistogram histogram;

		HistogramWorker(MyWindowOrganiser wo, PrecisionHistogram histogram)
		{
			super(wo);
			this.histogram = histogram;
		}

		@Override
		public Pair<WorkSettings, Object> doWork(Pair<WorkSettings, Object> work)
		{
			// Plot the histogram
			wo.add(histogram.plot(work.s.mean, work.s.sigma));
			return work;
		}
	}

	private class QPlotWorker extends BaseWorker
	{
		final QPlot qplot;

		QPlotWorker(MyWindowOrganiser wo, QPlot qplot)
		{
			super(wo);
			this.qplot = qplot;
		}

		@Override
		public boolean equalSettings(WorkSettings current, WorkSettings previous)
		{
			if (current.qValue != previous.qValue)
				return false;
			return super.equalSettings(current, previous);
		}

		@Override
		public Pair<WorkSettings, Object> doWork(Pair<WorkSettings, Object> work)
		{
			// Compute Q and then plot the scaled FRC numerator
			WorkSettings settings = work.s;
			for (PlotWindow pw : qplot.plot(settings.mean, settings.sigma, settings.qValue))
				wo.add(pw);
			return work;
		}
	}

	private boolean showQEstimationDialog(final PrecisionHistogram histogram, final QPlot qplot,
			final FRCCurve frcCurve, final double nmPerPixel)
	{
		// This is used for the initial layout of windows
		final MyWindowOrganiser wo = new MyWindowOrganiser();

		// Use a simple workflow
		Workflow<WorkSettings, Object> workflow = new Workflow<WorkSettings, Object>();

		// Split the work to two children with a dummy initial worker
		int previous = workflow.add(new BaseWorker(wo));
		workflow.add(new HistogramWorker(wo, histogram), previous);
		workflow.add(new QPlotWorker(wo, qplot), previous);

		workflow.start();

		// The number of plots
		wo.expected = 4;

		String KEY_MEAN = "mean_estimate";
		String KEY_SIGMA = "sigma_estimate";
		String KEY_Q = "q_estimate";

		String macroOptions = Macro.getOptions();
		if (macroOptions != null)
		{
			// If inside a macro then just get the options and run the work
			double mean = Double.parseDouble(Macro.getValue(macroOptions, KEY_MEAN, Double.toString(histogram.mean)));
			double sigma = Double
					.parseDouble(Macro.getValue(macroOptions, KEY_SIGMA, Double.toString(histogram.sigma)));
			double qValue = Double.parseDouble(Macro.getValue(macroOptions, KEY_Q, Double.toString(qplot.qValue)));
			workflow.run(new WorkSettings(mean, sigma, qValue));
			workflow.shutdown(false);
		}
		else
		{
			// Draw the plots with the first set of work
			workflow.run(new WorkSettings(histogram.mean, histogram.sigma, qplot.qValue));

			// Build the dialog
			NonBlockingExtendedGenericDialog gd = new NonBlockingExtendedGenericDialog(TITLE);
			gd.addHelp(About.HELP_URL);

			double mu = histogram.mean / nmPerPixel;
			double sd = histogram.sigma / nmPerPixel;
			double plateauness = qplot.computePlateauness(qplot.qValue, mu, sd);

			gd.addMessage("Estimate the blinking correction parameter Q for Fourier Ring Correlation\n \n" +
					String.format("Initial estimate:\nPrecision = %.3f +/- %.3f\n", histogram.mean, histogram.sigma) +
					String.format("Q = %s\nCost = %.3f", Utils.rounded(qplot.qValue), plateauness));

			double mean10 = histogram.mean * 10;
			double sd10 = histogram.sigma * 10;
			double q10 = qplot.qValue * 10;

			gd.addSlider("Mean (x10)", Math.max(0, mean10 - sd10 * 2), mean10 + sd10 * 2, mean10);
			gd.addSlider("Sigma (x10)", Math.max(0, sd10 / 2), sd10 * 2, sd10);
			gd.addSlider("Q (x10)", 0, Math.max(50, q10 * 2), q10);
			gd.addCheckbox("Reset_all", false);
			gd.addMessage("Double-click a slider to reset");

			gd.addDialogListener(new FIREDialogListener(gd, histogram, qplot, workflow));

			// Show this when the workers have finished drawing the plots so it is on top
			try
			{
				long timeout = System.currentTimeMillis() + 5000;
				while (wo.size < wo.expected)
				{
					Thread.sleep(50);
					if (System.currentTimeMillis() > timeout)
						break;
				}
			}
			catch (InterruptedException e)
			{
				// Ignore
			}

			gd.showDialog();

			// Finish the worker threads
			boolean cancelled = gd.wasCanceled();
			workflow.shutdown(cancelled);
			if (cancelled)
				return false;
		}

		// Store the Q value and the mean and sigma
		qValue = qplot.qValue;
		mean = qplot.mean;
		sigma = qplot.sigma;

		// Record the values for Macros since the NonBlockingDialog doesn't
		if (Recorder.record)
		{
			Recorder.recordOption(KEY_MEAN, Double.toString(mean));
			Recorder.recordOption(KEY_SIGMA, Double.toString(sigma));
			Recorder.recordOption(KEY_Q, Double.toString(qValue));
		}

		return true;
	}

	private class FIREDialogListener implements DialogListener, MouseListener
	{
		/**
		 * Delay (in milliseconds) used when entering new values in the dialog before the preview is processed
		 */
		@SuppressWarnings("unused")
		static final long DELAY = 500;

		long time;
		boolean notActive = true;
		volatile int ignore = 0;
		Workflow<WorkSettings, Object> workflow;
		double defaultMean, defaultSigma, defaultQValue;
		String m, s, q;
		TextField tf1, tf2, tf3;
		Scrollbar sl1, sl2, sl3;
		Checkbox cb;
		final boolean isMacro;

		FIREDialogListener(ExtendedGenericDialog gd, PrecisionHistogram histogram, QPlot qplot,
				Workflow<WorkSettings, Object> workflow)
		{
			time = System.currentTimeMillis() + 1000;
			this.workflow = workflow;
			this.defaultMean = histogram.mean;
			this.defaultSigma = histogram.sigma;
			this.defaultQValue = qplot.qValue;
			isMacro = Utils.isMacro();
			// For the reset
			tf1 = (TextField) gd.getNumericFields().get(0);
			tf2 = (TextField) gd.getNumericFields().get(1);
			tf3 = (TextField) gd.getNumericFields().get(2);
			cb = (Checkbox) (gd.getCheckboxes().get(0));
			// Sliders
			sl1 = (Scrollbar) gd.getSliders().get(0);
			sl2 = (Scrollbar) gd.getSliders().get(1);
			sl3 = (Scrollbar) gd.getSliders().get(2);
			sl1.addMouseListener(this);
			sl2.addMouseListener(this);
			sl3.addMouseListener(this);
			m = tf1.getText();
			s = tf2.getText();
			q = tf3.getText();

			// Implement a delay to allow typing.
			// This is also applied to the sliders which we do not want. 
			// Ideally we would have no delay for sliders (since they are in the correct place already
			// but a delay for typing in the text field). Unfortunately the AWTEvent raised by ImageJ
			// for the slider is actually from the TextField so we cannot tell the difference.
			// For now just have no delay.
			//if (!isMacro)
			//	workflow.startPreview();
		}

		public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
		{
			// Delay reading the dialog when in interactive mode. This is a workaround for a bug
			// where the dialog has not yet been drawn.
			if (notActive && !isMacro && System.currentTimeMillis() < time)
				return true;
			if (ignore-- > 0)
			{
				//System.out.println("ignored");
				return true;
			}

			notActive = false;

			double mean = Math.abs(gd.getNextNumber()) / 10;
			double sigma = Math.abs(gd.getNextNumber()) / 10;
			double qValue = Math.abs(gd.getNextNumber()) / 10;
			boolean reset = gd.getNextBoolean();

			// Even events from the slider come through as TextEvent from the TextField
			// since ImageJ captures the slider event as just updates the TextField.
			//System.out.printf("Event: %s, %f, %f\n", e, mean, sigma);

			// Allow reset to default
			if (reset)
			{
				// This does not trigger the event
				cb.setState(false);
				mean = this.defaultMean;
				sigma = this.defaultSigma;
				qValue = this.defaultQValue;
			}

			WorkSettings work = new WorkSettings(mean, sigma, qValue);

			// Offload this work onto a thread that just picks up the most recent dialog input.
			workflow.run(work);

			if (reset)
			{
				// These trigger dialogItemChanged(...) so do them after we added 
				// work to the queue and ignore the events
				ignore = 3;
				tf1.setText(m);
				tf2.setText(s);
				tf3.setText(q);
			}

			return true;
		}

		public void mouseClicked(MouseEvent e)
		{
			// Reset the slider on double-click
			if (e.getClickCount() < 2)
				return;
			if (e.getSource() == null || !(e.getSource() instanceof Scrollbar))
				return;
			Scrollbar sl = (Scrollbar) e.getSource();
			if (sl == sl1)
				tf1.setText(m);
			if (sl == sl2)
				tf2.setText(s);
			if (sl == sl3)
				tf3.setText(q);
		}

		public void mousePressed(MouseEvent e)
		{
			// Ignore			
		}

		public void mouseReleased(MouseEvent e)
		{
			// Ignore			
		}

		public void mouseEntered(MouseEvent e)
		{
			// Ignore			
		}

		public void mouseExited(MouseEvent e)
		{
			// Ignore			
		}
	}

	private static int imagejNThreads = Prefs.getThreads();
	private static int lastNThreads = imagejNThreads;

	private int nThreads = 0;

	/**
	 * Gets the last N threads used in the input dialog.
	 *
	 * @return the last N threads
	 */
	private static int getLastNThreads()
	{
		// See if ImageJ preference were updated
		if (imagejNThreads != Prefs.getThreads())
		{
			lastNThreads = imagejNThreads = Prefs.getThreads();
		}
		// Otherwise use the last user input
		return lastNThreads;
	}

	/**
	 * Gets the threads to use for multi-threaded computation.
	 *
	 * @return the threads
	 */
	private int getThreads()
	{
		if (nThreads == 0)
		{
			nThreads = Prefs.getThreads();
		}
		return nThreads;
	}

	/**
	 * Sets the threads to use for multi-threaded computation.
	 *
	 * @param nThreads
	 *            the new threads
	 */
	public void setThreads(int nThreads)
	{
		this.nThreads = Math.max(1, nThreads);
	}
}
