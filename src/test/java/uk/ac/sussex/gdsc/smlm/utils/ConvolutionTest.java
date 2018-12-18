package uk.ac.sussex.gdsc.smlm.utils;

import uk.ac.sussex.gdsc.smlm.utils.Convolution.ConvolutionValueProcedure;
import uk.ac.sussex.gdsc.smlm.utils.Convolution.DoubleConvolutionValueProcedure;
import uk.ac.sussex.gdsc.test.api.TestAssertions;
import uk.ac.sussex.gdsc.test.api.TestHelper;
import uk.ac.sussex.gdsc.test.api.function.DoubleDoubleBiPredicate;
import uk.ac.sussex.gdsc.test.junit5.RandomSeed;
import uk.ac.sussex.gdsc.test.junit5.SeededTest;
import uk.ac.sussex.gdsc.test.junit5.SpeedTag;
import uk.ac.sussex.gdsc.test.rng.RngUtils;
import uk.ac.sussex.gdsc.test.utils.TestComplexity;
import uk.ac.sussex.gdsc.test.utils.TestSettings;
import uk.ac.sussex.gdsc.test.utils.functions.FunctionUtils;

import gnu.trove.list.array.TDoubleArrayList;

import org.apache.commons.math3.util.FastMath;
import org.apache.commons.rng.UniformRandomProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;

import java.util.logging.Level;
import java.util.logging.Logger;

import pl.edu.icm.jlargearrays.ConcurrencyUtils;

@SuppressWarnings({"javadoc"})
public class ConvolutionTest {
  private static Logger logger;

  @BeforeAll
  public static void beforeAll() {
    logger = Logger.getLogger(ConvolutionTest.class.getName());
  }

  @AfterAll
  public static void afterAll() {
    logger = null;
  }

  int sizeLoops = 8;
  int sLoops = 6;

  static {
    // Compare speeds when single threaded
    ConcurrencyUtils.setNumberOfThreads(1);
  }

  @SeededTest
  public void canComputeConvolution(RandomSeed seed) {
    final UniformRandomProvider random = RngUtils.create(seed.getSeedAsLong());
    int size = 10;
    final DoubleDoubleBiPredicate predicate = TestHelper.doublesAreClose(1e-6, 0);
    for (int i = 0; i < sizeLoops; i++) {
      double s = 0.5;
      for (int j = 0; j < sLoops; j++) {
        final double[] data = randomData(random, size);
        final double[] kernel = createKernel(s);
        final double[] r1 = Convolution.convolve(data, kernel);
        final double[] r1b = Convolution.convolve(kernel, data);
        final double[] r2 = Convolution.convolveFFT(data, kernel);
        final double[] r2b = Convolution.convolveFFT(kernel, data);

        Assertions.assertEquals(r1.length, r1b.length);
        Assertions.assertEquals(r1.length, r2.length);
        Assertions.assertEquals(r1.length, r2b.length);

        TestAssertions.assertArrayTest(r1, r1b, predicate, "Spatial convolution doesn't match");
        TestAssertions.assertArrayTest(r2, r2b, predicate, "FFT convolution doesn't match");

        s *= 2;
      }
      size *= 2;
    }
  }

  @SeededTest
  public void canComputeDoubleConvolution(RandomSeed seed) {
    final UniformRandomProvider random = RngUtils.create(seed.getSeedAsLong());
    int size = 10;
    for (int i = 0; i < sizeLoops; i++) {
      double s = 0.5;
      for (int j = 0; j < sLoops; j++) {
        final double[] data1 = randomData(random, size);
        final double[] data2 = randomData(random, size);
        final double[] kernel = createKernel(s);

        double[] e1, e2;
        double[][] r1;
        for (int fft = 0; fft < 2; fft++) {
          if (fft == 1) {
            e1 = Convolution.convolveFFT(kernel, data1);
            e2 = Convolution.convolveFFT(kernel, data2);
            r1 = Convolution.convolveFFT(kernel, data1, data2);
          } else {
            e1 = Convolution.convolve(kernel, data1);
            e2 = Convolution.convolve(kernel, data2);
            r1 = Convolution.convolve(kernel, data1, data2);
          }

          Assertions.assertEquals(r1.length, 2);
          Assertions.assertEquals(e1.length, r1[0].length);
          Assertions.assertEquals(e2.length, r1[1].length);

          for (int k = 0; k < e1.length; k++) {
            // Exact match
            Assertions.assertEquals(e1[k], r1[0][k]);
            Assertions.assertEquals(e2[k], r1[1][k]);
          }
        }

        s *= 2;
      }
      size *= 2;
    }
  }

