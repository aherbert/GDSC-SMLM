package gdsc.smlm.function.gaussian.erf;

import gdsc.smlm.function.ValueProcedure;

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
 * Abstract base class for an 2-dimensional Gaussian function for a configured number of peaks.
 * <p>
 * The function will calculate the value of the Gaussian and evaluate the gradient of a set of parameters. The class can
 * specify which of the following parameters the function will evaluate:<br/>
 * background, signal, z-depth, position0, position1, sd0, sd1
 * <p>
 * The class provides an index of the position in the parameter array where the parameter is expected.
 */
public abstract class SingleErfGaussian2DFunction extends ErfGaussian2DFunction
{
	// Required for the PSF
	protected double tI;

	/**
	 * Instantiates a new erf gaussian 2D function.
	 *
	 * @param maxx
	 *            The maximum x value of the 2-dimensional data (used to unpack a linear index into coordinates)
	 * @param maxy
	 *            The maximum y value of the 2-dimensional data (used to unpack a linear index into coordinates)
	 */
	public SingleErfGaussian2DFunction(int maxx, int maxy)
	{
		super(1, maxx, maxy);
	}

	@Override
	public int getNPeaks()
	{
		return 1;
	}

	/**
	 * Evaluates an 2-dimensional Gaussian function for a single peak.
	 * 
	 * @param i
	 *            Input predictor
	 * @return The Gaussian value
	 * 
	 * @see gdsc.fitting.function.NonLinearFunction#eval(int)
	 */
	public double eval(final int i)
	{
		// Unpack the predictor into the dimensions
		final int y = i / maxx;
		final int x = i % maxx;

		return tB + tI * deltaEx[x] * deltaEy[y];
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.function.GradientFunction#forEach(gdsc.smlm.function.GradientFunction.ValueProcedure)
	 */
	public void forEach(ValueProcedure procedure)
	{
		for (int y = 0; y < maxy; y++)
		{
			final double tI_deltaEy = tI * deltaEy[y];
			for (int x = 0; x < maxx; x++)
			{
				procedure.execute(tB + tI_deltaEy * deltaEx[x]);
			}
		}
	}

	// Force implementation
	@Override
	public abstract int getNumberOfGradients();
}