package gdsc.smlm.fitting.nonlinear.gradient;

import java.util.ArrayList;

import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;
import org.ejml.data.DenseMatrix64F;
import org.junit.Assert;
import org.junit.Test;

import gdsc.core.utils.DoubleEquality;
import gdsc.smlm.TestSettings;
import gdsc.smlm.function.Gradient1Function;

/**
 * Contains speed tests for the methods for calculating the Hessian and gradient vector
 * for use in the LVM algorithm.
 */
public class PoissonGradientProcedureTest
{
	boolean speedTests = true;
	DoubleEquality eq = new DoubleEquality(6, 1e-16);

	int MAX_ITER = 20000;
	int blockWidth = 10;
	double Background = 0.5;
	double Signal = 100;
	double Angle = Math.PI;
	double Xpos = 5;
	double Ypos = 5;
	double Xwidth = 1.2;
	double Ywidth = 1.2;

	RandomDataGenerator rdg;

	@Test
	public void gradientProcedureFactoryCreatesOptimisedProcedures()
	{
		Assert.assertEquals(PoissonGradientProcedureFactory.create(new DummyGradientFunction(6)).getClass(),
				PoissonGradientProcedure6.class);
		Assert.assertEquals(PoissonGradientProcedureFactory.create(new DummyGradientFunction(5)).getClass(),
				PoissonGradientProcedure5.class);
		Assert.assertEquals(PoissonGradientProcedureFactory.create(new DummyGradientFunction(4)).getClass(),
				PoissonGradientProcedure4.class);
	}

	@Test
	public void gradientProcedureComputesSameAsGradientCalculator()
	{
		gradientProcedureComputesSameAsGradientCalculator(4);
		gradientProcedureComputesSameAsGradientCalculator(5);
		gradientProcedureComputesSameAsGradientCalculator(6);
		gradientProcedureComputesSameAsGradientCalculator(11);
		gradientProcedureComputesSameAsGradientCalculator(21);
	}

	@Test
	public void gradientProcedureIsNotSlowerThanGradientCalculator()
	{
		gradientProcedureIsNotSlowerThanGradientCalculator(4);
		gradientProcedureIsNotSlowerThanGradientCalculator(5);
		gradientProcedureIsNotSlowerThanGradientCalculator(6);
		// 2 peaks
		gradientProcedureIsNotSlowerThanGradientCalculator(11);
		// 4 peaks
		gradientProcedureIsNotSlowerThanGradientCalculator(21);
	}

