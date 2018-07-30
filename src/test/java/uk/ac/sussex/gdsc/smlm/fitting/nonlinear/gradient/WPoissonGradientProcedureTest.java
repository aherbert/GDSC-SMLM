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
package uk.ac.sussex.gdsc.smlm.fitting.nonlinear.gradient;

import java.util.ArrayList;

import org.apache.commons.rng.UniformRandomProvider;
import org.junit.jupiter.api.Assertions;

import uk.ac.sussex.gdsc.core.utils.DoubleEquality;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.smlm.function.DummyGradientFunction;
import uk.ac.sussex.gdsc.smlm.function.FakeGradientFunction;
import uk.ac.sussex.gdsc.smlm.function.Gradient1Function;
import uk.ac.sussex.gdsc.test.DataCache;
import uk.ac.sussex.gdsc.test.DataProvider;
import uk.ac.sussex.gdsc.test.TestLog;
import uk.ac.sussex.gdsc.test.TestSettings;
import uk.ac.sussex.gdsc.test.junit5.ExtraAssertions;
import uk.ac.sussex.gdsc.test.junit5.ExtraAssumptions;
import uk.ac.sussex.gdsc.test.junit5.RandomSeed;
import uk.ac.sussex.gdsc.test.junit5.SeededTest;
import uk.ac.sussex.gdsc.test.junit5.SpeedTag;

@SuppressWarnings({ "javadoc" })
public class WPoissonGradientProcedureTest implements DataProvider<RandomSeed, double[]>
{
	DoubleEquality eq = new DoubleEquality(1e-6, 1e-16);

	int MAX_ITER = 20000;
	static int blockWidth = 10;
	double background = 0.5;
	double signal = 100;
	double angle = Math.PI;
	double xpos = 5;
	double ypos = 5;
	double xwidth = 1.2;
	double ywidth = 1.2;

	private static final DataCache<RandomSeed, double[]> dataCache = new DataCache<>();

	@Override
	public double[] getData(RandomSeed source)
	{
		int n = blockWidth * blockWidth;
		final double[] var = new double[n];
		final UniformRandomProvider r = TestSettings.getRandomGenerator(source.getSeed());
		while (n-- > 0)
			// Range 0.9 to 1.1
			var[n] = 0.9 + 0.2 * r.nextDouble();
		return var;
	}

	@SeededTest
	public void gradientProcedureFactoryCreatesOptimisedProcedures(RandomSeed seed)
	{
		final double[] var = dataCache.getData(seed, this);
		final double[] y = SimpleArrayUtils.newDoubleArray(var.length, 1);
		Assertions.assertEquals(
				WPoissonGradientProcedureFactory.create(y, var, new DummyGradientFunction(6)).getClass(),
				WPoissonGradientProcedure6.class);
		Assertions.assertEquals(
				WPoissonGradientProcedureFactory.create(y, var, new DummyGradientFunction(5)).getClass(),
				WPoissonGradientProcedure5.class);
		Assertions.assertEquals(
				WPoissonGradientProcedureFactory.create(y, var, new DummyGradientFunction(4)).getClass(),
				WPoissonGradientProcedure4.class);
	}

	@SeededTest
	public void poissonGradientProcedureComputesSameAsWLSQGradientProcedure(RandomSeed seed)
	{
		poissonGradientProcedureComputesSameAsWLSQGradientProcedure(seed, 4);
		poissonGradientProcedureComputesSameAsWLSQGradientProcedure(seed, 5);
		poissonGradientProcedureComputesSameAsWLSQGradientProcedure(seed, 6);
		poissonGradientProcedureComputesSameAsWLSQGradientProcedure(seed, 11);
		poissonGradientProcedureComputesSameAsWLSQGradientProcedure(seed, 21);
	}

