package gdsc.smlm.ga;

/*----------------------------------------------------------------------------- 
 * GDSC SMLM Software
 * 
 * Copyright (C) 2015 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

/**
 * Defines mutation of a chromosome
 */
public interface Mutator<T extends Comparable<T>>
{
	/**
	 * Mutate the provided chromosome
	 * 
	 * @param chromosome
	 * @return a new sequence
	 */
	Chromosome<T> mutate(Chromosome<T> chromosome);
}
