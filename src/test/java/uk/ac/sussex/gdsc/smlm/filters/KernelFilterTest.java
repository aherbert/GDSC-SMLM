package uk.ac.sussex.gdsc.smlm.filters;

import uk.ac.sussex.gdsc.core.utils.DoubleEquality;
import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.core.utils.RandomUtils;
import uk.ac.sussex.gdsc.test.junit5.RandomSeed;
import uk.ac.sussex.gdsc.test.junit5.SeededTest;
import uk.ac.sussex.gdsc.test.rng.RngUtils;
import uk.ac.sussex.gdsc.test.utils.BaseTimingTask;
import uk.ac.sussex.gdsc.test.utils.TestComplexity;
import uk.ac.sussex.gdsc.test.utils.TestLogUtils;
import uk.ac.sussex.gdsc.test.utils.TestSettings;
import uk.ac.sussex.gdsc.test.utils.TimingService;
import uk.ac.sussex.gdsc.test.utils.functions.FunctionUtils;

import ij.plugin.filter.Convolver;
import ij.process.FloatProcessor;

import org.apache.commons.rng.UniformRandomProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;

import java.awt.Rectangle;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings({"javadoc"})
public class KernelFilterTest {
  private static Logger logger;

  @BeforeAll
  public static void beforeAll() {
    logger = Logger.getLogger(KernelFilterTest.class.getName());
  }

  @AfterAll
  public static void afterAll() {
    logger = null;
  }

  int size = 256;
  int[] borders = {0, 1, 2, 3, 5, 10};

  static float[] createKernel(int kw, int kh) {
    // Simple linear ramp
    final float[] k = new float[kw * kh];
    final int cx = kw / 2;
    final int cy = kh / 2;
    for (int y = 0, i = 0; y < kh; y++) {
      final int dy2 = MathUtils.pow2(cy - y);
      for (int x = 0; x < kw; x++) {
        final int dx2 = MathUtils.pow2(cx - x);
        k[i++] = (float) Math.sqrt(dx2 + dy2);
      }
    }
    // Invert
    final float max = k[0];
    for (int i = 0; i < k.length; i++) {
      k[i] = max - k[i];
    }
    return k;
  }

  private abstract class FilterWrapper {
    final float[] kernel;
    final int kw, kh;
    String name;

    FilterWrapper(String name, float[] kernel, int kw, int kh) {
      this.name = name + " " + kw + "x" + kh;
      this.kernel = kernel;
      this.kw = kw;
      this.kh = kh;
    }

    String getName() {
      return name;
    }

    abstract float[] filter(float[] d, int border);

    abstract void setWeights(float[] w);
  }

  private class ConvolverWrapper extends FilterWrapper {
    Convolver kf = new Convolver();

    ConvolverWrapper(float[] kernel, int kw, int kh) {
      super(Convolver.class.getSimpleName(), kernel, kw, kh);
    }

    @Override
    float[] filter(float[] d, int border) {
      final FloatProcessor fp = new FloatProcessor(size, size, d);
      if (border > 0) {
        final Rectangle roi = new Rectangle(border, border, size - 2 * border, size - 2 * border);
        fp.setRoi(roi);
      }
      kf.convolveFloat(fp, kernel, kw, kh);
      return d;
    }

    @Override
    void setWeights(float[] w) {
      // Ignored
    }
  }

  private class KernelFilterWrapper extends FilterWrapper {
    KernelFilter kf = new KernelFilter(kernel, kw, kh);

    KernelFilterWrapper(float[] kernel, int kw, int kh) {
      super(KernelFilterTest.class.getSimpleName(), kernel, kw, kh);
    }

    @Override
    float[] filter(float[] d, int border) {
      kf.convolve(d, size, size, border);
      return d;
    }

    @Override
    void setWeights(float[] w) {
      kf.setWeights(w, size, size);
    }
  }

  private class ZeroKernelFilterWrapper extends FilterWrapper {
    ZeroKernelFilter kf = new ZeroKernelFilter(kernel, kw, kh);

    ZeroKernelFilterWrapper(float[] kernel, int kw, int kh) {
      super(ZeroKernelFilterWrapper.class.getSimpleName(), kernel, kw, kh);
    }

    @Override
    float[] filter(float[] d, int border) {
      kf.convolve(d, size, size, border);
      return d;
    }

    @Override
    void setWeights(float[] w) {
      kf.setWeights(w, size, size);
    }
  }

  @SeededTest
  public void canRotate180() {
    for (int kw = 1; kw < 3; kw++) {
      for (int kh = 1; kh < 3; kh++) {
        final float[] kernel = createKernel(kw, kh);
        final FloatProcessor fp = new FloatProcessor(kw, kh, kernel.clone());
        fp.flipHorizontal();
        fp.flipVertical();
        KernelFilter.rotate180(kernel);
        Assertions.assertArrayEquals((float[]) fp.getPixels(), kernel);
      }
    }
  }

