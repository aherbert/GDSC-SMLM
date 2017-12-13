package gdsc.smlm.results;

import gdsc.core.utils.BooleanRollingArray;

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
 * Stop evaluating when a number of failures occurs within a window.
 */
public class RollingWindowFailCounter extends BaseFailCounter
{
	private final BooleanRollingArray rollingArray;

	/** The number of allowed failures. */
	private final int allowedFailures;

	/**
	 * Instantiates a new rolling window fail counter.
	 *
	 * @param allowedFailures
	 *            the number of allowed failures
	 * @param window
	 *            the window size
	 */
	private RollingWindowFailCounter(int allowedFailures, int window)
	{
		this.allowedFailures = allowedFailures;
		rollingArray = new BooleanRollingArray(window);
	}

	@Override
	protected String generateDescription()
	{
		return "rollingFailures=" + allowedFailures + "/" + getWindow();
	}

	/**
	 * Instantiates a new rolling window fail counter.
	 *
	 * @param allowedFailures
	 *            the number of allowed failures
	 * @param window
	 *            the window size
	 * @throws IllegalArgumentException
	 *             If the window is not strictly positive
	 */
	public static RollingWindowFailCounter create(int allowedFailures, int window) throws IllegalArgumentException
	{
		if (window < 1)
			throw new IllegalArgumentException("Window must be strictly positive");
		return new RollingWindowFailCounter(Math.max(0, allowedFailures), window);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.FailCounter#pass()
	 */
	public void pass()
	{
		rollingArray.add(false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.FailCounter#pass(int)
	 */
	public void pass(int n)
	{
		if (n < 0)
			throw new IllegalArgumentException("Number of passes must be positive");
		rollingArray.add(false, n);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.FailCounter#fail()
	 */
	public void fail()
	{
		rollingArray.add(true);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.FailCounter#fail(int)
	 */
	public void fail(int n)
	{
		if (n < 0)
			throw new IllegalArgumentException("Number of fails must be positive");
		rollingArray.add(true, n);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.FailCounter#isOK()
	 */
	public boolean isOK()
	{
		return (rollingArray.isFull()) ? getFailCount() <= allowedFailures : true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.FailCounter#newCounter()
	 */
	public FailCounter newCounter()
	{
		return new RollingWindowFailCounter(allowedFailures, getWindow());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.results.FailCounter#reset()
	 */
	public void reset()
	{
		rollingArray.clear();
	}

	/**
	 * Gets the window size.
	 *
	 * @return the window size
	 */
	public int getWindow()
	{
		return rollingArray.getCapacity();
	}

	/**
	 * Gets the fail count within the current window.
	 *
	 * @return the fail count
	 */
	public int getFailCount()
	{
		return rollingArray.getTrueCount();
	}

	/**
	 * Gets the current window size. This may be smaller than the window size if not enough pass/fail events have been
	 * registered.
	 *
	 * @return the current window size
	 */
	public int getCurrentWindowSize()
	{
		return rollingArray.getCount();
	}

	/**
	 * Gets the number of allowed failures.
	 *
	 * @return the number of allowed failures.
	 */
	public int getAllowedFailures()
	{
		return allowedFailures;
	}
}
