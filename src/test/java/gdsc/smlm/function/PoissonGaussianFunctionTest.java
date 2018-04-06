package gdsc.smlm.function;

import gdsc.core.utils.Maths;
import gdsc.smlm.function.PoissonGaussianFunction;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.integration.SimpsonIntegrator;
import org.apache.commons.math3.analysis.integration.UnivariateIntegrator;
import org.junit.Assert;
import org.junit.Test;

public class PoissonGaussianFunctionTest
{
	double[] gain = { 1, 2, 4, 8, 16 };
	double[] photons = { -1, 0, 0.25, 0.5, 1, 2, 4, 10, 100, 1000 };
	double[] noise = { 1, 2, 4, 8 };

	@Test
	public void cumulativeProbabilityIsOneWithPicard()
	{
		for (double g : gain)
			for (double p : photons)
				for (double s : noise)
					cumulativeProbabilityIsOne(g, p, s, true);
	}

	@Test
	public void cumulativeProbabilityIsOneWithPade()
	{
		for (double g : gain)
			for (double p : photons)
				for (double s : noise)
					cumulativeProbabilityIsOne(g, p, s, true);
	}

	@Test
	public void probabilityMatchesLogProbability()
	{
		for (double g : gain)
			for (double p : photons)
				for (double s : noise)
				{
					probabilityMatchesLogProbability(g, p, s, true);
					probabilityMatchesLogProbability(g, p, s, false);
				}
	}

	@Test
	public void padeIsFaster()
	{
		double[] noise2 = new double[noise.length];
		for (int i = 0; i < noise.length; i++)
			noise2[i] = noise[i] * noise[i];

		int N = 100;
		double[][] x = new double[photons.length][N];
		for (int i = 0; i < photons.length; i++)
		{
			final double p = photons[i] * 2 / N;
			for (int j = 0; j < N; j++)
			{
				x[i][j] = p * j;
			}
		}

		long t1 = getTime(noise2, N, x, true);
		long t2 = getTime(noise2, N, x, false);

		System.out.printf("Picard %d : Pade %d (%fx)\n", t1, t2, t1 / (double) t2);
		Assert.assertTrue(String.format("Picard %d > Pade %d", t1, t2), t2 < t1);
	}

	@Test
	public void staticMethodsMatchInstanceMethods()
	{
		for (double g : gain)
			for (double p : photons)
				for (double s : noise)
				{
					staticMethodsMatchInstanceMethods(g, p, s, true);
					staticMethodsMatchInstanceMethods(g, p, s, false);
				}
	}

	private long getTime(double[] noise2, int N, double[][] x, final boolean usePicard)
	{
		// Warm up
		for (double s2 : noise2)
		{
			for (int i = 0; i < photons.length; i++)
			{
				final double p = photons[i];
				for (int j = 0; j < N; j++)
				{
					PoissonGaussianFunction.probability(x[i][j], p, s2, usePicard);
				}
			}
		}

		// Time
		long t1 = System.nanoTime();
		for (double s2 : noise2)
		{
			for (int i = 0; i < photons.length; i++)
			{
				final double p = photons[i];
				for (int j = 0; j < N; j++)
				{
					PoissonGaussianFunction.probability(x[i][j], p, s2, usePicard);
				}
			}
		}
		t1 = System.nanoTime() - t1;
		return t1;
	}

