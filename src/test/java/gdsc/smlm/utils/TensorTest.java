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
package gdsc.smlm.utils;

import org.apache.commons.math3.random.RandomGenerator;
import org.junit.Assert;
import org.junit.Test;

import gdsc.test.TestSettings;
import gdsc.test.TestSettings.LogLevel;

@SuppressWarnings({ "javadoc" })
public class TensorTest
{
	@Test
	public void canComputeTensor3D()
	{
		//@formatter:off
		float[][] data = new float[][] {
			{ 2, 1, 0, 1, 2, 1, 0, 1, 2 },
			//{ 1, 0, 0, 0, 1, 0, 0, 0, 1 },
			//{ 1, 0, 0, 0, 1, 0, 0, 0, 1 },
		};
		//@formatter:on
		Tensor3D t = new Tensor3D(data, 3, 3);
		Assert.assertTrue(t.hasDecomposition());
		double[] com = t.getCentreOfMass();
		Assert.assertArrayEquals(new double[] { 1, 1, 0 }, com, 0);
		double[] v = t.getEigenValues();
		double[][] vv = t.getEigenVectors();
		print(com, v, vv);
		for (int i = 1; i < v.length; i++)
			Assert.assertTrue(v[i - 1] >= v[i]);
	}

	private void print(double[] com, double[] v, double[][] vv)
	{
		if (TestSettings.allow(LogLevel.INFO))
		{
			System.out.printf("com = %s\n", java.util.Arrays.toString(com));
			for (int i = 0; i < v.length; i++)
				System.out.printf("[%d] %f = %s  %.2f\n", i, v[i], java.util.Arrays.toString(vv[i]),
						180.0 * Math.atan2(vv[i][1], vv[i][0]) / Math.PI);
		}
	}

	@Test
	public void canComputeTensor2D()
	{
		//@formatter:off
		// Line through [0][0], [1][1], [2][2] 
		// longest axis of object is -45 degrees
		float[] data = new float[] { 
				//1, 0, 0, 0, 1, 0, 0, 0, 1 
				2, 1, 0, 1, 2, 1, 0, 1, 2 
				//2, 0, 0, 0, 0, 0, 0, 0, 2
				};
		//@formatter:on
		Tensor2D t = new Tensor2D(data, 3, 3);
		Assert.assertTrue(t.hasDecomposition());
		double[] com = t.getCentreOfMass();
		Assert.assertArrayEquals(new double[] { 1, 1 }, com, 0);
		double[] v = t.getEigenValues();
		double[][] vv = t.getEigenVectors();
		print(com, v, vv);
		for (int i = 1; i < v.length; i++)
			Assert.assertTrue(v[i - 1] >= v[i]);
	}

	@Test
	public void canComputeSameTensor()
	{
		RandomGenerator random = TestSettings.getRandomGenerator();
		int w = 3, h = 4;
		float[] data = new float[w * h];
		for (int i = 0; i < 10; i++)
		{
			for (int j = data.length; j-- > 0;)
				data[j] = random.nextFloat();

			Tensor2D t2 = new Tensor2D(data, w, h);
			Tensor3D t3 = new Tensor3D(new float[][] { data }, w, h);

			double[] com2 = t2.getCentreOfMass();
			double[] v2 = t2.getEigenValues();
			double[][] vv2 = t2.getEigenVectors();
			double[] com3 = t3.getCentreOfMass();
			double[] v3 = t3.getEigenValues();
			double[][] vv3 = t3.getEigenVectors();
			for (int k = 0; k < 2; k++)
			{
				Assert.assertEquals(com2[k], com3[k], 0);
				Assert.assertEquals(v2[k], v3[k + 1], Math.abs(v2[k] * 1e-6));
				for (int kk = 0; kk < 2; kk++)
				{
					// Swap vector direction
					if (Math.signum(vv2[k][kk]) != Math.signum(vv3[k + 1][kk]))
					{
						vv2[k][0] = -vv2[k][0];
						vv2[k][1] = -vv2[k][1];
					}
					Assert.assertEquals(vv2[k][kk], vv3[k + 1][kk], Math.abs(vv2[k][kk] * 1e-6));
				}
			}
		}
	}
}
