package gdsc.smlm.ij.plugins;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.regex.Pattern;

import gdsc.core.data.utils.TypeConverter;

/*----------------------------------------------------------------------------- 
 * GDSC SMLM Software
 * 
 * Copyright (C) 2013 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import gdsc.core.ij.Utils;
import gdsc.core.utils.NotImplementedException;
import gdsc.core.utils.TextUtils;
import gdsc.core.utils.UnicodeReader;
import gdsc.smlm.data.config.CalibrationProtos.Calibration;
import gdsc.smlm.data.config.CalibrationWriter;
import gdsc.smlm.data.config.FitProtos.PrecisionMethod;
import gdsc.smlm.data.config.GUIProtos.LoadLocalisationsSettings;
import gdsc.smlm.data.config.PSFHelper;
import gdsc.smlm.data.config.PSFProtos.PSFType;
import gdsc.smlm.data.config.UnitHelper;
import gdsc.smlm.data.config.UnitProtos.DistanceUnit;
import gdsc.smlm.data.config.UnitProtos.IntensityUnit;
import gdsc.smlm.data.config.UnitProtos.TimeUnit;
import gdsc.smlm.ij.settings.SettingsManager;
import gdsc.smlm.results.AttributePeakResult;
import gdsc.smlm.results.Gaussian2DPeakResultHelper;
import gdsc.smlm.results.MemoryPeakResults;
import gdsc.smlm.results.PeakResult;
import ij.IJ;
import ij.gui.ExtendedGenericDialog;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;

/**
 * Loads generic localisation files into memory
 */
public class LoadLocalisations implements PlugIn
{
	// Time units for the exposure time cannot be in frames as this makes no sense
	private static EnumSet<TimeUnit> set = EnumSet.allOf(TimeUnit.class);
	private static String[] tUnits;
	private static TimeUnit[] tUnitValues;
	static
	{
		set.remove(TimeUnit.FRAME);
		set.remove(TimeUnit.UNRECOGNIZED);
		tUnits = new String[set.size()];
		tUnitValues = new TimeUnit[set.size()];
		int i = 0;
		for (TimeUnit t : set)
		{
			tUnits[i] = SettingsManager.getName(UnitHelper.getName(t), UnitHelper.getShortName(t));
			tUnitValues[i] = t;
			i++;
		}
	}

	public static class Localisation
	{
		int t, id;
		float x, y, z, intensity, sx = -1, sy = -1, precision = -1;
	}

	public static class LocalisationList extends ArrayList<Localisation>
	{
		private static final long serialVersionUID = 6616011992365324247L;

		public final Calibration calibration;

		/**
		 * Instantiates a new localisation list.
		 * <p>
		 * The time unit for the calibration is expected to be the exposure time in a valid unit of time (e.g. seconds
		 * and not frames)
		 *
		 * @param calibration
		 *            the calibration
		 */
		public LocalisationList(Calibration calibration)
		{
			this.calibration = calibration;
		}

