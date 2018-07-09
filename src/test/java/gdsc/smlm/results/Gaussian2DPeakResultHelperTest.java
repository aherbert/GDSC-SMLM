/*-
 * #%L
 * Genome Damage and Stability Centre SMLM ImageJ Plugins
 * 
 * Software for single molecule localisation microscopy (SMLM)
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package gdsc.smlm.results;

import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.junit.Assert;
import org.junit.Test;

import gdsc.core.utils.DoubleEquality;
import gdsc.core.utils.Maths;
import gdsc.core.utils.SimpleArrayUtils;
import gdsc.smlm.data.config.CalibrationWriter;
import gdsc.smlm.data.config.PSFHelper;
import gdsc.smlm.data.config.PSFProtos.PSF;
import gdsc.smlm.data.config.PSFProtos.PSFType;
import gdsc.smlm.data.config.UnitProtos.DistanceUnit;
import gdsc.smlm.data.config.UnitProtos.IntensityUnit;
import gdsc.smlm.function.gaussian.Gaussian2DFunction;
import gdsc.smlm.function.gaussian.GaussianFunctionFactory;
import gdsc.test.TestAssert;
import gdsc.test.TestSettings;
import gdsc.test.TestSettings.LogLevel;
import gdsc.test.TestSettings.TestComplexity;

@SuppressWarnings({ "javadoc" })
public class Gaussian2DPeakResultHelperTest
{
	double[] test_a = { 100, 130, 160 };
	double[] test_s = { 80, 100, 140 };
	double[] test_N = { 1, 10, 30, 100, 1000 };
	double[] test_b2 = { 0, 1, 2, 4, 8 };
	int minPoints = 3, maxPoints = 20;

	@Test
	public void canCalculateMaximumLikelihoodVariance()
	{
		int min = Gaussian2DPeakResultHelper.POINTS;
		int max = min;
		if (TestSettings.allow(TestComplexity.HIGH))
		{
			min = 3;
			max = 20;
		}
		for (double a : test_a)
			for (double s : test_s)
				for (double N : test_N)
					for (double b2 : test_b2)
						for (int points = min; points <= max; points++)
						{
							Gaussian2DPeakResultHelper.getMLVarianceX(a, s, N, b2, true, points);
						}
	}

	@Test
	public void lowerIntegrationPointsApproximateMaximumLikelihoodVariance()
	{
		TestSettings.assume(LogLevel.INFO, TestComplexity.HIGH);

		double[] sum = new double[maxPoints + 1];
		int count = 0;
		for (double a : test_a)
			for (double s : test_s)
				for (double N : test_N)
					for (double b2 : test_b2)
					{
						count++;
						double e = Gaussian2DPeakResultHelper.getMLVarianceX(a, s, N, b2, true, 30);
						for (int points = minPoints; points <= maxPoints; points++)
						{
							double o = Gaussian2DPeakResultHelper.getMLVarianceX(a, s, N, b2, true, points);
							double error = DoubleEquality.relativeError(e, o);
							sum[points] += error;
							if (error > 1e-2)
							{
								TestAssert.fail("a=%f, s=%f, N=%f, b2=%f, points=%d : %f != %f : %f\n", a, s, N, b2,
										points, e, o, error);
							}
						}
					}

		for (int points = minPoints; points <= maxPoints; points++)
		{
			System.out.printf("Points = %d, Av error = %f\n", points, sum[points] / count);
		}
	}

	@Test
	public void runSpeedTest()
	{
		TestSettings.assume(LogLevel.INFO, TestComplexity.HIGH);

		// Test with realistic parameters

		// Warm-up
		for (double a : new double[] { 108 })
			for (double s : new double[] { 120 })
				for (double N : new double[] { 50, 100, 300 })
					for (double b2 : new double[] { 0.5, 1, 2 })
						for (int points = 3; points <= 20; points++)
						{
							Gaussian2DPeakResultHelper.getMLVarianceX(a, s, N, b2, true, points);
						}

		// Get average performance
		double[] sum = new double[maxPoints + 1];
		double[] sum2 = new double[sum.length];
		long[] time = new long[sum.length];
		long count = 0, count2 = 0;

		for (double a : new double[] { 108 })
			for (double s : new double[] { 120 })
				for (double N : new double[] { 50, 100, 300 })
					for (double b2 : new double[] { 0.5, 1, 2 })
					{
						long min = Long.MAX_VALUE;
						for (int points = 3; points <= 20; points++)
						{
							long t = System.nanoTime();
							for (int i = 0; i < 1000; i++)
								Gaussian2DPeakResultHelper.getMLVarianceX(a, s, N, b2, true, points);
							t = time[points] = System.nanoTime() - t;
							if (min > t)
								min = t;
						}
						// Proportional weighting to the calculation that takes the longest
						count++;
						count2 += min;

						// Store relative performance
						double factor = 1.0 / min;
						for (int points = 3; points <= 20; points++)
						{
							sum[points] += time[points] * factor;
							sum2[points] += time[points];
						}
					}

		for (int points = minPoints; points <= maxPoints; points++)
		{
			System.out.printf("Points = %d, Av relative time = %f, Slow down factor = %f\n", points,
					sum[points] / count, sum2[points] / count2);
		}
	}

	@Test
	public void canComputePixelAmplitude()
	{
		float[] x = new float[] { 0f, 0.1f, 0.3f, 0.5f, 0.7f, 1f };
		float[] s = new float[] { 0.8f, 1f, 1.5f, 2.2f };

		float[] paramsf = new float[1 + Gaussian2DFunction.PARAMETERS_PER_PEAK];
		paramsf[Gaussian2DFunction.BACKGROUND] = 0;
		paramsf[Gaussian2DFunction.SIGNAL] = 105;

		Gaussian2DFunction f = GaussianFunctionFactory.create2D(1, 1, 1, GaussianFunctionFactory.FIT_ERF_FREE_CIRCLE,
				null);

		SimpleRegression r = new SimpleRegression(false);

		for (float tx : x)
			for (float ty : x)
				for (float sx : s)
					for (float sy : s)
					{
						paramsf[Gaussian2DFunction.X_POSITION] = tx;
						paramsf[Gaussian2DFunction.Y_POSITION] = ty;
						paramsf[Gaussian2DFunction.X_SD] = sx;
						paramsf[Gaussian2DFunction.Y_SD] = sy;

						// Get the answer using a single pixel image
						// Note the Gaussian2D functions set the centre of the pixel as 0,0 so offset
						double[] params = SimpleArrayUtils.toDouble(paramsf);
						params[Gaussian2DFunction.X_POSITION] -= 0.5;
						params[Gaussian2DFunction.Y_POSITION] -= 0.5;
						f.initialise0(params);
						double e = f.eval(0);

						PSF psf = PSFHelper.create(PSFType.TWO_AXIS_GAUSSIAN_2D);
						CalibrationWriter calibration = new CalibrationWriter();
						calibration.setCountPerPhoton(1);
						calibration.setIntensityUnit(IntensityUnit.PHOTON);
						calibration.setNmPerPixel(1);
						calibration.setDistanceUnit(DistanceUnit.PIXEL);
						Gaussian2DPeakResultCalculator calc = Gaussian2DPeakResultHelper.create(psf, calibration,
								Gaussian2DPeakResultHelper.AMPLITUDE | Gaussian2DPeakResultHelper.PIXEL_AMPLITUDE);
						double o1 = calc.getAmplitude(paramsf);
						double o2 = calc.getPixelAmplitude(paramsf);

						//System.out.printf("e=%f, o1=%f, o2=%f\n", e, o1, o2);
						Assert.assertEquals(e, o2, 1e-3);
						r.addData(e, o1);
					}

		//System.out.printf("Regression: pixel amplitude vs amplitude = %f, slope=%f, n=%d\n", r.getR(), r.getSlope(),
		//		r.getN());
		// The simple amplitude over estimates the actual pixel amplitude
		Assert.assertTrue(r.getSlope() > 1);
	}

	@Test
	public void canComputeCumulative()
	{
		Assert.assertEquals(0, Gaussian2DPeakResultHelper.cumulative(0), 0);
		Assert.assertEquals(0.6827, Gaussian2DPeakResultHelper.cumulative(1), 1e-3);
		Assert.assertEquals(0.9545, Gaussian2DPeakResultHelper.cumulative(2), 1e-3);
		Assert.assertEquals(0.9974, Gaussian2DPeakResultHelper.cumulative(3), 1e-3);
		Assert.assertTrue(1 == Gaussian2DPeakResultHelper.cumulative(Double.POSITIVE_INFINITY));
	}

	@Test
	public void canComputeCumulative2DAndInverse()
	{
		Assert.assertEquals(0, Gaussian2DPeakResultHelper.cumulative2D(0), 0);
		Assert.assertTrue(1 == Gaussian2DPeakResultHelper.cumulative2D(Double.POSITIVE_INFINITY));
		Assert.assertEquals(0, Gaussian2DPeakResultHelper.inverseCumulative2D(0), 0);
		Assert.assertTrue(Double.POSITIVE_INFINITY == Gaussian2DPeakResultHelper.inverseCumulative2D(1));
		for (int i = 1; i <= 10; i++)
		{
			double r = i / 10.0;
			double p = Gaussian2DPeakResultHelper.cumulative2D(r);
			double r2 = Gaussian2DPeakResultHelper.inverseCumulative2D(p);
			Assert.assertEquals(r, r2, r * 1e-8);
		}
	}

	@Test
	public void canComputeMeanSignalUsingR()
	{
		RandomGenerator rg = TestSettings.getRandomGenerator();

		for (int i = 0; i < 10; i++)
		{
			double intensity = rg.nextDouble() * 100;
			double sx = rg.nextDouble() * 2;
			double sy = rg.nextDouble() * 2;
			double r = rg.nextDouble() * 5;
			assertEquals(intensity * Gaussian2DPeakResultHelper.cumulative2D(r) / (Math.PI * r * r * sx * sy),
					Gaussian2DPeakResultHelper.getMeanSignalUsingR(intensity, sx, sy, r));

			// Test fixed versions verse dynamic
			assertEquals(Gaussian2DPeakResultHelper.getMeanSignalUsingR(intensity, sx, sy, 1),
					Gaussian2DPeakResultHelper.getMeanSignalUsingR1(intensity, sx, sy));
			assertEquals(Gaussian2DPeakResultHelper.getMeanSignalUsingR(intensity, sx, sy, 2),
					Gaussian2DPeakResultHelper.getMeanSignalUsingR2(intensity, sx, sy));
		}
	}

	private static void assertEquals(double e, double o)
	{
		TestAssert.assertEqualsRelative(e, o, 1e-10);
	}

	@Test
	public void canComputeMeanSignalUsingP()
	{
		RandomGenerator rg = TestSettings.getRandomGenerator();

		for (int i = 0; i < 10; i++)
		{
			double intensity = rg.nextDouble() * 100;
			double sx = rg.nextDouble() * 2;
			double sy = rg.nextDouble() * 2;
			double p = rg.nextDouble();
			double e = intensity * p /
					(Math.PI * Maths.pow2(Gaussian2DPeakResultHelper.inverseCumulative2D(p)) * sx * sy);
			double o = Gaussian2DPeakResultHelper.getMeanSignalUsingP(intensity, sx, sy, p);
			assertEquals(e, o);

			// Test fixed versions verse dynamic
			e = Gaussian2DPeakResultHelper.getMeanSignalUsingP(intensity, sx, sy, 0.5);
			o = Gaussian2DPeakResultHelper.getMeanSignalUsingP05(intensity, sx, sy);
			assertEquals(e, o);
		}
	}
}
