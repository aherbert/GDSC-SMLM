package uk.ac.sussex.gdsc.smlm.filters;

import uk.ac.sussex.gdsc.core.utils.FloatEquality;
import uk.ac.sussex.gdsc.test.junit5.RandomSeed;
import uk.ac.sussex.gdsc.test.junit5.SeededTest;
import uk.ac.sussex.gdsc.test.junit5.SpeedTag;
import uk.ac.sussex.gdsc.test.rng.RngUtils;
import uk.ac.sussex.gdsc.test.utils.TestComplexity;
import uk.ac.sussex.gdsc.test.utils.TestLogUtils;
import uk.ac.sussex.gdsc.test.utils.TestSettings;

import org.apache.commons.rng.UniformRandomProvider;
import org.junit.jupiter.api.Assumptions;

import java.util.ArrayList;
import java.util.Arrays;

@SuppressWarnings({"deprecation", "javadoc"})
public class AverageFilterTest extends AbstractFilterTest {
  private final int InternalITER3 = 500;
  private final int InternalITER = 50;
  private final int ITER3 = 200;
  private final int ITER = 20;

  /**
   * Do a simple and stupid mean filter.
   *
   * @param data the data
   * @param maxx the maxx
   * @param maxy the maxy
   * @param boxSize the box size
   */
  public static void average(float[] data, int maxx, int maxy, float boxSize) {
    if (boxSize <= 0) {
      return;
    }

    final int n = (int) Math.ceil(boxSize);
    final int size = 2 * n + 1;
    final float[] weight = new float[size];
    Arrays.fill(weight, 1);
    if (boxSize != n) {
      weight[0] = weight[weight.length - 1] = boxSize - (n - 1);
    }

    float norm = 0;
    for (int yy = 0; yy < size; yy++) {
      for (int xx = 0; xx < size; xx++) {
        norm += weight[yy] * weight[xx];
      }
    }
    norm = (float) (1.0 / norm);

    final float[] out = new float[data.length];

    for (int y = 0; y < maxy; y++) {
      for (int x = 0; x < maxx; x++) {
        float sum = 0;
        for (int yy = 0; yy < size; yy++) {
          int yyy = y + yy - n;
          if (yyy < 0) {
            yyy = 0;
          }
          if (yyy >= maxy) {
            yyy = maxy - 1;
          }
          for (int xx = 0; xx < size; xx++) {
            int xxx = x + xx - n;
            if (xxx < 0) {
              xxx = 0;
            }
            if (xxx >= maxx) {
              xxx = maxx - 1;
            }
            final int index = yyy * maxx + xxx;
            sum += data[index] * weight[yy] * weight[xx];
          }
        }
        out[y * maxx + x] = sum * norm;
      }
    }
    System.arraycopy(out, 0, data, 0, out.length);
  }

  /**
   * Used to test the filter methods calculate the correct result.
   */
  private abstract class DataFilter {
    final String name;
    final boolean isInterpolated;

    public DataFilter(String name, boolean isInterpolated) {
      this.name = name;
      this.isInterpolated = isInterpolated;
    }

    AverageFilter f = new AverageFilter();

    public abstract void filter(float[] data, int width, int height, float boxSize);

    public abstract void filterInternal(float[] data, int width, int height, float boxSize);
  }

  private static void averageIsCorrect(UniformRandomProvider rg, int width, int height,
      float boxSize, boolean internal, DataFilter filter) {
    final float[] data1 = createData(rg, width, height);
    final float[] data2 = data1.clone();
    final FloatEquality eq = new FloatEquality(5e-5f, 1e-10f);

    AverageFilterTest.average(data1, width, height, boxSize);
    if (internal) {
      filter.filterInternal(data2, width, height, boxSize);
      floatArrayEquals(eq, data1, data2, width, height, boxSize,
          "Internal arrays do not match: [%dx%d] @ %.1f", width, height, boxSize);
    } else {
      filter.filter(data2, width, height, boxSize);
      floatArrayEquals(eq, data1, data2, width, height, 0, "Arrays do not match: [%dx%d] @ %.1f",
          width, height, boxSize);
    }
  }

  private static void checkIsCorrect(RandomSeed seed, DataFilter filter) {
    final UniformRandomProvider rg = RngUtils.create(seed.getSeedAsLong());
    for (final int width : primes) {
      for (final int height : primes) {
        for (final float boxSize : boxSizes) {
          for (final boolean internal : checkInternal) {
            averageIsCorrect(rg, width, height, boxSize, internal, filter);
            if (filter.isInterpolated) {
              averageIsCorrect(rg, width, height, boxSize - 0.3f, internal, filter);
              averageIsCorrect(rg, width, height, boxSize - 0.6f, internal, filter);
            }
          }
        }
      }
    }
  }

