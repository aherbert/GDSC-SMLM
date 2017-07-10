package gdsc.smlm.data.config;

import gdsc.core.data.utils.ConversionException;
import gdsc.core.data.utils.TypeConverter;
import gdsc.smlm.data.config.UnitProtos.AngleUnit;
import gdsc.smlm.data.config.CalibrationProtos.CalibrationOrBuilder;
import gdsc.smlm.data.config.CalibrationProtos.CameraType;
import gdsc.smlm.data.config.UnitProtos.DistanceUnit;
import gdsc.smlm.data.config.UnitProtos.IntensityUnit;

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

/**
 * Contains helper functions for reading the Calibration class.
 */
public class CalibrationReader
{
	/** The calibration or builder. */
	private CalibrationOrBuilder calibrationOrBuilder;

	/**
	 * Instantiates a new calibration reader with no calibration.
	 */
	protected CalibrationReader()
	{
	}

	/**
	 * Instantiates a new calibration reader.
	 *
	 * @param calibration
	 *            the calibration
	 * @throws IllegalArgumentException
	 *             if the calibration is null
	 */
	public CalibrationReader(CalibrationOrBuilder calibration) throws IllegalArgumentException
	{
		if (calibration == null)
			throw new IllegalArgumentException("Calibration is null");
		this.calibrationOrBuilder = calibration;
	}

	/**
	 * Gets the calibration or builder with the latest changes.
	 *
	 * @return the calibration or builder
	 */
	public CalibrationOrBuilder getCalibrationOrBuilder()
	{
		return calibrationOrBuilder;
	}


	/**
	 * Gets a distance converter to update values.
	 * <p>
	 * If the conversion is not possible then an exception is thrown.
	 *
	 * @param toDistanceUnit
	 *            the distance unit
	 * @return the distance converter
	 * @throws ConversionException
	 *             the conversion exception
	 */
	public TypeConverter<DistanceUnit> getDistanceConverter(DistanceUnit toDistanceUnit) throws ConversionException
	{
		return CalibrationHelper.getDistanceConverter(getCalibrationOrBuilder(), toDistanceUnit);
	}

	/**
	 * Gets an intensity converter to update values.
	 * <p>
	 * If the conversion is not possible then an exception is thrown.
	 *
	 * @param toIntensityUnit
	 *            the intensity unit
	 * @return the intensity converter
	 * @throws ConversionException
	 *             the conversion exception
	 */
	public TypeConverter<IntensityUnit> getIntensityConverter(IntensityUnit toIntensityUnit) throws ConversionException
	{
		return CalibrationHelper.getIntensityConverter(getCalibrationOrBuilder(), toIntensityUnit);
	}

	/**
	 * Gets an angle converter to update values.
	 * <p>
	 * If the conversion is not possible then an exception is thrown.
	 *
	 * @param toAngleUnit
	 *            the angle unit
	 * @return the angle converter
	 * @throws ConversionException
	 *             the conversion exception
	 */
	public TypeConverter<AngleUnit> getAngleConverter(AngleUnit toAngleUnit) throws ConversionException
	{
		return CalibrationHelper.getAngleConverter(getCalibrationOrBuilder(), toAngleUnit);
	}

	/**
	 * Gets a distance converter to update values.
	 * <p>
	 * If the calibration is already in the given units or conversion is not possible
	 * then an identity converter will be returned.
	 *
	 * @param toDistanceUnit
	 *            the distance unit
	 * @return CalibrationHelper.the distance converter
	 */
	public TypeConverter<DistanceUnit> getDistanceConverterSafe(DistanceUnit toDistanceUnit)
	{
		return CalibrationHelper.getDistanceConverterSafe(getCalibrationOrBuilder(), toDistanceUnit);
	}

