package gdsc.smlm.ij.settings;

import gdsc.core.clustering.optics.SampleMode;

/*----------------------------------------------------------------------------- 
 * GDSC SMLM Software
 * 
 * Copyright (C) 2016 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

/**
 * Contain the settings for the clustering algorithm
 * 
 * @author Alex Herbert
 */
public class OPTICSSettings implements Cloneable
{
	/**
	 * Options for displaying the clustering image
	 */
	public enum ImageMode
	{
		//@formatter:off
		CLUSTER_ID {
			@Override
			public String getName() { return "Cluster Id"; };
			@Override
			public float getValue(float value, int clusterId, int order) { return clusterId; }
			@Override
			public boolean isMapped() { return true; }
			@Override
			public boolean isRequiresClusters() { return true; }
		},
		CLUSTER_DEPTH {
			@Override
			public String getName() { return "Cluster Depth"; };
			@Override
			public float getValue(float value, int clusterId, int order) { return clusterId; }
			@Override
			public boolean isMapped() { return true; }
			@Override
			public boolean isRequiresClusters() { return true; }
		},
		CLUSTER_ORDER {
			@Override
			public String getName() { return "Cluster Order"; };
			@Override
			public float getValue(float value, int clusterId, int order) { return order; }
			@Override
			public boolean isMapped() { return true; }
			@Override
			public boolean isRequiresClusters() { return true; }
		},
		VALUE {
			@Override
			public String getName() { return "Value"; };
			@Override
			public boolean canBeWeighted() { return true; }
			@Override
			public float getValue(float value, int clusterId, int order) { return value; }
		},
		COUNT {
			@Override
			public String getName() { return "Count"; };
			@Override
			public boolean canBeWeighted() { return true; }
			@Override
			public float getValue(float value, int clusterId, int order) { return 1f; }
		},
		LOOP {
			@Override
			public String getName() { return "Local Outlier Probability (LoOP)"; };
			@Override
			public float getValue(float value, int clusterId, int order) { return order; }
			@Override
			public boolean isMapped() { return true; }
		},
		NONE {
			@Override
			public String getName() { return "None"; };
			@Override
			public float getValue(float value, int clusterId, int order) { return 0; }
		};
		//@formatter:on

		/**
		 * Gets the name.
		 *
		 * @return the name
		 */
		abstract public String getName();

		/**
		 * Return the value to draw.
		 *
		 * @param value
		 *            The value of the cluster point
		 * @param clusterId
		 *            The cluster Id of the cluster point
		 * @param order
		 *            the order of the cluster point
		 * @return The value
		 */
		abstract public float getValue(float value, int clusterId, int order);

		/**
		 * Return true if the value can be weighted amongst neighbour pixels int the output image
		 *
		 * @return true, if successful
		 */
		public boolean canBeWeighted()
		{
			return false;
		}

		/**
		 * Return true if the value should be mapped to the 1-255 range for the output image
		 *
		 * @return true, if is mapped
		 */
		public boolean isMapped()
		{
			return false;
		}

		@Override
		public String toString()
		{
			return getName();
		}

		/**
		 * Checks if the mode requires clusters.
		 *
		 * @return true, if requires clusters
		 */
		public boolean isRequiresClusters()
		{
			return false;
		}
	}

	/**
	 * Options for plotting the OPTICS algorithm
	 */
	public enum OPTICSMode
	{
		//@formatter:off
		FAST_OPTICS {
			@Override
			public String getName() { return "FastOPTICS"; };
		},
		OPTICS {
			@Override
			public String getName() { return "OPTICS"; };
		};
		//@formatter:on

		/**
		 * Gets the name.
		 *
		 * @return the name
		 */
		abstract public String getName();

		@Override
		public String toString()
		{
			return getName();
		}
	}

	/**
	 * Options for plotting the OPTICS results
	 */
	public enum ClusteringMode
	{
		//@formatter:off
		XI {
			@Override
			public String getName() { return "Xi"; };
		},
		DBSCAN {
			@Override
			public String getName() { return "pseudo-DBSCAN"; };
		};
		//@formatter:on

		/**
		 * Gets the name.
		 *
		 * @return the name
		 */
		abstract public String getName();

		@Override
		public String toString()
		{
			return getName();
		}
	}