	private void poissonGradientProcedureComputesSameAsWLSQGradientProcedure(RandomSeed seed, int nparams)
	{
		final double[] var = dataCache.getData(seed, this);

		final int iter = 10;

		final ArrayList<double[]> paramsList = new ArrayList<>(iter);

		final UniformRandomProvider r = TestSettings.getRandomGenerator(seed.getSeed());
		createFakeParams(r, nparams, iter, paramsList);
		final FakeGradientFunction func = new FakeGradientFunction(blockWidth, nparams);

		final String name = String.format("[%d]", nparams);

		for (int i = 0; i < paramsList.size(); i++)
		{
			final double[] y = createFakeData(r);
			final WPoissonGradientProcedure p1 = WPoissonGradientProcedureFactory.create(y, var, func);
			p1.computeFisherInformation(paramsList.get(i));
			final WLSQLVMGradientProcedure p2 = new WLSQLVMGradientProcedure(y, var, func);
			p2.gradient(paramsList.get(i));

			// Exactly the same ...
			ExtraAssertions.assertArrayEquals(p1.data, p2.alpha, "%s Observations: Not same alpha @ %d", name, i);
			ExtraAssertions.assertArrayEquals(p1.getLinear(), p2.getAlphaLinear(),
					"%s Observations: Not same alpha linear @ %d", name, i);
		}
	}

	private abstract class Timer
	{
		private int loops;
		int min;

		Timer()
		{
		}

		Timer(int min)
		{
			this.min = min;
		}

		long getTime()
		{
			// Run till stable timing
			long t1 = time();
			for (int i = 0; i < 10; i++)
			{
				final long t2 = t1;
				t1 = time();
				if (loops >= min && DoubleEquality.relativeError(t1, t2) < 0.02) // 2% difference
					break;
			}
			return t1;
		}

		long time()
		{
			loops++;
			long t = System.nanoTime();
			run();
			t = System.nanoTime() - t;
			//System.out.printf("[%d] Time = %d\n", loops, t);
			return t;
		}

		abstract void run();
	}

	@SeededTest
	public void gradientProcedureUnrolledComputesSameAsGradientProcedure(RandomSeed seed)
	{
		gradientProcedureUnrolledComputesSameAsGradientProcedure(seed, 4, false);
		gradientProcedureUnrolledComputesSameAsGradientProcedure(seed, 5, false);
		gradientProcedureUnrolledComputesSameAsGradientProcedure(seed, 6, false);
	}

	@SeededTest
	public void gradientProcedureUnrolledComputesSameAsGradientProcedureWithPrecomputed(RandomSeed seed)
	{
		gradientProcedureUnrolledComputesSameAsGradientProcedure(seed, 4, true);
		gradientProcedureUnrolledComputesSameAsGradientProcedure(seed, 5, true);
		gradientProcedureUnrolledComputesSameAsGradientProcedure(seed, 6, true);
	}

	private void gradientProcedureUnrolledComputesSameAsGradientProcedure(RandomSeed seed, int nparams,
			boolean precomputed)
	{
		final int iter = 10;

		final ArrayList<double[]> paramsList = new ArrayList<>(iter);

		final UniformRandomProvider r = TestSettings.getRandomGenerator(seed.getSeed());
		createFakeParams(r, nparams, iter, paramsList);
		final Gradient1Function func = new FakeGradientFunction(blockWidth, nparams);

		final double[] v = (precomputed) ? dataCache.getData(seed, this) : null;

		final String name = String.format("[%d]", nparams);
		for (int i = 0; i < paramsList.size(); i++)
		{
			final double[] y = createFakeData(r);
			final WPoissonGradientProcedure p1 = new WPoissonGradientProcedure(y, v, func);
			p1.computeFisherInformation(paramsList.get(i));

			final WPoissonGradientProcedure p2 = WPoissonGradientProcedureFactory.create(y, v, func);
			p2.computeFisherInformation(paramsList.get(i));

			// Exactly the same ...
			ExtraAssertions.assertArrayEquals(p1.getLinear(), p2.getLinear(), "%s Observations: Not same linear @ %d",
					name, i);
		}
	}

	@SpeedTag
	@SeededTest
	public void gradientProcedureIsFasterUnrolledThanGradientProcedure(RandomSeed seed)
	{
		gradientProcedureIsFasterUnrolledThanGradientProcedure(seed, 4, false);
		gradientProcedureIsFasterUnrolledThanGradientProcedure(seed, 5, false);
		gradientProcedureIsFasterUnrolledThanGradientProcedure(seed, 6, false);
	}

