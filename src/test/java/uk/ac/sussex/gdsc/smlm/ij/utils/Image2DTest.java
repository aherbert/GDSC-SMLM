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

package uk.ac.sussex.gdsc.smlm.ij.utils;

import ij.process.ImageProcessor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"javadoc"})
public abstract class Image2DTest {
  protected abstract Image2D createData(int w, int h);

  protected abstract Image2D createEmptyData(int w, int h);

  @Test
  public void canCrop() {
    canCrop(3, 4, 5, 6);
    canCrop(3, 4, 1, 1);
    canCrop(0, 0, 1, 2);
  }

  private void canCrop(int x, int y, int w, int h) {
    final Image2D image = createData(x + w + 1, y + h + 1);
    final ImageProcessor stack = image.getImageProcessor();

    final Image2D croppedData = image.crop(x, y, w, h);
    Assertions.assertEquals(croppedData.getWidth(), w);
    Assertions.assertEquals(croppedData.getHeight(), h);

    final Image2D croppedData2 = FloatImage2D.crop(stack, x, y, w, h, null);
    assertEquals(croppedData, croppedData2);

    final ImageProcessor croppedStack = image.cropToProcessor(x, y, w, h);
    Assertions.assertEquals(croppedStack.getWidth(), w);
    Assertions.assertEquals(croppedStack.getHeight(), h);

    assertEquals(croppedData, new FloatImage2D(croppedStack));

    // Test it is the correct region
    ImageProcessor fp = image.getImageProcessor();

    fp.setRoi(x, y, w, h);
    fp = fp.crop();
    final float[] e = (float[]) fp.getPixels();

    // Compare to the cropped stack
    final float[] o = (float[]) croppedStack.getPixels();
    Assertions.assertArrayEquals(e, o);

    // Compare to the cropped data
    for (int i = 0; i < e.length; i++) {
      Assertions.assertEquals(e[i], croppedData.get(i));
    }
  }

  @Test
  public void canInsert() {
    canInsert(3, 4, 5, 6);
    canInsert(3, 4, 1, 1);
    canInsert(0, 0, 1, 1);
  }

  private void canInsert(int x, int y, int w, int h) {
    // This test assumes that copy and crop work!
    for (final int pad : new int[] {0, 1}) {
      final Image2D image = createData(x + w + pad, y + h + pad);
      final Image2D image2 = image.copy();
      final Image2D image3 = image.copy();

      final Image2D blank = createEmptyData(w, h);

      image.insert(x, y, blank);

      Image2D croppedData = image.crop(x, y, w, h);

      assertEquals(croppedData, blank);

      final ImageProcessor blankStack = blank.getImageProcessor();
      image2.insert(x, y, blankStack);

      croppedData = image2.crop(x, y, w, h);

      assertEquals(croppedData, blank);

      image3.insert(x, y, blankStack);

      croppedData = image3.crop(x, y, w, h);

      assertEquals(croppedData, blank);
    }
  }

  @Test
  public void canFindMin() {
    final Image2D image = createData(2, 2);
    Assertions.assertEquals(0, image.findMinIndex(0, 0, 2, 2));
    Assertions.assertEquals(1, image.findMinIndex(1, 0, 2, 2));
    Assertions.assertEquals(2, image.findMinIndex(0, 1, 2, 2));
    Assertions.assertEquals(3, image.findMinIndex(1, 1, 2, 2));

    // Larger slices
    canFindMin(3, 4, 5, 6);
    canFindMin(3, 4, 1, 1);
    canFindMin(0, 0, 1, 1);
  }

  private void canFindMin(int x, int y, int w, int h) {
    // This test assumes that crop works!
    for (final int pad : new int[] {0, 1}) {
      final Image2D image = createData(x + w + pad, y + h + pad);

      final Image2D croppedData = image.crop(x, y, w, h);
      final int i = findMinIndex(croppedData);
      final int[] xy = croppedData.getXy(i);

      final int j = image.findMinIndex(x, y, w, h);
      final int[] xy2 = image.getXy(j);

      Assertions.assertEquals(xy[0] + x, xy2[0]);
      Assertions.assertEquals(xy[1] + y, xy2[1]);
    }
  }