		public MemoryPeakResults toPeakResults(String name)
		{
			CalibrationWriter calibrationWriter = new CalibrationWriter(this.calibration);

			// Convert exposure time to milliseconds
			TypeConverter<TimeUnit> timeConverter = calibrationWriter.getTimeConverter(TimeUnit.MILLISECOND);
			calibrationWriter.setExposureTime(timeConverter.convert(calibrationWriter.getExposureTime()));
			// This is currently not a method as it is assumed to be milliseconds.
			//calibration.setTimeUnit(TimeUnit.MILLISECOND);

			// Convert precision to nm
			TypeConverter<DistanceUnit> distanceConverter = calibrationWriter.getDistanceConverter(DistanceUnit.NM);

			MemoryPeakResults results = new MemoryPeakResults();
			results.setName(name);
			if (!hasPrecision())
				calibrationWriter.setPrecisionMethod(null);
			results.setCalibration(calibrationWriter.getCalibration());

			if (size() > 0)
			{
				// Guess the PSF type from the first localisation
				Localisation l = get(0);
				PSFType psfType = PSFType.CUSTOM;
				if (l.sx != -1)
				{
					psfType = PSFType.ONE_AXIS_GAUSSIAN_2D;
					if (l.sy != -1)
					{
						psfType = PSFType.TWO_AXIS_GAUSSIAN_2D;
					}
				}
				results.setPSF(PSFHelper.create(psfType));

				for (int i = 0; i < size(); i++)
				{
					l = get(i);
					float intensity = (l.intensity <= 0) ? 1 : (float) (l.intensity);
					float x = (float) (l.x);
					float y = (float) (l.y);
					float z = (float) (l.z);

					float[] params;
					switch (psfType)
					{
						case CUSTOM:
							params = PeakResult.createParams(0, intensity, x, y, z);
							break;
						case ONE_AXIS_GAUSSIAN_2D:
							params = Gaussian2DPeakResultHelper.createOneAxisParams(0, intensity, x, y, z,
									(float) l.sx);
							break;
						case TWO_AXIS_GAUSSIAN_2D:
							params = Gaussian2DPeakResultHelper.createTwoAxisParams(0, intensity, x, y, z, (float) l.sx,
									(float) l.sy);
							break;
						default:
							throw new NotImplementedException("Unsupported PSF type: " + psfType);
					}
					AttributePeakResult peakResult = new AttributePeakResult(l.t, (int) x, (int) y, 0, 0, 0, params,
							null);
					peakResult.setId(l.id);
					if (l.precision > 0)
						// Convert to nm
						peakResult.setPrecision(distanceConverter.convert(l.precision));
					results.add(peakResult);
				}
			}

			// Convert to preferred units. This can be done even if the results are empty.
			results.convertToPreferredUnits();

			return results;
		}

		private boolean hasPrecision()
		{
			for (int i = 0; i < size(); i++)
				if (get(i).precision > 0)
					return true;
			return false;
		}
	}

	private static final String TITLE = "Load Localisations";

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(String arg)
	{
		SMLMUsageTracker.recordPlugin(this.getClass(), arg);

		LoadLocalisationsSettings.Builder settings = SettingsManager.readLoadLocalisationsSettings(0).toBuilder();

		String[] path = Utils.decodePath(settings.getLocalisationsFilename());
		OpenDialog chooser = new OpenDialog("Localisations_File", path[0], path[1]);
		if (chooser.getFileName() == null)
			return;

		settings.setLocalisationsFilename(chooser.getDirectory() + chooser.getFileName());

		LocalisationList localisations = loadLocalisations(settings);

		SettingsManager.writeSettings(settings.build());

		if (localisations == null)
			// Cancelled
			return;

		if (localisations.isEmpty())
		{
			IJ.error(TITLE, "No localisations could be loaded");
			return;
		}

		MemoryPeakResults results = localisations.toPeakResults(settings.getName());

		// Create the in-memory results
		if (results.size() > 0)
		{
			MemoryPeakResults.addResults(results);
		}

		String msg = "Loaded " + TextUtils.pleural(results.size(), "localisation");
		IJ.showStatus(msg);
		Utils.log(msg);
	}

