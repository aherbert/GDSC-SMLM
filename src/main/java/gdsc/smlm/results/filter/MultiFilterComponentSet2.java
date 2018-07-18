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
package gdsc.smlm.results.filter;

/**
 * Contains a set of components of the multi filter.
 */
public class MultiFilterComponentSet2 extends MultiFilterComponentSet
{
	private MultiFilterComponent component0;
	private final MultiFilterComponent component1;

	/**
	 * Instantiates a new multi filter component set for 2 components.
	 *
	 * @param components
	 *            the components
	 */
public MultiFilterComponentSet2(MultiFilterComponent[] components)
	{
		this.component0 = components[0];
		this.component1 = components[1];
	}

	@Override
	public int getValidationFlags()
	{
		return component0.getType() | component1.getType();
	}

	@Override
	public int validate(final PreprocessedPeakResult peak)
	{
		//@formatter:off
		if (component0.fail(peak)) return component0.getType();
		if (component1.fail(peak)) return component1.getType();
		//@formatter:on
		return 0;
	}

	@Override
	void replace0(MultiFilterComponent c)
	{
		component0 = c;
	}
}
