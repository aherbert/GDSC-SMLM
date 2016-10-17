package gdsc.smlm.engine;

import gdsc.smlm.filters.Spot;
import gdsc.smlm.results.filter.MultiPathFilter;

import java.util.List;

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
 * Specifies additional parameters for the job.
 */
public class FitParameters
{
	public enum FitTask
	{
		PSF_FITTING("PSF Fitting"), MAXIMA_IDENITIFICATION("Maxima Identification"), BENCHMARKING("Benchmarking");

		private String name;

		private FitTask(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	/**
	 * The noise for the image data
	 */
	public float noise = Float.NaN;
	/**
	 * The spots to fit within the data
	 */
	public Spot[] spots = null;
	/**
	 * The maxima to fit within the data
	 */
	public int[] maxIndices = null;
	/**
	 * The background for the image data
	 */
	public float background = Float.NaN;
	/**
	 * Only maxima within the distance threshold to these coordinates will be included in the results.
	 */
	public List<float[]> filter = null;
	/**
	 * The distance threshold to use when checking if fitted peaks match the desired results.
	 */
	public float distanceThreshold = 1;
	/**
	 * The task to perform
	 */
	public FitTask fitTask = FitTask.PSF_FITTING;
	/**
	 * The frame acquisition end time for the input data. Used when data represents multiple frames.
	 */
	public int endT = -1;

	/**
	 * The filter used to pick the fitting path when benchmarking.
	 * <p>
	 * Note that during benchmarking all fitting paths will be computed. The current set of results is then built by
	 * validating the results with this filter (in addition to the fit configuration used to construct the
	 * FitWorker).
	 */
	public MultiPathFilter benchmarkFilter = null;

	/**
	 * The distance to an existing result to be declared a duplicate
	 * 
	 * @return The duplicate distance
	 */
	public double duplicateDistance = 0;
}
