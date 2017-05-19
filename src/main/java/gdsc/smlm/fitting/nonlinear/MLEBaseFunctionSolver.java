package gdsc.smlm.fitting.nonlinear;

import gdsc.smlm.fitting.FunctionSolverType;
import gdsc.smlm.fitting.MLEFunctionSolver;
import gdsc.smlm.function.ChiSquaredDistributionTable;
import gdsc.smlm.function.GradientFunction;

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
 * Abstract class with utility methods for the MLEFunctionSolver interface.
 */
public abstract class MLEBaseFunctionSolver extends BaseFunctionSolver implements MLEFunctionSolver
{
	protected double llr = Double.NaN;

	/**
	 * Default constructor
	 * 
	 * @throws NullPointerException
	 *             if the function is null
	 */
	public MLEBaseFunctionSolver(GradientFunction f)
	{
		super(FunctionSolverType.MLE, f);
	}

	@Override
	protected void preProcess()
	{
		llr = Double.NaN;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.fitting.MLEFunctionSolver#getLogLikelihood()
	 */
	public double getLogLikelihood()
	{
		return value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.fitting.MLEFunctionSolver#getLogLikelihoodRatio()
	 */
	public double getLogLikelihoodRatio()
	{
		if (Double.isNaN(llr) && lastY != null)
		{
			// From https://en.wikipedia.org/wiki/Likelihood-ratio_test#Use:
			// LLR = 2 * [ ln(likelihood for alternative model) - ln(likelihood for null model)]
			// The model with more parameters (here alternative) will always fit at least as well—
			// i.e., have the same or greater log-likelihood—than the model with fewer parameters 
			// (here null)

			double llAlternative = computeObservedLogLikelihood(lastY, lastA);
			double llNull = getLogLikelihood();

			// The alternative should always fit better (higher value) than the null model 
			if (llAlternative < llNull)
				llr = 0;
			else
				llr = 2 * (llAlternative - llNull);
		}
		return llr;
	}

	/**
	 * Compute the observed log likelihood (i.e. the log-likelihood with y as the function value).
	 *
	 * @param y
	 *            the y
	 * @param a
	 *            the a
	 * @return the observed log likelihood
	 */
	protected abstract double computeObservedLogLikelihood(double[] y, double[] a);

	/*
	 * (non-Javadoc)
	 * 
	 * @see gdsc.smlm.fitting.MLEFunctionSolver#getQ()
	 */
	public double getQ()
	{
		return ChiSquaredDistributionTable.computeQValue(getLogLikelihoodRatio(),
				getNumberOfFittedPoints() - getNumberOfFittedParameters());
	}
}