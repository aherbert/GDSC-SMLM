package gdsc.smlm.results.filter;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.thoughtworks.xstream.io.xml.DomDriver;

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

/**
 * Wraps the XStream functionality for reading/writing package members as XML. Initialises XStream for tidy XML.
 */
public abstract class XStreamWrapper
{
	@XStreamOmitField
	private static XStream xs = null;

	static
	{
		xs = new XStream(new DomDriver());
		if (xs != null)
		{
			xs.autodetectAnnotations(true);

			addAlias(FilterSet.class);

			// Add aliases for all Filter classes
			addAlias(AndFilter.class);
			addAlias(ANRFilter.class);
			addAlias(ANRFilter2.class);
			addAlias(CombinedFilter.class);
			addAlias(CoordinateFilter.class);
			addAlias(Filter.class);
			addAlias(HysteresisFilter.class);
			addAlias(MultiFilter.class);
			addAlias(OrFilter.class);
			addAlias(PrecisionFilter.class);
			addAlias(PrecisionFilter2.class);
			addAlias(PrecisionHysteresisFilter.class);
			addAlias(PrecisionHysteresisFilter2.class);
			addAlias(SBRFilter.class);
			addAlias(ShiftFilter.class);
			addAlias(SignalFilter.class);
			addAlias(SNRFilter.class);
			addAlias(SNRFilter2.class);
			addAlias(SNRHysteresisFilter.class);
			addAlias(TraceFilter.class);
			addAlias(WidthFilter.class);
			addAlias(WidthFilter2.class);

			// Removed dependency on reflections since this has other jar dependencies
			//Reflections reflections = new Reflections("gdsc.smlm.results.filter");
			//Set<Class<? extends Filter>> subTypes = reflections.getSubTypesOf(Filter.class);
			//for (Class<? extends Filter> type : subTypes)
			//	addAlias(type);
		}
	}

	/**
	 * Add a class name alias to the global XStream object used for serialisation.
	 * <p>
	 * Should be called to produce neater XML output for new sub-class types prior to using {@link #toXML(Object)} or
	 * {@link #fromXML(String)}.
	 * 
	 * @param type
	 *            The class
	 */
	public static void addAlias(Class<?> type)
	{
		if (xs != null)
			xs.alias(type.getSimpleName(), type);
	}

	/**
	 * @return An XML representation of this object
	 */
	public static String toXML(Object object)
	{
		if (xs != null)
		{
			try
			{
				return xs.toXML(object);
			}
			catch (XStreamException ex)
			{
				//ex.printStackTrace();
			}
		}
		return "";
	}

	/**
	 * Create the filter from the XML representation
	 * 
	 * @param xml
	 * @return the filter
	 */
	public static Object fromXML(String xml)
	{
		if (xs != null)
		{
			try
			{
				return xs.fromXML(xml);
			}
			catch (XStreamException ex)
			{
				//ex.printStackTrace();
			}
		}
		return null;
	}

	/**
	 * @return An XStream object for reading/writing package members
	 */
	public static XStream getInstance()
	{
		return xs;
	}
}