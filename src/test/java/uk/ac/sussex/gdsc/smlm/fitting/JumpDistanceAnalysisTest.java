package uk.ac.sussex.gdsc.smlm.fitting;

import uk.ac.sussex.gdsc.core.utils.rng.GaussianSamplerUtils;
import uk.ac.sussex.gdsc.smlm.fitting.JumpDistanceAnalysis.JumpDistanceCumulFunction;
import uk.ac.sussex.gdsc.smlm.fitting.JumpDistanceAnalysis.JumpDistanceFunction;
import uk.ac.sussex.gdsc.smlm.fitting.JumpDistanceAnalysis.MixedJumpDistanceCumulFunction;
import uk.ac.sussex.gdsc.smlm.fitting.JumpDistanceAnalysis.MixedJumpDistanceFunction;
import uk.ac.sussex.gdsc.test.api.TestAssertions;
import uk.ac.sussex.gdsc.test.api.TestHelper;
import uk.ac.sussex.gdsc.test.api.function.DoubleDoubleBiPredicate;
import uk.ac.sussex.gdsc.test.junit5.RandomSeed;
import uk.ac.sussex.gdsc.test.junit5.SeededTest;
import uk.ac.sussex.gdsc.test.rng.RngUtils;
import uk.ac.sussex.gdsc.test.utils.TestComplexity;
import uk.ac.sussex.gdsc.test.utils.TestSettings;
import uk.ac.sussex.gdsc.test.utils.functions.FunctionUtils;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.integration.SimpsonIntegrator;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.sampling.distribution.GaussianSampler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

@SuppressWarnings({"javadoc"})
public class JumpDistanceAnalysisTest {
  private static Logger logger;

  @BeforeAll
  public static void beforeAll() {
    logger = Logger.getLogger(JumpDistanceAnalysisTest.class.getName());
  }

  @AfterAll
  public static void afterAll() {
    logger = null;
  }

  // Based on the paper: Weimann, L., Ganzinger, K.A., McColl, J., Irvine, K.L., Davis, S.J.,
  // Gay, N.J., Bryant, C.E., Klenerman, D. (2013) A Quantitative Comparison of Single-Dye
  // Tracking Analysis Tools Using Monte Carlo Simulations. PLoS One 8, Issue 5, e64287
  //
  // This paper simulated tracks using 150 particle over 30 frames with a SNR of 6. The spots
  // were fit and then MSD or Jump Distance analysis performed. This means that each image
  // could have 150*29 jumps = 4350. They compute the fit using 750 trajectories (21750 jumps)
  // and repeat this 10 times to get a mean fit. They indicate success if
  // D is within 10% of the true value (the threshold for F is not explicitly stated
  // but appears to be around 20% or less). 2 population sample used D = 0.1, 0.02.
  // Good results were obtained when F(mobile) > 20%.

  // TODO - revise this so that fitting always works and then reduce the sample size down gradually
  // to see if there is a fail limit.

  // Test MLE fitting and histogram fitting separately.
  // Is MLE fitting worth doing. Can it be made better?

  final DoubleDoubleBiPredicate deltaD = TestHelper.doublesAreClose(0.1, 0);
  final DoubleDoubleBiPredicate deltaF = TestHelper.doublesAreClose(0.2, 0);
  // Used for testing single populations
  // Used for testing dual populations:
  // 15-fold, 5-fold, 3-fold difference between pairs
  // double[] D = new double[] { 0.2, 3, 1 };
  // 5-fold difference between pairs
  // double[] D = new double[] { 0.2, 1 };
  // For proteins with mass 823 and 347 kDa the
  // difference using predicted diffusion coefficients is 3:1
  double[] D = new double[] {3, 1};

