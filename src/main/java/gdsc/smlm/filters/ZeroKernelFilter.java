package gdsc.smlm.filters;

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
 * Computes a convolution in the spatial domain for each point within the array. Pixels outside the array are assumed to
 * be zero.
 * <p>
 * Adapted from {@link ij.plugin.filter.Convolver}
 */
public class ZeroKernelFilter extends KernelFilter
{
	/**
	 * Instantiates a new kernel filter.
	 *
	 * @param kernel
	 *            the kernel
	 * @param kw
	 *            the kernel width (must be odd)
	 * @param kh
	 *            the kernel height (must be odd)
	 */
	public ZeroKernelFilter(float[] kernel, int kw, int kh)
	{
		super(kernel, kw, kh);
	}

	@Override
	protected void convolveData(float[] in, float[] out, final int width, final int height, int border)
	{
		final int x1 = border;
		final int y1 = border;
		final int x2 = width - border;
		final int y2 = height - border;
		final int uc = kw / 2;
		final int vc = kh / 2;
		final int xedge = width - uc;
		final int yedge = height - vc;
		for (int y = y1; y < y2; y++)
		{
			final boolean edgeY = y < vc || y >= yedge;
			for (int x = x1, c = x1 + y * width; x < x2; x++)
			{
				double sum = 0.0;
				int i = 0;
				// Determine if at the edge
				if (edgeY || x < uc || x >= xedge)
				{
					for (int v = -vc; v <= vc; v++)
					{
						// Create a safe y-index
						int yIndex = y + v;
						if (yIndex < 0 || yIndex >= height)
						{
							// Nothing to convolve so skip forward
							i += kw;
							continue;
						}
						yIndex *= width;

						for (int u = -uc; u <= uc; u++)
						{
							//if (i >= kernel.length) // work around for JIT compiler bug on Linux
							//	IJ.log("kernel index error: " + i);
							sum += getPixel(x + u, yIndex, in, width) * kernel[i++];
						}
					}
				}
				else
				{
					// Internal
					for (int v = -vc; v <= vc; v++)
					{
						for (int u = -uc, offset = x - uc + (y + v) * width; u++ <= uc;)
						{
							sum += in[offset++] * kernel[i++];
						}
					}
				}
				out[c++] = (float) (sum * scale);
			}
		}
	}

	/**
	 * Gets the pixel respecting the image boundaries.
	 *
	 * @param x
	 *            the x
	 * @param yIndex
	 *            the y index in the 2D array
	 * @param pixels
	 *            the pixels
	 * @param width
	 *            the width
	 * @return the pixel
	 */
	private static float getPixel(int x, int yIndex, float[] pixels, int width)
	{
		if (x < 0 || x >= width)
			return 0f;
		return pixels[x + yIndex];
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	public ZeroKernelFilter clone()
	{
		ZeroKernelFilter o = (ZeroKernelFilter) super.clone();
		return o;
	}
}