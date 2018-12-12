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

package uk.ac.sussex.gdsc.smlm.results.filter;

import uk.ac.sussex.gdsc.core.match.FractionalAssignment;
import uk.ac.sussex.gdsc.smlm.function.gaussian.Gaussian2DFunction;
import uk.ac.sussex.gdsc.smlm.results.Gaussian2DPeakResultHelper;

/**
 * Specifies a peak fitting result for use in filtering.
 */
public class BasePreprocessedPeakResult implements AssignablePreprocessedPeakResult {
  /**
   * The result type.
   */
  public enum ResultType {
    /** A new result. */
    NEW,
    /** An existing result. */
    EXISTING,
    /** A candidate result. */
    CANDIDATE
  }

  private final int frame;
  private final int id;
  private final int candidateId;
  private final float signal;
  private final float meanSignal;
  private final float snr;
  private final float noise;
  private final float sd;
  private final float b;
  private final float amp;
  private final float angle;
  private final float x;
  private final float y;
  private final float z;
  private final float xshift2;
  private final float yshift2;
  private final float xsd;
  private final float ysd;
  private final float xwf;
  private final float ywf;
  private final double variance;
  private final double variance2;
  private final double varianceCRLB;
  private final boolean existingResult;
  private final boolean newResult;

  private ResultAssignment[] assignments;

  /** The unique id. */
  public int uniqueId;
  private int validationResult;
  private boolean ignore;
  private boolean notDuplicate;

  //@formatter:off
  /**
   * Create a new BasePreprocessedPeakResult.
   *
*
* <p>
   * Note: The candidate Id is usually the spot that was used to initiate the fit process.
   * However if neighbour spots were present then the candidate Id should be that of the neighbour.
   *
   * @param frame
   *            The frame
   * @param id
   *            the id
   * @param candidateId
   *            the candidate id
   * @param signal
   *            The signal (in photons)
   * @param meanSignal
   *            the mean signal
   * @param noise
   *            the noise estimate
   * @param b
   *            The background level (in photons)
   * @param angle
   *            The angle of the fit
   * @param x
   *            The x-position
   * @param y
   *            The y-position
   * @param z
   *            The z-position
   * @param x0
   *            The initial x-position
   * @param y0
   *            The initial y-position
   * @param xsd
   *            The x standard deviation
   * @param ysd
   *            The y standard deviation
   * @param xsd0
   *            The initial x standard deviation
   * @param ysd0
   *            The initial y standard deviation
   * @param variance
   *            The estimate of the localisation variance using the noise
   * @param variance2
   *            The estimate of the localisation variance using the local background
   * @param varianceCRLB
   *            the estimate of the localisation variance using the Cramér–Rao lower bound (CRLB)
   * @param resultType
   *            The type of result
   */
  public BasePreprocessedPeakResult(
      int frame,
      int id,
      int candidateId,
      double signal,
      double meanSignal,
      double noise,
      double b,
      double angle,
      double x,
      double y,
      double z,
      double x0,
      double y0,
      double xsd,
      double ysd,
      double xsd0,
      double ysd0,
      double variance,
      double variance2,
      double varianceCRLB,
      ResultType resultType
      )
  {
    //@formatter:on
    this.frame = frame;
    this.id = id;
    this.candidateId = candidateId;
    this.signal = (float) (signal);
    this.meanSignal = (float) (meanSignal);
    this.snr = (float) (signal / noise);
    this.noise = (float) (noise);
    this.sd = (float) (Gaussian2DPeakResultHelper.getStandardDeviation(xsd, ysd));
    this.b = (float) (b);
    this.amp = (float) (signal / (2 * Math.PI * xsd * ysd));
    this.angle = (float) (angle);
    this.x = (float) (x);
    this.y = (float) (y);
    this.z = (float) (z);
    this.xshift2 = squared((x - x0) / xsd0);
    this.yshift2 = squared((y - y0) / ysd0);
    this.xsd = (float) (xsd);
    this.ysd = (float) (ysd);
    this.xwf = (float) (xsd / xsd0);
    this.ywf = (float) (ysd / ysd0);
    this.variance = variance;
    this.variance2 = variance2;
    this.varianceCRLB = varianceCRLB;
    this.existingResult = resultType == ResultType.EXISTING;
    this.newResult = resultType == ResultType.NEW;
  }

  private static float squared(double f) {
    return (float) (f * f);
  }

  /** {@inheritDoc} */
  @Override
  public int getFrame() {
    return frame;
  }

  /** {@inheritDoc} */
  @Override
  public int getUniqueId() {
    return uniqueId;
  }

  /** {@inheritDoc} */
  @Override
  public int getId() {
    return id;
  }

  /** {@inheritDoc} */
  @Override
  public int getCandidateId() {
    return candidateId;
  }

  /** {@inheritDoc} */
  @Override
  public float getSignal() {
    return signal;
  }

