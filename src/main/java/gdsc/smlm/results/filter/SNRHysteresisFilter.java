package gdsc.smlm.results.filter;

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

import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

/**
 * Filter results using a signal-to-noise (SNR) threshold. Any results above the upper SNR limit are
 * included. Any results below the lower SNR limit are excluded. Any results between the limits are included only if
 * they can be traced through time, optionally via other candidates, to a valid result.
 */
public class SNRHysteresisFilter extends HysteresisFilter
{
	@XStreamAsAttribute
	final float lowerSnr;
	@XStreamAsAttribute
	final float range;
	@XStreamOmitField
	float upperSnr;

	public SNRHysteresisFilter(double searchDistance, int searchDistanceMode, float lowerSnr, float range)
	{
		super(searchDistance, searchDistanceMode);
		this.lowerSnr = lowerSnr;
		this.range = Math.abs(range);
	}

	@Override
	protected String generateName()
	{
		return String.format("SNR Hysteresis %.2f +%.2f (@%.2f %s)", lowerSnr, range, searchDistance, getSearchName());
	}

	@Override
	protected String generateType()
	{
		return "SNR Hysteresis";
	}

	@Override
	public void setup(MemoryPeakResults peakResults)
	{
		upperSnr = lowerSnr + range;
		super.setup(peakResults);
	}

	@Override
	protected PeakStatus getStatus(PeakResult result)
	{
		final float snr = SNRFilter.getSNR(result);
		if (snr >= upperSnr)
			return PeakStatus.OK;
		else if (snr >= lowerSnr)
			return PeakStatus.CANDIDATE;
		return PeakStatus.REJECT;
	}

	@Override
	public double getNumericalValue()
	{
		return lowerSnr;
	}

	@Override
	public String getNumericalValueName()
	{
		return "SNR +" + range;
	}

	@Override
	public String getDescription()
	{
		return "Filter results using a signal-to-noise (SNR) threshold. Any results above the upper SNR " +
				"limit are included. Any results below the lower SNR limit are excluded. " + super.getDescription();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.Filter#getNumberOfParameters()
	 */
	@Override
	public int getNumberOfParameters()
	{
		return 4;
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
				return searchDistance;
			case 1:
				return searchDistanceMode;
			case 2:
				return lowerSnr;
			default:
				return range;
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
				return "Search distance";
			case 1:
				return "Search mode";
			case 2:
				return "Lower SNR";
			default:
				return "Range";
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.Filter#adjustParameter(int, double)
	 */
	@Override
	public Filter adjustParameter(int index, double delta)
	{
		checkIndex(index);
		switch (index)
		{
			case 0:
				return new SNRHysteresisFilter(updateParameter(searchDistance, delta), searchDistanceMode, lowerSnr,
						range);
			case 1:
				return this;
			case 2:
				return new SNRHysteresisFilter(searchDistance, searchDistanceMode, updateParameter(lowerSnr, delta),
						range);
			default:
				return new SNRHysteresisFilter(searchDistance, searchDistanceMode, lowerSnr, updateParameter(range,
						delta));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.Filter#create(double[])
	 */
	@Override
	public Filter create(double... parameters)
	{
		return new SNRHysteresisFilter(parameters[0], (int) parameters[1], (float) parameters[2], (float) parameters[3]);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.Filter#weakestParameters(double[])
	 */
	@Override
	public void weakestParameters(double[] parameters)
	{
		setMax(parameters, 0, searchDistance);
		setMin(parameters, 2, lowerSnr);
		setMin(parameters, 3, range);
	}
}