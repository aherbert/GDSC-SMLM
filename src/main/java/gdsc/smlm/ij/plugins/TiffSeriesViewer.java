package gdsc.smlm.ij.plugins;

import java.awt.Choice;
import java.awt.Font;
import java.awt.Label;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.lang3.exception.ExceptionUtils;

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

import gdsc.core.ij.SeriesOpener;
import gdsc.core.ij.Utils;
import gdsc.core.logging.TrackProgress;
import gdsc.core.utils.TextUtils;
import gdsc.smlm.ij.SeriesImageSource;
import gdsc.smlm.ij.settings.Constants;
import gdsc.smlm.results.ImageSource.ReadHint;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.Prefs;
import ij.VirtualStack;
import ij.gui.ExtendedGenericDialog;
import ij.gui.ExtendedGenericDialog.OptionListener;
import ij.gui.ProgressBar;
import ij.io.ExtendedFileInfo;
import ij.io.FileInfo;
import ij.io.TiffEncoder;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/**
 * Reads a TIFF image using the series image source and presents it using a read-only virtual stack image.
 */
public class TiffSeriesViewer implements PlugIn, TrackProgress
{
	private static final String TITLE = "Tiff Series Viewer";
	private static final String[] MODE = { "Directory", "File" };
	private static int inputMode = (int) Prefs.get(Constants.tiffSeriesMode, 0);
	private static String inputDirectory = Prefs.get(Constants.tiffSeriesDirectory, "");
	private static String inputFile = Prefs.get(Constants.tiffSeriesFile, "");
	private static boolean logProgress = Prefs.getBoolean(Constants.tiffSeriesLogProgress, false);
	private static final String[] OUTPUT_MODE = { "Image", "Files" };
	private static int outputMode = (int) Prefs.get(Constants.tiffSeriesOutputMode, 0);
	private static int nImages = (int) Prefs.get(Constants.tiffSeriesOutputNImages, 1);
	private static String outputDirectory = Prefs.get(Constants.tiffSeriesOutputDirectory, "");

	private Label label;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(String arg)
	{
		SMLMUsageTracker.recordPlugin(this.getClass(), arg);

		final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);
		gd.addChoice("Mode", MODE, inputMode, new OptionListener<Integer>()
		{
			public boolean collectOptions(Integer value)
			{
				inputMode = value;
				return collectOptions(false);
			}

			public boolean collectOptions()
			{
				return collectOptions(true);
			}

			private boolean collectOptions(boolean silent)
			{
				// This has limited silent support to fake running in a macro
				if (inputMode == 0)
				{
					String dir = null;
					String title = "Select image series ...";
					if (silent)
					{
						String macroOptions = Macro.getOptions();
						if (macroOptions != null)
							dir = Macro.getValue(macroOptions, title, null);
					}
					else
					{
						dir = Utils.getDirectory(title, inputDirectory);
					}
					if (TextUtils.isNullOrEmpty(dir))
						return false;
					inputDirectory = dir;
				}
				else
				{
					String file = null;
					String title = "Select image ...";
					if (silent)
					{
						String macroOptions = Macro.getOptions();
						if (macroOptions != null)
							file = Macro.getValue(macroOptions, title, null);
					}
					else
					{
						file = Utils.getFilename(title, inputFile);
					}
					if (TextUtils.isNullOrEmpty(file))
						return false;
					inputFile = file;
				}
				updateLabel();
				return true;
			}
		});
		gd.addMessage("");
		label = gd.getLastLabel();
		if (Utils.isShowGenericDialog())
		{
			final Choice choice = gd.getLastChoice();
			choice.addItemListener(new ItemListener()
			{
				public void itemStateChanged(ItemEvent e)
				{
					inputMode = choice.getSelectedIndex();
					updateLabel();
				}
			});
			updateLabel();
		}
		gd.addCheckbox("Log_progress", logProgress);
		gd.addChoice("Output_mode", OUTPUT_MODE, outputMode, new OptionListener<Integer>()
		{
			public boolean collectOptions(Integer value)
			{
				outputMode = value;
				return collectOptions(false);
			}

			public boolean collectOptions()
			{
				return collectOptions(true);
			}

			private boolean collectOptions(boolean silent)
			{
				if (outputMode == 0)
				{
					// Nothing to do
					return false;
				}
				else
				{
					ExtendedGenericDialog egd = new ExtendedGenericDialog("Output Options");
					egd.addNumericField("Slices_per_image", nImages, 0);
					egd.addDirectoryField("Output_directory", outputDirectory);
					egd.setSilent(silent);
					egd.showDialog(true, gd);
					if (egd.wasCanceled())
						return false;
					nImages = (int) egd.getNextNumber();
					outputDirectory = egd.getNextString();
					return true;
				}
			}
		});
		gd.showDialog();
		if (gd.wasCanceled())
			return;