  @Test
  public void canFindMax() {
    final Image2D image = createData(2, 2);
    Assertions.assertEquals(3, image.findMaxIndex(0, 0, 2, 2));
    Assertions.assertEquals(2, image.findMaxIndex(0, 0, 1, 2));
    Assertions.assertEquals(1, image.findMaxIndex(0, 0, 2, 1));
    Assertions.assertEquals(0, image.findMaxIndex(0, 0, 1, 1));

    // Larger slices
    canFindMax(3, 4, 5, 6);
    canFindMax(3, 4, 1, 1);
    canFindMax(0, 0, 1, 1);
  }

  private void canFindMax(int x, int y, int w, int h) {
    // This test assumes that crop works!
    for (final int pad : new int[] {0, 1}) {
      final Image2D image = createData(x + w + pad, y + h + pad);

      final Image2D croppedData = image.crop(x, y, w, h);
      final int i = findMaxIndex(croppedData);
      final int[] xy = croppedData.getXy(i);

      final int j = image.findMaxIndex(x, y, w, h);
      final int[] xy2 = image.getXy(j);

      Assertions.assertEquals(xy[0] + x, xy2[0]);
      Assertions.assertEquals(xy[1] + y, xy2[1]);
    }
  }

  @Test
  public void canComputeSum() {
    // Bounds checks
    final Image2D image = createData(2, 2);
    Assertions.assertEquals(10, image.computeSum(0, 0, 2, 2));
    Assertions.assertEquals(0, image.computeSum(0, 0, 0, 0));
    Assertions.assertEquals(1, image.computeSum(0, 0, 1, 1));
    Assertions.assertEquals(2, image.computeSum(1, 0, 1, 1));
    Assertions.assertEquals(0, image.computeSum(-10, 0, 1, 1));
    Assertions.assertEquals(0, image.computeSum(10, 0, 1, 1));
    Assertions.assertEquals(0, image.computeSum(0, 10, 1, 1));
    Assertions.assertEquals(0, image.computeSum(0, -10, 1, 1));

    // Larger slices
    canComputeSum(3, 4, 5, 6);
    canComputeSum(3, 4, 1, 1);
    canComputeSum(0, 0, 1, 1);
  }

  private void canComputeSum(int x, int y, int w, int h) {
    // This test assumes that crop works!
    for (final int pad : new int[] {0, 1}) {
      final Image2D image = createData(x + w + pad, y + h + pad);

      final Image2D croppedData = image.crop(x, y, w, h);
      final double e = sum(croppedData);
      final double o = image.computeSum(x, y, w, h);

      Assertions.assertEquals(o, e);
    }
  }

  @Test
  public void canComputeRollingSumTable() {
    final int w = 2, h = 3;
    final Image2D image = createData(w, h);
    final double[] table = image.computeRollingSumTable(null);
    for (int hh = 1, i = 0; hh <= h; hh++) {
      for (int ww = 1; ww <= w; ww++) {
        final double e = image.computeSum(0, 0, ww, hh);
        final double o = table[i++];
        Assertions.assertEquals(e, o, 1e-3);
      }
    }
  }

  @Test
  public void canComputeSumUsingTable() {
    // Bounds checks
    final Image2D image = createData(2, 2);
    final double[] table = image.computeRollingSumTable(null);
    // testComputeSum(36, image,table, 0, 0, 0, 2, 2, 2);
    testComputeSum(10, image, table, 0, 0, 5, 7);
    testComputeSum(0, image, table, 0, 0, 0, 0);
    testComputeSum(1, image, table, 0, 0, 1, 1);
    testComputeSum(2, image, table, 1, 0, 1, 1);
    testComputeSum(0, image, table, -10, 0, 1, 1);
    testComputeSum(0, image, table, 10, 0, 1, 1);
    testComputeSum(0, image, table, 0, 10, 1, 1);
    testComputeSum(0, image, table, 0, -10, 1, 1);

    // Larger slices
    canComputeSumUsingTable(3, 4, 5, 6);
    canComputeSumUsingTable(3, 4, 1, 1);
    canComputeSumUsingTable(0, 0, 1, 1);
  }