  // Commented out as this test always passes
  // @Test
  public void canIntegrateProbabilityToCumulativeWithSinglePopulation() {
    final JumpDistanceAnalysis jd = new JumpDistanceAnalysis();
    jd.setMinD(0);
    jd.setMinFraction(0);
    final SimpsonIntegrator si =
        new SimpsonIntegrator(1e-3, 1e-8, 2, SimpsonIntegrator.SIMPSON_MAX_ITERATIONS_COUNT);
    final DoubleDoubleBiPredicate equality = TestHelper.doublesAreClose(1e-2, 0);
    for (final double d : D) {
      final double[] params = new double[] {d};
      final JumpDistanceFunction fp = new JumpDistanceFunction(null, d);
      final JumpDistanceCumulFunction fc = new JumpDistanceCumulFunction(null, null, d);
      double x = d / 8;
      final UnivariateFunction func = new UnivariateFunction() {
        @Override
        public double value(double x) {
          return fp.evaluate(x, params);
        }
      };
      for (int i = 1; i < 10; i++, x *= 2) {
        final double e = fc.evaluate(x, params);
        // Integrate
        final double o = si.integrate(10000, func, 0, x);
        // logger.info(FunctionUtils.getSupplier("Integrate d=%.1f : x=%.1f, e=%f, o=%f, iter=%d,
        // eval=%d", d, x, e, o, si.getIterations(),
        // si.getEvaluations());
        TestAssertions.assertTest(e, o, equality,
            FunctionUtils.getSupplier("Failed to integrate: x=%g", x));
      }
    }
  }

  // Commented out as this test always passes
  // @Test
  public void canIntegrateProbabilityToCumulativeWithMixedPopulation() {
    final JumpDistanceAnalysis jd = new JumpDistanceAnalysis();
    jd.setMinD(0);
    jd.setMinFraction(0);
    final SimpsonIntegrator si =
        new SimpsonIntegrator(1e-3, 1e-8, 2, SimpsonIntegrator.SIMPSON_MAX_ITERATIONS_COUNT);
    final DoubleDoubleBiPredicate equality = TestHelper.doublesAreClose(1e-2, 0);
    for (final double d : D) {
      for (final double f : new double[] {0, 0.1, 0.2, 0.4, 0.7, 0.9, 1}) {
        final double[] params = new double[] {f, d, 1 - f, d * 0.1};
        final MixedJumpDistanceFunction fp = new MixedJumpDistanceFunction(null, d, 2);
        final MixedJumpDistanceCumulFunction fc =
            new MixedJumpDistanceCumulFunction(null, null, d, 2);
        double x = d / 8;
        final UnivariateFunction func = new UnivariateFunction() {
          @Override
          public double value(double x) {
            return fp.evaluate(x, params);
          }
        };
        for (int i = 1; i < 10; i++, x *= 2) {
          final double e = fc.evaluate(x, params);
          // Integrate
          final double o = si.integrate(10000, func, 0, x);
          // logger.info(FunctionUtils.getSupplier("Integrate d=%.1f, f=%.1f : x=%.1f, e=%f, o=%f,
          // iter=%d, eval=%d", d, f, x, e, o,
          // si.getIterations(), si.getEvaluations());
          TestAssertions.assertTest(e, o, equality,
              FunctionUtils.getSupplier("Failed to integrate: x=%g", x));
        }
      }
    }
  }

  // @formatter:off
  @SeededTest
  public void canFitSinglePopulationMLE(RandomSeed seed)  { fitSinglePopulation(seed, true);  }
  @SeededTest
  public void canFitSinglePopulation(RandomSeed seed) { fitSinglePopulation(seed, false); }
  // @formatter:on

  private void fitSinglePopulation(RandomSeed seed, boolean mle) {
    final UniformRandomProvider rg = RngUtils.create(seed.getSeedAsLong());
    final String title = String.format("%s Single  ", (mle) ? "MLE" : "LSQ");
    AssertionError error = null;
    NEXT_D: for (final double d : D) {
      for (int samples = 500, k = 0; k < 6; samples *= 2, k++) {
        try {
          fit(rg, title, samples, 0, new double[] {d}, new double[] {1}, mle);
          error = null;
          continue NEXT_D;
        } catch (final AssertionError ex) {
          error = ex;
        }
      }
      if (error != null) {
        throw error;
      }
    }
  }