	/**
	 * Options for plotting the OPTICS results
	 */
	public enum PlotMode
	{
		//@formatter:off
		ON {
			@Override
			public String getName() { return "On"; };
		},
		HIGHLIGHTED {
			@Override
			public String getName() { return "Highlighted"; };
			@Override
			public boolean isHighlightProfile() { return true; }
		},
		COLOURED_BY_ID {
			@Override
			public String getName() { return "Coloured by Id"; };
			@Override
			public boolean isColourProfileById() { return true; }
		},
		COLOURED_BY_DEPTH {
			@Override
			public String getName() { return "Coloured by depth"; };
			@Override
			public boolean isColourProfileByDepth() { return true; }
		},
		COLOURED_BY_ORDER {
			@Override
			public String getName() { return "Coloured by order"; };
			@Override
			public boolean isColourProfileByOrder() { return true; }
		},
		WITH_CLUSTERS {
			@Override
			public String getName() { return "With clusters"; };
			@Override
			public boolean isDrawClusters() { return true; }
		},
		HIGHLIGHTED_WITH_CLUSTERS {
			@Override
			public String getName() { return "Highlighted with clusters"; };
			@Override
			public boolean isHighlightProfile() { return true; }
			@Override
			public boolean isDrawClusters() { return true; }
		},
		COLOURED_BY_ID_WITH_CLUSTERS {
			@Override
			public String getName() { return "Coloured by Id with clusters"; };
			@Override
			public boolean isColourProfileById() { return true; }
			@Override
			public boolean isDrawClusters() { return true; }
		},
		COLOURED_BY_DEPTH_WITH_CLUSTERS {
			@Override
			public String getName() { return "Coloured by depth with clusters"; };
			@Override
			public boolean isColourProfileByDepth() { return true; }
			@Override
			public boolean isDrawClusters() { return true; }
		},
		COLOURED_BY_ORDER_WITH_CLUSTERS {
			@Override
			public String getName() { return "Coloured by order with clusters"; };
			@Override
			public boolean isColourProfileByOrder() { return true; }
			@Override
			public boolean isDrawClusters() { return true; }
		},
		OFF {
			@Override
			public String getName() { return "Off"; };
		};
		//@formatter:on

		/**
		 * Gets the name.
		 *
		 * @return the name
		 */
		abstract public String getName();

		/**
		 * @return True if the profile should be highlighted for top-cluster regions
		 */
		public boolean isHighlightProfile()
		{
			return false;
		}

		/**
		 * @return True if the profile should be coloured using the OPTICS results
		 */
		public boolean isColourProfile()
		{
			return isColourProfileByDepth() || isColourProfileById() || isColourProfileByOrder();
		}

		/**
		 * @return True if the profile should be coloured using the cluster Id
		 */
		public boolean isColourProfileById()
		{
			return false;
		}

		/**
		 * @return True if the profile should be coloured using the cluster depth
		 */
		public boolean isColourProfileByDepth()
		{
			return false;
		}

		/**
		 * @return True if the profile should be coloured using the cluster order
		 */
		public boolean isColourProfileByOrder()
		{
			return false;
		}

		/**
		 * @return If clusters should be drawn on the plot
		 */
		public boolean isDrawClusters()
		{
			return false;
		}

		/**
		 * @return True if the clusters are needed
		 */
		public boolean requiresClusters()
		{
			return isDrawClusters() || isHighlightProfile() || isColourProfile();
		}

		@Override
		public String toString()
		{
			return getName();
		}
	}

	/**
	 * Options for plotting the OPTICS results
	 */
	public enum OutlineMode
	{
		//@formatter:off
		COLOURED_BY_CLUSTER {
			@Override
			public String getName() { return "Coloured by cluster"; };
		},
		COLOURED_BY_DEPTH {
			@Override
			public String getName() { return "Coloured by depth"; };
			@Override
			public boolean isColourByDepth() { return true; }
		},
		OFF {
			@Override
			public String getName() { return "Off"; };
			@Override
			public boolean isOutline() { return false; }
		};
		//@formatter:on

		/**
		 * Gets the name.
		 *
		 * @return the name
		 */
		abstract public String getName();

		/**
		 * @return True if the outline should be displayed
		 */
		public boolean isOutline()
		{
			return true;
		}

		/**
		 * @return True if the outline should be coloured using the cluster depth
		 */
		public boolean isColourByDepth()
		{
			return false;
		}

		@Override
		public String toString()
		{
			return getName();
		}
	}

