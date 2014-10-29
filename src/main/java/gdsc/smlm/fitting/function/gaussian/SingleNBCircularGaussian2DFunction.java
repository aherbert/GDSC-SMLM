package gdsc.smlm.fitting.function.gaussian;

import org.apache.commons.math3.util.FastMath;

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
 * Evaluates an 2-dimensional Gaussian function for a single peak.
 * <p>
 * The single parameter x in the {@link #eval(int, float[])} function is assumed to be a linear index into 2-dimensional
 * data. The dimensions of the data must be specified to allow unpacking to coordinates.
 * <p>
 * Data should be packed in descending dimension order, e.g. Y,X : Index for [x,y] = MaxX*y + x.
 */
public class SingleNBCircularGaussian2DFunction extends SingleCircularGaussian2DFunction
{
	private static int[] gradientIndices;
	static
	{
		gradientIndices = createGradientIndices(1, new SingleNBCircularGaussian2DFunction(1));
	}

	/**
	 * Constructor
	 * 
	 * @param maxx
	 *            The maximum x value of the 2-dimensional data (used to unpack a linear index into coordinates)
	 */
	public SingleNBCircularGaussian2DFunction(int maxx)
	{
		super(maxx);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.fitting.function.gaussian.SingleCircularGaussian2DFunction#eval(int, float[])
	 */
	public float eval(final int x, final float[] dyda)
	{
		// Unpack the predictor into the dimensions
		final int x1 = x / maxx;
		final int x0 = x % maxx;

		return background + gaussian(x0, x1, dyda);
	}

	private float gaussian(final int x0, final int x1, final float[] dy_da)
	{
		final float h = amplitude;

		final float dx = x0 - x0pos;
		final float dy = x1 - x1pos;
		final float dx2dy2 = dx * dx + dy * dy;

		//final float y = (float) (h * FastMath.exp(aa * (dx2dy2)));

		// Calculate gradients
		//dy_da[0] = y / h;
		
		dy_da[0] = (float) (FastMath.exp(aa * (dx2dy2)));
		final float y = h * dy_da[0];
		final float yaa2 = y * aa2;
		dy_da[1] = yaa2 * dx;
		dy_da[2] = yaa2 * dy;

		dy_da[3] = y * (ax * (dx2dy2));

		return y;
	}

	@Override
	public boolean evaluatesBackground()
	{
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.fitting.function.NonLinearFunction#gradientIndices()
	 */
	public int[] gradientIndices()
	{
		return gradientIndices;
	}
}
