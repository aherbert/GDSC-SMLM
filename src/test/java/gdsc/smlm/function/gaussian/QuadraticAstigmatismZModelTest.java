package gdsc.smlm.function.gaussian;

import org.junit.Assert;
import org.junit.Test;

import gdsc.core.utils.DoubleEquality;

public class QuadraticAstigmatismZModelTest
{
	protected DoubleEquality eq = new DoubleEquality(1e-5, 1e-7);

	// Compute as per Numerical Recipes 5.7.
	// Approximate error accuracy in single precision: Ef
	// Step size for derivatives:
	// h ~ (Ef)^(1/3) * xc
	// xc is the characteristic scale over which x changes, assumed to be 1 (not x as per NR since x is close to zero)
	protected double h_ = 0.0001; //(double) (Math.pow(1e-3f, 1.0 / 3));

	@Test
	public void canStaticComputeGradient()
	{
		canStaticComputeGradient(1.2);
		canStaticComputeGradient(0.3);
	}
	
	private void canStaticComputeGradient(double zDepth)
	{
		double[] ds_dz = new double[1];
		double[] ds_dz2 = new double[2];
		double[] ds_duz = new double[1];
		double[] ds_dlz = new double[1];
		for (double z = -0.5; z < 0.5; z += 0.01)
		{
			double s0 = QuadraticAstigmatismZModel.getS(z, zDepth);
			double s1 = QuadraticAstigmatismZModel.getS1(z, zDepth, ds_dz);
			double s2 = QuadraticAstigmatismZModel.getS2(z, zDepth, ds_dz2);

			Assert.assertEquals(s0, s1, 0);
			Assert.assertEquals(s0, s2, 0);
			Assert.assertEquals(ds_dz[0], ds_dz2[0], 0);

			double uz = z + h_;
			double lz = z - h_;
			double upper = QuadraticAstigmatismZModel.getS1(uz, zDepth, ds_duz);
			double lower = QuadraticAstigmatismZModel.getS1(lz, zDepth, ds_dlz);

			double e1 = (upper - lower) / (uz - lz);
			double o1 = ds_dz[0];

			// Second gradient
			double e2 = (ds_duz[0] - ds_dlz[0]) / (uz - lz);
			double o2 = ds_dz2[1];

			System.out.printf("z=%f s=%f : ds_dz=%g  %g  (%g): d2s_dz2=%g   %g  (%g)\n", z, s0, e1, o1,
					DoubleEquality.relativeError(o1, e1), e2, o2, DoubleEquality.relativeError(o2, e2));

			//double error = DoubleEquality.relativeError(o, e);
			if (Math.abs(z) > 0.02)
				Assert.assertTrue(e1 + " sign != " + o1, (e1 * o1) >= 0);
			Assert.assertTrue(e1 + " != " + o1, eq.almostEqualRelativeOrAbsolute(e1, o1));

			if (Math.abs(z) > 0.02)
				Assert.assertTrue(e2 + " sign != " + o2, (e2 * o2) >= 0);
			Assert.assertTrue(e2 + " != " + o2, eq.almostEqualRelativeOrAbsolute(e2, o2));
		}
	}
}