  @SpeedTag
  @SeededTest
  public void doSpeedTest(RandomSeed seed) {
    Assumptions.assumeTrue(logger.isLoggable(Level.INFO));
    Assumptions.assumeTrue(TestSettings.allow(TestComplexity.HIGH));
    final UniformRandomProvider rg = RngUtils.create(seed.getSeedAsLong());

    int size = 10;
    for (int i = 0; i < sizeLoops; i++) {
      double s = 0.5;
      for (int j = 0; j < sLoops; j++) {
        speedTest(rg, size, s);
        s *= 2;
      }
      size *= 2;
    }
  }

  private static void speedTest(UniformRandomProvider rg, int size, double s) {
    final int RUNS = 1000;

    final double[] data = randomData(rg, size);
    final double[] kernel = createKernel(s);

    // Warm up
    @SuppressWarnings("unused")
    double[] r1 = Convolution.convolve(kernel, data);
    @SuppressWarnings("unused")
    double[] r2 = Convolution.convolveFFT(kernel, data);

    long t1 = System.nanoTime();
    for (int i = 0; i < RUNS; i++) {
      r1 = Convolution.convolve(kernel, data);
    }
    t1 = System.nanoTime() - t1;

    long t2 = System.nanoTime();
    for (int i = 0; i < RUNS; i++) {
      r2 = Convolution.convolveFFT(kernel, data);
    }
    t2 = System.nanoTime() - t2;

    logger.info(FunctionUtils.getSupplier("Size=%d, s=%f (%d) [%d] : %d -> %d (%f)", size, s,
        kernel.length, size * kernel.length, t1, t2, t1 / (double) t2));
  }

  @SpeedTag
  @SeededTest
  public void doDoubleSpeedTest(RandomSeed seed) {
    Assumptions.assumeTrue(logger.isLoggable(Level.INFO));
    Assumptions.assumeTrue(TestSettings.allow(TestComplexity.HIGH));
    final UniformRandomProvider rg = RngUtils.create(seed.getSeedAsLong());

    int size = 10;
    for (int i = 0; i < sizeLoops; i++) {
      double s = 0.5;
      for (int j = 0; j < sLoops; j++) {
        doubleSpeedTest(rg, size, s);
        s *= 2;
      }
      size *= 2;
    }
  }

  private static void doubleSpeedTest(UniformRandomProvider rg, int size, double s) {
    final int RUNS = 1000;

    final double[] data1 = randomData(rg, size);
    final double[] data2 = randomData(rg, size);
    final double[] kernel = createKernel(s);

    // Warm up
    @SuppressWarnings("unused")
    double[][] r1 = Convolution.convolve(kernel, data1, data2);
    @SuppressWarnings("unused")
    double[][] r2 = Convolution.convolveFFT(kernel, data1, data2);

    long t1 = System.nanoTime();
    for (int i = 0; i < RUNS; i++) {
      r1 = Convolution.convolve(kernel, data1, data2);
    }
    t1 = System.nanoTime() - t1;

    long t2 = System.nanoTime();
    for (int i = 0; i < RUNS; i++) {
      r2 = Convolution.convolveFFT(kernel, data1, data2);
    }
    t2 = System.nanoTime() - t2;

    logger.info(FunctionUtils.getSupplier("Size=%d, s=%f (%d) [%d] : %d -> %d (%f)", size, s,
        kernel.length, size * kernel.length, t1, t2, t1 / (double) t2));
  }

