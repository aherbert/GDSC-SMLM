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
import org.junit.Assert;
import org.junit.Test;

import gdsc.test.TestSettings;

public class PeakResultTest
{
	@Test
	public void sameResultsIsEqual()
	{
		final RandomGenerator r = TestSettings.getRandomGenerator();
		PeakResult[] r1 = createResults(r, 1, 5, false, false, false, false);
		PeakResult p = r1[0];
		Assert.assertTrue("Same object", PeakResult.equals(p, p));
		Assert.assertTrue("Null object", PeakResult.equals(null, null));
	}

	@Test
	public void sameResultsAreEqual()
	{
		RandomGenerator r;
		int size = 10;
		int n = 5;
		boolean[] both = { true, false };
		for (boolean withDeviations : both)
			for (boolean withId : both)
				for (boolean withEndFrame : both)
					for (boolean withPrecision : both)
					{
						r = TestSettings.getRandomGenerator();
						PeakResult[] r1 = createResults(r, size, n, withDeviations, withId, withEndFrame,
								withPrecision);
						r = TestSettings.getRandomGenerator();
						PeakResult[] r2 = createResults(r, size, n, withDeviations, withId, withEndFrame,
								withPrecision);
						for (int i = 0; i < r1.length; i++)
							Assert.assertTrue(PeakResult.equals(r1[i], r2[i]));
					}
	}

	@Test
	public void differentResultsAreNotEqual()
	{
		RandomGenerator r;
		int size = 1;
		int n = 5;
		boolean[] both = { true, false };
		for (boolean withDeviations : both)
			for (boolean withId : both)
				for (boolean withEndFrame : both)
					for (boolean withPrecision : both)
					{
						r = TestSettings.getRandomGenerator();
						PeakResult[] r1 = createResults(r, size, n, withDeviations, withId, withEndFrame,
								withPrecision);
						PeakResult[] r2 = createResults(r, size, n, withDeviations, withId, withEndFrame,
								withPrecision);
						for (int i = 0; i < r1.length; i++)
							Assert.assertFalse(PeakResult.equals(r1[i], r2[i]));
					}
	}

	private PeakResult[] createResults(RandomGenerator r, int size, int n, boolean withDeviations, boolean withId,
			boolean withEndFrame, boolean withPrecision)
	{
		final ArrayPeakResultStore store = new ArrayPeakResultStore(10);
		while (size-- > 0)
		{
			float[] params = createParams(n, r);
			float[] paramsDev = (withDeviations) ? createParams(n, r) : null;
			AttributePeakResult p = new AttributePeakResult(r.nextInt(), r.nextInt(), r.nextInt(), r.nextFloat(),
					r.nextDouble(), r.nextFloat(), r.nextFloat(), params, paramsDev);
			if (withId)
				p.setId(r.nextInt());
			if (withEndFrame)
				//p.setEndFrame(p.getFrame() +  1 + r.nextInt(5));
				p.setEndFrame(r.nextInt());
			if (withPrecision)
				p.setPrecision(r.nextDouble());
			store.add(p);
		}
		return store.toArray();
	}

	private float[] createParams(int n, RandomGenerator r)
	{
		float[] p = new float[n];
		while (n-- > 0)
			p[n] = r.nextFloat();
		return p;
	}
}