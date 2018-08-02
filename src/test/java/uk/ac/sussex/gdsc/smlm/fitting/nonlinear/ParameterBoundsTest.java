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
package uk.ac.sussex.gdsc.smlm.fitting.nonlinear;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import uk.ac.sussex.gdsc.smlm.function.FakeGradientFunction;
import uk.ac.sussex.gdsc.test.junit5.ExtraAssertions;

@SuppressWarnings({ "javadoc" })
public class ParameterBoundsTest
{
    @Test
    public void canUpperBoundParameter()
    {
        canBoundParameter(1);
    }

    @Test
    public void canLowerBoundParameter()
    {
        canBoundParameter(-1);
    }

    private static void canBoundParameter(double s)
    {
        final ParameterBounds b = new ParameterBounds(new FakeGradientFunction(1, 1, 1, 1, 1));
        if (s < 0)
            b.setBounds(new double[] { 2 * s }, null);
        else
            b.setBounds(null, new double[] { 2 * s });
        final double[] a1 = new double[1];
        final double[] a2 = new double[1];
        final double[] step = new double[] { s };
        b.applyBounds(a1, step, a2);
        Assertions.assertArrayEquals(a2, new double[] { 1 * s }, "Step 1");
        b.applyBounds(a2, step, a1);
        Assertions.assertArrayEquals(a1, new double[] { 2 * s }, "Step 2");
        b.applyBounds(a1, step, a2);
        // Should be bounded
        Assertions.assertArrayEquals(a2, new double[] { 2 * s }, "Step 3");
    }

    @Test
    public void canDoubleBoundParameter()
    {
        final ParameterBounds b = new ParameterBounds(new FakeGradientFunction(1, 1, 1, 1, 1));
        final double s = 2;
        b.setBounds(new double[] { -s }, new double[] { s });
        final double[] a1 = new double[1];
        final double[] a2 = new double[1];
        b.applyBounds(a1, new double[] { 10 }, a2);
        Assertions.assertArrayEquals(a2, new double[] { s }, "Step 10");

        b.applyBounds(a1, new double[] { -10 }, a2);
        Assertions.assertArrayEquals(a2, new double[] { -s }, "Step -10");
    }

    @Test
    public void canStepParameter()
    {
        canStepParameter(0.5);
        canStepParameter(1);
        canStepParameter(-0.5);
        canStepParameter(-1);
    }

    private static void canStepParameter(double s)
    {
        final ParameterBounds b = new ParameterBounds(new FakeGradientFunction(1, 1, 1, 1, 1));
        double[] a1 = new double[1];
        double[] a2 = new double[1];
        double[] tmp;
        final double[] step = new double[] { s };
        for (int i = 1; i <= 10; i++)
        {
            b.applyBounds(a1, step, a2);
            ExtraAssertions.assertArrayEquals(a2, new double[] { i * s }, "Step %d", i);
            tmp = a1;
            a1 = a2;
            a2 = tmp;
        }
    }
}