  @SpeedTag
  @SeededTest
  public void doSingleVsDoubleSpeedTest(RandomSeed seed) {
    Assumptions.assumeTrue(logger.isLoggable(Level.INFO));
    Assumptions.assumeTrue(TestSettings.allow(TestComplexity.HIGH));

    int size = 10;
    for (int i = 0; i < sizeLoops / 2; i++) {
      double s = 0.5;
      for (int j = 0; j < sLoops; j++) {
        singleVsDoubleSpeedTest(seed, size, s);
        s *= 2;
      }
      size *= 2;
    }
  }

  private static void singleVsDoubleSpeedTest(RandomSeed seed, int size, double s) {
    final UniformRandomProvider random = RngUtils.create(seed.getSeedAsLong());
    final int RUNS = 1000;

    final double[] data1 = randomData(random, size);
    final double[] data2 = randomData(random, size);
    final double[] kernel = createKernel(s);

    // Warm up
    @SuppressWarnings("unused")
    double[] r1;
    r1 = Convolution.convolve(kernel, data1);
    r1 = Convolution.convolve(kernel, data2);
    @SuppressWarnings("unused")
    double[][] r2 = Convolution.convolve(kernel, data1, data2);

    long t1 = System.nanoTime();
    for (int i = 0; i < RUNS; i++) {
      r1 = Convolution.convolve(kernel, data1);
      r1 = Convolution.convolve(kernel, data2);
    }
    t1 = System.nanoTime() - t1;

    long t2 = System.nanoTime();
    for (int i = 0; i < RUNS; i++) {
      r2 = Convolution.convolve(kernel, data1, data2);
    }
    t2 = System.nanoTime() - t2;

    logger.info(FunctionUtils.getSupplier("Size=%d, s=%f (%d) [%d] : %d -> %d (%f)", size, s,
        kernel.length, size * kernel.length, t1, t2, t1 / (double) t2));
  }

  @SpeedTag
  @SeededTest
  public void doSingleVsDoubleFFTSpeedTest(RandomSeed seed) {
    Assumptions.assumeTrue(logger.isLoggable(Level.INFO));
    Assumptions.assumeTrue(TestSettings.allow(TestComplexity.HIGH));

    int size = 10;
    for (int i = 0; i < sizeLoops / 2; i++) {
      double s = 0.5;
      for (int j = 0; j < sLoops; j++) {
        singleVsDoubleFFTSpeedTest(seed, size, s);
        s *= 2;
      }
      size *= 2;
    }
  }

  private static void singleVsDoubleFFTSpeedTest(RandomSeed seed, int size, double s) {
    final UniformRandomProvider random = RngUtils.create(seed.getSeedAsLong());
    final int RUNS = 1000;

    final double[] data1 = randomData(random, size);
    final double[] data2 = randomData(random, size);
    final double[] kernel = createKernel(s);

    // Warm up
    @SuppressWarnings("unused")
    double[] r1;
    r1 = Convolution.convolveFFT(kernel, data1);
    r1 = Convolution.convolveFFT(kernel, data2);
    @SuppressWarnings("unused")
    double[][] r2 = Convolution.convolveFFT(kernel, data1, data2);

    long t1 = System.nanoTime();
    for (int i = 0; i < RUNS; i++) {
      r1 = Convolution.convolveFFT(kernel, data1);
      r1 = Convolution.convolveFFT(kernel, data2);
    }
    t1 = System.nanoTime() - t1;

    long t2 = System.nanoTime();
    for (int i = 0; i < RUNS; i++) {
      r2 = Convolution.convolveFFT(kernel, data1, data2);
    }
    t2 = System.nanoTime() - t2;

    logger.info(FunctionUtils.getSupplier("Size=%d, s=%f (%d) [%d] : %d -> %d (%f)", size, s,
        kernel.length, size * kernel.length, t1, t2, t1 / (double) t2));
  }

  private static double[] randomData(UniformRandomProvider random, int size) {
    final double[] data = new double[size];
    for (int i = 0; i < size; i++) {
      data[i] = random.nextDouble();
    }
    return data;
  }