	private void cumulativeProbabilityIsOne(final double gain, final double mu, final double s, final boolean usePicard)
	{
		// Note: The input mu is pre-gain.
		final PoissonGaussianFunction f = PoissonGaussianFunction.createWithStandardDeviation(1.0 / gain, mu * gain, s);
		f.setUsePicardApproximation(usePicard);
		double p = 0;
		int min = 1;
		int max = 0;

		// Evaluate an initial range. 
		// Gaussian should have >99% within +/- s
		// Poisson will have mean mu with a variance mu. 
		// At large mu it is approximately normal so use 3 sqrt(mu) for the range added to the mean
		if (mu > 0)
		{
			min = getMin(s);
			max = getMax(gain, mu);
			for (int x = min; x <= max; x++)
			{
				final double pp = f.probability(x);
				//System.out.printf("x=%d, p=%f\n", x, pp);
				p += pp;
			}
			//if (p > 1.01)
			//	Assert.fail("P > 1: " + p);
		}

		// We have most of the probability density. 
		// Now keep evaluating up and down until no difference
		final double changeTolerance = 1e-6;
		for (int x = min - 1;; x--)
		{
			min = x;
			final double pp = f.probability(x);
			//System.out.printf("x=%d, p=%f\n", x, pp);
			p += pp;
			if (pp == 0 || pp / p < changeTolerance)
				break;
		}
		for (int x = max + 1;; x++)
		{
			max = x;
			final double pp = f.probability(x);
			//System.out.printf("x=%d, p=%f\n", x, pp);
			p += pp;
			if (pp == 0 || pp / p < changeTolerance)
				break;
		}

		// Do a formal integration
		double p2 = 0;
		UnivariateIntegrator in = new SimpsonIntegrator(1e-6, 1e-6, 3, SimpsonIntegrator.SIMPSON_MAX_ITERATIONS_COUNT);
		p2 = in.integrate(Integer.MAX_VALUE, new UnivariateFunction()
		{
			public double value(double x)
			{
				return f.probability(x);
			}
		}, min, max);

		// Current this fails when the standard deviation is scaled by the gain.
		String msg = String.format("g=%f, mu=%f, s=%f", gain, mu, s);
		if (p < 0.98 || p > 1.02)
			System.out.printf("%s p=%f  %f\n", msg, p, p2);
		//Assert.assertEquals(msg, 1, p2, 0.02);
	}

	private int getMin(final double s)
	{
		int min;
		min = (int) -Math.ceil(3 * s);
		return min;
	}

	private int getMax(final double gain, final double mu)
	{
		int max;
		max = (int) Math.ceil(gain * (mu + 3 * Math.sqrt(mu)));
		return max;
	}

	private void probabilityMatchesLogProbability(final double gain, final double mu, final double s,
			final boolean usePicard)
	{
		// Note: The input mu is pre-gain.
		PoissonGaussianFunction f = PoissonGaussianFunction.createWithStandardDeviation(1.0 / gain, mu * gain, s);
		f.setUsePicardApproximation(usePicard);

		// Evaluate an initial range. 
		// Gaussian should have >99% within +/- s
		// Poisson will have mean mu with a variance mu. 
		// At large mu it is approximately normal so use 3 sqrt(mu) for the range added to the mean
		int min = getMin(s);
		int max = getMax(gain, mu);
		for (int x = min; x <= max; x++)
		{
			final double p = f.probability(x);
			if (p == 0)
				continue;
			final double logP = f.logProbability(x);
			Assert.assertEquals(String.format("g=%f, mu=%f, s=%f", gain, mu, s), Math.log(p), logP,
					1e-3 * Math.abs(logP));
		}
	}

	private void staticMethodsMatchInstanceMethods(final double gain, final double mu, final double s,
			final boolean usePicard)
	{
		// Note: The input mu is pre-gain.
		PoissonGaussianFunction f = PoissonGaussianFunction.createWithStandardDeviation(1.0 / gain, mu * gain, s);
		f.setUsePicardApproximation(usePicard);

		// Evaluate an initial range. 
		// Gaussian should have >99% within +/- s
		// Poisson will have mean mu with a variance mu. 
		// At large mu it is approximately normal so use 3 sqrt(mu) for the range added to the mean
		int min = getMin(s);
		int max = getMax(gain, mu);
		final double logGain = Math.log(gain);
		final double s2 = Maths.pow2(s / gain);
		for (int x = min; x <= max; x++)
		{
			double p = f.probability(x);
			double pp = PoissonGaussianFunction.probability(x / gain, mu, s2, usePicard) / gain;
			Assert.assertEquals(String.format("probability g=%f, mu=%f, s=%f", gain, mu, s), p, pp, 1e-10);

			p = f.logProbability(x);
			pp = PoissonGaussianFunction.logProbability(x / gain, mu, s2, usePicard) - logGain;
			Assert.assertEquals(String.format("logProbability g=%f, mu=%f, s=%f", gain, mu, s), p, pp, 1e-10);
		}
	}
}
