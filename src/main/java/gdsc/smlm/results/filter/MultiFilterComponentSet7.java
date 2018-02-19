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
 * Contains a set of components of the multi filter.
 */
public class MultiFilterComponentSet7 extends MultiFilterComponentSet
{
	private MultiFilterComponent component0;
	private MultiFilterComponent component1;
	private MultiFilterComponent component2;
	private MultiFilterComponent component3;
	private MultiFilterComponent component4;
	private MultiFilterComponent component5;
	private MultiFilterComponent component6;

	public MultiFilterComponentSet7(MultiFilterComponent[] components)
	{
		this.component0 = components[0];
		this.component1 = components[1];
		this.component2 = components[2];
		this.component3 = components[3];
		this.component4 = components[4];
		this.component5 = components[5];
		this.component6 = components[6];
	}

	@Override
	public int getValidationFlags()
	{
		return component0.getType() | component1.getType() | component2.getType() | component3.getType() |
				component4.getType() | component5.getType() | component6.getType();
	}

	@Override
	public int validate(final PreprocessedPeakResult peak)
	{
		//@formatter:off
		if (component0.fail(peak)) return component0.getType();
		if (component1.fail(peak)) return component1.getType();
		if (component2.fail(peak)) return component2.getType();
		if (component3.fail(peak)) return component3.getType();
		if (component4.fail(peak)) return component4.getType();
		if (component5.fail(peak)) return component5.getType();
		if (component6.fail(peak)) return component6.getType();
		//@formatter:on
		return 0;
	}

	@Override
	void replace0(MultiFilterComponent c)
	{
		component0 = c;
	}
}