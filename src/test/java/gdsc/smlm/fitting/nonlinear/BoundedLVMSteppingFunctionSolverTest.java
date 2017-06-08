package gdsc.smlm.fitting.nonlinear;

import org.junit.Assert;
import org.junit.Test;

import gdsc.core.utils.StoredDataStatistics;

/**
 * Test that an LVM stepping solver can return the same results with and without bounds.
 */
public class BoundedLVMSteppingFunctionSolverTest extends BaseSteppingFunctionSolverTest
{
	// This test is a copy of the BoundedFunctionSolverTest for the LVMSteppingFunctionSolver.
	// The class allows comparison between the old and new FunctionSolver implementations.
	// The tests in this class can be skipped since they are a subset of the tests performed
	// in the SteppingFunctionSolverTest.
	boolean runTests = false;

	// The following tests ensure that the LVM can fit data without 
	// requiring a bias (i.e. an offset to the background).
	// In a previous version the LVM fitter was stable only if a bias existed.
	// The exact source of this instability is unknown as it could be due to 
	// how the data was processed before or after fitting, or within the LVM 
	// fitter itself. However the process should be the same without a bias
	// and these tests ensure that is true.

	@Test
	public void fitSingleGaussianLVMWithoutBias()
	{
		fitSingleGaussianLVMWithoutBias(false, 0);
	}

	@Test
	public void fitSingleGaussianCLVMWithoutBias()
	{
		fitSingleGaussianLVMWithoutBias(false, 1);
	}

	@Test
	public void fitSingleGaussianDCLVMWithoutBias()
	{
		fitSingleGaussianLVMWithoutBias(false, 2);
	}

	@Test
	public void fitSingleGaussianBLVMWithoutBias()
	{
		fitSingleGaussianLVMWithoutBias(true, 0);
	}

	@Test
	public void fitSingleGaussianBCLVMWithoutBias()
	{
		fitSingleGaussianLVMWithoutBias(true, 1);
	}

	@Test
	public void fitSingleGaussianBDCLVMWithoutBias()
	{
		fitSingleGaussianLVMWithoutBias(true, 2);
	}

	private void fitSingleGaussianLVMWithoutBias(boolean applyBounds, int clamping)
	{
		org.junit.Assume.assumeTrue(runTests);

		double bias = 100;

		SteppingFunctionSolver solver = getSolver(clamping, false);
		SteppingFunctionSolver solver2 = getSolver(clamping, false);

		String name = getLVMName(applyBounds, clamping, false);

		int LOOPS = 5;
		randomGenerator.setSeed(seed);
		StoredDataStatistics[] stats = new StoredDataStatistics[6];

		for (double s : signal)
		{
			double[] expected = createParams(1, s, 0, 0, 1);
			double[] lower = null, upper = null;
			if (applyBounds)
			{
				lower = createParams(0, s * 0.5, -0.2, -0.2, 0.8);
				upper = createParams(3, s * 2, 0.2, 0.2, 1.2);
				solver.setBounds(lower, upper);
			}

			double[] expected2 = addBiasToParams(expected, bias);
			if (applyBounds)
			{
				double[] lower2 = addBiasToParams(lower, bias);
				double[] upper2 = addBiasToParams(upper, bias);
				solver2.setBounds(lower2, upper2);
			}

			for (int loop = LOOPS; loop-- > 0;)
			{
				double[] data = drawGaussian(expected);
				double[] data2 = data.clone();
				for (int i = 0; i < data.length; i++)
					data2[i] += bias;

				for (int i = 0; i < stats.length; i++)
					stats[i] = new StoredDataStatistics();

				for (double db : base)
					for (double dx : shift)
						for (double dy : shift)
							for (double dsx : factor)
							{
								double[] p = createParams(db, s, dx, dy, dsx);
								double[] p2 = addBiasToParams(p, bias);

								double[] fp = fitGaussian(solver, data, p, expected);
								double[] fp2 = fitGaussian(solver2, data2, p2, expected2);

								// The result should be the same without a bias
								Assert.assertEquals(name + " Iterations", solver.getEvaluations(),
										solver2.getEvaluations());
								fp2[0] -= bias;
								Assert.assertArrayEquals(name + " Solution", fp, fp2, 1e-6);
							}
			}
		}
	}

	// Standard LVM
	@Test
	public void canFitSingleGaussianLVM()
	{
		fitSingleGaussianLVM(0, 0, false);
	}

	// Bounded/Clamped LVM

	@Test
	public void canFitSingleGaussianBLVMNoBounds()
	{
		fitSingleGaussianLVM(1, 0, false);
	}

	@Test
	public void canFitSingleGaussianBLVM()
	{
		fitSingleGaussianLVM(2, 0, false);
	}

	@Test
	public void canFitSingleGaussianCLVM()
	{
		fitSingleGaussianLVM(0, 1, false);
	}

	@Test
	public void canFitSingleGaussianDCLVM()
	{
		fitSingleGaussianLVM(0, 2, false);
	}

	@Test
	public void canFitSingleGaussianBCLVM()
	{
		fitSingleGaussianLVM(2, 1, false);
	}

	@Test
	public void canFitSingleGaussianBDCLVM()
	{
		fitSingleGaussianLVM(2, 2, false);
	}