  @SeededTest
  public void kernelFilterIsSameAsIJFilter(RandomSeed seed) {
    final int kw = 5, kh = 5;
    final float[] kernel = createKernel(kw, kh);
    filter1IsSameAsFilter2(seed, new KernelFilterWrapper(kernel, kw, kh),
        new ConvolverWrapper(kernel, kw, kh), false, 1e-2);
  }

  @SeededTest
  public void zeroKernelFilterIsSameAsIJFilter(RandomSeed seed) {
    final int kw = 5, kh = 5;
    final float[] kernel = createKernel(kw, kh);
    filter1IsSameAsFilter2(seed, new ZeroKernelFilterWrapper(kernel, kw, kh),
        new ConvolverWrapper(kernel, kw, kh), true, 1e-2);
  }

  private void filter1IsSameAsFilter2(RandomSeed seed, FilterWrapper f1, FilterWrapper f2,
      boolean internal, double tolerance) {
    final UniformRandomProvider rand = RngUtils.create(seed.getSeedAsLong());
    final float[] data = createData(rand, size, size);

    final int testBorder = (internal) ? f1.kw / 2 : 0;
    for (final int border : borders) {
      filter1IsSameAsFilter2(f1, f2, data, border, testBorder, tolerance);
    }
  }

  private void filter1IsSameAsFilter2(FilterWrapper f1, FilterWrapper f2, float[] data, int border,
      int testBorder, double tolerance) {
    final float[] e = data.clone();
    f2.filter(e, border);
    final float[] o = data.clone();
    f1.filter(o, border);

    double max = 0;
    if (testBorder == 0) {
      for (int i = 0; i < e.length; i++) {
        final double d = DoubleEquality.relativeError(e[i], o[i]);
        if (max < d) {
          max = d;
        }
      }
    } else {
      final int limit = size - testBorder;
      for (int y = testBorder; y < limit; y++) {
        for (int x = testBorder, i = y * size + x; x < limit; x++, i++) {
          final double d = DoubleEquality.relativeError(e[i], o[i]);
          if (max < d) {
            max = d;
          }
        }
      }
    }

    logger.fine(
        FunctionUtils.getSupplier("%s vs %s @ %d = %g", f1.getName(), f2.getName(), border, max));
    Assertions.assertTrue(max < tolerance);
  }

  private class MyTimingTask extends BaseTimingTask {
    FilterWrapper filter;
    float[][] data;
    int border;

    public MyTimingTask(FilterWrapper filter, float[][] data, int border) {
      super(filter.getName() + " " + border);
      this.filter = filter;
      this.data = data;
      this.border = border;
    }

    @Override
    public int getSize() {
      return data.length;
    }

    @Override
    public Object getData(int i) {
      return data[i].clone();
    }

    @Override
    public Object run(Object data) {
      final float[] d = (float[]) data;
      return filter.filter(d, border);
    }
  }

  @SeededTest
  public void floatFilterIsFasterThanIJFilter(RandomSeed seed) {
    floatFilterIsFasterThanIJFilter(seed, 5);
    floatFilterIsFasterThanIJFilter(seed, 11);
  }

  private void floatFilterIsFasterThanIJFilter(RandomSeed seed, int k) {
    Assumptions.assumeTrue(TestSettings.allow(TestComplexity.MEDIUM));
    final UniformRandomProvider rg = RngUtils.create(seed.getSeedAsLong());

    final float[][] data = new float[10][];
    for (int i = 0; i < data.length; i++) {
      data[i] = createData(rg, size, size);
    }

    final float[] kernel = createKernel(k, k);
    for (final int border : borders) {
      final TimingService ts = new TimingService();
      ts.execute(new MyTimingTask(new ConvolverWrapper(kernel, k, k), data, border));
      ts.execute(new MyTimingTask(new KernelFilterWrapper(kernel, k, k), data, border));
      ts.execute(new MyTimingTask(new ZeroKernelFilterWrapper(kernel, k, k), data, border));
      final int size = ts.getSize();
      ts.repeat();
      if (logger.isLoggable(Level.INFO)) {
        logger.info(ts.getReport(size));
      }
      logger.log(TestLogUtils.getTimingRecord(ts.get(-3), ts.get(-1)));
    }
  }

  private static float[] createData(UniformRandomProvider rg, int width, int height) {
    final float[] data = new float[width * height];
    for (int i = data.length; i-- > 0;) {
      data[i] = i;
    }

    RandomUtils.shuffle(data, rg);

    return data;
  }
}