  @SeededTest
  public void blockAverageIsCorrect(RandomSeed seed) {
    final DataFilter filter = new DataFilter("block", true) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.blockAverage(data, width, height, boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.blockAverageInternal(data, width, height, boxSize);
      }
    };
    checkIsCorrect(seed, filter);
  }

  @SeededTest
  public void stripedBlockAverageIsCorrect(RandomSeed seed) {
    final DataFilter filter = new DataFilter("stripedBlock", true) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage(data, width, height, boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageInternal(data, width, height, boxSize);
      }
    };
    checkIsCorrect(seed, filter);
  }

  @SeededTest
  public void rollingBlockAverageIsCorrect(RandomSeed seed) {
    final DataFilter filter = new DataFilter("rollingBlock", false) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.rollingBlockAverage(data, width, height, (int) boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.rollingBlockAverageInternal(data, width, height, (int) boxSize);
      }
    };
    checkIsCorrect(seed, filter);
  }

  private void speedTest(RandomSeed seed, DataFilter fast, DataFilter slow) {
    speedTest(seed, fast, slow, boxSizes);
  }

  private void speedTest(RandomSeed seed, DataFilter fast, DataFilter slow, int[] testBoxSizes) {
    // These test a deprecated filter
    Assumptions.assumeTrue(TestSettings.allow(TestComplexity.VERY_HIGH));

    ArrayList<float[]> dataSet = getSpeedData(seed, ITER3);

    final ArrayList<Long> fastTimes = new ArrayList<>();

    final float[] boxSizes = new float[testBoxSizes.length];
    final float offset = (fast.isInterpolated && slow.isInterpolated) ? 0.3f : 0;
    for (int i = 0; i < boxSizes.length; i++) {
      boxSizes[i] = testBoxSizes[i] - offset;
    }

    // Initialise
    for (final float boxSize : boxSizes) {
      fast.filter(dataSet.get(0).clone(), primes[0], primes[0], boxSize);
      slow.filter(dataSet.get(0).clone(), primes[0], primes[0], boxSize);
    }

    for (final float boxSize : boxSizes) {
      final int iter = (boxSize == 1) ? ITER3 : ITER;
      for (final int width : primes) {
        for (final int height : primes) {
          dataSet = getSpeedData(seed, iter);

          final long start = System.nanoTime();
          for (final float[] data : dataSet) {
            fast.filter(data, width, height, boxSize);
          }
          final long time = System.nanoTime() - start;
          fastTimes.add(time);
        }
      }
    }

    long slowTotal = 0;
    long fastTotal = 0;
    int index = 0;
    for (final float boxSize : boxSizes) {
      final int iter = (boxSize == 1) ? ITER3 : ITER;
      long boxSlowTotal = 0;
      long boxFastTotal = 0;
      for (final int width : primes) {
        for (final int height : primes) {
          dataSet = getSpeedData(seed, iter);

          final long start = System.nanoTime();
          for (final float[] data : dataSet) {
            slow.filter(data, width, height, boxSize);
          }
          final long time = System.nanoTime() - start;

          final long fastTime = fastTimes.get(index++);
          slowTotal += time;
          fastTotal += fastTime;
          boxSlowTotal += time;
          boxFastTotal += fastTime;
          if (debug) {
            logger.fine(() -> String.format("%s [%dx%d] @ %.1f : %d => %s %d = %.2fx", slow.name,
                width, height, boxSize, time, fast.name, fastTime, speedUpFactor(time, fastTime)));
          }
        }
      }
      // if (debug)
      logger.log(TestLogUtils.getStageTimingRecord(slow.name + " " + boxSize, boxSlowTotal,
          fast.name, boxFastTotal));
    }
    logger.log(TestLogUtils.getTimingRecord(slow.name, slowTotal, fast.name, fastTotal));
  }

  private void speedTestInternal(RandomSeed seed, DataFilter fast, DataFilter slow) {
    speedTestInternal(seed, fast, slow, boxSizes);
  }

  private void speedTestInternal(RandomSeed seed, DataFilter fast, DataFilter slow,
      int[] testBoxSizes) {
    // These test a deprecated filter
    Assumptions.assumeTrue(TestSettings.allow(TestComplexity.VERY_HIGH));

    ArrayList<float[]> dataSet = getSpeedData(seed, InternalITER3);

    final ArrayList<Long> fastTimes = new ArrayList<>();

    final float[] boxSizes = new float[testBoxSizes.length];
    final float offset = (fast.isInterpolated && slow.isInterpolated) ? 0.3f : 0;
    for (int i = 0; i < boxSizes.length; i++) {
      boxSizes[i] = testBoxSizes[i] - offset;
    }

    // Initialise
    for (final float boxSize : boxSizes) {
      fast.filterInternal(dataSet.get(0).clone(), primes[0], primes[0], boxSize);
      slow.filterInternal(dataSet.get(0).clone(), primes[0], primes[0], boxSize);
    }

    for (final float boxSize : boxSizes) {
      final int iter = (boxSize == 1) ? InternalITER3 : InternalITER;
      for (final int width : primes) {
        for (final int height : primes) {
          dataSet = getSpeedData(seed, iter);

          final long start = System.nanoTime();
          for (final float[] data : dataSet) {
            fast.filterInternal(data, width, height, boxSize);
          }
          final long time = System.nanoTime() - start;
          fastTimes.add(time);
        }
      }
    }

    long slowTotal = 0;
    long fastTotal = 0;
    int index = 0;
    for (final float boxSize : boxSizes) {
      final int iter = (boxSize == 1) ? InternalITER3 : InternalITER;
      long boxSlowTotal = 0;
      long boxFastTotal = 0;
      for (final int width : primes) {
        for (final int height : primes) {
          dataSet = getSpeedData(seed, iter);

          final long start = System.nanoTime();
          for (final float[] data : dataSet) {
            slow.filterInternal(data, width, height, boxSize);
          }
          final long time = System.nanoTime() - start;

          final long fastTime = fastTimes.get(index++);
          slowTotal += time;
          fastTotal += fastTime;
          boxSlowTotal += time;
          boxFastTotal += fastTime;
          if (debug) {
            logger.fine(() -> String.format("Internal %s [%dx%d] @ %.1f : %d => %s %d = %.2fx",
                slow.name, width, height, boxSize, time, fast.name, fastTime,
                speedUpFactor(time, fastTime)));
          }
        }
      }
      // if (debug)
      logger.log(TestLogUtils.getStageTimingRecord("Internal " + slow.name + " " + boxSize,
          boxSlowTotal, fast.name, boxFastTotal));
    }
    logger.log(
        TestLogUtils.getTimingRecord("Internal " + slow.name, slowTotal, fast.name, fastTotal));
  }

  @SpeedTag
  @SeededTest
  public void stripedBlockIsFasterThanBlock(RandomSeed seed) {
    final DataFilter slow = new DataFilter("block", false) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.blockAverage(data, width, height, (int) boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.blockAverageInternal(data, width, height, (int) boxSize);
      }
    };
    final DataFilter fast = new DataFilter("stripedBlock", false) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage(data, width, height, (int) boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageInternal(data, width, height, (int) boxSize);
      }
    };

    speedTest(seed, fast, slow);
    speedTestInternal(seed, fast, slow);
  }

  @SpeedTag
  @SeededTest
  public void interpolatedStripedBlockIsFasterThanBlock(RandomSeed seed) {
    final DataFilter slow = new DataFilter("block", true) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.blockAverage(data, width, height, boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.blockAverageInternal(data, width, height, boxSize);
      }
    };
    final DataFilter fast = new DataFilter("stripedBlock", true) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage(data, width, height, boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageInternal(data, width, height, boxSize);
      }
    };

    speedTest(seed, fast, slow);
    speedTestInternal(seed, fast, slow);
  }

  @SpeedTag
  @SeededTest
  public void rollingBlockIsFasterThanBlock(RandomSeed seed) {
    final DataFilter slow = new DataFilter("block", false) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.blockAverage(data, width, height, (int) boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.blockAverageInternal(data, width, height, (int) boxSize);
      }
    };
    final DataFilter fast = new DataFilter("rollingBlock", false) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.rollingBlockAverage(data, width, height, (int) boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.rollingBlockAverageInternal(data, width, height, (int) boxSize);
      }
    };

    speedTest(seed, fast, slow);
    speedTestInternal(seed, fast, slow);
  }

  @SpeedTag
  @SeededTest
  public void rollingBlockIsFasterThanStripedBlock(RandomSeed seed) {
    final DataFilter slow = new DataFilter("stripedBlock", false) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage(data, width, height, (int) boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageInternal(data, width, height, (int) boxSize);
      }
    };
    final DataFilter fast = new DataFilter("rollingBlock", false) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.rollingBlockAverage(data, width, height, (int) boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.rollingBlockAverageInternal(data, width, height, (int) boxSize);
      }
    };

    speedTest(seed, fast, slow);
    speedTestInternal(seed, fast, slow);
  }

  @SpeedTag
  @SeededTest
  public void stripedBlock3x3IsFasterThanStripedBlockNxN(RandomSeed seed) {
    final DataFilter slow = new DataFilter("stripedBlockNxN", false) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageNxN(data, width, height, (int) boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageNxNInternal(data, width, height, (int) boxSize);
      }
    };
    final DataFilter fast = new DataFilter("stripedBlock3x3", false) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage3x3(data, width, height);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage3x3Internal(data, width, height);
      }
    };

    final int[] testBoxSizes = new int[] {1};
    speedTest(seed, fast, slow, testBoxSizes);
    speedTestInternal(seed, fast, slow, testBoxSizes);
  }

  @SpeedTag
  @SeededTest
  public void interpolatedStripedBlock3x3IsFasterThanStripedBlockNxN(RandomSeed seed) {
    final DataFilter slow = new DataFilter("stripedBlockNxN", true) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageNxN(data, width, height, boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageNxNInternal(data, width, height, boxSize);
      }
    };
    final DataFilter fast = new DataFilter("stripedBlock3x3", true) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage3x3(data, width, height, boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage3x3Internal(data, width, height, boxSize);
      }
    };

    final int[] testBoxSizes = new int[] {1};
    speedTest(seed, fast, slow, testBoxSizes);
    speedTestInternal(seed, fast, slow, testBoxSizes);
  }

  @SpeedTag
  @SeededTest
  public void stripedBlock5x5IsFasterThanStripedBlockNxN(RandomSeed seed) {
    final DataFilter slow = new DataFilter("stripedBlockNxN", false) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageNxN(data, width, height, (int) boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageNxNInternal(data, width, height, (int) boxSize);
      }
    };
    final DataFilter fast = new DataFilter("stripedBlock5x5", false) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage5x5(data, width, height);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage5x5Internal(data, width, height);
      }
    };

    final int[] testBoxSizes = new int[] {2};
    speedTest(seed, fast, slow, testBoxSizes);
    speedTestInternal(seed, fast, slow, testBoxSizes);
  }

  @SpeedTag
  @SeededTest
  public void interpolatedStripedBlock5x5IsFasterThanStripedBlockNxN(RandomSeed seed) {
    final DataFilter slow = new DataFilter("stripedBlockNxN", true) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageNxN(data, width, height, boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageNxNInternal(data, width, height, boxSize);
      }
    };
    final DataFilter fast = new DataFilter("stripedBlock5x5", true) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage5x5(data, width, height, boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage5x5Internal(data, width, height, boxSize);
      }
    };

    final int[] testBoxSizes = new int[] {2};
    speedTest(seed, fast, slow, testBoxSizes);
    speedTestInternal(seed, fast, slow, testBoxSizes);
  }

  @SpeedTag
  @SeededTest
  public void stripedBlock7x7IsFasterThanStripedBlockNxN(RandomSeed seed) {
    final DataFilter slow = new DataFilter("stripedBlockNxN", false) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageNxN(data, width, height, (int) boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageNxNInternal(data, width, height, (int) boxSize);
      }
    };
    final DataFilter fast = new DataFilter("stripedBlock7x7", false) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage7x7(data, width, height);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage7x7Internal(data, width, height);
      }
    };

    final int[] testBoxSizes = new int[] {3};
    speedTest(seed, fast, slow, testBoxSizes);
    speedTestInternal(seed, fast, slow, testBoxSizes);
  }

  @SpeedTag
  @SeededTest
  public void interpolatedStripedBlock7x7IsFasterThanStripedBlockNxN(RandomSeed seed) {
    final DataFilter slow = new DataFilter("stripedBlockNxN", true) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageNxN(data, width, height, boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverageNxNInternal(data, width, height, boxSize);
      }
    };
    final DataFilter fast = new DataFilter("stripedBlock7x7", true) {
      @Override
      public void filter(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage7x7(data, width, height, boxSize);
      }

      @Override
      public void filterInternal(float[] data, int width, int height, float boxSize) {
        f.stripedBlockAverage7x7Internal(data, width, height, boxSize);
      }
    };

    final int[] testBoxSizes = new int[] {3};
    speedTest(seed, fast, slow, testBoxSizes);
    speedTestInternal(seed, fast, slow, testBoxSizes);
  }
}