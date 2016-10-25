package gdsc.smlm.results.filter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

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

import gdsc.smlm.results.MemoryPeakResults;
import gdsc.smlm.results.PeakResult;

/**
 * Filter results using multiple thresholds: Signal, SNR, width, coordinate shift and precision
 */
public class MultiFilter extends DirectFilter implements IMultiFilter
{
	@XStreamAsAttribute
	final double signal;
	@XStreamAsAttribute
	final float snr;
	@XStreamAsAttribute
	final double minWidth;
	@XStreamAsAttribute
	final double maxWidth;
	@XStreamAsAttribute
	final double shift;
	@XStreamAsAttribute
	final double eshift;
	@XStreamAsAttribute
	final double precision;

	@XStreamOmitField
	float signalThreshold;
	@XStreamOmitField
	float lowerSigmaThreshold;
	@XStreamOmitField
	float upperSigmaThreshold;
	@XStreamOmitField
	float offset;
	@XStreamOmitField
	float eoffset;
	@XStreamOmitField
	double variance;
	@XStreamOmitField
	double nmPerPixel = 100;
	@XStreamOmitField
	boolean emCCD = true;
	@XStreamOmitField
	double gain = 1;
	@XStreamOmitField
	boolean widthEnabled;
	@XStreamOmitField
	MultiFilterComponentSet components = null;
	@XStreamOmitField
	MultiFilterComponentSet componentsNoWidth = null;
	@XStreamOmitField
	MultiFilterComponentSet componentsWidth = null;

	public MultiFilter(double signal, float snr, double minWidth, double maxWidth, double shift, double eshift,
			double precision)
	{
		this.signal = Math.max(0, signal);
		this.snr = Math.max(0, snr);
		// Only swap if max width is enabled
		if (maxWidth != 0 && maxWidth < minWidth)
		{
			final double f = maxWidth;
			maxWidth = minWidth;
			minWidth = f;
		}
		this.minWidth = Math.max(0, minWidth);
		this.maxWidth = Math.max(0, maxWidth);
		this.shift = Math.max(0, shift);
		this.eshift = Math.max(0, eshift);
		this.precision = Math.max(0, precision);
	}

	@Override
	protected String generateName()
	{
		return String.format("Multi: Signal=%.1f, SNR=%.1f, Width=%.2f-%.2f, Shift=%.2f, EShift=%.2f, Precision=%.1f",
				signal, snr, minWidth, maxWidth, shift, eshift, precision);
	}

	@Override
	protected String generateType()
	{
		return "Multi";
	}

	@Override
	public void setup(MemoryPeakResults peakResults)
	{
		// Set the signal limit using the gain
		gain = peakResults.getGain();
		signalThreshold = (float) (signal * gain);

		// Set the width limit
		lowerSigmaThreshold = 0;
		upperSigmaThreshold = Float.POSITIVE_INFINITY;
		// Set the shift limit
		offset = Float.POSITIVE_INFINITY;
		eoffset = Float.POSITIVE_INFINITY;
		Pattern pattern = Pattern.compile("initialSD0>([\\d\\.]+)");
		Matcher match = pattern.matcher(peakResults.getConfiguration());
		if (match.find())
		{
			double s = Double.parseDouble(match.group(1));
			lowerSigmaThreshold = (float) (s * minWidth);
			upperSigmaThreshold = Filter.getUpperLimit(s * maxWidth);
			offset = Filter.getUpperLimit(s * shift);
			// Convert to squared distance
			eoffset = Filter.getUpperSquaredLimit(s * eshift);
		}

		// Configure the precision limit
		variance = Filter.getDUpperSquaredLimit(precision);
		nmPerPixel = peakResults.getNmPerPixel();
		emCCD = peakResults.isEMCCD();
	}

	@Override
	public void setup()
	{
		setup(true);
	}

	@Override
	public void setup(int flags)
	{
		setup(!areSet(flags, DirectFilter.NO_WIDTH));
	}