	@SpeedTag
	@SeededTest
	public void gradientProcedureIsFasterUnrolledThanGradientProcedureWithPrecomputed(RandomSeed seed)
	{
		gradientProcedureIsFasterUnrolledThanGradientProcedure(seed, 4, true);
		gradientProcedureIsFasterUnrolledThanGradientProcedure(seed, 5, true);
		gradientProcedureIsFasterUnrolledThanGradientProcedure(seed, 6, true);
	}

	private void gradientProcedureIsFasterUnrolledThanGradientProcedure(RandomSeed seed, final int nparams,
			final boolean precomputed)
	{
		ExtraAssumptions.assumeSpeedTest();

		final int iter = 100;

		final ArrayList<double[]> paramsList = new ArrayList<>(iter);
		final ArrayList<double[]> yList = new ArrayList<>(iter);

		createFakeData(TestSettings.getRandomGenerator(seed.getSeed()), nparams, iter, paramsList, yList);

		// Remove the timing of the function call by creating a dummy function
		final FakeGradientFunction func = new FakeGradientFunction(blockWidth, nparams);
		final double[] v = (precomputed) ? dataCache.getData(seed, this) : null;

		for (int i = 0; i < paramsList.size(); i++)
		{
			final double[] y = yList.get(i);
			final WPoissonGradientProcedure p1 = new WPoissonGradientProcedure(y, v, func);
			p1.computeFisherInformation(paramsList.get(i));

			final WPoissonGradientProcedure p2 = WPoissonGradientProcedureFactory.create(y, v, func);
			p2.computeFisherInformation(paramsList.get(i));

			// Check they are the same
			ExtraAssertions.assertArrayEquals(p1.getLinear(), p2.getLinear(), "M %d", i);
		}

		// Realistic loops for an optimisation
		final int loops = 15;

		// Run till stable timing
		final Timer t1 = new Timer()
		{
			@Override
			void run()
			{
				for (int i = 0, k = 0; i < paramsList.size(); i++)
				{
					final WPoissonGradientProcedure p1 = new WPoissonGradientProcedure(yList.get(i), v, func);
					for (int j = loops; j-- > 0;)
						p1.computeFisherInformation(paramsList.get(k++ % iter));
				}
			}
		};
		final long time1 = t1.getTime();

		final Timer t2 = new Timer(t1.loops)
		{
			@Override
			void run()
			{
				for (int i = 0, k = 0; i < paramsList.size(); i++)
				{
					final WPoissonGradientProcedure p2 = WPoissonGradientProcedureFactory.create(yList.get(i), v, func);
					for (int j = loops; j-- > 0;)
						p2.computeFisherInformation(paramsList.get(k++ % iter));
				}
			}
		};
		final long time2 = t2.getTime();

		TestLog.logSpeedTestResult(time2 < time1, "Precomputed=%b : Standard %d : Unrolled %d = %d : %fx\n",
				precomputed, time1, nparams, time2, (1.0 * time1) / time2);
	}

	@SpeedTag
	@SeededTest
	public void gradientProcedureIsFasterThanWLSEGradientProcedure(RandomSeed seed)
	{
		gradientProcedureIsFasterThanWLSEGradientProcedure(seed, 4);
		gradientProcedureIsFasterThanWLSEGradientProcedure(seed, 5);
		gradientProcedureIsFasterThanWLSEGradientProcedure(seed, 6);
		gradientProcedureIsFasterThanWLSEGradientProcedure(seed, 11);
	}