	// MLE LVM

	@Test
	public void canFitSingleGaussianLVMMLE()
	{
		fitSingleGaussianLVM(0, 0, true);
	}

	@Test
	public void canFitSingleGaussianBLVMMLENoBounds()
	{
		fitSingleGaussianLVM(1, 0, true);
	}

	@Test
	public void canFitSingleGaussianBLVMMLE()
	{
		fitSingleGaussianLVM(2, 0, true);
	}

	private void fitSingleGaussianLVM(int bounded, int clamping, boolean mle)
	{
		org.junit.Assume.assumeTrue(runTests);
		canFitSingleGaussian(getSolver(clamping, mle), bounded == 2);
	}

	// Is Bounded/Clamped LVM better?

	@Test
	public void fitSingleGaussianBLVMBetterThanLVM()
	{
		fitSingleGaussianBetterLVM(true, 0, false, false, 0, false);
	}

	@Test
	public void fitSingleGaussianCLVMBetterThanLVM()
	{
		fitSingleGaussianBetterLVM(false, 1, false, false, 0, false);
	}

	@Test
	public void fitSingleGaussianBCLVMBetterThanLVM()
	{
		fitSingleGaussianBetterLVM(true, 1, false, false, 0, false);
	}

	@Test
	public void fitSingleGaussianDCLVMBetterThanLVM()
	{
		fitSingleGaussianBetterLVM(false, 2, false, false, 0, false);
	}

	@Test
	public void fitSingleGaussianBDCLVMBetterThanLVM()
	{
		fitSingleGaussianBetterLVM(true, 2, false, false, 0, false);
	}

	@Test
	public void fitSingleGaussianLVMMLEBetterThanLVM()
	{
		fitSingleGaussianBetterLVM(false, 0, true, false, 0, false);
	}

	@Test
	public void fitSingleGaussianBLVMMLEBetterThanLVM()
	{
		fitSingleGaussianBetterLVM(true, 0, true, false, 0, false);
	}

	@Test
	public void fitSingleGaussianCLVMMLEBetterThanLVM()
	{
		fitSingleGaussianBetterLVM(false, 1, true, false, 0, false);
	}

	@Test
	public void fitSingleGaussianBCLVMMLEBetterThanLVM()
	{
		fitSingleGaussianBetterLVM(true, 1, true, false, 0, false);
	}

	@Test
	public void fitSingleGaussianDCLVMMLEBetterThanLVM()
	{
		fitSingleGaussianBetterLVM(false, 2, true, false, 0, false);
	}

	@Test
	public void fitSingleGaussianBDCLVMMLEBetterThanLVM()
	{
		fitSingleGaussianBetterLVM(true, 2, true, false, 0, false);
	}

	@Test
	public void fitSingleGaussianBLVMMLEBetterThanLVMMLE()
	{
		fitSingleGaussianBetterLVM(true, 0, true, false, 0, true);
	}

	@Test
	public void fitSingleGaussianCLVMMLEBetterThanLVMMLE()
	{
		fitSingleGaussianBetterLVM(false, 1, true, false, 0, true);
	}

	@Test
	public void fitSingleGaussianDCLVMMLEBetterThanLVMMLE()
	{
		fitSingleGaussianBetterLVM(false, 2, true, false, 0, true);
	}

	@Test
	public void fitSingleGaussianBDCLVMMLEBetterThanLVMMLE()
	{
		fitSingleGaussianBetterLVM(true, 2, true, false, 0, true);
	}

	@Test
	public void fitSingleGaussianBLVMMLEBetterThanBLVM()
	{
		fitSingleGaussianBetterLVM(true, 0, true, true, 0, false);
	}

	@Test
	public void fitSingleGaussianBCLVMMLEBetterThanBCLVM()
	{
		fitSingleGaussianBetterLVM(true, 1, true, true, 1, false);
	}

	@Test
	public void fitSingleGaussianBDCLVMMLEBetterThanBDCLVM()
	{
		fitSingleGaussianBetterLVM(true, 2, true, true, 2, false);
	}

	private void fitSingleGaussianBetterLVM(boolean bounded2, int clamping2, boolean mle2, boolean bounded,
			int clamping, boolean mle)
	{
		org.junit.Assume.assumeTrue(runTests);

		SteppingFunctionSolver solver = getSolver(clamping, mle);
		SteppingFunctionSolver solver2 = getSolver(clamping2, mle2);
		canFitSingleGaussianBetter(solver, bounded, solver2, bounded2, getLVMName(bounded, clamping, mle),
				getLVMName(bounded2, clamping2, mle2));
	}

	SteppingFunctionSolver getSolver(int clamping, boolean mle)
	{
		SteppingFunctionSolverClamp clamp = (clamping == 0) ? NO_CLAMP : (clamping == 1) ? CLAMP : DYNAMIC_CLAMP;
		SteppingFunctionSolverType type = (mle) ? MLELVM : LSELVM;
		return getSolver(clamp, type);

	}

	private String getLVMName(boolean bounded, int clamping, boolean mle)
	{
		return ((bounded) ? "B" : "") + ((clamping == 0) ? "" : ((clamping == 1) ? "C" : "DC")) + "LVM" +
				((mle) ? " MLE" : "");
	}
}