	static LocalisationList loadLocalisations(LoadLocalisationsSettings.Builder settings)
	{
		if (!getFields(settings))
			return null;

		LocalisationList localisations = new LocalisationList(settings.getCalibration());

		final String comment = settings.getComment();
		final boolean hasComment = !TextUtils.isNullOrEmpty(comment);
		int errors = 0;
		int count = 0;
		int h = Math.max(0, settings.getHeaderLines());

		BufferedReader input = null;
		try
		{
			FileInputStream fis = new FileInputStream(settings.getLocalisationsFilename());
			input = new BufferedReader(new UnicodeReader(fis, null));
			Pattern p = Pattern.compile(settings.getDelimiter());

			final int it = settings.getFieldT();
			final int iid = settings.getFieldId();
			final int ix = settings.getFieldX();
			final int iy = settings.getFieldY();
			final int iz = settings.getFieldZ();
			final int ii = settings.getFieldI();
			final int isx = settings.getFieldSx();
			final int isy = settings.getFieldSy();
			final int ip = settings.getFieldPrecision();

			String line;
			while ((line = input.readLine()) != null)
			{
				// Skip header
				if (h-- > 0)
					continue;
				// Skip empty lines
				if (line.length() == 0)
					continue;
				// Skip comments
				if (hasComment && line.startsWith(comment))
					continue;

				count++;
				final String[] fields = p.split(line);

				Localisation l = new Localisation();
				try
				{
					if (it >= 0)
						l.t = Integer.parseInt(fields[it]);
					if (iid >= 0)
						l.id = Integer.parseInt(fields[iid]);
					l.x = Float.parseFloat(fields[ix]);
					l.y = Float.parseFloat(fields[iy]);
					if (iz >= 0)
						l.z = Float.parseFloat(fields[iz]);
					if (ii >= 0)
						l.intensity = Float.parseFloat(fields[ii]);
					if (isx >= 0)
						l.sy = l.sx = Integer.parseInt(fields[isx]);
					if (isy >= 0)
						l.sy = Integer.parseInt(fields[isy]);
					if (ip >= 0)
						l.precision = Float.parseFloat(fields[ip]);

					localisations.add(l);
				}
				catch (NumberFormatException e)
				{
					if (errors++ == 0)
						Utils.log("%s error on record %d: %s", TITLE, count, e.getMessage());
				}
				catch (IndexOutOfBoundsException e)
				{
					if (errors++ == 0)
						Utils.log("%s error on record %d: %s", TITLE, count, e.getMessage());
				}
			}
		}
		catch (IOException e)
		{
			Utils.log("%s IO error: %s", TITLE, e.getMessage());
		}
		finally
		{
			try
			{
				if (input != null)
					input.close();
			}
			catch (IOException e)
			{
				// Ignore
			}
			if (errors != 0)
				Utils.log("%s has %d / %d error lines", TITLE, errors, count);
		}

		return localisations;
	}

	private static boolean getFields(LoadLocalisationsSettings.Builder settings)
	{
		ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);

		gd.addMessage("Load delimited localisations");
		gd.addStringField("Dataset_name", settings.getName(), 30);

		gd.addMessage("Calibration:");
		// Allow the full camera type top be captured
		Calibration.Builder calibrationBuilder = settings.getCalibrationBuilder();
		CalibrationWriter cw = new CalibrationWriter(calibrationBuilder);
		PeakFit.addCameraOptions(gd, 0, cw);
		// Only primitive support for other calibration
		gd.addNumericField("Pixel_size", cw.getNmPerPixel(), 3, 8, "nm");
		gd.addNumericField("Exposure_time", cw.getExposureTime(), 3, 8, "");

		// This is the unit for the exposure time (used to convert the exposure time to milliseconds).
		// Use the name as the list is a truncated list of the full enum.
		TimeUnit t = calibrationBuilder.getTimeCalibration().getTimeUnit();
		gd.addChoice("Time_unit", tUnits, SettingsManager.getName(UnitHelper.getName(t), UnitHelper.getShortName(t)));

		gd.addMessage("Records:");
		gd.addNumericField("Header_lines", settings.getHeaderLines(), 0);
		gd.addStringField("Comment", settings.getComment());
		gd.addStringField("Delimiter", settings.getDelimiter());
		gd.addChoice("Distance_unit", SettingsManager.getDistanceUnitNames(), cw.getDistanceUnitValue());
		gd.addChoice("Intensity_unit", SettingsManager.getIntensityUnitNames(), cw.getIntensityUnitValue());