	/**
	 * Gets an intensity converter to update values.
	 * <p>
	 * If the calibration is already in the given units or conversion is not possible
	 * then an identity converter will be returned.
	 *
	 * @param toIntensityUnit
	 *            the intensity unit
	 * @return CalibrationHelper.the intensity converter
	 */
	public TypeConverter<IntensityUnit> getIntensityConverterSafe(IntensityUnit toIntensityUnit)
	{
		return CalibrationHelper.getIntensityConverterSafe(getCalibrationOrBuilder(), toIntensityUnit);
	}

	/**
	 * Gets an angle converter to update values.
	 * <p>
	 * If the calibration is already in the given units or conversion is not possible
	 * then an identity converter will be returned.
	 *
	 * @param toAngleUnit
	 *            the angle unit
	 * @return CalibrationHelper.the angle converter
	 */
	public TypeConverter<AngleUnit> getAngleConverterSafe(AngleUnit toAngleUnit)
	{
		return CalibrationHelper.getAngleConverterSafe(getCalibrationOrBuilder(), toAngleUnit);
	}

	/**
	 * Gets image pixel size in nanometers.
	 *
	 * @return image pixel size in nanometers
	 */
	public double getNmPerPixel()
	{
		CalibrationOrBuilder c = getCalibrationOrBuilder();
		return (c.hasDistanceCalibration()) ? c.getDistanceCalibration().getNmPerPixel() : 0;
	}

	/**
	 * Checks for nm per pixel.
	 *
	 * @return true, if successful
	 */
	public boolean hasNmPerPixel()
	{
		return getNmPerPixel() > 0;
	}

	/**
	 * Gets the gain (Count/photon). Can be used to convert the signal in count units to photons.
	 *
	 * @return the gain
	 */
	public double getGain()
	{
		CalibrationOrBuilder c = getCalibrationOrBuilder();
		return (c.hasIntensityCalibration()) ? c.getIntensityCalibration().getGain() : 0;
	}

	/**
	 * Checks for gain.
	 *
	 * @return true, if successful
	 */
	public boolean hasGain()
	{
		return getGain() > 0;
	}

	/**
	 * Gets the exposure time in milliseconds per frame.
	 *
	 * @return the exposure time
	 */
	public double getExposureTime()
	{
		CalibrationOrBuilder c = getCalibrationOrBuilder();
		return (c.hasTimeCalibration()) ? c.getTimeCalibration().getExposureTime() : 0;
	}

	/**
	 * Checks for exposure time.
	 *
	 * @return true, if successful
	 */
	public boolean hasExposureTime()
	{
		return getExposureTime() > 0;
	}

	/**
	 * Gets the camera Gaussian read noise (in Count units).
	 *
	 * @return the camera Gaussian read noise (in Count units)
	 */
	public double getReadNoise()
	{
		CalibrationOrBuilder c = getCalibrationOrBuilder();
		return (c.hasCameraCalibration()) ? c.getCameraCalibration().getReadNoise() : 0;
	}

	/**
	 * Checks for read noise.
	 *
	 * @return true, if successful
	 */
	public boolean hasReadNoise()
	{
		return getReadNoise() > 0;
	}

	/**
	 * Gets camera bias (in Count units).
	 *
	 * @return camera bias (in Count units)
	 */
	public double getBias()
	{
		CalibrationOrBuilder c = getCalibrationOrBuilder();
		return (c.hasCameraCalibration()) ? c.getCameraCalibration().getBias() : 0;
	}

	/**
	 * Checks for bias.
	 *
	 * @return true, if successful
	 */
	public boolean hasBias()
	{
		// Bias can be zero (the default) so check for a camera calibration first
		CalibrationOrBuilder c = getCalibrationOrBuilder();
		return c.hasCameraCalibration() && c.getCameraCalibration().getBias() >= 0;
	}

	/**
	 * Get the camera type.
	 *
	 * @return the camera type
	 */
	public CameraType getCameraType()
	{
		CalibrationOrBuilder c = getCalibrationOrBuilder();
		return (c.hasCameraCalibration()) ? c.getCameraCalibration().getCameraType() : CameraType.CAMERA_TYPE_NA;
	}

