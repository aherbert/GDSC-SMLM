package uk.ac.sussex.gdsc.smlm.function;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;
import org.apache.commons.rng.UniformRandomProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import uk.ac.sussex.gdsc.core.utils.DoubleEquality;
import uk.ac.sussex.gdsc.test.BaseTimingTask;
import uk.ac.sussex.gdsc.test.TestLog;
import uk.ac.sussex.gdsc.test.TestSettings;
import uk.ac.sussex.gdsc.test.TimingService;
import uk.ac.sussex.gdsc.test.junit5.ExtraAssertions;
import uk.ac.sussex.gdsc.test.junit5.ExtraAssumptions;
import uk.ac.sussex.gdsc.test.junit5.RandomSeed;
import uk.ac.sussex.gdsc.test.junit5.SeededTest;

@SuppressWarnings({ "javadoc" })
public class ErfTest
{
    private static Logger logger;

    @BeforeAll
    public static void beforeAll()
    {
        logger = Logger.getLogger(ErfTest.class.getName());
    }

    @AfterAll
    public static void afterAll()
    {
        logger = null;
    }

    //@formatter:off
	private static abstract class BaseErf
	{
		String name;
		BaseErf(String name) { this.name = name; }
		abstract double erf(double x);
		abstract double erf(double x1, double x2);
	}
	private static class ApacheErf extends BaseErf
	{
		ApacheErf() {	super("apache erf"); }
		@Override
		double erf(double x) { return org.apache.commons.math3.special.Erf.erf(x); }
		@Override
		double erf(double x1, double x2) { return org.apache.commons.math3.special.Erf.erf(x1, x2); }
	}
	private static class Erf extends BaseErf
	{
		Erf() {	super("erf"); }
		@Override
		double erf(double x) { return uk.ac.sussex.gdsc.smlm.function.Erf.erf(x); }
		@Override
		double erf(double x1, double x2) { return uk.ac.sussex.gdsc.smlm.function.Erf.erf(x1, x2); }
	}
	private static class Erf0 extends BaseErf
	{
		Erf0() { super("erf0"); }
		@Override
		double erf(double x) { return uk.ac.sussex.gdsc.smlm.function.Erf.erf0(x); }
		@Override
		double erf(double x1, double x2) { return uk.ac.sussex.gdsc.smlm.function.Erf.erf0(x1, x2); }
	}
	private static class Erf2 extends BaseErf
	{
		Erf2() { super("erf2"); }
		@Override
		double erf(double x) { return uk.ac.sussex.gdsc.smlm.function.Erf.erf2(x); }
		@Override
		double erf(double x1, double x2) { return uk.ac.sussex.gdsc.smlm.function.Erf.erf2(x1, x2); }
	}
	//@formatter:on

    @SeededTest
    public void erf0xHasLowError(RandomSeed seed)
    {
        erfxHasLowError(seed, new Erf0(), 5e-4);
    }

    @SeededTest
    public void erfxHasLowError(RandomSeed seed)
    {
        erfxHasLowError(seed, new Erf(), 3e-7);
    }

    @SeededTest
    public void erf2xHasLowError(RandomSeed seed)
    {
        erfxHasLowError(seed, new Erf2(), 1.3e-4);
    }

    private static void erfxHasLowError(RandomSeed seed, BaseErf erf, double expected)
    {
        final UniformRandomProvider rg = TestSettings.getRandomGenerator(seed.getSeed());
        final int range = 8;
        double max = 0;

        for (int xi = -range; xi <= range; xi++)
            for (int i = 0; i < 5; i++)
            {
                final double x = xi + rg.nextDouble();
                final double o = erf.erf(x);
                final double e = org.apache.commons.math3.special.Erf.erf(x);
                final double error = Math.abs(o - e);
                if (max < error)
                    max = error;
                //logger.fine(TestLog.getSupplier("x=%f, e=%f, o=%f, error=%f", x, e, o, error);
                Assertions.assertTrue(error < expected);
            }
        logger.log(TestLog.getRecord(Level.INFO, "erfx %s max error = %g", erf.name, max));
    }

    @Test
    public void erfApachexIndistinguishableFrom1()
    {
        erfxIndistinguishableFrom1(new ApacheErf());
    }

