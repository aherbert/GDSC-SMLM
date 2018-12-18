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

/**
 * Specifies a the result of fitting a frame using different fitting methods.
 *
 * <p>The multi-path results can be evaluated by the MultiPathFilter to determine which result from
 * the different paths should be accepted.
 *
 * <p>This class is used for benchmarking the fitting path options in the PeakFit algorithm.
 */
public class MultiPathFitResults implements IMultiPathFitResults, Cloneable {
  /** The frame containing the results. */
  public final int frame;

  /** The multi-path results. */
  public final MultiPathFitResult[] multiPathFitResults;

  /**
   * The total number of candidates. This may be greater than the size of the
   * {@link #multiPathFitResults} array if this is a subset of the results, i.e. has been
   * prefiltered.
   */
  public final int totalCandidates;

  /**
   * The number of actual results in the frame. Used during filter scoring.
   */
  public final int nActual;

  /**
   * Instantiates a new multi path fit results.
   *
   * @param frame the frame
   * @param multiPathFitResults the multi path fit results
   */
  public MultiPathFitResults(int frame, MultiPathFitResult[] multiPathFitResults) {
    this(frame, multiPathFitResults, (multiPathFitResults == null) ? 0 : multiPathFitResults.length,
        0);
  }

  /**
   * Instantiates a new multi path fit results.
   *
   * @param frame the frame
   * @param multiPathFitResults the multi path fit results
   * @param totalCandidates the total candidates
   * @param nActual the number of actual results in the frame
   */
  public MultiPathFitResults(int frame, MultiPathFitResult[] multiPathFitResults,
      int totalCandidates, int nActual) {
    this.frame = frame;
    this.multiPathFitResults = multiPathFitResults;
    this.totalCandidates = totalCandidates;
    this.nActual = nActual;
  }

  /** {@inheritDoc} */
  @Override
  public int getFrame() {
    return frame;
  }

  /** {@inheritDoc} */
  @Override
  public int getNumberOfResults() {
    return multiPathFitResults.length;
  }

  /** {@inheritDoc} */
  @Override
  public MultiPathFitResult getResult(int index) {
    return multiPathFitResults[index];
  }

  /** {@inheritDoc} */
  @Override
  public void complete(int index) {
    // Do nothing
  }

  /** {@inheritDoc} */
  @Override
  public int getTotalCandidates() {
    return totalCandidates;
  }

  /** {@inheritDoc} */
  @Override
  public MultiPathFitResults clone() {
    final MultiPathFitResult[] list = new MultiPathFitResult[multiPathFitResults.length];
    for (int i = 0; i < list.length; i++) {
      list[i] = multiPathFitResults[i].clone();
    }
    return new MultiPathFitResults(frame, list, totalCandidates, nActual);
  }
}