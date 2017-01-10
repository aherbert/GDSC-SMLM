package gdsc.smlm.results;

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
 * Contains the options to set before reading the results.
 */
public class ResultOption
{

	/** The id. */
	public final int id;

	/** The name. */
	public final String name;

	/** The set of valid values. This can be null. */
	public final Object[] values;

	/** The value. */
	private Object value;

	ResultOption(int id, String name, Object value, Object[] values)
	{
		this.id = id;
		this.name = name;
		this.values = values;
		this.setValue(value);
	}

	/**
	 * Gets the value.
	 *
	 * @return the value
	 */
	public Object getValue()
	{
		return value;
	}

	/**
	 * Sets the value. If the list of valid values is not empty then an exception will be thrown if the value is one of
	 * the valid values.
	 *
	 * @param value
	 *            the new value
	 * @throws IllegalArgumentException
	 *             If the value is not in the list of valid values
	 */
	public void setValue(Object value)
	{
		checkValue(value);
		this.value = value;
	}

	private void checkValue(Object value)
	{
		if (hasValues())
		{
			for (int i = 0; i < values.length; i++)
				if (values[i].equals(value))
					return;
			throw new IllegalArgumentException("Not a valid value: " + value);
		}
	}

	/**
	 * Checks for valid values.
	 *
	 * @return true, if the list of valid values is not empty
	 */
	public boolean hasValues()
	{
		return values != null && values.length > 0;
	}
}