  // @formatter:off
  @SeededTest
  public void canFitDual0_1PopulationMLE(RandomSeed seed) { fitDualPopulation(seed, true,  0.1); }
  @SeededTest
  public void canFitDual0_1Population(RandomSeed seed)    { fitDualPopulation(seed, false, 0.1); }
  @SeededTest
  public void canFitDual0_2PopulationMLE(RandomSeed seed) { fitDualPopulation(seed, true,  0.2); }
  @SeededTest
  public void canFitDual0_2Population(RandomSeed seed)    { fitDualPopulation(seed, false, 0.2); }
  @SeededTest
  public void canFitDual0_3PopulationMLE(RandomSeed seed) { fitDualPopulation(seed, true,  0.3); }
  @SeededTest
  public void canFitDual0_3Population(RandomSeed seed)    { fitDualPopulation(seed, false, 0.3); }
  @SeededTest
  public void canFitDual0_4PopulationMLE(RandomSeed seed) { fitDualPopulation(seed, true,  0.4); }
  @SeededTest
  public void canFitDual0_4Population(RandomSeed seed)    { fitDualPopulation(seed, false, 0.4); }
  @SeededTest
  public void canFitDual0_5PopulationMLE(RandomSeed seed) { fitDualPopulation(seed, true,  0.5); }
  @SeededTest
  public void canFitDual0_5Population(RandomSeed seed)    { fitDualPopulation(seed, false, 0.5); }
  @SeededTest
  public void canFitDual0_6PopulationMLE(RandomSeed seed) { fitDualPopulation(seed, true,  0.6); }
  @SeededTest
  public void canFitDual0_6Population(RandomSeed seed)    { fitDualPopulation(seed, false, 0.6); }
  @SeededTest
  public void canFitDual0_7PopulationMLE(RandomSeed seed) { fitDualPopulation(seed, true,  0.7); }
  @SeededTest
  public void canFitDual0_7Population(RandomSeed seed)    { fitDualPopulation(seed, false, 0.7); }
  @SeededTest
  public void canFitDual0_8PopulationMLE(RandomSeed seed) { fitDualPopulation(seed, true,  0.8); }
  @SeededTest
  public void canFitDual0_8Population(RandomSeed seed)    { fitDualPopulation(seed, false, 0.8); }
  @SeededTest
  public void canFitDual0_9PopulationMLE(RandomSeed seed) { fitDualPopulation(seed, true,  0.9); }
  @SeededTest
  public void canFitDual0_9Population(RandomSeed seed)    { fitDualPopulation(seed, false, 0.9); }
  // @formatter:on

  private void fitDualPopulation(RandomSeed seed, boolean mle, double fraction) {
    Assumptions.assumeTrue(TestSettings.allow(TestComplexity.MAXIMUM));
    final UniformRandomProvider rg = RngUtils.create(seed.getSeedAsLong());

    final String title = String.format("%s Dual=%.1f", (mle) ? "MLE" : "LSQ", fraction);
    AssertionError error = null;
    for (int i = 0; i < D.length; i++) {
      NEXT_D: for (int j = i + 1; j < D.length; j++) {
        for (int samples = 500, k = 0; k < 6; samples *= 2, k++) {
          try {
            fit(rg, title, samples, 0, new double[] {D[i], D[j]},
                new double[] {fraction, 1 - fraction}, mle);
            error = null;
            continue NEXT_D;
          } catch (final AssertionError ex) {
            error = ex;
          }
        }
        if (error != null) {
          throw error;
        }
      }
    }
  }

  private OutputStreamWriter out = null;

  /**
   * This is not actually a test but runs the fitting algorithm many times to collect benchmark data
   * to file.
   */
  @SeededTest
  public void canDoBenchmark(RandomSeed seed) {
    // Skip this as it is slow
    Assumptions.assumeTrue(false);
    final UniformRandomProvider rg = RngUtils.create(seed.getSeedAsLong());

    out = null;
    try {
      final FileOutputStream fos = new FileOutputStream("JumpDistanceAnalysisTest.dat");
      out = new OutputStreamWriter(fos, "UTF-8");

      // Run the fitting to produce benchmark data for a mixed population of 2
      final int n = 2;
      writeHeader(n);
      for (int repeat = 10; repeat-- > 0;) {
        resetData();
        for (final boolean mle : new boolean[] {true, false}) {
          for (int f = 1; f <= 9; f++) {
            final double fraction = f / 10.0;
            final String title = String.format("%s Dual=%.1f", (mle) ? "MLE" : "LSQ", fraction);
            for (int samples = 500, k = 0; k < 6; samples *= 2, k++) {
              for (int i = 0; i < D.length; i++) {
                for (int j = i + 1; j < D.length; j++) {
                  try {
                    fit(rg, title, samples, 0, new double[] {D[i], D[j]},
                        new double[] {fraction, 1 - fraction}, mle);
                  } catch (final AssertionError ex) {
                    // Carry on with the benchmarking
                  }
                  // If the fit had the correct N then no need to repeat
                  if (fitN == n) {
                    continue;
                  }
                  try {
                    fit(rg, title + " Fixed", samples, n, new double[] {D[i], D[j]},
                        new double[] {fraction, 1 - fraction}, mle);
                  } catch (final AssertionError ex) {
                    // Carry on with the benchmarking
                  }
                }
              }
            }
          }
        }
      }
    } catch (final Exception ex) {
      throw new AssertionError("Failed to complete benchmark", ex);
    } finally {
      closeOutput();
    }
  }

