package gdsc.smlm.ij.plugins;

import gdsc.smlm.data.config.CalibrationReader;
import gdsc.smlm.data.config.CalibrationWriter;
import gdsc.smlm.data.config.UnitHelper;
import gdsc.smlm.data.config.UnitProtos.AngleUnit;
import gdsc.smlm.data.config.UnitProtos.DistanceUnit;
import gdsc.smlm.data.config.UnitProtos.IntensityUnit;
import gdsc.smlm.data.config.CalibrationProtos.Calibration;

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

import gdsc.smlm.ij.plugins.ResultsManager.InputSource;
import gdsc.smlm.ij.settings.SettingsManager;
import gdsc.smlm.results.MemoryPeakResults;
import ij.IJ;
import ij.gui.ExtendedGenericDialog;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

/**
 * Allows results held in memory to be converted to different units.
 */
public class ConvertResults implements PlugIn
{
	private static final String TITLE = "Convert Results";

	private static String inputOption = "";

	/*
	 * (non-)
	 * 
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(String arg)
	{
		SMLMUsageTracker.recordPlugin(this.getClass(), arg);

		if (!showInputDialog())
			return;

		MemoryPeakResults results = ResultsManager.loadInputResults(inputOption, false, null, null);
		if (results == null || results.size() == 0)
		{
			IJ.error(TITLE, "No results could be loaded");
			return;
		}

		if (!showDialog(results))
			return;

		IJ.showStatus("Converted " + results.getName());
	}

	private boolean showInputDialog()
	{
		int size = MemoryPeakResults.countMemorySize();
		if (size == 0)
		{
			IJ.error(TITLE, "There are no fitting results in memory");
			return false;
		}

		ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);
		gd.addHelp(About.HELP_URL);
		gd.addMessage("Select results to convert");

		ResultsManager.addInput(gd, inputOption, InputSource.MEMORY);

		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		inputOption = ResultsManager.getInputSource(gd);

		return true;
	}

	private boolean showDialog(MemoryPeakResults results)
	{
		GenericDialog gd = new GenericDialog(TITLE);
		gd.addMessage("Convert the current units for the results");
		gd.addHelp(About.HELP_URL);

		CalibrationReader cr = CalibrationWriter.create(results.getCalibration());

		gd.addChoice("Distance_unit", SettingsManager.getDistanceUnitNames(), UnitHelper.getName(cr.getDistanceUnit()));
		gd.addNumericField("Calibration (nm/px)", cr.getNmPerPixel(), 2);
		gd.addChoice("Intensity_unit", SettingsManager.getIntensityUnitNames(),
				UnitHelper.getName(cr.getIntensityUnit()));
		gd.addNumericField("Gain (Count/photon)", cr.getCountPerPhoton(), 2);
		gd.addChoice("Angle_unit", SettingsManager.getAngleUnitNames(), UnitHelper.getName(cr.getAngleUnit()));

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		CalibrationWriter cw = results.getCalibrationWriterSafe();
		DistanceUnit distanceUnit = SettingsManager.getDistanceUnitValues()[gd.getNextChoiceIndex()];
		cw.setNmPerPixel(Math.abs(gd.getNextNumber()));
		IntensityUnit intensityUnit = SettingsManager.getIntensityUnitValues()[gd.getNextChoiceIndex()];
		cw.setCountPerPhoton(Math.abs(gd.getNextNumber()));
		AngleUnit angleUnit = SettingsManager.getAngleUnitValues()[gd.getNextChoiceIndex()];

		// Don't set the calibration with bad values
		if (distanceUnit.getNumber() > 0 && !(cw.getNmPerPixel() > 0))
		{
			IJ.error(TITLE, "Require positive nm/pixel for conversion");
			return false;
		}
		if (intensityUnit.getNumber() > 0 && !(cw.getCountPerPhoton() > 0))
		{
			IJ.error(TITLE, "Require positive Count/photon for conversion");
			return false;
		}

		Calibration newCalibration = cw.getCalibration();
		results.setCalibration(newCalibration);

		if (!results.convertToUnits(distanceUnit, intensityUnit, angleUnit))
		{
			IJ.error(TITLE, "Conversion failed");
			return false;
		}

		return true;
	}
}