	private void gradientProcedureComputesSameAsGradientCalculator(int nparams)
	{
		int iter = 10;
		rdg = new RandomDataGenerator(new Well19937c(30051977));

		ArrayList<double[]> paramsList = new ArrayList<double[]>(iter);

		createFakeParams(nparams, iter, paramsList);
		int n = blockWidth * blockWidth;
		FakeGradientFunction func = new FakeGradientFunction(blockWidth, nparams);

		GradientCalculator calc = GradientCalculatorFactory.newCalculator(nparams, false);

		String name = String.format("[%d]", nparams);

		for (int i = 0; i < paramsList.size(); i++)
		{
			PoissonGradientProcedure p = PoissonGradientProcedureFactory.create(func);
			p.computeFisherInformation(paramsList.get(i));
			double[][] m = calc.fisherInformationMatrix(n, paramsList.get(i), func);
			// Exactly the same ...
			double[] al = p.getLinear();
			Assert.assertArrayEquals(name + " Observations: Not same alpha @ " + i, al, new DenseMatrix64F(m).data, 0);

			double[][] am = p.getMatrix();
			for (int j = 0; j < nparams; j++)
				Assert.assertArrayEquals(name + " Observations: Not same alpha @ " + i, am[j], m[j], 0);
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
				long t2 = t1;
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

	private void gradientProcedureIsNotSlowerThanGradientCalculator(final int nparams)
	{
		org.junit.Assume.assumeTrue(speedTests || TestSettings.RUN_SPEED_TESTS);

		final int iter = 1000;
		rdg = new RandomDataGenerator(new Well19937c(30051977));

		final ArrayList<double[]> paramsList = new ArrayList<double[]>(iter);

		createFakeParams(nparams, iter, paramsList);
		final int n = blockWidth * blockWidth;
		final FakeGradientFunction func = new FakeGradientFunction(blockWidth, nparams);

		GradientCalculator calc = GradientCalculatorFactory.newCalculator(nparams, false);

		for (int i = 0; i < paramsList.size(); i++)
			calc.fisherInformationMatrix(n, paramsList.get(i), func);

		for (int i = 0; i < paramsList.size(); i++)
		{
			PoissonGradientProcedure p = PoissonGradientProcedureFactory.create(func);
			p.computeFisherInformation(paramsList.get(i));
		}

		// Realistic loops for an optimisation
		final int loops = 15;

		// Run till stable timing
		Timer t1 = new Timer()
		{
			@Override
			void run()
			{
				for (int i = 0, k = 0; i < iter; i++)
				{
					GradientCalculator calc = GradientCalculatorFactory.newCalculator(nparams, false);
					for (int j = loops; j-- > 0;)
						calc.fisherInformationMatrix(n, paramsList.get(k++ % iter), func);
				}
			}
		};
		long time1 = t1.getTime();

		Timer t2 = new Timer(t1.loops)
		{
			@Override
			void run()
			{
				for (int i = 0, k = 0; i < iter; i++)
				{
					PoissonGradientProcedure p = PoissonGradientProcedureFactory.create(func);
					for (int j = loops; j-- > 0;)
						p.computeFisherInformation(paramsList.get(k++ % iter));
				}
			}
		};
		long time2 = t2.getTime();

		log("GradientCalculator = %d : PoissonGradientProcedure %d = %d : %fx\n", time1, nparams, time2,
				(1.0 * time1) / time2);
		if (TestSettings.ASSERT_SPEED_TESTS)
		{
			// Add contingency
			Assert.assertTrue(time2 < time1 * 1.5);
		}
	}

	@Test
	public void gradientProcedureUnrolledComputesSameAsGradientProcedure()
	{
		gradientProcedureUnrolledComputesSameAsGradientProcedure(4);
		gradientProcedureUnrolledComputesSameAsGradientProcedure(5);
		gradientProcedureUnrolledComputesSameAsGradientProcedure(6);
	}

	private void gradientProcedureUnrolledComputesSameAsGradientProcedure(int nparams)
	{
		int iter = 10;
		rdg = new RandomDataGenerator(new Well19937c(30051977));

		ArrayList<double[]> paramsList = new ArrayList<double[]>(iter);

		createFakeParams(nparams, iter, paramsList);
		FakeGradientFunction func = new FakeGradientFunction(blockWidth, nparams);

		String name = String.format("[%d]", nparams);
		for (int i = 0; i < paramsList.size(); i++)
		{
			PoissonGradientProcedure p1 = new PoissonGradientProcedure(func);
			p1.computeFisherInformation(paramsList.get(i));

			PoissonGradientProcedure p2 = PoissonGradientProcedureFactory.create(func);
			p2.computeFisherInformation(paramsList.get(i));

			// Exactly the same ...
			Assert.assertArrayEquals(name + " Observations: Not same alpha @ " + i, p1.getLinear(),
					p2.getLinear(), 0);

			double[][] am1 = p1.getMatrix();
			double[][] am2 = p2.getMatrix();
			for (int j = 0; j < nparams; j++)
				Assert.assertArrayEquals(name + " Observations: Not same alpha @ " + i, am1[j], am2[j], 0);
		}
	}

	@Test
	public void gradientProcedureIsFasterUnrolledThanGradientProcedure()
	{
		gradientProcedureIsFasterUnrolledThanGradientProcedure(4);
		gradientProcedureIsFasterUnrolledThanGradientProcedure(5);
		gradientProcedureIsFasterUnrolledThanGradientProcedure(6);
	}

	private void gradientProcedureIsFasterUnrolledThanGradientProcedure(final int nparams)
	{
		org.junit.Assume.assumeTrue(speedTests || TestSettings.RUN_SPEED_TESTS);

		final int iter = 100;
		rdg = new RandomDataGenerator(new Well19937c(30051977));

		final ArrayList<double[]> paramsList = new ArrayList<double[]>(iter);

		createFakeParams(nparams, iter, paramsList);

		// Remove the timing of the function call by creating a dummy function
		final Gradient1Function func = new FakeGradientFunction(blockWidth, nparams);

		for (int i = 0; i < paramsList.size(); i++)
		{
			PoissonGradientProcedure p1 = new PoissonGradientProcedure(func);
			p1.computeFisherInformation(paramsList.get(i));
			p1.computeFisherInformation(paramsList.get(i));

			PoissonGradientProcedure p2 = PoissonGradientProcedureFactory.create(func);
			p2.computeFisherInformation(paramsList.get(i));
			p2.computeFisherInformation(paramsList.get(i));

			// Check they are the same
			Assert.assertArrayEquals("M " + i, p1.getLinear(), p2.getLinear(), 0);
		}

		// Realistic loops for an optimisation
		final int loops = 15;

		// Run till stable timing
		Timer t1 = new Timer()
		{
			@Override
			void run()
			{
				for (int i = 0, k = 0; i < paramsList.size(); i++)
				{
					PoissonGradientProcedure p1 = new PoissonGradientProcedure(func);
					for (int j = loops; j-- > 0;)
						p1.computeFisherInformation(paramsList.get(k++ % iter));
				}
			}
		};
		long time1 = t1.getTime();

		Timer t2 = new Timer(t1.loops)
		{
			@Override
			void run()
			{
				for (int i = 0, k = 0; i < paramsList.size(); i++)
				{
					PoissonGradientProcedure p2 = PoissonGradientProcedureFactory.create(func);
					for (int j = loops; j-- > 0;)
						p2.computeFisherInformation(paramsList.get(k++ % iter));
				}
			}
		};
		long time2 = t2.getTime();

		log("Standard %d : Unrolled %d = %d : %fx\n", time1, nparams, time2, (1.0 * time1) / time2);
		Assert.assertTrue(time2 < time1);
	}

	protected int[] createFakeData(int nparams, int iter, ArrayList<double[]> paramsList, ArrayList<double[]> yList)
	{
		int[] x = new int[blockWidth * blockWidth];
		for (int i = 0; i < x.length; i++)
			x[i] = i;
		for (int i = 0; i < iter; i++)
		{
			double[] params = new double[nparams];
			double[] y = createFakeData(params);
			paramsList.add(params);
			yList.add(y);
		}
		return x;
	}

	private double[] createFakeData(double[] params)
	{
		int n = blockWidth * blockWidth;
		RandomGenerator r = rdg.getRandomGenerator();

		for (int i = 0; i < params.length; i++)
		{
			params[i] = r.nextDouble();
		}

		double[] y = new double[n];
		for (int i = 0; i < y.length; i++)
		{
			y[i] = r.nextDouble();
		}

		return y;
	}

	protected void createFakeParams(int nparams, int iter, ArrayList<double[]> paramsList)
	{
		for (int i = 0; i < iter; i++)
		{
			double[] params = new double[nparams];
			createFakeParams(params);
			paramsList.add(params);
		}
	}

	private void createFakeParams(double[] params)
	{
		RandomGenerator r = rdg.getRandomGenerator();
		for (int i = 0; i < params.length; i++)
		{
			params[i] = r.nextDouble();
		}
	}

	protected ArrayList<double[]> copyList(ArrayList<double[]> paramsList)
	{
		ArrayList<double[]> params2List = new ArrayList<double[]>(paramsList.size());
		for (int i = 0; i < paramsList.size(); i++)
		{
			params2List.add(copydouble(paramsList.get(i)));
		}
		return params2List;
	}

	private double[] copydouble(double[] d)
	{
		double[] d2 = new double[d.length];
		for (int i = 0; i < d.length; i++)
			d2[i] = d[i];
		return d2;
	}

	void log(String format, Object... args)
	{
		System.out.printf(format, args);
	}
}