		inputMode = gd.getNextChoiceIndex();
		logProgress = gd.getNextBoolean();
		outputMode = gd.getNextChoiceIndex();

		Prefs.set(Constants.tiffSeriesMode, inputMode);
		Prefs.set(Constants.tiffSeriesDirectory, inputDirectory);
		Prefs.set(Constants.tiffSeriesFile, inputFile);
		Prefs.set(Constants.tiffSeriesLogProgress, logProgress);
		Prefs.set(Constants.tiffSeriesOutputMode, outputMode);
		Prefs.set(Constants.tiffSeriesOutputNImages, nImages);
		Prefs.set(Constants.tiffSeriesOutputDirectory, outputDirectory);

		SeriesImageSource source;
		if (inputMode == 0)
		{
			SeriesOpener series = new SeriesOpener(inputDirectory);
			if (series.getNumberOfImages() == 0)
			{
				IJ.error(TITLE, "No images in the selected directory:\n" + inputDirectory);
				return;
			}
			source = new SeriesImageSource(PeakFit.getName(series.getImageList()), series);
		}
		else
		{
			File file = new File(inputFile);
			source = new SeriesImageSource(file.getName(), new String[] { inputFile });
		}

		source.setBufferLimit(0); // No memory buffer
		source.setReadHint(ReadHint.NONSEQUENTIAL);

		if (!source.isTiffSeries)
		{
			IJ.error(TITLE, "Not a TIFF image");
			return;
		}
		Utils.showStatus("Opening TIFF ...");
		source.setTrackProgress(this);
		if (!source.open())
		{
			IJ.error(TITLE, "Cannot open the image");
			return;
		}
		Utils.showStatus("");