	private void setup(final boolean widthEnabled)
	{
		if (componentsWidth == null)
		{
			// Create the components we require
			final MultiFilterComponent[] components1 = new MultiFilterComponent[6];
			final MultiFilterComponent[] components2 = new MultiFilterComponent[6];
			int s1 = 0, s2 = 0;

			// Current order of filter power obtained from BenchmarkFilterAnalysis:
			// SNR, Max Width, Precision, Shift, Min width
			if (snr != 0)
			{
				components1[s1++] = components2[s2++] = new MultiFilterSNRComponent(snr);
			}
			if (maxWidth != 0 || minWidth != 0)
			{
				components1[s1++] = new MultiFilterWidthComponent(minWidth, maxWidth);
			}
			if (precision != 0)
			{
				components1[s1++] = components2[s2++] = new MultiFilterVarianceComponent(precision);
			}
			if (shift != 0)
			{
				components1[s1++] = components2[s2++] = new MultiFilterShiftComponent(shift);
			}
			if (signal != 0)
			{
				components1[s1++] = components2[s2++] = new MultiFilterSignalComponent(signal);
			}
			if (eshift != 0)
			{
				components1[s1++] = components2[s2++] = new MultiFilterEShiftComponent(eshift);
			}
			componentsWidth = MultiFilterComponentSetFactory.create(components1, s1);
			componentsNoWidth = MultiFilterComponentSetFactory.create(components2, s2);
		}

		components = (widthEnabled) ? componentsWidth : componentsNoWidth;

		//		// This is the legacy support for all components together
		//		this.widthEnabled = widthEnabled;
		//		signalThreshold = (float) signal;
		//		if (widthEnabled)
		//		{
		//			lowerSigmaThreshold = (float) minWidth;
		//			upperSigmaThreshold = Filter.getUpperLimit(maxWidth);
		//		}
		//		offset = Filter.getUpperSquaredLimit(shift);
		//		eoffset = Filter.getUpperSquaredLimit(eshift);
		//		variance = Filter.getDUpperSquaredLimit(precision);
	}

	@Override
	public boolean accept(PeakResult peak)
	{
		// Current order of filter power obtained from BenchmarkFilterAnalysis:
		// SNR, Max Width, Precision, Shift, Min width

		if (SNRFilter.getSNR(peak) < this.snr)
			return false;

		// Width
		final float sd = peak.getSD();
		if (sd > upperSigmaThreshold || sd < lowerSigmaThreshold)
			return false;
		
		// Precision
		final double s = nmPerPixel * sd;
		final double N = peak.getSignal();
		if (PeakResult.getVariance(nmPerPixel, s, N / gain, peak.getNoise() / gain, emCCD) > variance)
			return false;

		// Shift
		if (Math.abs(peak.getXPosition()) > offset || Math.abs(peak.getYPosition()) > offset)
			return false;

		if (peak.getSignal() < signalThreshold)
			return false;

		// Euclidian shift
		final float dx = peak.getXPosition();
		final float dy = peak.getYPosition();
		if (dx * dx + dy * dy > eoffset)
			return false;

		return true;
	}

	@Override
	public int validate(final PreprocessedPeakResult peak)
	{
		return components.validate(peak);

		//		// This is the legacy support for all components together
		//		
		//		// Current order of filter power obtained from BenchmarkFilterAnalysis:
		//		// Precision, Max Width, SNR, Shift, Min width
		//
		//		if (peak.getLocationVariance() > variance)
		//			return V_LOCATION_VARIANCE;
		//
		//		if (widthEnabled)
		//		{
		//			final float xsdf = peak.getXSDFactor();
		//			if (xsdf > upperSigmaThreshold || xsdf < lowerSigmaThreshold)
		//				return V_X_SD_FACTOR;
		//		}
		//
		//		if (peak.getSNR() < this.snr)
		//			return V_SNR;
		//
		//		// Shift
		//		final float xs2 = peak.getXRelativeShift2();
		//		if (xs2 > offset)
		//			return V_X_RELATIVE_SHIFT;
		//		final float ys2 = peak.getYRelativeShift2();
		//		if (ys2 > offset)
		//			return V_Y_RELATIVE_SHIFT;
		//
		//		if (peak.getPhotons() < signal)
		//			return V_PHOTONS;
		//
		//		// Euclidian shift
		//		if (xs2 + ys2 > eoffset)
		//			return V_X_RELATIVE_SHIFT | V_Y_RELATIVE_SHIFT;
		//
		//		return 0;
	}

	@Override
	public double getNumericalValue()
	{
		return snr;
	}