	/**
	 * Checks for camera type.
	 *
	 * @return true, if successful
	 */
	public boolean hasCameraType()
	{
		return getCameraType().getNumber() > 0;
	}

	/**
	 * Checks for a CCD camera.
	 *
	 * @return true, if successful
	 */
	public boolean isCCDCamera()
	{
		CalibrationOrBuilder c = getCalibrationOrBuilder();
		if (c.hasCameraCalibration())
		{
			switch (c.getCameraCalibration().getCameraType())
			{
				case CCD:
				case EMCCD:
					return true;
				default:
					break;
			}
		}
		return false;
	}

	/**
	 * Checks if the camera type was an Electron Multiplying (EM) CCD.
	 *
	 * @return true, if the camera type was an Electron Multiplying (EM) CCD
	 */
	public boolean isEMCCD()
	{
		return getCameraType() == CameraType.EMCCD;
	}

	/**
	 * Checks if the camera type was a standard CCD.
	 *
	 * @return true, if the camera type was a standard CCD.
	 */
	public boolean isCCD()
	{
		return getCameraType() == CameraType.CCD;
	}

	/**
	 * Checks if the camera type was a sCMOS.
	 *
	 * @return true, if the camera type was a sCMOS.
	 */
	public boolean isSCMOS()
	{
		return getCameraType() == CameraType.SCMOS;
	}

	/**
	 * Get the camera amplification (Count/e-) used when modelling a microscope camera.
	 * <p>
	 * Note that the camera noise model assumes that electrons are converted to Count units by amplification that is not
	 * perfect (i.e. it has noise). The amplification is equal to the gain (Count/photon) divided by the quantum
	 * efficiency (e-/photon).
	 *
	 * @return the amplification
	 */
	public double getAmplification()
	{
		CalibrationOrBuilder c = getCalibrationOrBuilder();
		return (c.hasCameraCalibration()) ? c.getCameraCalibration().getAmplification() : 0;
	}

	/**
	 * Checks for amplification.
	 *
	 * @return true, if successful
	 */
	public boolean hasAmplification()
	{
		return getAmplification() > 0;
	}

	/**
	 * Get the distance unit used for the results.
	 *
	 * @return the distanceUnit
	 */
	public DistanceUnit getDistanceUnit()
	{
		CalibrationOrBuilder c = getCalibrationOrBuilder();
		return (c.hasDistanceCalibration()) ? c.getDistanceCalibration().getDistanceUnit() : DistanceUnit.DISTANCE_UNIT_NA;
	}

	/**
	 * Checks for distance unit.
	 *
	 * @return true, if successful
	 */
	public boolean hasDistanceUnit()
	{
		return getDistanceUnit().getNumber() > 0;
	}

	/**
	 * Get the intensity unit used for the results.
	 *
	 * @return the intensityUnit
	 */
	public IntensityUnit getIntensityUnit()
	{
		CalibrationOrBuilder c = getCalibrationOrBuilder();
		return (c.hasIntensityCalibration()) ? c.getIntensityCalibration().getIntensityUnit() : IntensityUnit.INTENSITY_UNIT_NA;
	}

	/**
	 * Checks for intensity unit.
	 *
	 * @return true, if successful
	 */
	public boolean hasIntensityUnit()
	{
		return getIntensityUnit().getNumber() > 0;
	}

	/**
	 * Get the angle unit used for the results.
	 *
	 * @return the angleUnit
	 */
	public AngleUnit getAngleUnit()
	{
		CalibrationOrBuilder c = getCalibrationOrBuilder();
		return (c.hasAngleCalibration()) ? c.getAngleCalibration().getAngleUnit() : AngleUnit.ANGLE_UNIT_NA;
	}

	/**
	 * Checks for angle unit.
	 *
	 * @return true, if successful
	 */
	public boolean hasAngleUnit()
	{
		return getAngleUnit().getNumber() > 0;
	}
}