  private void closeOutput() {
    if (out == null) {
      return;
    }
    try {
      out.close();
    } catch (final Exception ex) {
      // Ignore exception
    } finally {
      out = null;
    }
  }

  // Store the fitted N to allow repeat in the benchmark with fixed N if necessary
  int fitN = 0;

  private void fit(UniformRandomProvider rg, String title, int samples, int n, double[] d,
      double[] f, boolean mle) {
    // Used for testing
    // @formatter:off
    //if (!mle) return;
    //if (mle) return;
    // For easy mixed populations
    //if (f.length == 2 && Math.min(f[0],f[1])/(f[0]+f[1]) <= 0.2) return;
    //n = 2;
    // @formatter:on

    JumpDistanceAnalysis.sort(d, f);
    final double[] jumpDistances = createData(rg, samples, d, f);
    final JumpDistanceAnalysis jd = new JumpDistanceAnalysis();
    jd.setFitRestarts(3);
    double[][] fit;
    if (n == 0) {
      jd.setMinFraction(0.05);
      jd.setMinDifference(2);
      jd.setMaxN(10);
      fit = (mle) ? jd.fitJumpDistancesMLE(jumpDistances) : jd.fitJumpDistances(jumpDistances);
    } else {
      // No validation
      jd.setMinFraction(0);
      jd.setMinDifference(0);
      fit =
          (mle) ? jd.fitJumpDistancesMLE(jumpDistances, n) : jd.fitJumpDistances(jumpDistances, n);
    }
    final double[] fitD = (fit == null) ? new double[0] : fit[0];
    final double[] fitF = (fit == null) ? new double[0] : fit[1];

    // Record results to file
    if (out != null) {
      writeResult(title, sample.d, sample.f, samples, n, d, f, mle, fitD, fitF);
    }

    fitN = fitD.length;
    AssertionError error = null;
    try {
      Assertions.assertEquals(d.length, fitD.length, "Failed to fit n");
      TestAssertions.assertArrayTest(d, fitD, deltaD, "Failed to fit d");
      TestAssertions.assertArrayTest(f, fitF, deltaF, "Failed to fit f");
    } catch (final AssertionError ex) {
      error = ex;
    } finally {
      final double[] e1 = getPercentError(d, fitD);
      final double[] e2 = getPercentError(f, fitF);
      logger.info(
          FunctionUtils.getSupplier("%s %s N=%d sample=%d, n=%d : %s = %s [%s] : %s = %s [%s]",
              (error == null) ? "+++ Pass" : "--- Fail", title, d.length, samples, n, toString(d),
              toString(fitD), toString(e1), toString(f), toString(fitF), toString(e2)));
      if (error != null) {
        throw error;
      }
    }
  }

  private void writeHeader(int size) {
    final StringBuilder sb = new StringBuilder("title");
    sb.append('\t').append("repeat");
    for (int i = 0; i < size; i++) {
      sb.append('\t').append("D").append(i);
    }
    for (int i = 0; i < size; i++) {
      sb.append('\t').append("F").append(i);
    }
    sb.append('\t').append("samples");
    sb.append('\t').append("mle");
    sb.append('\t').append("n");
    sb.append('\t').append("size");
    sb.append('\t').append("fsize");
    for (int i = 0; i < size; i++) {
      sb.append('\t').append("d").append(i);
    }
    for (int i = 0; i < size; i++) {
      sb.append('\t').append("fd").append(i);
    }
    for (int i = 0; i < size; i++) {
      sb.append('\t').append("ed").append(i);
    }
    for (int i = 0; i < size; i++) {
      sb.append('\t').append("f").append(i);
    }
    for (int i = 0; i < size; i++) {
      sb.append('\t').append("ff").append(i);
    }
    for (int i = 0; i < size; i++) {
      sb.append('\t').append("ef").append(i);
    }
    sb.append(System.getProperty("line.separator"));
    try {
      out.write(sb.toString());
    } catch (final IOException ex) {
      throw new AssertionError("Failed to write result to file", ex);
    }
  }

