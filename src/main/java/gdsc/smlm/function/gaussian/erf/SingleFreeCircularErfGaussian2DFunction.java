package gdsc.smlm.function.gaussian.erf;

//import org.apache.commons.math3.special.Erf;
import org.apache.commons.math3.util.FastMath;

import gdsc.smlm.function.Erf;
import gdsc.smlm.function.ExtendedGradient2Procedure;
import gdsc.smlm.function.Gradient1Procedure;
import gdsc.smlm.function.Gradient2Procedure;
import gdsc.smlm.function.gaussian.Gaussian2DFunction;

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
 * Evaluates a 2-dimensional Gaussian function for a single peak.
 */
public class SingleFreeCircularErfGaussian2DFunction extends SingleErfGaussian2DFunction
{
	static final int[] gradientIndices;
	static
	{
		gradientIndices = createGradientIndices(1, new SingleFreeCircularErfGaussian2DFunction(1, 1));
	}

	/**
	 * Constructor.
	 *
	 * @param maxx
	 *            The maximum x value of the 2-dimensional data (used to unpack a linear index into coordinates)
	 * @param maxy
	 *            The maximum y value of the 2-dimensional data (used to unpack a linear index into coordinates)
	 */
	public SingleFreeCircularErfGaussian2DFunction(int maxx, int maxy)
	{
		super(maxx, maxy);
	}

