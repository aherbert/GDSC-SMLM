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
 * Support direct filtering of PreprocessedPeakResult objects.
 *
 * <p>The decision to support for filtering as both a DirectFilter and Filter concurrently is left
 * to the implementing class. It is not a requirement.
 */
public interface IDirectFilter {
  /**
   * Validation flag for the signal in photons.
   */
  public static final int V_PHOTONS = 0x000000001;

  /**
   * Validation flag for the SNR.
   */
  public static final int V_SNR = 0x000000002;

  /**
   * Validation flag for the noise.
   */
  public static final int V_NOISE = 0x000000004;

  /**
   * Validation flag for the location variance.
   */
  public static final int V_LOCATION_VARIANCE = 0x000000008;

  /**
   * Validation flag for the location variance using the local background.
   */
  public static final int V_LOCATION_VARIANCE2 = 0x000000010;

  /**
   * Validation flag for the average peak standard deviation in the X and Y dimension.
   */
  public static final int V_SD = 0x000000020;

  /**
   * Validation flag for the background.
   */
  public static final int V_BACKGROUND = 0x000000040;

  /**
   * Validation flag for the amplitude.
   */
  public static final int V_AMPLITUDE = 0x000000080;

  /**
   * Validation flag for the angle (for an elliptical Gaussian peak).
   */
  public static final int V_ANGLE = 0x000000100;

  /**
   * Validation flag for the x position.
   */
  public static final int V_X = 0x000000200;

  /**
   * Validation flag for the y position.
   */
  public static final int V_Y = 0x000000400;

  /**
   * Validation flag for the relative x position shift squared.
   */
  public static final int V_X_RELATIVE_SHIFT = 0x000000800;

  /**
   * Validation flag for the relative y position shift squared.
   */
  public static final int V_Y_RELATIVE_SHIFT = 0x000001000;

  /**
   * Validation flag for the x-dimension standard deviation.
   */
  public static final int V_X_SD = 0x000002000;

  /**
   * Validation flag for the y-dimension standard deviation.
   */
  public static final int V_Y_SD = 0x000004000;

  /**
   * Validation flag for the x-dimension width factor.
   */
  public static final int V_X_SD_FACTOR = 0x000008000;

  /**
   * Validation flag for the y-dimension width factor.
   */
  public static final int V_Y_SD_FACTOR = 0x000010000;

  /**
   * Validation flag for the location variance using the fitted x/y parameter Cramér-Rao lower bound
   */
  public static final int V_LOCATION_VARIANCE_CRLB = 0x000020000;

  /**
   * Validation flag for the z position.
   */
  public static final int V_Z = 0x000040000;

  /**
   * Disable filtering using the width of the result.
   */
  public static final int NO_WIDTH = 0x000000001;

  /**
   * Disable filtering using the shift of the result.
   */
  public static final int NO_SHIFT = 0x000000002;

  /**
   * Enable filtering both X and Y widths.
   */
  public static final int XY_WIDTH = 0x000000004;

  /**
   * Disable Z filtering (use when not fitting in 3D).
   */
  public static final int NO_Z = 0x000000008;

  /**
   * Gets the flags indicating all the fields that are used during validation. These flags may be
   * returned by the filter {@link #validate(PreprocessedPeakResult)} method if the result fails
   * validation.
   *
   * @return the validation flags
   */
  public int getValidationFlags();

  /**
   * Called before the accept method is called for PreprocessedPeakResult.
   *
   * <p>This should be called once to initialise the filter before processing a batch of results.
   *
   * @see #validate(PreprocessedPeakResult)
   */
  public void setup();

  /**
   * Called before the accept method is called for PreprocessedPeakResult. The flags can control the
   * type of filtering requested. Filters are asked to respect the flags defined in this class.
   *
   * <p>This should be called once to initialise the filter before processing a batch of results.
   *
   * @param flags Flags used to control the filter
   * @see #validate(PreprocessedPeakResult)
   */
  public void setup(final int flags);

  /**
   * Called before the accept method is called for PreprocessedPeakResult. The filter data can
   * control the type of filtering requested.
   *
   * <p>This should be called once to initialise the filter before processing a batch of results.
   *
   * @param flags Flags used to control the filter
   * @param filterSetupData Data used to control the filter
   * @see #validate(PreprocessedPeakResult)
   */
  public void setup(final int flags, final FilterSetupData... filterSetupData);

  /**
   * Gets the flags required to reinitialise the current filter state using {@link #setup(int)} or
   * {@link #setup(int, FilterSetupData...)}
   *
   * @return the flags
   * @throws IllegalStateException If setup has not been called and the flags cannot be created
   */
  public int getFilterSetupFlags() throws IllegalStateException;

  /**
   * Gets the filter setup data required to reinitialise the current filter state using
   * {@link #setup(int, FilterSetupData...)}
   *
   * @return the filter setup data (can be null)
   * @throws IllegalStateException If setup has not been called and the data cannot be created
   */
  public FilterSetupData[] getFilterSetupData() throws IllegalStateException;

  /**
   * Filter the peak result.
   *
   * <p>Calls {@link #validate(PreprocessedPeakResult)} and stores the result. This can be obtained
   * using {@link #getResult()}.
   *
   * @param peak The peak result
   * @return true if the peak should be accepted
   */
  public boolean accept(final PreprocessedPeakResult peak);

  /**
   * Filter the peak result.
   *
   * @param peak The peak result
   * @return zero if the peak should be accepted, otherwise set to flags indicating the field that
   *         failed validation.
   */
  public int validate(final PreprocessedPeakResult peak);

  /**
   * Return the type of filter. This should be a DirectFilter.
   *
   * @return Should return DirectFilter
   */
  public FilterType getFilterType();

  /**
   * Return the result flag generated during the last call to
   * {@link #accept(PreprocessedPeakResult)}.
   *
   * @return the validation result from the last call to {@link #accept(PreprocessedPeakResult)}
   */
  public int getResult();

  /**
   * Copy this filter.
   *
   * @return the copy
   */
  public IDirectFilter copy();
}