  private void writeResult(String title, double[] actualD, double[] actualF, int samples, int n,
      double[] d, double[] f, boolean mle, double[] fd, double[] ff) {
    final int size = d.length;
    final int fsize = fd.length;
    // Pad results if they are too small
    if (fsize < size) {
      fd = Arrays.copyOf(fd, size);
      ff = Arrays.copyOf(ff, size);
    }
    final double[] ed = getRelativeError(d, fd);
    final double[] ef = getRelativeError(f, ff);

    final StringBuilder sb = new StringBuilder(title);
    sb.append('\t').append(repeat);
    for (int i = 0; i < size; i++) {
      sb.append('\t').append(actualD[i]);
    }
    for (int i = 0; i < size; i++) {
      sb.append('\t').append(actualF[i]);
    }
    sb.append('\t').append(samples);
    sb.append('\t').append(mle);
    sb.append('\t').append(n);
    sb.append('\t').append(size);
    sb.append('\t').append(fsize);
    for (int i = 0; i < size; i++) {
      sb.append('\t').append(d[i]);
    }
    for (int i = 0; i < size; i++) {
      sb.append('\t').append(fd[i]);
    }
    for (int i = 0; i < size; i++) {
      sb.append('\t').append(ed[i]);
    }
    for (int i = 0; i < size; i++) {
      sb.append('\t').append(f[i]);
    }
    for (int i = 0; i < size; i++) {
      sb.append('\t').append(ff[i]);
    }
    for (int i = 0; i < size; i++) {
      sb.append('\t').append(ef[i]);
    }
    sb.append(System.getProperty("line.separator"));
    try {
      out.write(sb.toString());
    } catch (final IOException ex) {
      throw new AssertionError("Failed to write result to file", ex);
    }
  }

  private static double[] getPercentError(double[] e, double[] o) {
    final double[] error = new double[Math.min(e.length, o.length)];
    for (int i = 0; i < error.length; i++) {
      error[i] = 100.0 * (o[i] - e[i]) / e[i];
    }
    return error;
  }

  private static double[] getRelativeError(double[] e, double[] o) {
    final double[] error = new double[Math.min(e.length, o.length)];
    for (int i = 0; i < error.length; i++) {
      // As per the Weimann Plos One paper
      error[i] = Math.abs(o[i] - e[i]) / e[i];
    }
    // Use the relative error from the largest value
    // error[i] = uk.ac.sussex.gdsc.smlm.utils.DoubleEquality.relativeError(o[i], e[i]);
    return error;
  }

  private static String toString(double[] d) {
    if (d.length == 0) {
      return "";
    }
    if (d.length == 1) {
      return format(d[0]);
    }
    final StringBuilder sb = new StringBuilder();
    sb.append(format(d[0]));
    for (int i = 1; i < d.length; i++) {
      sb.append(',').append(format(d[i]));
    }
    return sb.toString();
  }

  private static String format(double d) {
    return String.format("%.3f", d);
  }

  class DataSample {
    double[] d, f, s;
    double[] data = null;
    int[] sample = null;

