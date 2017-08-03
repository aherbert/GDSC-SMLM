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
 * Null implementation of the CoordinateStore interface.
 */
public class NullCoordinateStore implements CoordinateStore
{
	// Note: We have package level constructors so that the factory must be used to create an instance.

	/**
	 * Instantiates a new null coordinate store.
	 */
	NullCoordinateStore()
	{
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.CoordinateStore#getResolution()
	 */
	public double getXYResolution()
	{
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.CoordinateStore#getZResolution()
	 */
	public double getZResolution()
	{
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.CoordinateStore#addToQueue(double, double, double)
	 */
	public void addToQueue(double x, double y, double z)
	{
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.CoordinateStore#flush()
	 */
	public void flush()
	{
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.CoordinateStore#add(double, double, double)
	 */
	public void add(double x, double y, double z)
	{
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.CoordinateStore#clear()
	 */
	public void clear()
	{
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.CoordinateStore#contains(double, double, double)
	 */
	public boolean contains(double x, double y, double z)
	{
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.CoordinateStore#find(double, double, double)
	 */
	public double[] find(double x, double y, double z)
	{
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.CoordinateStore#newInstance()
	 */
	public CoordinateStore newInstance()
	{
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.CoordinateStore#resize(int, int, int, int)
	 */
	public CoordinateStore resize(int minx, int miny, int maxx, int maxy)
	{
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.CoordinateStore#getMinX()
	 */
	public int getMinX()
	{
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.CoordinateStore#getMinY()
	 */
	public int getMinY()
	{
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.CoordinateStore#getWidth()
	 */
	public int getWidth()
	{
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.filter.CoordinateStore#getHeight()
	 */
	public int getHeight()
	{
		return 0;
	}
}