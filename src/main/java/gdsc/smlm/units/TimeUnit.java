package gdsc.smlm.units;

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
 * Unit for measuring time
 */
public enum TimeUnit implements Unit
{
	/** Frame units */
	FRAME
	{
		public String getName()
		{
			return "frame";
		}

		@Override
		public String getShortName()
		{
			return "t";
		}

		UnitConverter<TimeUnit> buildConverter(TimeUnit to, double msPerFrame) throws UnitConversionException
		{
			if (to == TimeUnit.MILLISECOND)
				return new MultiplyUnitConverter<TimeUnit>(this, to, msPerFrame);
			if (to == TimeUnit.SECOND)
				return new MultiplyUnitConverter<TimeUnit>(this, to, msPerFrame / 1e3);
			throw new UnitConversionException(this + " to " + to);
		}
	},

	/** Second units */
	SECOND
	{
		public String getName()
		{
			return "second";
		}

		public String getShortName()
		{
			return "s";
		}

		UnitConverter<TimeUnit> buildConverter(TimeUnit to, double msPerFrame) throws UnitConversionException
		{
			if (to == TimeUnit.FRAME)
				return new MultiplyUnitConverter<TimeUnit>(this, to, 1e3 / msPerFrame);
			if (to == TimeUnit.MILLISECOND)
				return new MultiplyUnitConverter<TimeUnit>(this, to, 1e3);
			throw new UnitConversionException(this + " to " + to);
		}
	},

	/** Millesecond units */
	MILLISECOND
	{
		public String getName()
		{
			return "millisecond";
		}

		public String getShortName()
		{
			return "ms";
		}

		UnitConverter<TimeUnit> buildConverter(TimeUnit to, double msPerFrame) throws UnitConversionException
		{
			if (to == TimeUnit.FRAME)
				return new MultiplyUnitConverter<TimeUnit>(this, to, 1.0 / msPerFrame);
			if (to == TimeUnit.SECOND)
				return new MultiplyUnitConverter<TimeUnit>(this, to, 1e-3);
			throw new UnitConversionException(this + " to " + to);
		}
	},;

	/**
	 * Gets the name.
	 *
	 * @return the name
	 */
	public abstract String getName();

	/**
	 * Gets the short name.
	 *
	 * @return the short name
	 */
	public abstract String getShortName();

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Enum#toString()
	 */
	public String toString()
	{
		return getName();
	}

	/**
	 * Creates the converter.
	 *
	 * @param to
	 *            the to
	 * @param msPerFrame
	 *            the ms per frame
	 * @return the unit converter
	 * @throws UnitConversionException
	 *             if a converter cannot be created
	 */
	public UnitConverter<TimeUnit> createConverter(TimeUnit to, double msPerFrame) throws UnitConversionException
	{
		if (this == to)
			return new IdentityUnitConverter<TimeUnit>(this);

		if (!(msPerFrame > 0 && msPerFrame <= java.lang.Double.MAX_VALUE))
			throw new UnitConversionException("ms/frame must be positive");

		return buildConverter(to, msPerFrame);
	}

	/**
	 * Build the converter for the unit.
	 *
	 * @param to
	 *            the to
	 * @param msPerFrame
	 *            the ms per frame
	 * @return the unit converter
	 * @throws UnitConversionException
	 *             if a converter cannot be created
	 */
	abstract UnitConverter<TimeUnit> buildConverter(TimeUnit to, double msPerFrame) throws UnitConversionException;

	/**
	 * Creates the converter.
	 *
	 * @param to
	 *            the to
	 * @return the unit converter
	 * @throws UnitConversionException
	 *             if a converter cannot be created
	 */
	public UnitConverter<TimeUnit> createConverter(TimeUnit to) throws UnitConversionException
	{
		if (this == to)
			return new IdentityUnitConverter<TimeUnit>(this);
		if (to == TimeUnit.FRAME)
			throw new UnitConversionException(this + " to " + to + " requires ms/frame");
		return buildConverter(to, 1.0);
	}
}