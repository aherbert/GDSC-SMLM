package gdsc.smlm.filters;

import java.util.List;

/*----------------------------------------------------------------------------- 
 * GDSC SMLM Software
 * 
 * Copyright (C) 2015 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

/**
 * Identifies candidate spots (local maxima) in an image. The image is pre-processed with a single filter.
 */
public class SingleSpotFilter extends MaximaSpotFilter
{
	private DataProcessor processor;

	/**
	 * Constructor
	 * 
	 * @param search
	 *            The search width for non-maximum suppression
	 * @param border
	 *            The border to ignore for maxima
	 * @param processor
	 *            The data processor
	 * @throws IllegalArgumentException
	 *             if processor is null
	 */
	public SingleSpotFilter(int search, int border, DataProcessor processor)
	{
		super(search, border);
		if (processor == null)
			throw new IllegalArgumentException("Processor is null");
		this.processor = processor;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.filters.SpotFilter#isAbsoluteIntensity()
	 */
	public boolean isAbsoluteIntensity()
	{
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.filters.MaximaSpotFilter#preprocessData(float[], int, int)
	 */
	@Override
	public float[] preprocessData(float[] data, int width, int height)
	{
		return processor.process(data, width, height);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	public SingleSpotFilter clone()
	{
		SingleSpotFilter f = (SingleSpotFilter) super.clone();
		// Ensure the object is duplicated and not passed by reference.
		f.processor = processor.clone();
		return f;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.filters.SpotFilter#getName()
	 */
	@Override
	public String getName()
	{
		return "Single";
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.filters.MaximaSpotFilter#getParameters()
	 */
	@Override
	public List<String> getParameters()
	{
		List<String> list = super.getParameters();
		list.add("Filter = " + processor.getDescription());
		return list;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.filters.SpotFilter#getSpread()
	 */
	@Override
	public double getSpread()
	{
		return processor.getSpread();
	}
}