  private void canComputeSumUsingTable(int x, int y, int w, int h) {
    // This test assumes that crop works!
    for (final int pad : new int[] {0, 1}) {
      final Image2D image = createData(x + w + pad, y + h + pad);
      final double[] table = image.computeRollingSumTable(null);

      final Image2D croppedData = image.crop(x, y, w, h);
      final double e = sum(croppedData);

      testComputeSum(e, image, table, x, y, w, h);
    }
  }

  private static void testComputeSum(double e, Image2D image, double[] table, int x, int y, int w,
      int h) {
    final double o = image.computeSum(table, x, y, w, h);
    final double o2 = image.computeSumFast(table, x, y, w, h);

    // This may be different due to floating point error
    // but we are adding integers so it should be OK
    Assertions.assertEquals(e, o);
    Assertions.assertEquals(e, o2);
  }

  @Test
  public void canFill() {
    canFill(3, 4, 5, 6);
    canFill(3, 4, 1, 1);
    canFill(0, 0, 1, 1);
  }

  private void canFill(int x, int y, int w, int h) {
    // This test assumes that copy, crop and insert work!
    for (final int pad : new int[] {0, 1}) {
      final Image2D image = createEmptyData(x + w + pad, y + h + pad);
      image.fill(1);
      image.fill(x, y, w, h, 2);

      final Image2D image2 = createEmptyData(x + w + pad, y + h + pad);
      image2.fill(1);
      final Image2D blank = createEmptyData(w, h);
      blank.fill(2);
      image2.insert(x, y, blank);

      final Image2D croppedData = image.crop(x, y, w, h);

      assertEquals(croppedData, blank);

      assertEquals(image, image2);
    }
  }

  @Test
  public void canFillOutside() {
    canFillOutside(3, 4, 5, 6);
    canFillOutside(3, 4, 1, 1);
    canFillOutside(0, 0, 1, 1);
  }

  private void canFillOutside(int x, int y, int w, int h) {
    // This test assumes that copy, crop and insert work!
    for (final int pad : new int[] {0, 1}) {
      final Image2D image = createEmptyData(x + w + pad, y + h + pad);
      image.fill(1);
      image.fillOutside(x, y, w, h, 2);

      final Image2D image2 = createEmptyData(x + w + pad, y + h + pad);
      image2.fill(2);

      final Image2D blank = createEmptyData(w, h);
      blank.fill(1);
      image2.insert(x, y, blank);

      final Image2D croppedData = image.crop(x, y, w, h);

      assertEquals(croppedData, blank);

      assertEquals(image, image2);
    }
  }

  private static void assertEquals(Image2D a, Image2D b) {
    for (int i = a.getDataLength(); i-- > 0;) {
      if (a.get(i) != b.get(i)) {
        Assertions.assertEquals(a.get(i), b.get(i), "Not equal @ " + i);
      }
    }
  }

  private static int findMinIndex(Image2D image) {
    int min = 0;
    for (int i = image.getDataLength(); i-- > 0;) {
      if (image.get(i) < image.get(min)) {
        min = i;
      }
    }
    return min;
  }

  private static int findMaxIndex(Image2D image) {
    int max = 0;
    for (int i = image.getDataLength(); i-- > 0;) {
      if (image.get(i) > image.get(max)) {
        max = i;
      }
    }
    return max;
  }

  private static double sum(Image2D image) {
    double sum = 0;
    for (int i = image.getDataLength(); i-- > 0;) {
      sum += image.get(i);
    }
    return sum;
  }
}