    DataSample(double[] d, double[] f) {
      this.d = d.clone();

      // Convert diffusion co-efficient into the standard deviation for the random move in each
      // dimension
      // For 1D diffusion: sigma^2 = 2D
      // sigma = sqrt(2D)
      // See: https://en.wikipedia.org/wiki/Brownian_motion#Einstein.27s_theory
      s = new double[d.length];
      double sum = 0;
      for (int i = 0; i < s.length; i++) {
        s[i] = Math.sqrt(2 * d[i]);
        sum += f[i];
      }
      this.f = new double[f.length];
      for (int i = 0; i < f.length; i++) {
        this.f[i] = f[i] / sum;
      }
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof DataSample)) {
        return super.equals(obj);
      }
      final DataSample that = (DataSample) obj;
      if (that.d.length != this.d.length) {
        return false;
      }
      for (int i = d.length; i-- > 0;) {
        if (that.d[i] != this.d[i]) {
          return false;
        }
        if (that.f[i] != this.f[i]) {
          return false;
        }
      }
      return true;
    }

    void add(double[] data2, int[] sample2) {
      if (data == null) {
        data = data2;
        sample = sample2;
        return;
      }
      final int size = data.length;
      final int newSize = size + data2.length;
      data = Arrays.copyOf(data, newSize);
      sample = Arrays.copyOf(sample, newSize);
      System.arraycopy(data2, 0, data, size, data2.length);
      System.arraycopy(sample2, 0, sample, size, sample2.length);
    }

    int getSize() {
      return (data == null) ? 0 : data.length;
    }

    double[][] getSample(UniformRandomProvider random, int size) {
      if (size > getSize()) {
        final GaussianSampler gs = GaussianSamplerUtils.createGaussianSampler(random, 0, 1);
        final int extra = size - getSize();

        // Get cumulative fraction
        final double[] c = new double[f.length];
        double sum = 0;
        for (int i = 0; i < f.length; i++) {
          sum += f[i];
          c[i] = sum;
        }

        final double[] data = new double[extra];

        // Pick the population using the fraction.
        // Do this before sampling since the nextGaussian function computes random variables
        // in pairs so we want to process all the same sample together
        final int[] sample = new int[extra];
        if (c.length > 1) {
          for (int i = 0; i < data.length; i++) {
            sample[i] = pick(c, random.nextDouble());
          }
        }
        Arrays.sort(sample);

        for (int i = 0; i < data.length; i++) {
          // Pick the population using the fraction
          final int j = sample[i];
          // Get the x/y shifts
          final double x = gs.sample() * s[j];
          final double y = gs.sample() * s[j];
          // Get the squared jump distance
          data[i] = x * x + y * y;
        }
        add(data, sample);
      }

      // Build the sample data and return the D and fractions
      final double[] data = Arrays.copyOf(this.data, size);
      final double[] d = new double[this.d.length];
      final double[] f = new double[d.length];
      for (int i = 0; i < size; i++) {
        final int j = sample[i];
        d[j] += data[i];
        f[j]++;
      }
      for (int i = 0; i < d.length; i++) {
        // 4D = MSD
        // D = MSD / 4
        d[i] = (d[i] / f[i]) / 4;
        f[i] /= size;
      }

      return new double[][] {data, d, f};
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
      return super.hashCode();
    }
  }

  static ArrayList<DataSample> samples = new ArrayList<>();
  DataSample sample = null;
  private int repeat = 0;

  private void resetData() {
    samples.clear();
    repeat++;
  }

  /**
   * Create random jump distances.
   *
   * @param n Number of jump distances
   * @param d Diffusion rate (should be ascending order of magnitude)
   * @param f Fraction of population (will be updated with the actual fractions, normalised to sum
   *        to 1)
   * @return The jump distances
   */
  private double[] createData(UniformRandomProvider rg, int n, double[] d, double[] f) {
    // Cache the data so that if we run a second test with
    // the same d and f we use the same data
    sample = new DataSample(d, f);
    final int index = samples.indexOf(sample);
    if (index != -1) {
      sample = samples.get(index);
    } else {
      samples.add(sample);
    }

    final double[][] dataSample = sample.getSample(rg, n);
    final double[] data = dataSample[0];
    final double[] d2 = dataSample[1];
    final double[] f2 = dataSample[2];

    // Update with the real values
    for (int i = 0; i < d.length; i++) {
      d[i] = d2[i];
      f[i] = f2[i];
    }

    // Debug
    // uk.ac.sussex.gdsc.core.utils.StoredDataStatistics stats = new
    // uk.ac.sussex.gdsc.core.utils.StoredDataStatistics(data);
    // uk.ac.sussex.gdsc.core.ij.new HistogramPlotBuilder(
    // "MSD",
    // stats,
    // "MSD",
    // 0,
    // 0,
    // n / 10,
    // true,
    // String.format("%s : %s : u=%f, sd=%f", toString(d), toString(f), stats.getMean(),
    // stats.getStandardDeviation()));

    return data;
  }

  private static int pick(double[] f, double nextDouble) {
    for (int i = 0; i < f.length; i++) {
      if (nextDouble < f[i]) {
        return i;
      }
    }
    return f.length - 1;
  }
}