		gd.addMessage("Define the fields:");
		gd.addNumericField("T", settings.getFieldT(), 0);
		gd.addNumericField("ID", settings.getFieldId(), 0);
		gd.addNumericField("X", settings.getFieldX(), 0);
		gd.addNumericField("Y", settings.getFieldY(), 0);
		gd.addNumericField("Z", settings.getFieldZ(), 0);
		gd.addNumericField("Intensity", settings.getFieldI(), 0);
		gd.addNumericField("Sx", settings.getFieldSx(), 0);
		gd.addNumericField("Sy", settings.getFieldSy(), 0);
		gd.addNumericField("Precision", settings.getFieldPrecision(), 0);
		gd.addChoice("Precision_method", SettingsManager.getPrecisionMethodNames(), cw.getPrecisionMethodValue());

		gd.showDialog();
		if (gd.wasCanceled())
		{
			return false;
		}

		settings.setName(getNextString(gd, settings.getName()));
		cw.setCameraType(SettingsManager.getCameraTypeValues()[gd.getNextChoiceIndex()]);
		cw.setNmPerPixel(gd.getNextNumber());
		cw.setExposureTime(gd.getNextNumber());

		// The time units used a truncated list so look-up the value from the index
		calibrationBuilder.getTimeCalibrationBuilder().setTimeUnit(tUnitValues[gd.getNextChoiceIndex()]);

		settings.setHeaderLines((int) gd.getNextNumber());
		settings.setComment(gd.getNextString());
		settings.setDelimiter(getNextString(gd, settings.getDelimiter()));

		cw.setDistanceUnit(DistanceUnit.forNumber(gd.getNextChoiceIndex()));
		cw.setIntensityUnit(IntensityUnit.forNumber(gd.getNextChoiceIndex()));

		int[] columns = new int[9];
		for (int i = 0; i < columns.length; i++)
			columns[i] = (int) gd.getNextNumber();

		{
			int i = 0;
			settings.setFieldI(columns[i++]);
			settings.setFieldId(columns[i++]);
			settings.setFieldX(columns[i++]);
			settings.setFieldY(columns[i++]);
			settings.setFieldZ(columns[i++]);
			settings.setFieldI(columns[i++]);
			settings.setFieldSx(columns[i++]);
			settings.setFieldSy(columns[i++]);
			settings.setFieldPrecision(columns[i++]);
		}

		cw.setPrecisionMethod(PrecisionMethod.forNumber(gd.getNextChoiceIndex()));

		// Collect the camera calibration
		gd.collectOptions();

		// Validate after reading the dialog (so we store the last entered values)
		if (gd.invalidNumber())
		{
			IJ.error(TITLE, "Invalid number in input fields");
			return false;
		}

		for (int i = 0; i < columns.length; i++)
		{
			if (columns[i] < 0)
				continue;
			for (int j = i + 1; j < columns.length; j++)
			{
				if (columns[j] < 0)
					continue;
				if (columns[i] == columns[j])
				{
					IJ.error(TITLE, "Duplicate indicies: " + columns[i]);
					return false;
				}
			}
		}
		if (cw.getNmPerPixel() <= 0)
		{
			IJ.error(TITLE, "Require positive pixel pitch");
			return false;
		}
		if (cw.isCCDCamera())
		{
			if (!cw.hasCountPerPhoton())
			{
				IJ.error(TITLE, "Require positive count/photon for CCD camera type");
				return false;
			}
		}
		else
		{
			// Q.Validate other camera types?
		}
		if (settings.getFieldX() < 0 || settings.getFieldY() < 0)
		{
			IJ.error(TITLE, "Require valid X and Y indices");
			return false;
		}
		return true;
	}

	private static String getNextString(GenericDialog gd, String defaultValue)
	{
		String value = gd.getNextString();
		if (TextUtils.isNullOrEmpty(value))
			return defaultValue;
		return value;
	}
}
