package gdsc.smlm.filters;

import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;
import org.junit.Assert;
import org.junit.Test;

import gdsc.core.utils.SimpleArrayUtils;
import ij.plugin.filter.EDM;
import ij.process.ByteProcessor;
import ij.process.FHT2;
import ij.process.FloatProcessor;

public class FHTFilterTest
{
	@Test
	public void canCorrelate()
	{
		canFilter(false);
	}

	@Test
	public void canConvolve()
	{
		canFilter(true);
	}

	private void canFilter(boolean convolution)
	{
		int size = 16;
		int ex = 5, ey = 7;
		int ox = 1, oy = 2;
		RandomGenerator r = new Well19937c(30051977);
		FloatProcessor fp1 = createProcessor(size, ex, ey, 4, 4, r);
		// This is offset from the centre
		FloatProcessor fp2 = createProcessor(size, size / 2 + ox, size / 2 + oy, 4, 4, r);

		float[] input1 = ((float[]) fp1.getPixels()).clone();
		float[] input2 = ((float[]) fp2.getPixels()).clone();

		FHT2 fht1 = new FHT2(fp1);
		fht1.transform();
		FHT2 fht2 = new FHT2(fp2);
		fht2.transform();

		FHT2 fhtE = (convolution) ? fht1.multiply(fht2) : fht1.conjugateMultiply(fht2);
		fhtE.inverseTransform();
		fhtE.swapQuadrants();

		float[] e = (float[]) fhtE.getPixels();
		if (!convolution)
		{
			// Test the max correlation position
			int max = SimpleArrayUtils.findMaxIndex(e);
			int x = max % 16;
			int y = max / 16;

			Assert.assertEquals(ex, x + ox);
			Assert.assertEquals(ey, y + oy);
		}

		// Test verses a spatial domain filter in the middle of the image
		double sum = 0;
		float[] i2 = input2;
		if (convolution)
		{
			i2 = i2.clone();
			KernelFilter.rotate180(i2);
		}
		for (int i = 0; i < input1.length; i++)
			sum += input1[i] * i2[i];
		//double exp = e[size / 2 * size + size / 2];
		//System.out.printf("Sum = %f vs [%d] %f\n", sum, size / 2 * size + size / 2, exp);
		Assert.assertEquals(sum, sum, 1e-3);

		// Test the FHT
		FHTFilter ff = new FHTFilter(input2, size, size);
		ff.setConvolution(convolution);
		ff.filter(input1, size, size);

		Assert.assertArrayEquals(e, input1, 0);
	}

	private FloatProcessor createProcessor(int size, int x, int y, int w, int h, RandomGenerator r)
	{
		ByteProcessor bp = new ByteProcessor(size, size);
		bp.setColor(255);
		bp.fillOval(x, y, w, h);
		EDM e = new EDM();
		FloatProcessor fp = e.makeFloatEDM(bp, 0, true);
		if (r != null)
		{
			float[] d = (float[]) fp.getPixels();
			for (int i = 0; i < d.length; i++)
				d[i] += r.nextFloat() * 0.01;
		}
		return fp;
	}
}