  /** {@inheritDoc} */
  @Override
  public float getMeanSignal() {
    return meanSignal;
  }

  /** {@inheritDoc} */
  @Override
  public float getSNR() {
    return snr;
  }

  /** {@inheritDoc} */
  @Override
  public float getNoise() {
    return noise;
  }

  /** {@inheritDoc} */
  @Override
  public double getLocationVariance() {
    return variance;
  }

  /** {@inheritDoc} */
  @Override
  public double getLocationVariance2() {
    return variance2;
  }

  /** {@inheritDoc} */
  @Override
  public double getLocationVarianceCRLB() {
    return varianceCRLB;
  }

  /** {@inheritDoc} */
  @Override
  public float getSD() {
    return sd;
  }

  /** {@inheritDoc} */
  @Override
  public float getBackground() {
    return b;
  }

  /** {@inheritDoc} */
  @Override
  public float getAmplitude() {
    return amp;
  }

  /** {@inheritDoc} */
  @Override
  public float getAngle() {
    return angle;
  }

  /** {@inheritDoc} */
  @Override
  public float getX() {
    return x;
  }

  /** {@inheritDoc} */
  @Override
  public float getY() {
    return y;
  }

  /** {@inheritDoc} */
  @Override
  public float getZ() {
    return z;
  }

  /** {@inheritDoc} */
  @Override
  public float getXRelativeShift2() {
    return xshift2;
  }

  /** {@inheritDoc} */
  @Override
  public float getYRelativeShift2() {
    return yshift2;
  }

  /** {@inheritDoc} */
  @Override
  public float getXSD() {
    return xsd;
  }

  /** {@inheritDoc} */
  @Override
  public float getYSD() {
    return ysd;
  }

  /** {@inheritDoc} */
  @Override
  public float getXSDFactor() {
    return xwf;
  }

  /** {@inheritDoc} */
  @Override
  public float getYSDFactor() {
    return ywf;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isExistingResult() {
    return existingResult;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isNewResult() {
    return newResult;
  }

  /**
   * Returns a new array and so is thread-safe (unless another thread updates the assignments
   * concurrently). It should be thread safe for use in scoring of the result using a multi-path
   * filter.
   *
   * @param predictedId the predicted id
   * @return the assignments
   * @see uk.ac.sussex.gdsc.smlm.results.filter.PreprocessedPeakResult#getAssignments(int)
   */
  @Override
  public FractionalAssignment[] getAssignments(final int predictedId) {
    if (assignments == null || assignments.length == 0) {
      return null;
    }
    // Create a new set of assignments. Since this will be new and all other members are final the
    // class is thread-safe.
    final FractionalAssignment[] out = new FractionalAssignment[assignments.length];
    for (int i = 0; i < out.length; i++) {
      out[i] = assignments[i].toFractionalAssignment(predictedId, this);
    }
    return out;
  }

  /**
   * Checks for assignments.
   *
   * @return true, if successful
   */
  public boolean hasAssignments() {
    return assignments != null;
  }

  /** {@inheritDoc} */
  @Override
  public void setAssignments(ResultAssignment[] assignments) {
    this.assignments = assignments;
  }

  /** {@inheritDoc} */
  @Override
  public boolean ignore() {
    return ignore;
  }

  /** {@inheritDoc} */
  @Override
  public void setIgnore(boolean ignore) {
    this.ignore = ignore;
  }

  /**
   * Convert this to the parameters for a Gaussian2DFunction.
   *
   * @return the parameters
   */
  @Override
  public double[] toGaussian2DParameters() {
    final double[] p = new double[1 + Gaussian2DFunction.PARAMETERS_PER_PEAK];
    p[Gaussian2DFunction.BACKGROUND] = b;
    p[Gaussian2DFunction.SIGNAL] = signal;
    p[Gaussian2DFunction.X_POSITION] = x;
    p[Gaussian2DFunction.Y_POSITION] = y;
    p[Gaussian2DFunction.Z_POSITION] = z;
    p[Gaussian2DFunction.X_SD] = xsd;
    p[Gaussian2DFunction.Y_SD] = ysd;
    p[Gaussian2DFunction.ANGLE] = angle;
    return p;
  }

  /** {@inheritDoc} */
  @Override
  public int getValidationResult() {
    return validationResult;
  }

  /** {@inheritDoc} */
  @Override
  public void setValidationResult(int validationResult) {
    this.validationResult = validationResult;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isNotDuplicate() {
    return notDuplicate;
  }

  /**
   * Sets the not duplicate flag. Set to true if this result cannot be a duplicate (i.e. no
   * preceeding results in the same frame within a close distance).
   *
   * @param notDuplicate the new not duplicate flag
   */
  public void setNotDuplicate(boolean notDuplicate) {
    this.notDuplicate = notDuplicate;
  }
}