    @Test
    public void erf0xIndistinguishableFrom1()
    {
        erfxIndistinguishableFrom1(new Erf0());
    }

    @Test
    public void erfxIndistinguishableFrom1()
    {
        erfxIndistinguishableFrom1(new Erf());
    }

    @Test
    public void erf2xIndistinguishableFrom1()
    {
        erfxIndistinguishableFrom1(new Erf2());
    }

    private static void erfxIndistinguishableFrom1(BaseErf erf)
    {
        ExtraAssumptions.assume(logger, Level.INFO);

        // Find switch using a binary search
        double lower = 1;
        double upper = 40;
        while (DoubleEquality.complement(lower, upper) > 1)
        {
            final double mid = (upper + lower) * 0.5;
            final double o = erf.erf(mid);
            if (o == 1)
                upper = mid;
            else
                lower = mid;
        }

        logger.info(TestLog.getSupplier("erfx %s indistinguishable from 1: x > %s, x >= %s", erf.name,
                Double.toString(lower), Double.toString(upper)));
    }

    @SeededTest
    public void erf0xxHasLowError(RandomSeed seed)
    {
        erfxxHasLowError(seed, new Erf0(), 4e-2);
    }

    @SeededTest
    public void erfxxHasLowError(RandomSeed seed)
    {
        erfxxHasLowError(seed, new Erf(), 7e-4);
    }

    @SeededTest
    public void erf2xxHasLowError(RandomSeed seed)
    {
        erfxxHasLowError(seed, new Erf2(), 1.1e-2);
    }

    private static void erfxxHasLowError(RandomSeed seed, BaseErf erf, double expected)
    {
        final UniformRandomProvider rg = TestSettings.getRandomGenerator(seed.getSeed());

        final int range = 3;
        double max = 0;

        for (int xi = -range; xi <= range; xi++)
            for (int xi2 = -range; xi2 <= range; xi2++)
                for (int i = 0; i < 5; i++)
                {
                    final double x = xi + rg.nextDouble();
                    for (int j = 0; j < 5; j++)
                    {
                        final double x2 = xi2 + rg.nextDouble();

                        final double o = erf.erf(x, x2);
                        final double e = org.apache.commons.math3.special.Erf.erf(x, x2);
                        final double error = Math.abs(o - e);
                        if (max < error)
                            max = error;
                        //logger.fine(TestLog.getSupplier("x=%f, x2=%f, e=%f, o=%f, error=%f", x, x2, e, o, error);
                        Assertions.assertTrue(error < expected);
                    }
                }

        logger.log(TestLog.getRecord(Level.INFO, "erfxx %s max error = %g", erf.name, max));
    }

    @Test
    public void erf0xxHasLowErrorForUnitBlocks()
    {
        erfxxHasLowErrorForUnitBlocks(new Erf0(), 5e-4);
    }

    @Test
    public void erfxxHasLowErrorForUnitBlocks()
    {
        erfxxHasLowErrorForUnitBlocks(new Erf(), 5e-7);
    }

    @Test
    public void erf2xxHasLowErrorForUnitBlocks()
    {
        erfxxHasLowErrorForUnitBlocks(new Erf2(), 1e-4);
    }

    private static void erfxxHasLowErrorForUnitBlocks(BaseErf erf, double expected)
    {
        final int range = 8;
        double max = 0;

        for (int xi = -range; xi <= range; xi++)
        {
            final double x = xi;
            final double x2 = xi + 1;
            final double o = erf.erf(x, x2);
            final double e = org.apache.commons.math3.special.Erf.erf(x, x2);
            final double error = Math.abs(o - e);
            if (max < error)
                max = error;
            //logger.fine(TestLog.getSupplier("x=%f, x2=%f, e=%f, o=%f, error=%f", x, x2, e, o, error);
            Assertions.assertTrue(error < expected);
        }

        logger.log(TestLog.getRecord(Level.INFO, "erfxx %s unit max error = %g", erf.name, max));
    }

    @Test
    public void erf0xxHasLowerErrorThanGaussianApproximationForUnitBlocks()
    {
        erfxxHasLowerErrorThanGaussianApproximationForUnitBlocks(new Erf0());
    }

