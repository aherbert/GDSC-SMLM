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
package gdsc.smlm.results.filter;

import org.apache.commons.math3.random.RandomGenerator;
import org.junit.Assert;
import org.junit.Test;

import gdsc.core.utils.XmlUtils;
import gdsc.test.TestSettings;
import gdsc.test.TestSettings.LogLevel;
import gdsc.test.TimingResult;
import gdsc.test.TimingService;
import gdsc.test.TimingTask;

public class FilterTest
{
	@Test
	public void canCompareMultiFilter()
	{
		RandomGenerator randomGenerator = TestSettings.getRandomGenerator();
		MultiFilter f = new MultiFilter(0, 0, 0, 0, 0, 0, 0, 0, 0);
		for (int i = 1000; i-- > 0;)
		{
			MultiFilter f1 = (MultiFilter) f.create(random(f.getNumberOfParameters(), randomGenerator));
			MultiFilter f2 = (MultiFilter) f.create(random(f.getNumberOfParameters(), randomGenerator));
			int e = f1.weakest((Filter) f2);
			int o = f1.weakest(f2);
			Assert.assertEquals(e, o);
		}
	}

	@Test
	public void canCompareMultiFilter2()
	{
		RandomGenerator randomGenerator = TestSettings.getRandomGenerator();
		MultiFilter2 f = new MultiFilter2(0, 0, 0, 0, 0, 0, 0, 0, 0);
		for (int i = 1000; i-- > 0;)
		{
			MultiFilter2 f1 = (MultiFilter2) f.create(random(f.getNumberOfParameters(), randomGenerator));
			MultiFilter2 f2 = (MultiFilter2) f.create(random(f.getNumberOfParameters(), randomGenerator));
			int e = f1.weakest((Filter) f2);
			int o = f1.weakest(f2);
			Assert.assertEquals(e, o);
		}
	}

	@Test
	public void directCompareMultiFilterIsFaster()
	{
		TestSettings.assumeMediumComplexity();

		RandomGenerator randomGenerator = TestSettings.getRandomGenerator();
		final MultiFilter f1 = new MultiFilter(0, 0, 0, 0, 0, 0, 0, 0, 0);
		final MultiFilter2 f2 = new MultiFilter2(0, 0, 0, 0, 0, 0, 0, 0, 0);

		final double[][][] data = new double[1000][][];
		for (int i = data.length; i-- > 0;)
		{
			data[i] = new double[][] { random(f1.getNumberOfParameters(), randomGenerator),
					random(f1.getNumberOfParameters(), randomGenerator) };
		}

		TimingService ts = new TimingService();

		ts.execute(new TimingTask()
		{
			@Override
			public Object getData(int i)
			{
				return new MultiFilter[] { (MultiFilter) f1.create(data[i][0]), (MultiFilter) f1.create(data[i][1]) };
			}

			@Override
			public Object run(Object data)
			{
				MultiFilter f1 = ((MultiFilter[]) data)[0];
				MultiFilter f2 = ((MultiFilter[]) data)[1];
				f1.weakest((Filter) f2);
				return null;
			}

			@Override
			public void check(int i, Object result)
			{
			}

			@Override
			public int getSize()
			{
				return data.length;
			}

			@Override
			public String getName()
			{
				return "MultiFilter";
			}
		});

		ts.execute(new TimingTask()
		{
			@Override
			public Object getData(int i)
			{
				return new MultiFilter[] { (MultiFilter) f1.create(data[i][0]), (MultiFilter) f1.create(data[i][1]) };
			}

			@Override
			public Object run(Object data)
			{
				MultiFilter f1 = ((MultiFilter[]) data)[0];
				MultiFilter f2 = ((MultiFilter[]) data)[1];
				f1.weakest(f2);
				return null;
			}

			@Override
			public void check(int i, Object result)
			{
			}

			@Override
			public int getSize()
			{
				return data.length;
			}

			@Override
			public String getName()
			{
				return "MultiFilter direct";
			}
		});

		ts.execute(new TimingTask()
		{
			@Override
			public Object getData(int i)
			{
				return new MultiFilter2[] { (MultiFilter2) f2.create(data[i][0]),
						(MultiFilter2) f2.create(data[i][1]) };
			}

			@Override
			public Object run(Object data)
			{
				MultiFilter2 f1 = ((MultiFilter2[]) data)[0];
				MultiFilter2 f2 = ((MultiFilter2[]) data)[1];
				f1.weakest((Filter) f2);
				return null;
			}

			@Override
			public void check(int i, Object result)
			{
			}

			@Override
			public int getSize()
			{
				return data.length;
			}

			@Override
			public String getName()
			{
				return "MultiFilter2";
			}
		});

		ts.execute(new TimingTask()
		{
			@Override
			public Object getData(int i)
			{
				return new MultiFilter2[] { (MultiFilter2) f2.create(data[i][0]),
						(MultiFilter2) f2.create(data[i][1]) };
			}

			@Override
			public Object run(Object data)
			{
				MultiFilter2 f1 = ((MultiFilter2[]) data)[0];
				MultiFilter2 f2 = ((MultiFilter2[]) data)[1];
				f1.weakest(f2);
				return null;
			}

			@Override
			public void check(int i, Object result)
			{
			}

			@Override
			public int getSize()
			{
				return data.length;
			}

			@Override
			public String getName()
			{
				return "MultiFilter2 direct";
			}
		});

		ts.check();

		int size = ts.repeat();
		ts.repeat(size);
		if (TestSettings.allow(LogLevel.INFO))
			ts.report(size);

		for (int i = 0; i < size; i += 2)
		{
			TimingResult slow = ts.get(-(i + 2));
			TimingResult fast = ts.get(-(i + 1));
			Assert.assertTrue(slow.getMin() > fast.getMin());
		}
	}

	private double[] random(int n, RandomGenerator r)
	{
		double[] p = new double[n];
		while (n-- > 0)
			p[n] = r.nextInt(3);
		return p;
	}

	@Test
	public void canSerialiseMultiFilter()
	{
		// Check the XStream serialisation supports inheritance
		RandomGenerator randomGenerator = TestSettings.getRandomGenerator();
		testSerialisation(new MultiFilter(0, 0, 0, 0, 0, 0, 0, 0, 0), randomGenerator);
		testSerialisation(new MultiFilter2(0, 0, 0, 0, 0, 0, 0, 0, 0), randomGenerator);
		testSerialisation(new MultiFilterCRLB(0, 0, 0, 0, 0, 0, 0, 0, 0), randomGenerator);
	}

	private void testSerialisation(MultiFilter f, RandomGenerator randomGenerator)
	{
		for (int i = 10; i-- > 0;)
		{
			MultiFilter f1 = (MultiFilter) f.create(random(f.getNumberOfParameters(), randomGenerator));
			String xml = f1.toXML();
			TestSettings.debugln(XmlUtils.prettyPrintXml(xml));
			MultiFilter f2 = (MultiFilter) Filter.fromXML(xml);
			Assert.assertTrue(f1.getClass().equals(f2.getClass()));
			Assert.assertEquals(f1, f2);
		}
	}
}