  /**
   * Create a Gaussian kernel of standard deviation s.
   *
   * @param s the standard deviation
   * @return the kernel
   */
  private static double[] createKernel(double s) {
    final int radius = (int) Math.ceil(Math.abs(s) * 4) + 1;
    final double[] kernel = new double[2 * radius + 1];
    final double norm = -0.5 / (s * s);
    for (int i = 0, j = radius, jj = radius; j < kernel.length; i++, j++, jj--) {
      kernel[j] = kernel[jj] = FastMath.exp(norm * i * i);
    }
    // Normalise
    double sum = 0;
    for (int j = 0; j < kernel.length; j++) {
      sum += kernel[j];
    }
    for (int j = 0; j < kernel.length; j++) {
      kernel[j] /= sum;
    }
    return kernel;
  }

  @SeededTest
  public void canComputeScaledConvolution(RandomSeed seed) {
    final UniformRandomProvider random = RngUtils.create(seed.getSeedAsLong());
    final TDoubleArrayList list = new TDoubleArrayList();
    int size = 10;
    for (int i = 0; i < sizeLoops; i++) {
      double s = 0.5;
      for (int j = 0; j < sLoops; j++) {
        final double[] data = randomData(random, size);
        final double[] kernel = createKernel(s);

        for (int scale = 2; scale < 5; scale++) {
          final double[] e = convolve(kernel, data, list, scale);
          final double[] o = Convolution.convolve(kernel, data, scale);
          final double[] o2 = new double[o.length];
          Convolution.convolve(kernel, data, scale, new ConvolutionValueProcedure() {
            int i = 0;

            @Override
            public boolean execute(double value) {
              o2[i++] = value;
              return true;
            }
          });

          Assertions.assertArrayEquals(e, o);
          Assertions.assertArrayEquals(e, o2);
        }

        s *= 2;
      }
      size *= 2;
    }
  }

  @SeededTest
  public void canComputeDoubleScaledConvolution(RandomSeed seed) {
    final UniformRandomProvider random = RngUtils.create(seed.getSeedAsLong());
    final TDoubleArrayList list = new TDoubleArrayList();
    int size = 10;
    for (int i = 0; i < sizeLoops / 2; i++) {
      double s = 0.5;
      for (int j = 0; j < sLoops; j++) {
        final double[] data1 = randomData(random, size);
        final double[] data2 = randomData(random, size);
        final double[] kernel = createKernel(s);

        for (int scale = 2; scale < 5; scale++) {
          final double[] e1 = convolve(kernel, data1, list, scale);
          final double[] e2 = convolve(kernel, data2, list, scale);
          final double[][] o = Convolution.convolve(kernel, data1, data2, scale);
          final double[][] o2 = new double[2][o[0].length];
          Convolution.convolve(kernel, data1, data2, scale, new DoubleConvolutionValueProcedure() {
            int i = 0;

            @Override
            public boolean execute(double value1, double value2) {
              o2[0][i] = value1;
              o2[1][i] = value2;
              i++;
              return true;
            }
          });

          Assertions.assertArrayEquals(e1, o[0]);
          Assertions.assertArrayEquals(e1, o2[0]);
          Assertions.assertArrayEquals(e2, o[1]);
          Assertions.assertArrayEquals(e2, o2[1]);
        }

        s *= 2;
      }
      size *= 2;
    }
  }

  private static double[] convolve(double[] kernel, double[] data, TDoubleArrayList list,
      int scale) {
    final double[] data1 = scale(data, list, scale);
    return Convolution.convolve(kernel, data1);
  }

  private static double[] scale(double[] data, TDoubleArrayList list, int scale) {
    list.resetQuick();
    final double[] fill = new double[scale - 1];
    list.add(data[0]);
    for (int i = 1; i < data.length; i++) {
      list.add(fill);
      list.add(data[i]);
    }
    return list.toArray();
  }