    @Test
    public void erfxxHasLowerErrorThanGaussianApproximationForUnitBlocks()
    {
        erfxxHasLowerErrorThanGaussianApproximationForUnitBlocks(new Erf());
    }

    @Test
    public void erf2xxHasLowerErrorThanGaussianApproximationForUnitBlocks()
    {
        erfxxHasLowerErrorThanGaussianApproximationForUnitBlocks(new Erf2());
    }

    private static void erfxxHasLowerErrorThanGaussianApproximationForUnitBlocks(BaseErf erf)
    {
        final int range = 5;
        double max = 0, max2 = 0;

        // Standard deviation
        final double s = 1.3;
        final double twos2 = 2 * s * s;
        final double norm = 1 / (Math.PI * twos2);
        final double denom = 1.0 / (Math.sqrt(2.0) * s);

        double sum1 = 0, sum2 = 0, sum3 = 0;

        for (int x = -range; x <= range; x++)
        {
            final double o1 = 0.5 * erf.erf((x - 0.5) * denom, (x + 0.5) * denom);
            final double e1 = 0.5 * org.apache.commons.math3.special.Erf.erf((x - 0.5) * denom, (x + 0.5) * denom);
            for (int y = -range; y <= range; y++)
            {
                final double o2 = 0.5 * erf.erf((y - 0.5) * denom, (y + 0.5) * denom);
                final double e2 = 0.5 * org.apache.commons.math3.special.Erf.erf((y - 0.5) * denom, (y + 0.5) * denom);

                final double o = o1 * o2;
                final double e = e1 * e2;
                final double oo = norm * FastMath.exp(-(x * x + y * y) / twos2);

                sum1 += e;
                sum2 += o;
                sum3 += oo;

                final double absError = Math.abs(o - e);
                if (e < 1e-4 || absError < 1e-10)
                    continue;
                final double error = DoubleEquality.relativeError(o, e);
                final double error2 = DoubleEquality.relativeError(oo, e);
                if (max < error)
                    max = error;
                if (max2 < error2)
                    max2 = error2;
                //logger.fine(TestLog.getSupplier("x=%d, y=%d, e=%g, o=%g, o2=%g, error=%f, error2=%f", x, y, e, o, oo, error, error2);
                Assertions.assertTrue(error < error2);
            }
        }

        Assertions.assertTrue(sum1 > 0.999, () -> erf.name + " Gaussian 2D integral is not 1");
        Assertions.assertTrue(DoubleEquality.relativeError(sum1, sum2) < 1e-3,
                () -> erf.name + " Erf approx integral is incorrect");
        Assertions.assertTrue(DoubleEquality.relativeError(sum1, sum3) < 1e-3,
                () -> erf.name + " Gaussian approx integral is incorrect");

        logger.log(TestLog.getRecord(Level.INFO, "%s Erf approx pixel unit max error = %f", erf.name, max));
        logger.log(TestLog.getRecord(Level.INFO, "%s Gaussian approx pixel unit max error = %f", erf.name, max2));
    }

    private static class ErfTimingTask extends BaseTimingTask
    {
        BaseErf erf;
        double[] x;

        public ErfTimingTask(BaseErf erf, double[] x)
        {
            super(erf.name);
            this.erf = erf;
            this.x = x;
        }

        @Override
        public int getSize()
        {
            return 1;
        }

        @Override
        public Object getData(int i)
        {
            return null;
        }

        @Override
        public Object run(Object data)
        {
            for (int i = 0; i < x.length; i++)
                erf.erf(x[i]);
            return null;
        }
    }

    @Test
    public void erfApproxIsFaster()
    {
        ExtraAssumptions.assumeMediumComplexity();

        final int range = 5;
        final int steps = 10000;
        final double[] x = new double[steps];
        final double total = 2 * range;
        final double step = total / steps;
        for (int i = 0; i < steps; i++)
            x[i] = -range + i * step;

        final TimingService ts = new TimingService(5);
        ts.execute(new ErfTimingTask(new ApacheErf(), x));
        ts.execute(new ErfTimingTask(new Erf(), x));
        ts.execute(new ErfTimingTask(new Erf0(), x));
        ts.execute(new ErfTimingTask(new Erf2(), x));

        final int size = ts.getSize();
        ts.repeat(size);
        if (logger.isLoggable(Level.INFO))
            ts.report(logger, size);

        Assertions.assertTrue(ts.get(-3).getMean() < ts.get(-4).getMean());
    }

