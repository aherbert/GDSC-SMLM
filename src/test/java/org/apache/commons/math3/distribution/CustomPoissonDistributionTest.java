package org.apache.commons.math3.distribution;

import org.apache.commons.math3.distribution.CustomPoissonDistribution;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;
import org.junit.Assert;
import org.junit.Test;

import gdsc.core.test.BaseTimingTask;
import gdsc.core.test.TimingService;

public class CustomPoissonDistributionTest
{
	private abstract class MyTimingTask extends BaseTimingTask
	{
		RandomGenerator r;
		double mean;
		double min;
		int n, m = 10;

		public MyTimingTask(String name, double min, double max)
		{
			super(String.format("%s %.1f - %.1f", name, min, max));
			r = new Well19937c();
			this.min = min;
			mean = min;
			n = 0;
			while (mean < max)
			{
				n++;
				mean += 1;
			}
		}

		public int getSize()
		{
			return 1;
		}

		public Object getData(int i)
		{
			r.setSeed(30051977);
			mean = min;
			return null;
		}
	}

	private class StaticTimingTask extends MyTimingTask
	{
		RandomDataGenerator rdg;

		public StaticTimingTask(double min, double max)
		{
			super("RandomDataGenerator", min, max);
			rdg = new RandomDataGenerator(r);
		}

		public Object run(Object data)
		{
			long[] e = new long[n * m];
			for (int i = 0, k = 0; i < n; i++)
			{
				for (int j = 0; j < m; j++, k++)
				{
					e[k] = rdg.nextPoisson(mean);
				}
				mean += 1;
			}
			return e;
		}
	}

	private class InstanceTimingTask extends MyTimingTask
	{
		CustomPoissonDistribution dist;

		public InstanceTimingTask(double min, double max)
		{
			super("Instance", min, max);
			dist = new CustomPoissonDistribution(r, 1);
		}

		public Object run(Object data)
		{
			long[] e = new long[n * m];
			for (int i = 0, k = 0; i < n; i++)
			{
				dist.setMean(mean);
				for (int j = 0; j < m; j++, k++)
				{
					e[k] = dist.sample();
				}
				mean += 1;
			}
			return e;
		}
	}
	
	@Test
	public void canCreateSamples()
	{
		StaticTimingTask t1 = new StaticTimingTask(0.5, 60);
		t1.getData(0);
		long[] e = (long[]) t1.run(null);
		
		InstanceTimingTask t2 = new InstanceTimingTask(0.5, 60);
		t2.getData(0);
		long[] o = (long[]) t2.run(null);
		
		Assert.assertArrayEquals(e, o);
	}

	@Test
	public void customDistributionIsFasterWithTinyMean()
	{
		TimingService ts = new TimingService(5);
		ts.execute(new StaticTimingTask(0.5, 10));
		ts.execute(new InstanceTimingTask(0.5, 10));

		int size = ts.getSize();
		ts.repeat(size);
		ts.report(size);
		
		Assert.assertTrue(ts.get(-1).getMean() < ts.get(-2).getMean());
	}

	@Test
	public void customDistributionIsFasterWithSmallMean()
	{
		TimingService ts = new TimingService(5);
		ts.execute(new StaticTimingTask(10, 38));
		ts.execute(new InstanceTimingTask(10, 38));

		int size = ts.getSize();
		ts.repeat(size);
		ts.report(size);
		
		Assert.assertTrue(ts.get(-1).getMean() < ts.get(-2).getMean());
	}

	@Test
	public void customDistributionIsFasterWithBigMean()
	{
		// When the mean is above 40 the PoissonDistribution switches to a different
		// sampling method and this is so slow that the speed increase from using 
		// the instance class is negligible. However test it is still faster. If this fails
		// then Apache commons may have changed their implementation and the custom
		// class should be updated.
		
		TimingService ts = new TimingService(5);
		ts.execute(new StaticTimingTask(40.5, 60));
		ts.execute(new InstanceTimingTask(40.5, 60));

		int size = ts.getSize();
		ts.repeat(size);
		ts.report(size);
		
		Assert.assertTrue(ts.get(-1).getMean() < ts.get(-2).getMean());
	}
}