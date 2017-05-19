package gdsc.smlm.function;

import java.util.Arrays;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.special.Gamma;

/*----------------------------------------------------------------------------- 
 * GDSC SMLM Software
 * 
 * Copyright (C) 2017 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

/**
 * Computes probability values from the Chi-squared distribution
 * <p>
 * Note that Wilk's theorem states that the log-likelihood ratio asymptotically approaches a
 * Chi-squared distribution with degrees of freedom equal to the difference in dimensionality of the two models
 * (alternative and null).
 * 
 * @see https://en.wikipedia.org/wiki/Likelihood-ratio_test#Wilks.27_theorem
 */
public class ChiSquaredDistributionTable
{
	/**
	 * The p-value for computing the value using the cumulative probability
	 */
	private final double p;

	/** The chi squared critical value table */
	final double[] chiSquared;

	/**
	 * The direction of the critical value table. Values more extreme that the critical value in this direction are
	 * rejected.
	 */
	final int direction;

	/**
	 * Instantiates a new chi squared distribution table with p the cumulative probability of the chi-squared
	 * distribution.
	 * <p>
	 * Setting q will cause {@link #getCrititalValue(int)} to generate a value where the probability of a random
	 * chi-squared observation above that value is equal to q.
	 * <p>
	 * Setting Q to a lower value allows poorer Chi squared values to be passed as significant. A typical value is 0.05
	 * to 0.001.
	 *
	 * @param significance
	 *            the significance
	 * @param df
	 *            the maximum degrees of freedom
	 * @param upperTailed
	 *            Set to true to create upper tailed significance testing (otherwise lower tailed)
	 */
	private ChiSquaredDistributionTable(double significance, int df, boolean upperTailed)
	{
		// Convert the significance to a cumulative probability
		this.p = (upperTailed) ? 1 - significance : significance;
		// Fill table with NaN
		chiSquared = new double[df + 1];
		Arrays.fill(chiSquared, Double.NaN);
		// Set the direction of the table
		direction = (upperTailed) ? 1 : -1;
	}

	/**
	 * Creates an upper-tailed Chi squared critical value table. If the test statistic is above the critical value then
	 * it will be rejected. A low significance value indicates greater statistical significance, i.e. greater confidence
	 * that the observed deviation from the null hypothesis is significant. The test will provide 100 * (1 -
	 * significance) percent confidence.
	 *
	 * @param significance
	 *            the significance
	 * @param maximumDegreesOfFreedom
	 *            the maximum degrees of freedom
	 * @return the chi squared distribution table
	 */
	public static ChiSquaredDistributionTable createUpperTailed(double significance, int maximumDegreesOfFreedom)
	{
		return new ChiSquaredDistributionTable(significance, maximumDegreesOfFreedom, true);
	}

	/**
	 * Creates a lower-tailed Chi squared critical value table. If the test statistic is below the critical value then
	 * it will be rejected. A low significance value indicates greater statistical significance, i.e. greater confidence
	 * that the observed deviation from the null hypothesis is significant. The test will provide 100 * (1 -
	 * significance) percent confidence.
	 *
	 * @param significance
	 *            the significance
	 * @param maximumDegreesOfFreedom
	 *            the maximum degrees of freedom
	 * @return the chi squared distribution table
	 */
	public static ChiSquaredDistributionTable createLowerTailed(double significance, int maximumDegreesOfFreedom)
	{
		return new ChiSquaredDistributionTable(significance, maximumDegreesOfFreedom, false);
	}

	/**
	 * Checks if the table upper tailed. Values more extreme than this direction (upper/lower) are rejected.
	 *
	 * @return true, if is upper tailed
	 */
	public boolean isUpperTailed()
	{
		return direction == 1;
	}

	/**
	 * Get the p-value for significance.
	 * <p>
	 * Refers to the probability of Chi squared being more extreme than the critical value by chance. The direction of
	 * the extreme is determined by {@link #isUpperTailed()}.
	 *
	 * @return the q value
	 */
	public double getSignificanceValue()
	{
		return (isUpperTailed()) ? 1 - p : p;
	}

	/**
	 * Gets the critical value of chi squared value for the configured significance level and degrees of freedom.
	 * <p>
	 * This is the value of Chi squared where the chance of obtaining a value more extreme than this point is equal to
	 * the configured significance level.
	 *
	 * @param degreesOfFreedom
	 *            the degrees of freedom
	 * @return the chi squared
	 */
	public double getCrititalValue(int degreesOfFreedom)
	{
		if (degreesOfFreedom >= chiSquared.length)
			throw new IllegalStateException("Maximum degrees of freedom = " + (chiSquared.length - 1));
		if (Double.isNaN(chiSquared[degreesOfFreedom]))
			chiSquared[degreesOfFreedom] = getChiSquared(p, degreesOfFreedom);
		return chiSquared[degreesOfFreedom];
	}

	/**
	 * Checks if the value of chi-squared is more extreme than the critical value at the configured significance level.
	 * <p>
	 * Returns true iff the null hypothesis can be rejected with 100 * (1 - significance) percent confidence.
	 *
	 * @param chiSquared
	 *            the chi squared
	 * @param degreesOfFreedom
	 *            the degrees of freedom
	 * @return true, if is more extreme
	 */
	public boolean reject(double chiSquared, int degreesOfFreedom)
	{
		return Double.compare(chiSquared, getCrititalValue(degreesOfFreedom)) == direction;
	}

	/**
	 * Gets the chi squared value for the cumulative probability and degrees of freedom.
	 *
	 * @param p
	 *            the cumulative probability
	 * @param degreesOfFreedom
	 *            the degrees of freedom
	 * @return the chi squared
	 */
	public static double getChiSquared(double p, int degreesOfFreedom)
	{
		return new ChiSquaredDistribution(null, degreesOfFreedom).inverseCumulativeProbability(p);
	}

	/**
	 * Compute the q-value of the Chi-squared distribution.
	 * <p>
	 * This is the probability of obtaining a value more extreme than this point by chance. A chi-squared value is
	 * significant if q is higher than the p-value for significance (e.g. 0.05).
	 *
	 * @param chiSquared
	 *            the chi squared
	 * @param degreesOfFreedom
	 *            the degrees of freedom
	 * @return the q-value
	 */
	public static double computeQValue(double chiSquared, int degreesOfFreedom)
	{
		if (chiSquared <= 0)
		{
			return 1;
		}
		else
		{
			return Gamma.regularizedGammaQ(degreesOfFreedom / 2.0, chiSquared / 2.0);
		}
	}

	/**
	 * Compute the p-value of the Chi-squared distribution. This is the cumulative probability of the chi-squared
	 * distribution.
	 * <p>
	 * This is the probability of obtaining a value less extreme than this point by chance.
	 *
	 * @param chiSquared
	 *            the chi squared
	 * @param degreesOfFreedom
	 *            the degrees of freedom
	 * @return the p-value
	 */
	public static double computePValue(double chiSquared, int degreesOfFreedom)
	{
		if (chiSquared <= 0)
		{
			return 0;
		}
		else
		{
			return Gamma.regularizedGammaP(degreesOfFreedom / 2.0, chiSquared / 2.0);
		}
	}
}