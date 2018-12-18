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

package uk.ac.sussex.gdsc.smlm.function.cspline;

import uk.ac.sussex.gdsc.core.data.TrivalueProvider;
import uk.ac.sussex.gdsc.core.math.interpolation.CubicSplinePosition;
import uk.ac.sussex.gdsc.core.math.interpolation.CustomTricubicFunction;

import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.LinearSolver;
import org.ejml.factory.LinearSolverFactory;

/**
 * Computes the cubic spline coefficients for a 3D cubic spline from interpolated points.
 */
public class CubicSplineCalculator {
  // Based on the code provided by Hazen Babcock for 3D spline fitting
  // https://github.com/ZhuangLab/storm-analysis/blob/master/storm_analysis/spliner/spline3D.py

  private static final DenseMatrix64F A;

  static {
    A = new DenseMatrix64F(64, 64);
    final CubicSplinePosition[] s = new CubicSplinePosition[4];
    for (int i = 0; i < 4; i++) {
      s[i] = new CubicSplinePosition((double) i / 3);
    }
    int c = 0;
    // int c2 = 0;
    for (int k = 0; k < 4; k++) {
      for (int j = 0; j < 4; j++) {
        for (int i = 0; i < 4; i++) {
          final double[] t = CustomTricubicFunction.computePowerTable(s[i], s[j], s[k]);
          System.arraycopy(t, 0, A.data, c, 64);
          c += 64;
          // for (int x = 0; x < 64; x++)
          // A.set(c2, x, t[x]);
          // c2++;
        }
      }
    }
  }

  private final LinearSolver<DenseMatrix64F> solver;

  /**
   * Instantiates a new cubic spline calculator.
   */
  public CubicSplineCalculator() {
    solver = LinearSolverFactory.linear(64);
    // Note: Linear solver should not modify A or B for this to work!
    if (!solver.setA(A) || solver.modifiesA() || solver.modifiesB()) {
      throw new IllegalStateException("Unable to create linear solver");
    }
  }

  /**
   * Compute the coefficients given the spline node value at interpolated points. The value should
   * be interpolated at [0,1/3,2/3,1] in each dimension.
   *
   * @param value the value
   * @return the coefficients (or null if computation failed)
   */
  public double[] compute(double[][][] value) {
    final DenseMatrix64F B = new DenseMatrix64F(64, 1);
    int c = 0;
    for (int k = 0; k < 4; k++) {
      for (int j = 0; j < 4; j++) {
        for (int i = 0; i < 4; i++) {
          B.data[c++] = value[i][j][k];
        }
      }
    }
    solver.solve(B, B);
    return B.data;
  }

  /**
   * Compute the coefficients given the spline node value at interpolated points. The value should
   * be interpolated at [0,1/3,2/3,1] in each dimension.
   *
   * @param value the value
   * @return the coefficients (or null if computation failed)
   */
  public double[] compute(TrivalueProvider value) {
    final DenseMatrix64F B = new DenseMatrix64F(64, 1);
    int c = 0;
    for (int k = 0; k < 4; k++) {
      for (int j = 0; j < 4; j++) {
        for (int i = 0; i < 4; i++) {
          B.data[c++] = value.get(i, j, k);
        }
      }
    }
    solver.solve(B, B);
    return B.data;
  }

  /**
   * Compute the coefficients given the spline node value at interpolated points. The value should
   * be interpolated at [0,1/3,2/3,1] in each dimension.
   *
   * @param value the value (packed in order : i = z*16+4*y+x) for x,y,z in [0,1,2,3])
   * @return the coefficients (or null if computation failed)
   */
  public double[] compute(double[] value) {
    final DenseMatrix64F B = DenseMatrix64F.wrap(64, 1, value);
    solver.solve(B, B);
    return B.data;
  }

  /**
   * Compute the coefficients given the spline node value at interpolated points. The value should
   * be interpolated at [0,1/3,2/3,1] in each dimension.
   *
   * @param value the value (packed in order : i = z*16+4*y+x) for x,y,z in [0,1,2,3])
   * @return the coefficients (or null if computation failed)
   */
  public double[] compute(float[] value) {
    final DenseMatrix64F B = new DenseMatrix64F(64, 1);
    for (int i = 0; i < 64; i++) {
      B.data[i] = value[i];
    }
    solver.solve(B, B);
    return B.data;
  }
}