	@Override
	public ErfGaussian2DFunction copy()
	{
		return new SingleFreeCircularErfGaussian2DFunction(maxx, maxy);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.function.gaussian.Gaussian2DFunction#initialise0(double[])
	 */
	public void initialise0(double[] a)
	{
		tB = a[Gaussian2DFunction.BACKGROUND];
		tI = a[Gaussian2DFunction.SIGNAL];
		// Pre-compute the offset by 0.5
		final double tx = a[Gaussian2DFunction.X_POSITION] + 0.5;
		final double ty = a[Gaussian2DFunction.Y_POSITION] + 0.5;
		final double tsx = a[Gaussian2DFunction.X_SD];
		final double tsy = a[Gaussian2DFunction.Y_SD];

		createDeltaETable(ONE_OVER_ROOT2 / tsx, deltaEx, tx);
		createDeltaETable(ONE_OVER_ROOT2 / tsy, deltaEy, ty);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.function.gaussian.Gaussian2DFunction#initialise1(double[])
	 */
	public void initialise1(double[] a)
	{
		create1Arrays();
		tB = a[Gaussian2DFunction.BACKGROUND];
		tI = a[Gaussian2DFunction.SIGNAL];
		// Pre-compute the offset by 0.5
		final double tx = a[Gaussian2DFunction.X_POSITION] + 0.5;
		final double ty = a[Gaussian2DFunction.Y_POSITION] + 0.5;
		final double tsx = a[Gaussian2DFunction.X_SD];
		final double tsy = a[Gaussian2DFunction.Y_SD];

		// We can pre-compute part of the derivatives for position and sd in arrays 
		// since the Gaussian is XY separable
		createFirstOrderTables(tI, deltaEx, du_dtx, du_dtsx, tx, tsx);
		createFirstOrderTables(tI, deltaEy, du_dty, du_dtsy, ty, tsy);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.function.Gradient2Function#initialise2(double[])
	 */
	public void initialise2(double[] a)
	{
		create2Arrays();
		tB = a[Gaussian2DFunction.BACKGROUND];
		tI = a[Gaussian2DFunction.SIGNAL];
		// Pre-compute the offset by 0.5
		final double tx = a[Gaussian2DFunction.X_POSITION] + 0.5;
		final double ty = a[Gaussian2DFunction.Y_POSITION] + 0.5;
		final double tsx = a[Gaussian2DFunction.X_SD];
		final double tsy = a[Gaussian2DFunction.Y_SD];

		// We can pre-compute part of the derivatives for position and sd in arrays 
		// since the Gaussian is XY separable
		createSecondOrderTables(tI, deltaEx, du_dtx, du_dtsx, d2u_dtx2, d2u_dtsx2, tx, tsx);
		createSecondOrderTables(tI, deltaEy, du_dty, du_dtsy, d2u_dty2, d2u_dtsy2, ty, tsy);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.function.ExtendedGradient2Function#initialiseExtended2(double[])
	 */
	public void initialiseExtended2(double[] a)
	{
		createEx2Arrays();
		tB = a[Gaussian2DFunction.BACKGROUND];
		tI = a[Gaussian2DFunction.SIGNAL];
		// Pre-compute the offset by 0.5
		final double tx = a[Gaussian2DFunction.X_POSITION] + 0.5;
		final double ty = a[Gaussian2DFunction.Y_POSITION] + 0.5;
		final double tsx = a[Gaussian2DFunction.X_SD];
		final double tsy = a[Gaussian2DFunction.Y_SD];

		// We can pre-compute part of the derivatives for position and sd in arrays 
		// since the Gaussian is XY separable
		createExSecondOrderTables(tI, deltaEx, du_dtx, du_dtsx, d2u_dtx2, d2u_dtsx2, d2deltaEx_dtsxdx, tx, tsx);
		createExSecondOrderTables(tI, deltaEy, du_dty, du_dtsy, d2u_dty2, d2u_dtsy2, d2deltaEy_dtsydy, ty, tsy);
	}

	/**
	 * Creates the delta E array. This is the sum of the Gaussian function using the error function for each of the
	 * pixels from 0 to n.
	 *
	 * @param one_sSqrt2
	 *            one over (s times sqrt(2))
	 * @param deltaE
	 *            the delta E for dimension 0 (difference between the error function at the start and end of each pixel)
	 * @param u
	 *            the mean of the Gaussian for dimension 0
	 */
	protected static void createDeltaETable(double one_sSqrt2, double[] deltaE, double u)
	{
		// For documentation see SingleFreeCircularErfGaussian2DFunction.createSecondOrderTables(...)

		double x_u_p12 = -u;
		double erf_x_minus = 0.5 * Erf.erf(x_u_p12 * one_sSqrt2);
		for (int i = 0, n = deltaE.length; i < n; i++)
		{
			x_u_p12 += 1.0;
			final double erf_x_plus = 0.5 * Erf.erf(x_u_p12 * one_sSqrt2);
			deltaE[i] = erf_x_plus - erf_x_minus;
			erf_x_minus = erf_x_plus;
		}
	}

	/**
	 * Creates the first order derivatives.
	 *
	 * @param tI
	 *            the target intensity
	 * @param deltaE
	 *            the delta E for dimension 0 (difference between the error function at the start and end of each pixel)
	 * @param du_dx
	 *            the first order x derivative for dimension 0
	 * @param du_ds
	 *            the first order s derivative for dimension 0
	 * @param u
	 *            the mean of the Gaussian for dimension 0
	 * @param s
	 *            the standard deviation of the Gaussian for dimension 0
	 */
	protected static void createFirstOrderTables(double tI, double[] deltaE, double[] du_dx, double[] du_ds, double u,
			double s)
	{
		createFirstOrderTables(ONE_OVER_ROOT2 / s, 0.5 / (s * s), tI * ONE_OVER_ROOT2PI / s,
				tI * ONE_OVER_ROOT2PI / (s * s), deltaE, du_dx, du_ds, u);
	}

	/**
	 * Creates the first order derivatives.
	 *
	 * @param one_sSqrt2
	 *            one over (s times sqrt(2))
	 * @param one_2ss
	 *            one over (2 * s^2)
	 * @param I_sSqrt2pi
	 *            the intensity over (s * sqrt(2*pi))
	 * @param I_ssSqrt2pi
	 *            the intensity over (s^2 * sqrt(2*pi))
	 * @param deltaE
	 *            the delta E for dimension 0 (difference between the error function at the start and end of each pixel)
	 * @param du_dx
	 *            the first order x derivative for dimension 0
	 * @param du_ds
	 *            the first order s derivative for dimension 0
	 * @param u
	 *            the mean of the Gaussian for dimension 0
	 */
	protected static void createFirstOrderTables(double one_sSqrt2, double one_2ss, double I_sSqrt2pi,
			double I_ssSqrt2pi, double[] deltaE, double[] du_dx, double[] du_ds, double u)
	{
		// For documentation see SingleFreeCircularErfGaussian2DFunction.createSecondOrderTables(...)

		double x_u_p12 = -u;
		double erf_x_minus = 0.5 * Erf.erf(x_u_p12 * one_sSqrt2);
		double exp_x_minus = FastMath.exp(-(x_u_p12 * x_u_p12 * one_2ss));
		for (int i = 0, n = deltaE.length; i < n; i++)
		{
			double x_u_m12 = x_u_p12;
			x_u_p12 += 1.0;
			final double erf_x_plus = 0.5 * Erf.erf(x_u_p12 * one_sSqrt2);
			deltaE[i] = erf_x_plus - erf_x_minus;
			erf_x_minus = erf_x_plus;

			final double exp_x_plus = FastMath.exp(-(x_u_p12 * x_u_p12 * one_2ss));
			du_dx[i] = I_sSqrt2pi * (exp_x_minus - exp_x_plus);
			// Compute: I0 * G21(xk)
			du_ds[i] = I_ssSqrt2pi * (x_u_m12 * exp_x_minus - x_u_p12 * exp_x_plus);

			exp_x_minus = exp_x_plus;
		}
	}

	/**
	 * Creates the first and second order derivatives.
	 *
	 * @param tI
	 *            the target intensity
	 * @param deltaE
	 *            the delta E for dimension 0 (difference between the error function at the start and end of each pixel)
	 * @param du_dx
	 *            the first order x derivative for dimension 0
	 * @param du_ds
	 *            the first order s derivative for dimension 0
	 * @param d2u_dx2
	 *            the second order x derivative for dimension 0
	 * @param d2u_ds2
	 *            the second order s derivative for dimension 0
	 * @param u
	 *            the mean of the Gaussian for dimension 0
	 * @param s
	 *            the standard deviation of the Gaussian for dimension 0
	 */
	protected static void createSecondOrderTables(double tI, double[] deltaE, double[] du_dx, double[] du_ds,
			double[] d2u_dx2, double[] d2u_ds2, double u, double s)
	{
		final double ss = s * s;
		final double one_sSqrt2pi = ONE_OVER_ROOT2PI / s;
		final double one_ssSqrt2pi = ONE_OVER_ROOT2PI / ss;
		final double one_sssSqrt2pi = one_sSqrt2pi / ss;
		final double one_sssssSqrt2pi = one_sssSqrt2pi / ss;
		createSecondOrderTables(tI, ONE_OVER_ROOT2 / s, 0.5 / ss, tI * one_sSqrt2pi, tI * one_ssSqrt2pi,
				tI * one_sssSqrt2pi, ss, one_sssSqrt2pi, one_sssssSqrt2pi, deltaE, du_dx, du_ds, d2u_dx2, d2u_ds2, u);
	}

	/**
	 * Creates the first and second order derivatives.
	 *
	 * @param tI
	 *            the target intensity
	 * @param one_sSqrt2
	 *            one over (s times sqrt(2))
	 * @param one_2ss
	 *            one over (2 * s^2)
	 * @param I_sSqrt2pi
	 *            the intensity over (s * sqrt(2*pi))
	 * @param I_ssSqrt2pi
	 *            the intensity over (s^2 * sqrt(2*pi))
	 * @param I_sssSqrt2pi
	 *            the intensity over (s^3 * sqrt(2*pi))
	 * @param ss
	 *            the standard deviation squared
	 * @param one_sssSqrt2pi
	 *            one over (s^3 * sqrt(2*pi))
	 * @param one_sssssSqrt2pi
	 *            one over (s^5 * sqrt(2*pi))
	 * @param deltaE
	 *            the delta E for dimension 0 (difference between the error function at the start and end of each pixel)
	 * @param du_dx
	 *            the first order x derivative for dimension 0
	 * @param du_ds
	 *            the first order s derivative for dimension 0
	 * @param d2u_dx2
	 *            the second order x derivative for dimension 0
	 * @param d2u_ds2
	 *            the second order s derivative for dimension 0
	 * @param u
	 *            the mean of the Gaussian for dimension 0
	 */
	protected static void createSecondOrderTables(double tI, double one_sSqrt2, double one_2ss, double I_sSqrt2pi,
			double I_ssSqrt2pi, double I_sssSqrt2pi, double ss, double one_sssSqrt2pi, double one_sssssSqrt2pi,
			double[] deltaE, double[] du_dx, double[] du_ds, double[] d2u_dx2, double[] d2u_ds2, double u)
	{
		// Note: The paper by Smith, et al computes the integral for the kth pixel centred at (x,y)
		// If x=u then the Erf will be evaluated at x-u+0.5 - x-u-0.5 => integral from -0.5 to 0.5.
		// This code sets the first pixel at (0,0).

		// All computations for pixel k (=(x,y)) that require the exponential can use x,y indices for the 
		// lower boundary value and x+1,y+1 indices for the upper value.

		// Working example of this in GraspJ source code:
		// https://github.com/isman7/graspj/blob/master/graspj/src/main/java/eu/brede/graspj/opencl/src/functions/
		// I have used the same notation for clarity

		// The first position:
		// Offset x by the position and get the pixel lower bound.
		// (x - u - 0.5) with x=0 and u offset by +0.5
		double x_u_p12 = -u;
		double erf_x_minus = 0.5 * Erf.erf(x_u_p12 * one_sSqrt2);
		double exp_x_minus = FastMath.exp(-(x_u_p12 * x_u_p12 * one_2ss));
		for (int i = 0, n = deltaE.length; i < n; i++)
		{
			double x_u_m12 = x_u_p12;
			x_u_p12 += 1.0;
			final double erf_x_plus = 0.5 * Erf.erf(x_u_p12 * one_sSqrt2);
			deltaE[i] = erf_x_plus - erf_x_minus;
			erf_x_minus = erf_x_plus;

			final double exp_x_plus = FastMath.exp(-(x_u_p12 * x_u_p12 * one_2ss));
			du_dx[i] = I_sSqrt2pi * (exp_x_minus - exp_x_plus);
			// Compute: I0 * G21(xk)
			final double pre2 = (x_u_m12 * exp_x_minus - x_u_p12 * exp_x_plus);
			du_ds[i] = I_ssSqrt2pi * pre2;

			// Second derivatives
			d2u_dx2[i] = I_sssSqrt2pi * pre2;

			// Compute G31(xk)
			final double G31 = one_sssSqrt2pi * pre2;

			// Compute G53(xk)
			x_u_m12 = x_u_m12 * x_u_m12 * x_u_m12;
			final double ux = x_u_p12 * x_u_p12 * x_u_p12;
			final double G53 = one_sssssSqrt2pi * (x_u_m12 * exp_x_minus - ux * exp_x_plus);
			d2u_ds2[i] = tI * (G53 - 2 * G31);

			exp_x_minus = exp_x_plus;
		}
	}

	/**
	 * Creates the first and extended second order derivatives.
	 *
	 * @param tI
	 *            the target intensity
	 * @param deltaE
	 *            the delta E for dimension 0 (difference between the error function at the start and end of each pixel)
	 * @param du_dx
	 *            the first order x derivative for dimension 0
	 * @param du_ds
	 *            the first order s derivative for dimension 0
	 * @param d2u_dx2
	 *            the second order x derivative for dimension 0
	 * @param d2u_ds2
	 *            the second order s derivative for dimension 0
	 * @param d2deltaE_dsdx
	 *            the second order deltaE s,x derivative for dimension 0
	 * @param u
	 *            the mean of the Gaussian for dimension 0
	 * @param s
	 *            the standard deviation of the Gaussian for dimension 0
	 */
	private void createExSecondOrderTables(double tI, double[] deltaE, double[] du_dx, double[] du_ds, double[] d2u_dx2,
			double[] d2u_ds2, double[] d2deltaE_dsdx, double u, double s)
	{
		final double ss = s * s;
		final double one_sSqrt2pi = ONE_OVER_ROOT2PI / s;
		final double one_ssSqrt2pi = ONE_OVER_ROOT2PI / ss;
		final double one_sssSqrt2pi = one_sSqrt2pi / ss;
		final double one_ssssSqrt2pi = one_ssSqrt2pi / ss;
		final double one_sssssSqrt2pi = one_sssSqrt2pi / ss;
		createExSecondOrderTables(tI, ONE_OVER_ROOT2 / s, 0.5 / ss, tI * one_sSqrt2pi, tI * one_ssSqrt2pi,
				tI * one_sssSqrt2pi, tI * one_ssssSqrt2pi, ss, one_sssSqrt2pi, one_sssssSqrt2pi, deltaE, du_dx, du_ds,
				d2u_dx2, d2u_ds2, d2deltaE_dsdx, u);
	}

	/**
	 * Creates the first and second order derivatives.
	 *
	 * @param tI
	 *            the target intensity
	 * @param one_sSqrt2
	 *            one over (s times sqrt(2))
	 * @param one_2ss
	 *            one over (2 * s^2)
	 * @param I_sSqrt2pi
	 *            the intensity over (s * sqrt(2*pi))
	 * @param I_ssSqrt2pi
	 *            the intensity over (s^2 * sqrt(2*pi))
	 * @param I_sssSqrt2pi
	 *            the intensity over (s^3 * sqrt(2*pi))
	 * @param I_ssssSqrt2pi
	 *            the intensity over (s^4 * sqrt(2*pi))
	 * @param ss
	 *            the standard deviation squared
	 * @param one_sssSqrt2pi
	 *            one over (s^3 * sqrt(2*pi))
	 * @param one_sssssSqrt2pi
	 *            one over (s^5 * sqrt(2*pi))
	 * @param deltaE
	 *            the delta E for dimension 0 (difference between the error function at the start and end of each pixel)
	 * @param du_dx
	 *            the first order x derivative for dimension 0
	 * @param du_ds
	 *            the first order s derivative for dimension 0
	 * @param d2u_dx2
	 *            the second order x derivative for dimension 0
	 * @param d2u_ds2
	 *            the second order s derivative for dimension 0
	 * @param d2deltaE_dsdx
	 *            the second order deltaE s,x derivative for dimension 0
	 * @param u
	 *            the mean of the Gaussian for dimension 0
	 */
	protected static void createExSecondOrderTables(double tI, double one_sSqrt2, double one_2ss, double I_sSqrt2pi,
			double I_ssSqrt2pi, double I_sssSqrt2pi, double I_ssssSqrt2pi, double ss, double one_sssSqrt2pi,
			double one_sssssSqrt2pi, double[] deltaE, double[] du_dx, double[] du_ds, double[] d2u_dx2,
			double[] d2u_ds2, double[] d2deltaE_dsdx, double u)
	{
		// Note: The paper by Smith, et al computes the integral for the kth pixel centred at (x,y)
		// If x=u then the Erf will be evaluated at x-u+0.5 - x-u-0.5 => integral from -0.5 to 0.5.
		// This code sets the first pixel at (0,0).

		// All computations for pixel k (=(x,y)) that require the exponential can use x,y indices for the 
		// lower boundary value and x+1,y+1 indices for the upper value.

		// Working example of this in GraspJ source code:
		// https://github.com/isman7/graspj/blob/master/graspj/src/main/java/eu/brede/graspj/opencl/src/functions/
		// I have used the same notation for clarity

		// The first position:
		// Offset x by the position and get the pixel lower bound.
		// (x - u - 0.5) with x=0 and u offset by +0.5
		double x_u_p12 = -u;
		double erf_x_minus = 0.5 * Erf.erf(x_u_p12 * one_sSqrt2);
		double exp_x_minus = FastMath.exp(-(x_u_p12 * x_u_p12 * one_2ss));
		for (int i = 0, n = deltaE.length; i < n; i++)
		{
			double x_u_m12 = x_u_p12;
			x_u_p12 += 1.0;
			final double erf_x_plus = 0.5 * Erf.erf(x_u_p12 * one_sSqrt2);
			deltaE[i] = erf_x_plus - erf_x_minus;
			erf_x_minus = erf_x_plus;

			final double exp_x_plus = FastMath.exp(-(x_u_p12 * x_u_p12 * one_2ss));
			du_dx[i] = I_sSqrt2pi * (exp_x_minus - exp_x_plus);
			// Compute: I0 * G21(xk)
			final double pre2 = (x_u_m12 * exp_x_minus - x_u_p12 * exp_x_plus);
			du_ds[i] = I_ssSqrt2pi * pre2;

			// Second derivatives
			d2u_dx2[i] = I_sssSqrt2pi * pre2;

			// Compute G31(xk)
			final double G31 = one_sssSqrt2pi * pre2;

			// XXX Fix this
			d2deltaE_dsdx[i] = I_ssssSqrt2pi * (x_u_p12 * x_u_p12  * exp_x_plus - x_u_m12 * x_u_m12 * exp_x_minus);

			// Compute G53(xk)
			x_u_m12 = x_u_m12 * x_u_m12 * x_u_m12;
			final double ux = x_u_p12 * x_u_p12 * x_u_p12;
			final double G53 = one_sssssSqrt2pi * (x_u_m12 * exp_x_minus - ux * exp_x_plus);
			d2u_ds2[i] = tI * (G53 - 2 * G31);
			
			exp_x_minus = exp_x_plus;
		}
	}

	/**
	 * Evaluates an 2-dimensional Gaussian function for a single peak.
	 * 
	 * @param i
	 *            Input predictor
	 * @param duda
	 *            Partial gradient of function with respect to each coefficient
	 * @return The predicted value
	 * 
	 * @see gdsc.smlm.function.NonLinearFunction#eval(int, double[])
	 */
	public double eval(final int i, final double[] duda)
	{
		// Unpack the predictor into the dimensions
		final int y = i / maxx;
		final int x = i % maxx;

		// Return in order of Gaussian2DFunction.createGradientIndices().
		// Use pre-computed gradients
		duda[0] = 1.0;
		duda[1] = deltaEx[x] * deltaEy[y];
		duda[2] = du_dtx[x] * deltaEy[y];
		duda[3] = du_dty[y] * deltaEx[x];
		duda[4] = du_dtsx[x] * deltaEy[y];
		duda[5] = du_dtsy[y] * deltaEx[x];

		return tB + tI * duda[1];
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.function.gaussian.erf.ErfGaussian2DFunction#eval(int, double[], double[])
	 */
	public double eval(final int i, final double[] duda, final double[] d2uda2)
	{
		// Unpack the predictor into the dimensions
		final int y = i / maxx;
		final int x = i % maxx;

		// Return in order of Gaussian2DFunction.createGradientIndices().
		// Use pre-computed gradients
		duda[0] = 1.0;
		duda[1] = deltaEx[x] * deltaEy[y];
		duda[2] = du_dtx[x] * deltaEy[y];
		duda[3] = du_dty[y] * deltaEx[x];
		duda[4] = du_dtsx[x] * deltaEy[y];
		duda[5] = du_dtsy[y] * deltaEx[x];
		d2uda2[0] = 0;
		d2uda2[1] = 0;
		d2uda2[2] = d2u_dtx2[x] * deltaEy[y];
		d2uda2[3] = d2u_dty2[y] * deltaEx[x];
		d2uda2[4] = d2u_dtsx2[x] * deltaEy[y];
		d2uda2[5] = d2u_dtsy2[y] * deltaEx[x];

		return tB + tI * duda[1];
	}

	@Override
	public boolean evaluatesBackground()
	{
		return true;
	}

	@Override
	public boolean evaluatesSignal()
	{
		return true;
	}

	@Override
	public boolean evaluatesShape()
	{
		return false;
	}

	@Override
	public boolean evaluatesPosition()
	{
		return true;
	}

	@Override
	public boolean evaluatesSD0()
	{
		return true;
	}

	@Override
	public boolean evaluatesSD1()
	{
		return true;
	}

	@Override
	public int getParametersPerPeak()
	{
		return 5;
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.function.GradientFunction#getNumberOfGradients()
	 */
	public int getNumberOfGradients()
	{
		return 6;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.function.GradientFunction#forEach(gdsc.smlm.function.GradientFunction.Gradient1Procedure)
	 */
	public void forEach(Gradient1Procedure procedure)
	{
		final double[] duda = new double[getNumberOfGradients()];
		duda[0] = 1.0;
		for (int y = 0; y < maxy; y++)
		{
			final double du_dty = this.du_dty[y];
			final double deltaEy = this.deltaEy[y];
			final double du_dtsy = this.du_dtsy[y];
			for (int x = 0; x < maxx; x++)
			{
				duda[1] = deltaEx[x] * deltaEy;
				duda[2] = du_dtx[x] * deltaEy;
				duda[3] = du_dty * deltaEx[x];
				duda[4] = du_dtsx[x] * deltaEy;
				duda[5] = du_dtsy * deltaEx[x];
				procedure.execute(tB + tI * duda[1], duda);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.function.Gradient2Function#forEach(gdsc.smlm.function.Gradient2Procedure)
	 */
	public void forEach(Gradient2Procedure procedure)
	{
		final double[] duda = new double[getNumberOfGradients()];
		final double[] d2uda2 = new double[getNumberOfGradients()];
		duda[0] = 1.0;
		for (int y = 0; y < maxy; y++)
		{
			final double du_dty = this.du_dty[y];
			final double deltaEy = this.deltaEy[y];
			final double du_dtsy = this.du_dtsy[y];
			final double d2u_dty2 = this.d2u_dty2[y];
			final double d2u_dtsy2 = this.d2u_dtsy2[y];
			for (int x = 0; x < maxx; x++)
			{
				duda[1] = deltaEx[x] * deltaEy;
				duda[2] = du_dtx[x] * deltaEy;
				duda[3] = du_dty * deltaEx[x];
				duda[4] = du_dtsx[x] * deltaEy;
				duda[5] = du_dtsy * deltaEx[x];
				d2uda2[2] = d2u_dtx2[x] * deltaEy;
				d2uda2[3] = d2u_dty2 * deltaEx[x];
				d2uda2[4] = d2u_dtsx2[x] * deltaEy;
				d2uda2[5] = d2u_dtsy2 * deltaEx[x];
				procedure.execute(tB + tI * duda[1], duda, d2uda2);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.function.ExtendedGradient2Function#forEach(gdsc.smlm.function.ExtendedGradient2Procedure)
	 */
	public void forEach(ExtendedGradient2Procedure procedure)
	{
		final int n = getNumberOfGradients();
		final double[] duda = new double[n];
		final double[] d2udadb = new double[n * n];
		duda[0] = 1.0;
		for (int y = 0; y < maxy; y++)
		{
			final double du_dty = this.du_dty[y];
			final double deltaEy = this.deltaEy[y];
			final double du_dtsy = this.du_dtsy[y];
			final double d2u_dty2 = this.d2u_dty2[y];
			final double d2u_dtsy2 = this.d2u_dtsy2[y];
			final double d2deltaEy_dtsydy = this.d2deltaEy_dtsydy[y];
			for (int x = 0; x < maxx; x++)
			{
				duda[1] = deltaEx[x] * deltaEy;
				duda[2] = du_dtx[x] * deltaEy;
				duda[3] = du_dty * deltaEx[x];
				duda[4] = du_dtsx[x] * deltaEy;
				duda[5] = du_dtsy * deltaEx[x];

				// TODO:
				// Compute all the partial second order derivatives

				// Background are all 0

				int k = n;
				// Signal,X
				d2udadb[k + 2] = duda[2] / tI;
				// Signal,Y
				d2udadb[k + 3] = duda[3] / tI;
				// Signal,X SD
				d2udadb[k + 4] = duda[4] / tI;
				// Signal,Y SD
				d2udadb[k + 5] = duda[5] / tI;

				k += n;
				// X,Signal
				d2udadb[k + 1] = d2udadb[k - n + 2];
				// X,X
				d2udadb[k + 2] = d2u_dtx2[x] * deltaEy;
				// X,Y
				d2udadb[k + 3] = du_dtx[x] * du_dty / tI;
				// X,X SD
				d2udadb[k + 4] = deltaEy * d2deltaEx_dtsxdx[x];
				// X,Y SD
				d2udadb[k + 5] = du_dtx[x] * du_dtsy / tI;

				k += n;
				// Y,Signal
				d2udadb[k + 1] = d2udadb[k - 2 * n + 3];
				// Y,X
				d2udadb[k + 2] = d2udadb[k - n + 3];
				// Y,Y
				d2udadb[k + 3] = d2u_dty2 * deltaEx[x];
				// Y,X SD
				d2udadb[k + 4] = du_dty * du_dtsx[x] / tI;
				// Y,Y SD
				d2udadb[k + 5] = deltaEx[x] * d2deltaEy_dtsydy;

				k += n;
				// X SD,Signal
				d2udadb[k + 1] = d2udadb[k - 3 * n + 4];
				// X SD,X
				d2udadb[k + 2] = d2udadb[k - 2 * n + 4];
				// X SD,Y
				d2udadb[k + 3] = d2udadb[k - n + 4];
				// X SD,X SD
				d2udadb[k + 4] = d2u_dtsx2[x] * deltaEy;
				// X SD,Y SD
				d2udadb[k + 5] = du_dtsy * du_dtsx[x] / tI;

				k += n;
				// Y SD,Signal
				d2udadb[k + 1] = d2udadb[k - 4 * n + 5];
				// Y SD,X
				d2udadb[k + 2] = d2udadb[k - 3 * n + 5];
				// Y SD,Y
				d2udadb[k + 3] = d2udadb[k - 2 * n + 5];
				// Y SD,X SD
				d2udadb[k + 4] = d2udadb[k - 1 * n + 5];
				// Y SD,Y SD
				d2udadb[k + 5] = d2u_dtsy2 * deltaEx[x];

				procedure.executeExtended(tB + tI * duda[1], duda, d2udadb);
			}
		}
	}
}