	/**
	 * Options for plotting the OPTICS results
	 */
	public enum SpanningTreeMode
	{
		//@formatter:off
		COLOURED_BY_CLUSTER {
			@Override
			public String getName() { return "Coloured by cluster"; };
		},
		COLOURED_BY_DEPTH {
			@Override
			public String getName() { return "Coloured by depth"; };
			@Override
			public boolean isColourByDepth() { return true; }
		},
		COLOURED_BY_ORDER {
			@Override
			public String getName() { return "Coloured by order"; };
			@Override
			public boolean isColourByOrder() { return true; }
		},
		OFF {
			@Override
			public String getName() { return "Off"; };
			@Override
			public boolean isSpanningTree() { return false; }
		};
		//@formatter:on

		/**
		 * Gets the name.
		 *
		 * @return the name
		 */
		abstract public String getName();

		/**
		 * @return True if the spanning tree should be displayed
		 */
		public boolean isSpanningTree()
		{
			return true;
		}

		/**
		 * @return True if the spanning tree should be coloured using the cluster depth
		 */
		public boolean isColourByDepth()
		{
			return false;
		}

		/**
		 * @return True if the spanning tree should be coloured using the cluster order
		 */
		public boolean isColourByOrder()
		{
			return false;
		}

		@Override
		public String toString()
		{
			return getName();
		}
	}

	// Affect creating the OPTICS manager

	/**
	 * The input results dataset to use
	 */
	public String inputOption = "";

	// Affect running OPTICS

	/**
	 * The OPTICS algorithm to use.
	 */
	private OPTICSMode opticsMode = OPTICSMode.FAST_OPTICS;

	/** The number of splits to compute (if below 1 it will be auto-computed using the size of the data) */
	public int numberOfSplitSets = 0;

	/**
	 * Set to true to use random vectors for the projections. The default is to uniformly create vectors on the
	 * semi-circle interval.
	 */
	public boolean useRandomVectors = false;

	/**
	 * Set to true to save all sets that are approximately min split size. The default is to only save sets smaller than
	 * min split size.
	 */
	public boolean saveApproximateSets = false;

	/** The sample mode. */
	private SampleMode sampleMode = SampleMode.RANDOM;

	/**
	 * The generating distance, i.e. the distance to search for neighbours of a point. Set to zero to auto-calibrate
	 * using the expected density of uniformly spread random points.
	 */
	public double generatingDistance = 0;

	/**
	 * The minimum number of neighbours to define a core point.
	 * <p>
	 * Note that the minimum cardinality (i.e. count of the number of neighbours) in the paper discussing Generalised
	 * DBSCAN is recommended to be 2 x dimensions, so 4 for a 2D dataset.
	 */
	public int minPoints = 4;

	// OPTICS clustering

	/**
	 * The clustering mode to use on the OPTICS results.
	 */
	private ClusteringMode clusteringMode = ClusteringMode.XI;

	// Affect running OPTICS Xi

	/**
	 * The steepness parameter for the OPTICS hierarchical clustering algorithm using the reachability profile.
	 */
	public double xi = 0.03;
	/**
	 * Set to true to only show the top-level clusters, i.e. child clusters will be merged into their parents.
	 */
	public boolean topLevel = false;

	// Affect DBSCAN clustering

	/**
	 * The number of samples to take for the k-distance plot. This should be 1-10% of the data.
	 */
	public int samples = 100;
	/**
	 * The fraction of the data to sample for the k-distance plot. Recommended to be 1-10%.
	 */
	public double sampleFraction = 0.05;
	/**
	 * The fraction of noise in the k-distance plot. The clustering distance is set as the next distance after noise has
	 * been ignored.
	 */
	public double fractionNoise = 0.05;
	/**
	 * The clustering distance for DBSCAN.
	 */
	public double clusteringDistance = 0;
	/**
	 * Set to true to only include core point in clusters. Note: Non-core points can be assigned arbitrarily to clusters
	 * if they are on the border of two clusters due to the arbitrary processing order of input points.
	 */
	public boolean core = false;

	// Affect display of results

	/**
	 * The magnification scale of the output image
	 */
	public double imageScale = 2;
	/**
	 * The output image mode
	 */
	private ImageMode imageMode = ImageMode.VALUE;
	/**
	 * Set to true to weight the image data over nearest neighbour pixels
	 */
	public boolean weighted = true;
	/**
	 * Set to true to equalise the image histogram (allowing viewing high dynamic range data)
	 */
	public boolean equalised = true;