  @SeededTest
  public void canComputeScaledConvolutionWithEarlyExit(RandomSeed seed) {
    final UniformRandomProvider random = RngUtils.create(seed.getSeedAsLong());
    int size = 10;
    final int sizeLoops = 4;
    final int sLoops = 2;
    for (int i = 0; i < sizeLoops; i++) {
      double s = 0.5;
      for (int j = 0; j < sLoops; j++) {
        final double[] data = randomData(random, size);
        final double[] kernel = createKernel(s);

        for (int scale = 2; scale < 5; scale++) {
          final double[] e = Convolution.convolve(kernel, data, scale);
          final double[] o = new double[e.length];
          final int limit = data.length;
          Convolution.convolve(kernel, data, scale, new ConvolutionValueProcedure() {
            int i = 0;

            @Override
            public boolean execute(double value) {
              o[i++] = value;
              return i < limit;
            }
          });

          int k = 0;
          for (; k < limit; k++) {
            Assertions.assertEquals(e[k], o[k]);
          }
          while (k < o.length) {
            Assertions.assertEquals(0, o[k++]);
          }
        }

        s *= 2;
      }
      size *= 2;
    }
  }

  @SeededTest
  public void canComputeDoubleScaledConvolutionWithEarlyExit(RandomSeed seed) {
    final UniformRandomProvider random = RngUtils.create(seed.getSeedAsLong());
    int size = 10;
    final int sizeLoops = 4;
    final int sLoops = 2;
    for (int i = 0; i < sizeLoops; i++) {
      double s = 0.5;
      for (int j = 0; j < sLoops; j++) {
        final double[] data1 = randomData(random, size);
        final double[] data2 = randomData(random, size);
        final double[] kernel = createKernel(s);

        for (int scale = 2; scale < 5; scale++) {
          final double[][] e = Convolution.convolve(kernel, data1, data2, scale);
          final double[][] o = new double[2][e[0].length];
          final int limit = data1.length;
          Convolution.convolve(kernel, data1, data2, scale, new DoubleConvolutionValueProcedure() {
            int i = 0;

            @Override
            public boolean execute(double value1, double value2) {
              o[0][i] = value1;
              o[1][i] = value1;
              i++;
              return i < limit;
            }
          });

          int k = 0;
          for (; k < limit; k++) {
            Assertions.assertEquals(e[0][k], o[0][k]);
            Assertions.assertEquals(e[0][k], o[1][k]);
          }
          while (k < o.length) {
            Assertions.assertEquals(0, o[0][k]);
            Assertions.assertEquals(0, o[1][k]);
            k++;
          }
        }

        s *= 2;
      }
      size *= 2;
    }
  }

  @SpeedTag
  @SeededTest
  public void doScaledSpeedTest(RandomSeed seed) {
    Assumptions.assumeTrue(logger.isLoggable(Level.INFO));
    Assumptions.assumeTrue(TestSettings.allow(TestComplexity.HIGH));

    int size = 10;
    for (int scale = 4; scale <= 8; scale *= 2) {
      for (int i = 0; i < 4; i++) {
        double s = 0.5;
        for (int j = 0; j < 4; j++) {
          doScaledSpeedTest(seed, size, s, scale);
          s *= 2;
        }
        size *= 2;
      }
    }
  }

  private static void doScaledSpeedTest(RandomSeed seed, int size, double s, int scale) {
    final UniformRandomProvider random = RngUtils.create(seed.getSeedAsLong());
    final int RUNS = 100;

    final double[] data1 = randomData(random, size);
    final double[] kernel = createKernel(s);
    final TDoubleArrayList list = new TDoubleArrayList();

    // Warm up
    convolve(kernel, data1, list, scale);
    Convolution.convolve(kernel, data1, scale);

    long t1 = System.nanoTime();
    for (int i = 0; i < RUNS; i++) {
      convolve(kernel, data1, list, scale);
    }
    t1 = System.nanoTime() - t1;

    long t2 = System.nanoTime();
    for (int i = 0; i < RUNS; i++) {
      Convolution.convolve(kernel, data1, scale);
    }
    t2 = System.nanoTime() - t2;

    logger.info(FunctionUtils.getSupplier("Size=%d, s=%f, scale=%d (%d) [%d] : %d -> %d (%f)", size,
        s, scale, kernel.length, size * kernel.length, t1, t2, t1 / (double) t2));
  }
}