	private void gradientProcedureIsFasterThanWLSEGradientProcedure(RandomSeed seed, final int nparams)
	{
		ExtraAssumptions.assumeSpeedTest();

		final int iter = 100;

		final ArrayList<double[]> paramsList = new ArrayList<>(iter);
		final ArrayList<double[]> yList = new ArrayList<>(iter);

		final double[] var = dataCache.getData(seed, this);
		createFakeData(TestSettings.getRandomGenerator(seed.getSeed()), nparams, iter, paramsList, yList);

		// Remove the timing of the function call by creating a dummy function
		final FakeGradientFunction func = new FakeGradientFunction(blockWidth, nparams);
		for (int i = 0; i < paramsList.size(); i++)
		{
			final double[] y = yList.get(i);
			final WLSQLVMGradientProcedure p1 = WLSQLVMGradientProcedureFactory.create(y, var, func);
			p1.gradient(paramsList.get(i));

			final WPoissonGradientProcedure p2 = WPoissonGradientProcedureFactory.create(y, var, func);
			p2.computeFisherInformation(paramsList.get(i));

			// Check they are the same
			ExtraAssertions.assertArrayEquals(p1.getAlphaLinear(), p2.getLinear(), "M %d", i);
		}

		// Realistic loops for an optimisation
		final int loops = 15;

		// Run till stable timing
		final Timer t1 = new Timer()
		{
			@Override
			void run()
			{
				for (int i = 0, k = 0; i < paramsList.size(); i++)
				{
					final WLSQLVMGradientProcedure p1 = WLSQLVMGradientProcedureFactory.create(yList.get(i), var, func);
					for (int j = loops; j-- > 0;)
						p1.gradient(paramsList.get(k++ % iter));
				}
			}
		};
		final long time1 = t1.getTime();

		final Timer t2 = new Timer(t1.loops)
		{
			@Override
			void run()
			{
				for (int i = 0, k = 0; i < paramsList.size(); i++)
				{
					final WPoissonGradientProcedure p2 = WPoissonGradientProcedureFactory.create(yList.get(i), var,
							func);
					for (int j = loops; j-- > 0;)
						p2.computeFisherInformation(paramsList.get(k++ % iter));
				}
			}
		};
		final long time2 = t2.getTime();

		TestLog.logSpeedTestResult(time2 < time1,
				"WLSQLVMGradientProcedure %d : WPoissonGradientProcedure %d = %d : %fx\n", time1, nparams, time2,
				(1.0 * time1) / time2);
	}

	protected int[] createFakeData(UniformRandomProvider r, int nparams, int iter, ArrayList<double[]> paramsList,
			ArrayList<double[]> yList)
	{
		final int[] x = new int[blockWidth * blockWidth];
		for (int i = 0; i < x.length; i++)
			x[i] = i;
		for (int i = 0; i < iter; i++)
		{
			final double[] params = new double[nparams];
			final double[] y = createFakeData(r, params);
			paramsList.add(params);
			yList.add(y);
		}
		return x;
	}

	private static double[] createFakeData(UniformRandomProvider r, double[] params)
	{
		final int n = blockWidth * blockWidth;

		for (int i = 0; i < params.length; i++)
			params[i] = r.nextDouble();

		final double[] y = new double[n];
		for (int i = 0; i < y.length; i++)
			y[i] = r.nextDouble();

		return y;
	}

	private static double[] createFakeData(UniformRandomProvider r)
	{
		final int n = blockWidth * blockWidth;

		final double[] y = new double[n];
		for (int i = 0; i < y.length; i++)
			y[i] = r.nextDouble();

		return y;
	}

	protected void createFakeParams(UniformRandomProvider r, int nparams, int iter, ArrayList<double[]> paramsList)
	{
		for (int i = 0; i < iter; i++)
		{
			final double[] params = new double[nparams];
			createFakeParams(r, params);
			paramsList.add(params);
		}
	}

	private static void createFakeParams(UniformRandomProvider r, double[] params)
	{
		for (int i = 0; i < params.length; i++)
			params[i] = r.nextDouble();
	}

	protected ArrayList<double[]> copyList(ArrayList<double[]> paramsList)
	{
		final ArrayList<double[]> params2List = new ArrayList<>(paramsList.size());
		for (int i = 0; i < paramsList.size(); i++)
			params2List.add(copydouble(paramsList.get(i)));
		return params2List;
	}

	private static double[] copydouble(double[] d)
	{
		final double[] d2 = new double[d.length];
		for (int i = 0; i < d.length; i++)
			d2[i] = d[i];
		return d2;
	}
}