	@Override
	public String getNumericalValueName()
	{
		return "SNR";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.Filter#getDescription()
	 */
	@Override
	public String getDescription()
	{
		return "Filter results using an multiple thresholds: Signal, SNR, width, shift, Euclidian shift and precision";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.Filter#getNumberOfParameters()
	 */
	@Override
	public int getNumberOfParameters()
	{
		return 7;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.Filter#getParameterValue(int)
	 */
	@Override
	public double getParameterValue(int index)
	{
		checkIndex(index);
		switch (index)
		{
			case 0:
				return signal;
			case 1:
				return snr;
			case 2:
				return minWidth;
			case 3:
				return maxWidth;
			case 4:
				return shift;
			case 5:
				return eshift;
			default:
				return precision;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.Filter#getParameterIncrement(int)
	 */
	@Override
	public double getParameterIncrement(int index)
	{
		checkIndex(index);
		switch (index)
		{
			case 0:
				return SignalFilter.DEFAULT_INCREMENT;
			case 1:
				return SNRFilter.DEFAULT_INCREMENT;
			case 2:
				return WidthFilter2.DEFAULT_MIN_INCREMENT;
			case 3:
				return WidthFilter.DEFAULT_INCREMENT;
			case 4:
				return ShiftFilter.DEFAULT_INCREMENT;
			case 5:
				return EShiftFilter.DEFAULT_INCREMENT;
			default:
				return PrecisionFilter.DEFAULT_INCREMENT;
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.Filter#getParameterName(int)
	 */
	@Override
	public String getParameterName(int index)
	{
		checkIndex(index);
		switch (index)
		{
			case 0:
				return "Signal";
			case 1:
				return "SNR";
			case 2:
				return "Min width";
			case 3:
				return "Max width";
			case 4:
				return "Shift";
			case 5:
				return "EShift";
			default:
				return "Precision";
		}
	}

	static double[] defaultRange = new double[] { SignalFilter.DEFAULT_RANGE, SNRFilter.DEFAULT_RANGE,
			WidthFilter2.DEFAULT_MIN_RANGE, WidthFilter.DEFAULT_RANGE, ShiftFilter.DEFAULT_RANGE,
			EShiftFilter.DEFAULT_RANGE, PrecisionFilter.DEFAULT_RANGE };

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.Filter#adjustParameter(int, double)
	 */
	@Override
	public Filter adjustParameter(int index, double delta)
	{
		checkIndex(index);
		double[] params = new double[] { signal, snr, minWidth, maxWidth, shift, eshift, precision };
		params[index] = updateParameter(params[index], delta, defaultRange[index]);
		return new MultiFilter(params[0], (float) params[1], params[2], params[3], params[4], params[5], params[6]);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.Filter#create(double[])
	 */
	@Override
	public Filter create(double... parameters)
	{
		return new MultiFilter(parameters[0], (float) parameters[1], parameters[2], parameters[3], parameters[4],
				parameters[5], parameters[6]);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.Filter#weakestParameters(double[])
	 */
	@Override
	public void weakestParameters(double[] parameters)
	{
		setMin(parameters, 0, signal);
		setMin(parameters, 1, snr);
		setMin(parameters, 2, minWidth);
		setMax(parameters, 3, maxWidth);
		setMax(parameters, 4, shift);
		setMax(parameters, 5, eshift);
		setMax(parameters, 6, precision);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.ga.Chromosome#length()
	 */
	public int length()
	{
		return 6;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.Filter#upperLimit()
	 */
	@Override
	public double[] upperLimit()
	{
		return new double[] { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, WidthFilter.UPPER_LIMIT,
				WidthFilter.UPPER_LIMIT, ShiftFilter.UPPER_LIMIT, EShiftFilter.UPPER_LIMIT,
				PrecisionFilter.UPPER_LIMIT };
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.ga.Chromosome#sequence()
	 */
	public double[] sequence()
	{
		return new double[] { signal, snr, minWidth, maxWidth, shift, eshift, precision };
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.ga.Chromosome#mutationStepRange()
	 */
	public double[] mutationStepRange()
	{
		return defaultRange;
	}

	public double getSignal()
	{
		return signal;
	}

	public double getSNR()
	{
		return snr;
	}

	public double getMinWidth()
	{
		return minWidth;
	}

	public double getMaxWidth()
	{
		return maxWidth;
	}

	public double getShift()
	{
		return shift;
	}

	public double getEShift()
	{
		return eshift;
	}

	public double getPrecision()
	{
		return precision;
	}

	public boolean isPrecisionUsesLocalBackground()
	{
		return false;
	}
}