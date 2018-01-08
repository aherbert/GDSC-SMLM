package gdsc.smlm.results.filter;

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
 * Filter results using Euclidian Shift
 */
public class MultiFilterEShiftComponent extends MultiFilterComponent
{
	final static int type = IDirectFilter.V_X_RELATIVE_SHIFT | IDirectFilter.V_Y_RELATIVE_SHIFT;
	
	final float eoffset;

	public MultiFilterEShiftComponent(double eshift)
	{
		this.eoffset = Filter.getUpperSquaredLimit(eshift);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.MultiFilterComponent#fail(gdsc.smlm.results.filter.PreprocessedPeakResult)
	 */
	public boolean fail(final PreprocessedPeakResult peak)
	{
		return (peak.getXRelativeShift2() + peak.getYRelativeShift2() > eoffset);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.MultiFilterComponent#getType()
	 */
	public int getType()
	{
		return type;
	}
}