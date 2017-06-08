package gdsc.smlm.units;

import gdsc.core.utils.Maths;

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
 * Perform conversion by multiplication then addition
 */
public class MultiplyAddUnitConverter<T extends Unit> extends MultiplyUnitConverter<T>
{
	private final double addition;

	/**
	 * Instantiates a new multiplication then add unit converter.
	 *
	 * @param from
	 *            unit to convert from
	 * @param to
	 *            unit to convert to
	 * @param multiplication
	 *            the multiplication
	 * @param addition
	 *            the value to add after multiplication
	 * @throws UnitConversionException
	 *             If the input units are null
	 * @throws UnitConversionException
	 *             If the multiplication is not finite
	 * @throws UnitConversionException
	 *             If the addition is not finite
	 */
	public MultiplyAddUnitConverter(T from, T to, double multiplication, double addition) throws UnitConversionException
	{
		super(from, to, multiplication);
		if (!Maths.isFinite(addition))
			throw new UnitConversionException("addition must be finite");
		this.addition = addition;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.units.UnitConverter#convert(double)
	 */
	public double convert(double value)
	{
		return value * multiplication + addition;
	}
}