		// Create a virtual stack
		TiffSeriesVirtualStack stack = new TiffSeriesVirtualStack(source);
		if (outputMode == 0)
		{
			stack.show();
		}
		else
		{
			int nImages = Math.max(1, TiffSeriesViewer.nImages);
			ImagePlus imp = stack.createImp();
			// The calibration only has the offset so ignore for speed.
			//Calibration cal = imp.getCalibration();
			int size = stack.getSize();

			// Create the format string
			int digits = String.format("%d", size).length();
			String format = new File(outputDirectory, imp.getShortTitle() + "%0" + digits + "d.tif").getPath();

			IJ.showStatus("Saving image ...");
			Utils.setShowStatus(false);
			Utils.setShowProgress(false);
			ProgressBar progressBar = Utils.getProgressBar();
			progressBar.show(0);
			int step = Utils.getProgressInterval(size);
			int next = step;
			try
			{
				for (int i = 1; i <= size; i += nImages)
				{
					if (Utils.isInterrupted())
						break;
					if (i > next)
					{
						progressBar.show(i, size);
						next += step;
					}
					String path = String.format(format, i);
					//System.out.println(path);
					ImageStack out = new ImageStack(source.getWidth(), source.getHeight());
					for (int j = 0, k = i; j < nImages && k <= size; j++, k++)
					{
						out.addSlice(null, stack.getPixels(k));
					}
					ImagePlus outImp = new ImagePlus(path, out);
					//outImp.setCalibration(cal);
					saveAsTiff(outImp, path);
				}
			}
			catch (IOException e)
			{
				IJ.log(ExceptionUtils.getStackTrace(e));
				IJ.error(TITLE, "Failed to save image: " + e.getMessage());
				IJ.showStatus("Failed to save image");
				return;
			}
			finally
			{
				Utils.setShowStatus(true);
				Utils.setShowProgress(true);
				progressBar.show(1);
			}
			IJ.showStatus("Saved image");
		}
	}

	private void saveAsTiff(ImagePlus imp, String path) throws IOException
	{
		IJ.saveAsTiff(imp, path);

		FileInfo fi = imp.getFileInfo();
		fi.nImages = imp.getStackSize();
		DataOutputStream out = null;
		try
		{
			TiffEncoder file = new TiffEncoder(fi);
			out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
			file.write(out);
			out.close();
		}
		finally
		{
			if (out != null)
				try
				{
					out.close();
				}
				catch (IOException e)
				{
				}
		}
	}

	private void updateLabel()
	{
		if (inputMode == 0)
			label.setText(inputDirectory);
		else
			label.setText(inputFile);
	}

	/**
	 * Override methods in the ij.VirtualStack class to provide the pixels from a TIFF series. The stack cannot be
	 * modified.
	 */
	public static class TiffSeriesVirtualStack extends VirtualStack
	{
		SeriesImageSource source;

		/**
		 * Instantiates a new tiff series virtual stack with a source. The source must have been successfully opened.
		 *
		 * @param source
		 *            the source
		 */
		public TiffSeriesVirtualStack(SeriesImageSource source)
		{
			super(source.getWidth(), source.getHeight(), null, null);
			if (!source.isValid(1))
				throw new IllegalArgumentException("Source has no frames");
			this.source = source;
			Object pixels = source.getRaw(1);
			if (pixels == null)
				throw new IllegalArgumentException("Source has no first frame");
			setBitDepth(Utils.getBitDepth(pixels));
		}

		/**
		 * Wrap the series in a ImagePlus object.
		 *
		 * @return the image plus
		 */
		public ImagePlus createImp()
		{
			ImagePlus imp = new ImagePlus(source.getName(), this);
			addInfo(imp);
			return imp;
		}

		/**
		 * Show the series in a ImagePlus object.
		 *
		 * @return the image plus
		 */
		public ImagePlus show()
		{
			ImagePlus imp = Utils.display(source.getName(), this);
			addInfo(imp);
			return imp;
		}

		private void addInfo(ImagePlus imp)
		{
			// So the FileSaver can save the stack make sure the FileInfo is not null
			//FileInfo fi = new FileInfo();
			//imp.setFileInfo(fi);
			imp.getFileInfo();

			// Get metadata from the source
			ExtendedFileInfo[] fileInfo = source.getFileInfo(0);
			if (fileInfo != null && fileInfo[0] != null)
			{
				ExtendedFileInfo efi = fileInfo[0];
				if (efi.extendedMetaData != null)
					imp.setProperty("Info", efi.extendedMetaData);
				else if (efi.info != null)
					imp.setProperty("Info", efi.info);
			}
			if (source.getXOrigin() != 0 || source.getYOrigin() != 0)
			{
				Calibration cal = imp.getLocalCalibration();
				cal.xOrigin = -source.getXOrigin();
				cal.yOrigin = -source.getYOrigin();
			}
		}

		/**
		 * Does nothing
		 * 
		 * @see ij.VirtualStack#addSlice(java.lang.String)
		 */
		@Override
		public void addSlice(String name)
		{
		}

		/**
		 * Does nothing
		 * 
		 * @see ij.VirtualStack#deleteSlice()
		 */
		@Override
		public void deleteSlice(int n)
		{
		}

		@Override
		public ImageProcessor getProcessor(int n)
		{
			Object pixels = source.getRaw(n);
			ImageProcessor ip = null;
			int depthThisImage = 0;
			if (pixels != null)
			{
				ip = Utils.createProcessor(getWidth(), getHeight(), pixels);
			}
			else
			{
				ip = new ByteProcessor(getWidth(), getHeight());
				ip.invert();
				int size = getHeight() / 20;
				if (size < 9)
					size = 9;
				Font font = new Font("Helvetica", Font.PLAIN, size);
				ip.setFont(font);
				ip.setAntialiasedText(true);
				ip.setColor(0);
				ip.drawString("Error opening frame " + n, size, size * 2);
				depthThisImage = 8;
			}
			if (depthThisImage != getBitDepth())
			{
				switch (getBitDepth())
				{
					case 8:
						ip = ip.convertToByte(true);
						break;
					case 16:
						ip = ip.convertToShort(true);
						break;
					case 24:
						ip = ip.convertToRGB();
						break;
					case 32:
						ip = ip.convertToFloat();
						break;
				}
			}
			// This will not happen as the source checks the dimensions
			//if (ip.getWidth() != getWidth() || ip.getHeight() != getHeight())
			//{
			//	ImageProcessor ip2 = ip.createProcessor(getWidth(), getHeight());
			//	ip2.insert(ip, 0, 0);
			//	ip = ip2;
			//}
			return ip;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see ij.VirtualStack#saveChanges(int)
		 */
		@Override
		public int saveChanges(int n)
		{
			return -1; // Not implemented
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see ij.VirtualStack#getSize()
		 */
		@Override
		public int getSize()
		{
			return source.getFrames();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see ij.VirtualStack#getSliceLabel(int)
		 */
		@Override
		public String getSliceLabel(int n)
		{
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see ij.VirtualStack#getDirectory()
		 */
		@Override
		public String getDirectory()
		{
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see ij.VirtualStack#getFileName(int)
		 */
		@Override
		public String getFileName(int n)
		{
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see ij.VirtualStack#sortDicom(java.lang.String[], java.lang.String[], int)
		 */
		@Override
		public ImageStack sortDicom(String[] strings, String[] info, int maxDigits)
		{
			// Don't sort
			return this;
		}
	}

	public void progress(double fraction)
	{
		IJ.showProgress(fraction);
	}

	public void progress(long position, long total)
	{
		IJ.showProgress((double) position / total);
	}

	public void incrementProgress(double fraction)
	{
		// Ignore
	}

	public void log(String format, Object... args)
	{
		if (logProgress)
			Utils.log(format, args);
	}

	public void status(String format, Object... args)
	{
		IJ.showStatus(String.format(format, args));
	}

	public boolean isEnded()
	{
		// Ignore
		return false;
	}

	public boolean isProgress()
	{
		return true;
	}

	public boolean isLog()
	{
		return logProgress;
	}

	public boolean isStatus()
	{
		return true;
	}
}
