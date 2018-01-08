package gdsc.smlm.results.filter;

import gdsc.smlm.data.NamedObject;

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

/**
 * Define the type of parameter
 */
public enum ParameterType implements NamedObject
{
	//@formatter:off
	SIGNAL("Signal"),
	SIGNAL_RANGE("Signal Range"),
	SNR("Signal-to-Noise Ratio","SNR"),
	SNR_RANGE("Signal-to-Noise Ratio Range","SNR Range"),
	MIN_WIDTH("Min Width"),
	MIN_WIDTH_RANGE("Min Width Range"),
	MAX_WIDTH("Max Width"),
	MAX_WIDTH_RANGE("Max Width Range"),
	SHIFT("Shift"),
	SHIFT_RANGE("Shift Range"),
	ESHIFT("Euclidian Shift","EShift"),
	PRECISION("Precision"),
	PRECISION_RANGE("Precision Range"),
	PRECISION2("Precision using local background","Precision2"),
	PRECISION2_RANGE("Precision using local background Range","Precision2 Range"),
	PRECISION_CRLB("Precision using CRLB","Precision CRLB"),
	PRECISION_CRLB_RANGE("Precision using CRLB Range","Precision CRLB Range"),
	ANR("Amplitude-to-Noise Ratio","ANR"),
	SBR("Signal-to-Background Ratio","SBR"),
	DISTANCE_THRESHOLD("Distance Threshold","D-threshold"),
	DISTANCE_THRESHOLD_MODE("Distance Threshold Mode","D-threshold mode"),
	TIME_THRESHOLD("Time Threshold","T-threshold"),
	TIME_THRESHOLD_MODE("Time Threshold Mode","T-threshold mode"),
	MIN_X("Min X"),
	MAX_X("Max X"),
	MIN_Y("Min Y"),
	MAX_Y("Max Y");
	
	//@formatter:on

	private final String name;
	private final String shortName;

	private ParameterType(String name)
	{
		this(name, name);
	}

	private ParameterType(String name, String sname)
	{
		this.name = name;
		this.shortName = sname;
	}

	@Override
	public String toString()
	{
		return shortName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.data.NamedObject#getName()
	 */
	public String getName()
	{
		return name;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.data.NamedObject#getShortName()
	 */
	public String getShortName()
	{
		return shortName;
	}
}