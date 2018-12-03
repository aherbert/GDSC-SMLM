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
package uk.ac.sussex.gdsc.smlm.filters;

import java.util.Arrays;
import java.util.List;

/**
 * Identifies candidate spots (local maxima) in an image. The image is pre-processed with a collection of filters and
 * the combined height used to identify candidates.
 */
public final class JurySpotFilter extends MaximaSpotFilter
{
    private DataProcessor[] processors;

    /**
     * Constructor.
     *
     * @param search
     *            The search width for non-maximum suppression
     * @param border
     *            The border to ignore for maxima
     * @param processors
     *            The data processors
     * @throws IllegalArgumentException
     *             if processor is null
     */
    public JurySpotFilter(int search, int border, DataProcessor... processors)
    {
        super(search, border);
        if (processors == null || processors.length == 0)
            throw new IllegalArgumentException("No processors");
        for (int i = 0; i < processors.length; i++)
            if (processors[i] == null)
                throw new IllegalArgumentException("Processor " + (i + 1) + " is null");
        this.processors = processors;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAbsoluteIntensity()
    {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isWeighted()
    {
        for (final DataProcessor processor : processors)
            if (processor.isWeighted())
                return true;
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void setWeights(float[] weights, int width, int height)
    {
        for (final DataProcessor processor : processors)
            processor.setWeights(weights, width, height);
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasWeights()
    {
        for (final DataProcessor processor : processors)
            if (processor.hasWeights())
                return true;
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected Spot[] find(float[] data, int width, int height)
    {
        // Run all the processors and store the total maxima intensity at each index
        final float[] intensity = new float[width * height];
        final float[] sum = new float[intensity.length];
        for (int i = 0; i < processors.length; i++)
        {
            final float[] data2 = processors[i].process(data, width, height);
            for (int j = 0; j < sum.length; j++)
                sum[j] += data2[j];
            final int[] maxIndices = getMaxima(data2, width, height);
            for (final int index : maxIndices)
                intensity[index] += data2[index];
        }

        // Note: A simple jury using any non-zero point in the maxima intensity will work if the background
        // noise is uniform. If the noise is sloped then larger filters may result in the centre of the maxima
        // moving and the jury will return two smaller candidates in adjacent pixels. To mitigate this we get the
        // maxima again from the image where maxima where found.
        final int[] maxIndices = getMaxima(intensity, width, height);
        if (maxIndices.length == 0)
            return null;

        // Normalise the intensity across all processors
        final float divisor = (float) (1.0 / processors.length);

        int count = 0;
        final Spot[] spots = new Spot[maxIndices.length];
        for (int n = 0; n < maxIndices.length; n++)
            if (intensity[maxIndices[n]] > 0)
            {
                final int y = maxIndices[n] / width;
                final int x = maxIndices[n] % width;
                spots[count++] = new Spot(x, y, sum[maxIndices[n]] * divisor);
            }
        return Arrays.copyOf(spots, count);
    }

    /** {@inheritDoc} */
    @Override
    public float[] preprocessData(float[] data, int width, int height)
    {
        // Run all the processors and store the total maxima intensity at each index
        final float[] intensity = new float[width * height];
        final float[] sum = new float[intensity.length];
        for (int i = 0; i < processors.length; i++)
        {
            final float[] data2 = processors[i].process(data, width, height);
            for (int j = 0; j < sum.length; j++)
                sum[j] += data2[j];
        }
        final float divisor = (float) (1.0 / processors.length);
        for (int j = 0; j < sum.length; j++)
            sum[j] *= divisor;
        return sum;
    }

    /** {@inheritDoc} */
    @Override
    public JurySpotFilter clone()
    {
        final JurySpotFilter f = (JurySpotFilter) super.clone();
        // Ensure the object is duplicated and not passed by reference.
        f.processors = processors.clone();
        for (int i = 0; i < processors.length; i++)
            f.processors[i] = processors[i].clone();
        return f;
    }

    /** {@inheritDoc} */
    @Override
    public String getName()
    {
        return "Jury";
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getParameters()
    {
        final List<String> list = super.getParameters();
        for (int i = 0; i < processors.length; i++)
            list.add("Filter = " + processors[i].getDescription());
        return list;
    }

    /** {@inheritDoc} */
    @Override
    public double getSpread()
    {
        double max = processors[0].getSpread();
        for (int i = 1; i < processors.length; i++)
            max = Math.max(processors[i].getSpread(), max);
        return max;
    }
}