    @Test
    public void gaussianIntegralApproximatesErf()
    {
        final double x = 1.3, y = 2.2, s = 1.14;
        final int minx = (int) x;
        final int miny = (int) y;
        final int maxx = minx + 1;
        final int maxy = miny + 1;

        // Full integration using the Erf
        // Note: The PSF of a 2D Gaussian is described in Smith et all using a denominator
        // of (2.0 * s * s) for both x and Y directions. This is wrong. We need the
        // integral of the single Guassian in each dimension so the denomiator is (sqrt(2.0) * s).
        // See: Smith et al, (2010). Fast, single-molecule localisation that achieves
        // theoretically minimum uncertainty. Nature Methods 7, 373-375
        // (supplementary note).
        //final double denom = 1.0 / (2.0 * s * s); // As per Smith, etal (2010),

        final double denom = 1.0 / (Math.sqrt(2.0) * s);
        final double e1 = 0.5 * org.apache.commons.math3.special.Erf.erf(minx * denom, maxx * denom);
        final double e2 = 0.5 * org.apache.commons.math3.special.Erf.erf(miny * denom, maxy * denom);
        final double e = e1 * e2;

        double o = 0;
        // Numeric integration
        final double twos2 = 2 * s * s;
        final double norm = 1 / (Math.PI * twos2);
        for (int i = 0, steps = 1; i < 4; i++, steps = (int) FastMath.pow(10, i))
        {
            // Gaussian is: FastMath.exp(-(x * x + y * y) / twos2) over all x and y
            // But we can do this by separating x and y:
            // FastMath.exp(-(x * x) / twos2) * FastMath.exp(-(y * y) / twos2)

            // pre-compute
            final double[] ex = new double[steps];
            double sumey = 0;
            if (steps == 1)
            {
                // Use the actual values for x and y
                ex[0] = FastMath.exp(-(x * x) / twos2);
                sumey = FastMath.exp(-(y * y) / twos2);
            }
            else
                for (int j = 0; j < steps; j++)
                {
                    final double xx = minx + (double) j / steps;
                    final double yy = miny + (double) j / steps;
                    ex[j] = FastMath.exp(-(xx * xx) / twos2);
                    sumey += FastMath.exp(-(yy * yy) / twos2);
                }

            double sum = 0;
            for (int j = 0; j < steps; j++)
                sum += ex[j] * sumey;

            //// Check
            //double sum2 = 0;
            //for (int j = 0; j <= steps; j++)
            //{
            //	double xx = minx + (double) j / steps;
            //	for (int k = 0; k <= steps; k++)
            //	{
            //		double yy = miny + (double) k / steps;
            //		sum2 += FastMath.exp(-(xx * xx + yy * yy) / twos2);
            //	}
            //}
            //logger.fine(TestLog.getSupplier("sum=%f, sum2=%f", sum, sum2);

            final int n = steps * steps;
            o = norm * sum / n;
            logger.log(TestLog.getRecord(Level.INFO, "n=%d, e=%f, o=%f, error=%f", n, e, o, DoubleEquality.relativeError(e, o)));
        }

        ExtraAssertions.assertEqualsRelative(e, o, 1e-2);
    }

    @Test
    public void analyticErfGradientCorrectForErfApproximation()
    {
        final BaseErf erf = new Erf();
        final int range = 7;
        final int steps = 10000;
        final double step = (double) range / steps;
        final double delta = 1e-3;
        final DoubleEquality eq = new DoubleEquality(5e-4, 1e-6);
        for (int i = 0; i < steps; i++)
        {
            final double x = i * step;
            final double x1 = x + Precision.representableDelta(x, delta);
            final double x2 = x - Precision.representableDelta(x, delta);
            final double o1 = erf.erf(x1);
            final double o2 = erf.erf(x2);
            final double delta2 = x1 - x2;
            final double g = (o1 - o2) / delta2;
            final double e = uk.ac.sussex.gdsc.smlm.function.Erf.dErf_dx(x);
            if (!eq.almostEqualRelativeOrAbsolute(e, g))
                Assertions.fail(x + " : " + e + " != " + g);
        }
    }