	/**
	 * The plot mode for the reachability distance profile
	 */
	private PlotMode plotMode = PlotMode.COLOURED_BY_DEPTH_WITH_CLUSTERS;

	/**
	 * The outline mode for the reachability distance profile
	 */
	private OutlineMode outlineMode = OutlineMode.COLOURED_BY_CLUSTER;

	/**
	 * The spanningTree mode for the reachability distance profile
	 */
	private SpanningTreeMode spanningTreeMode = SpanningTreeMode.OFF;

	public OPTICSMode getOPTICSMode()
	{
		return opticsMode;
	}

	public int getOPTICSModeOridinal()
	{
		if (opticsMode == null)
			return 0;
		return opticsMode.ordinal();
	}

	public void setOPTICSMode(OPTICSMode mode)
	{
		opticsMode = mode;
	}

	public void setOPTICSMode(int mode)
	{
		OPTICSMode[] values = OPTICSMode.values();
		if (mode < 0 || mode >= values.length)
			mode = 0;
		this.opticsMode = values[mode];
	}

	public ImageMode getImageMode()
	{
		return imageMode;
	}

	public int getImageModeOridinal()
	{
		if (imageMode == null)
			return 0;
		return imageMode.ordinal();
	}

	public void setImageMode(ImageMode mode)
	{
		imageMode = mode;
	}

	public void setImageMode(int mode)
	{
		ImageMode[] values = ImageMode.values();
		if (mode < 0 || mode >= values.length)
			mode = 0;
		this.imageMode = values[mode];
	}

	public SampleMode getSampleMode()
	{
		return sampleMode;
	}

	public int getSampleModeOridinal()
	{
		if (sampleMode == null)
			return 0;
		return sampleMode.ordinal();
	}

	public void setSampleMode(SampleMode mode)
	{
		sampleMode = mode;
	}

	public void setSampleMode(int mode)
	{
		SampleMode[] values = SampleMode.values();
		if (mode < 0 || mode >= values.length)
			mode = 0;
		this.sampleMode = values[mode];
	}

	public ClusteringMode getClusteringMode()
	{
		return clusteringMode;
	}

	public int getClusteringModeOridinal()
	{
		if (clusteringMode == null)
			return 0;
		return clusteringMode.ordinal();
	}

	public void setClusteringMode(ClusteringMode mode)
	{
		clusteringMode = mode;
	}

	public void setClusteringMode(int mode)
	{
		ClusteringMode[] values = ClusteringMode.values();
		if (mode < 0 || mode >= values.length)
			mode = 0;
		this.clusteringMode = values[mode];
	}

	public PlotMode getPlotMode()
	{
		return plotMode;
	}

	public int getPlotModeOridinal()
	{
		if (plotMode == null)
			return 0;
		return plotMode.ordinal();
	}

	public void setPlotMode(PlotMode mode)
	{
		plotMode = mode;
	}

	public void setPlotMode(int mode)
	{
		PlotMode[] values = PlotMode.values();
		if (mode < 0 || mode >= values.length)
			mode = 0;
		this.plotMode = values[mode];
	}

	public OutlineMode getOutlineMode()
	{
		return outlineMode;
	}

	public int getOutlineModeOridinal()
	{
		if (outlineMode == null)
			return 0;
		return outlineMode.ordinal();
	}

	public void setOutlineMode(OutlineMode mode)
	{
		outlineMode = mode;
	}

	public void setOutlineMode(int mode)
	{
		OutlineMode[] values = OutlineMode.values();
		if (mode < 0 || mode >= values.length)
			mode = 0;
		this.outlineMode = values[mode];
	}

	public SpanningTreeMode getSpanningTreeMode()
	{
		return spanningTreeMode;
	}

	public int getSpanningTreeModeOridinal()
	{
		if (spanningTreeMode == null)
			return 0;
		return spanningTreeMode.ordinal();
	}

	public void setSpanningTreeMode(SpanningTreeMode mode)
	{
		spanningTreeMode = mode;
	}

	public void setSpanningTreeMode(int mode)
	{
		SpanningTreeMode[] values = SpanningTreeMode.values();
		if (mode < 0 || mode >= values.length)
			mode = 0;
		this.spanningTreeMode = values[mode];
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public OPTICSSettings clone()
	{
		try
		{
			return (OPTICSSettings) super.clone();
		}
		catch (CloneNotSupportedException ex)
		{
			return null;
		}
	}
}
