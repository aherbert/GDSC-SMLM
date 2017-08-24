package gdsc.smlm.filters;

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
 * Defines normalisation of an area around a point using a per-pixel normalisation
 */
public class PerPixelNormaliser implements Normaliser
{
	/** The normalisation. */
	public final float[] normalisation;

	/**
	 * Instantiates a new fixed normaliser.
	 *
	 * @param normalisation
	 *            the normalisation
	 */
	public PerPixelNormaliser(float[] normalisation)
	{
		this.normalisation = normalisation;
	}

	/**
	 * Normalise the sum.
	 *
	 * @param sum
	 *            the sum
	 * @param index
	 *            the index
	 * @return the normalised value
	 */
	public float normalise(double sum, int index)
	{
		return (float) (sum / normalisation[index]);
	}

	/**
	 * Normalise the sum.
	 *
	 * @param sum
	 *            the sum
	 * @param index
	 *            the index
	 * @return the normalised value
	 */
	public float normalise(float sum, int index)
	{
		return sum / normalisation[index];
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.filters.Normaliser#normalise(float[], int)
	 */
	public void normalise(float[] data, int size)
	{
		for (int i = 0; i < size; i++)
			data[i] /= normalisation[i];
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.filters.Normaliser#normalise(float[], float[], int)
	 */
	public void normalise(float[] data, float[] out, int size)
	{
		for (int i = 0; i < size; i++)
			out[i] = data[i] / normalisation[i];
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.filters.Normaliser#normalise(float[], int, int, int)
	 */
	public void normalise(float[] data, int maxx, int maxy, int border)
	{
		int xlimit = maxx - border;
		int ylimit = maxy - border;
		for (int y = border; y < ylimit; y++)
		{
			for (int x = border, i = y * maxx + border; x < xlimit; x++, i++)
			{
				data[i] /= normalisation[i];
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.filters.Normaliser#normalise(float[], float[], int, int, int)
	 */
	public void normalise(float[] data, float[] out, int maxx, int maxy, int border)
	{
		int xlimit = maxx - border;
		int ylimit = maxy - border;
		for (int y = border; y < ylimit; y++)
		{
			for (int x = border, i = y * maxx + border; x < xlimit; x++, i++)
			{
				out[i] = data[i] / normalisation[i];
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.filters.Normaliser#normalise(double[], float[], int)
	 */
	public void normalise(double[] data, float[] out, int size)
	{
		for (int i = 0; i < size; i++)
			out[i] = (float) (data[i] / normalisation[i]);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.filters.Normaliser#normalise(double[], float[], int, int, int)
	 */
	public void normalise(double[] data, float[] out, int maxx, int maxy, int border)
	{
		int xlimit = maxx - border;
		int ylimit = maxy - border;
		for (int y = border; y < ylimit; y++)
		{
			for (int x = border, i = y * maxx + border; x < xlimit; x++, i++)
			{
				out[i] = (float) (data[i] / normalisation[i]);
			}
		}
	}
}