    @Test
    public void canComputePower4()
    {
        for (int i = -10; i <= 10; i++)
            for (final double d : new double[] { 0, 0.1, 0.01, 0.001 })
            {
                final double f = i + d;
                final double e = Math.pow(f, 4);
                final double o = uk.ac.sussex.gdsc.smlm.function.Erf.pow4(f);
                ExtraAssertions.assertEqualsRelative(e, o, 1e-10, "x=%s", f);
            }
    }

    @Test
    public void canComputePower16()
    {
        for (int i = -10; i <= 10; i++)
            for (final double d : new double[] { 0, 0.1, 0.01, 0.001 })
            {
                final double f = i + d;
                final double e = Math.pow(f, 16);
                final double o = uk.ac.sussex.gdsc.smlm.function.Erf.pow16(f);
                ExtraAssertions.assertEqualsRelative(e, o, 1e-10, "x=%s", f);
            }
    }

    // See if power functions are faster

    //@formatter:off
	private static abstract class BasePow
	{
		String name;
		BasePow(String name) { this.name = name; }
		abstract double pow(double x);
	}
	private static class MathPow4 extends BasePow
	{
		MathPow4() {	super("Math pow4"); }
		@Override
		double pow(double x) { return Math.pow(x, 4); }
	}
	private static class FastMathPow4 extends BasePow
	{
		FastMathPow4() {	super("FastMath pow4"); }
		@Override
		double pow(double x) { return FastMath.pow(x, 4L); }
	}
	private static class Pow4 extends BasePow
	{
		Pow4() {	super("pow4"); }
		@Override
		double pow(double x) { return uk.ac.sussex.gdsc.smlm.function.Erf.pow4(x); }
	}
	private static class MathPow16 extends BasePow
	{
		MathPow16() {	super("Math pow16"); }
		@Override
		double pow(double x) { return Math.pow(x, 16); }
	}
	private static class FastMathPow16 extends BasePow
	{
		FastMathPow16() {	super("FastMath pow16"); }
		@Override
		double pow(double x) { return FastMath.pow(x, 16); }
	}
	private static class Pow16 extends BasePow
	{
		Pow16() {	super("pow16"); }
		@Override
		double pow(double x) { return uk.ac.sussex.gdsc.smlm.function.Erf.pow16(x); }
	}
	//@formatter:on

    private static class PowTimingTask extends BaseTimingTask
    {
        BasePow pow;
        double[] x;

        public PowTimingTask(BasePow pow, double[] x)
        {
            super(pow.name);
            this.pow = pow;
            this.x = x;
        }

        @Override
        public int getSize()
        {
            return 1;
        }

        @Override
        public Object getData(int i)
        {
            return null;
        }

        @Override
        public Object run(Object data)
        {
            for (int i = 0; i < x.length; i++)
                pow.pow(x[i]);
            return null;
        }
    }

    @Test
    public void powerApproxIsFaster()
    {
        ExtraAssumptions.assumeMediumComplexity();

        final int range = 5000;
        final int steps = 100000;
        final double[] x = new double[steps];
        final double step = range / steps;
        for (int i = 0; i < steps; i++)
            x[i] = i * step;

        final TimingService ts = new TimingService(5);
        ts.execute(new PowTimingTask(new MathPow4(), x));
        ts.execute(new PowTimingTask(new FastMathPow4(), x));
        ts.execute(new PowTimingTask(new Pow4(), x));
        ts.execute(new PowTimingTask(new MathPow16(), x));
        ts.execute(new PowTimingTask(new FastMathPow16(), x));
        ts.execute(new PowTimingTask(new Pow16(), x));

        final int size = ts.getSize();
        ts.repeat(size);
        if (logger.isLoggable(Level.INFO))
            ts.report();

        for (int i = 0; i < 2; i++)
        {
            final int j = -(1 + i * 3);
            Assertions.assertTrue(ts.get(j).getMean() < ts.get(j - 1).getMean());
            Assertions.assertTrue(ts.get(j).getMean() < ts.get(j - 2).getMean());
        }
    }
}
