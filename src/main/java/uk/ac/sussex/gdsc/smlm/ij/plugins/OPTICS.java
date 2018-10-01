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
package uk.ac.sussex.gdsc.smlm.ij.plugins;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.TreeSet;

import org.apache.commons.math3.random.Well19937c;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PlotCanvas;
import ij.gui.PlotWindow;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import ij.process.FloatPolygon;
import ij.process.LUT;
import ij.text.TextPanel;
import ij.text.TextWindow;
import uk.ac.sussex.gdsc.core.clustering.optics.ClusteringResult;
import uk.ac.sussex.gdsc.core.clustering.optics.DBSCANResult;
import uk.ac.sussex.gdsc.core.clustering.optics.OPTICSCluster;
import uk.ac.sussex.gdsc.core.clustering.optics.OPTICSManager;
import uk.ac.sussex.gdsc.core.clustering.optics.OPTICSManager.Option;
import uk.ac.sussex.gdsc.core.clustering.optics.OPTICSResult;
import uk.ac.sussex.gdsc.core.clustering.optics.SampleMode;
import uk.ac.sussex.gdsc.core.data.detection.BinarySearchDetectionGrid;
import uk.ac.sussex.gdsc.core.data.detection.DetectionGrid;
import uk.ac.sussex.gdsc.core.ij.BufferedTextWindow;
import uk.ac.sussex.gdsc.core.ij.IJTrackProgress;
import uk.ac.sussex.gdsc.core.ij.Utils;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.ij.gui.NonBlockingExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.ij.gui.Plot2;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog.OptionCollectedEvent;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog.OptionCollectedListener;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog.OptionListener;
import uk.ac.sussex.gdsc.core.ij.process.LUTHelper;
import uk.ac.sussex.gdsc.core.ij.process.LUTHelper.LUTMapper;
import uk.ac.sussex.gdsc.core.ij.process.LUTHelper.LutColour;
import uk.ac.sussex.gdsc.core.ij.text.TextWindow2;
import uk.ac.sussex.gdsc.core.match.RandIndex;
import uk.ac.sussex.gdsc.core.utils.ConvexHull;
import uk.ac.sussex.gdsc.core.utils.Maths;
import uk.ac.sussex.gdsc.core.utils.NotImplementedException;
import uk.ac.sussex.gdsc.core.utils.Settings;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.Sort;
import uk.ac.sussex.gdsc.core.utils.TextUtils;
import uk.ac.sussex.gdsc.core.utils.TurboList;
import uk.ac.sussex.gdsc.smlm.data.config.GUIProtos.OpticsEventSettings;
import uk.ac.sussex.gdsc.smlm.data.config.GUIProtos.OpticsSettings;
import uk.ac.sussex.gdsc.smlm.data.config.UnitHelper;
import uk.ac.sussex.gdsc.smlm.data.config.UnitProtos.DistanceUnit;
import uk.ac.sussex.gdsc.smlm.ij.plugins.ResultsManager.InputSource;
import uk.ac.sussex.gdsc.smlm.ij.results.IJImagePeakResults;
import uk.ac.sussex.gdsc.smlm.ij.results.IJTablePeakResults;
import uk.ac.sussex.gdsc.smlm.ij.settings.SettingsManager;
import uk.ac.sussex.gdsc.smlm.results.IdPeakResult;
import uk.ac.sussex.gdsc.smlm.results.MemoryPeakResults;
import uk.ac.sussex.gdsc.smlm.results.PeakResult;
import uk.ac.sussex.gdsc.smlm.results.Trace;
import uk.ac.sussex.gdsc.smlm.results.count.Counter;
import uk.ac.sussex.gdsc.smlm.results.procedures.PeakResultProcedure;
import uk.ac.sussex.gdsc.smlm.results.procedures.StandardResultProcedure;
import uk.ac.sussex.gdsc.smlm.utils.Pair;

/**
 * Run the OPTICS algorithm on the peak results.
 * <p>
 * This is an implementation of the OPTICS method. Mihael Ankerst, Markus M Breunig, Hans-Peter Kriegel, and Jorg
 * Sander. Optics: ordering points to identify the clustering structure. In ACM Sigmod Record, volume 28, pages
 * 49–60. ACM, 1999.
 */
public class OPTICS implements PlugIn
{
    /**
     * Options for displaying the clustering image
     */
    public enum ImageMode
    {
        //@formatter:off
		/** Cluster ID */
		CLUSTER_ID {
			@Override
			public String getName() { return "Cluster Id"; }
			@Override
			public float getValue(float value, int clusterId, int order) { return clusterId; }
			@Override
			public boolean isMapped() { return true; }
			@Override
			public boolean isRequiresClusters() { return true; }
		},
		/** Cluster Depth */
		CLUSTER_DEPTH {
			@Override
			public String getName() { return "Cluster Depth"; }
			@Override
			public float getValue(float value, int clusterId, int order) { return clusterId; }
			@Override
			public boolean isMapped() { return true; }
			@Override
			public boolean isRequiresClusters() { return true; }
		},
		/** Cluster Order */
		CLUSTER_ORDER {
			@Override
			public String getName() { return "Cluster Order"; }
			@Override
			public float getValue(float value, int clusterId, int order) { return order; }
			@Override
			public boolean isMapped() { return true; }
			@Override
			public boolean isRequiresClusters() { return true; }
		},
		/** Value */
		VALUE {
			@Override
			public String getName() { return "Value"; }
			@Override
			public boolean canBeWeighted() { return true; }
			@Override
			public float getValue(float value, int clusterId, int order) { return value; }
		},
		/** Count */
		COUNT {
			@Override
			public String getName() { return "Count"; }
			@Override
			public boolean canBeWeighted() { return true; }
			@Override
			public float getValue(float value, int clusterId, int order) { return 1f; }
		},
		/** Local Outlier Probability (LoOP) */
		LOOP {
			@Override
			public String getName() { return "Local Outlier Probability (LoOP)"; }
			@Override
			public float getValue(float value, int clusterId, int order) { return order; }
			@Override
			public boolean isMapped() { return true; }
		},
		/** None */
		NONE {
			@Override
			public String getName() { return "None"; }
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

        /**
         * Gets the image mode from the ordinal.
         *
         * @param ordinal
         *            the ordinal
         * @return the image mode
         */
        public static ImageMode get(int ordinal)
        {
            if (ordinal < 0 || ordinal >= values().length)
                ordinal = 0;
            return values()[ordinal];
        }
    }

    /**
     * Options for plotting the OPTICS algorithm
     */
    public enum OpticsMode
    {
        //@formatter:off
		/** FastOPTICS */
		FAST_OPTICS {
			@Override
			public String getName() { return "FastOPTICS"; }
		},
		/** OPTICS */
		OPTICS {
			@Override
			public String getName() { return "OPTICS"; }
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

        /**
         * Gets the optics mode from the ordinal.
         *
         * @param ordinal
         *            the ordinal
         * @return the optics mode
         */
        public static OpticsMode get(int ordinal)
        {
            if (ordinal < 0 || ordinal >= values().length)
                ordinal = 0;
            return values()[ordinal];
        }
    }

    /**
     * Options for plotting the OPTICS results
     */
    public enum ClusteringMode
    {
        //@formatter:off
		/** Xi */
		XI {
			@Override
			public String getName() { return "Xi"; }
		},
		/** pseudo-DBSCAN */
		DBSCAN {
			@Override
			public String getName() { return "pseudo-DBSCAN"; }
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

        /**
         * Gets the clustering mode from the ordinal.
         *
         * @param ordinal
         *            the ordinal
         * @return the clustering mode
         */
        public static ClusteringMode get(int ordinal)
        {
            if (ordinal < 0 || ordinal >= values().length)
                ordinal = 0;
            return values()[ordinal];
        }
    }

    /**
     * Options for plotting the OPTICS results
     */
    public enum PlotMode
    {
        //@formatter:off
		/** On */
		ON {
			@Override
			public String getName() { return "On"; }
		},
		/** Highlighted */
		HIGHLIGHTED {
			@Override
			public String getName() { return "Highlighted"; }
			@Override
			public boolean isHighlightProfile() { return true; }
		},
		/** Coloured by Id */
		COLOURED_BY_ID {
			@Override
			public String getName() { return "Coloured by Id"; }
			@Override
			public boolean isColourProfileById() { return true; }
		},
		/** Coloured by depth */
		COLOURED_BY_DEPTH {
			@Override
			public String getName() { return "Coloured by depth"; }
			@Override
			public boolean isColourProfileByDepth() { return true; }
		},
		/** Coloured by order */
		COLOURED_BY_ORDER {
			@Override
			public String getName() { return "Coloured by order"; }
			@Override
			public boolean isColourProfileByOrder() { return true; }
		},
		/** With clusters */
		WITH_CLUSTERS {
			@Override
			public String getName() { return "With clusters"; }
			@Override
			public boolean isDrawClusters() { return true; }
		},
		/** Highlighted with clusters */
		HIGHLIGHTED_WITH_CLUSTERS {
			@Override
			public String getName() { return "Highlighted with clusters"; }
			@Override
			public boolean isHighlightProfile() { return true; }
			@Override
			public boolean isDrawClusters() { return true; }
		},
		/** Coloured by Id with clusters */
		COLOURED_BY_ID_WITH_CLUSTERS {
			@Override
			public String getName() { return "Coloured by Id with clusters"; }
			@Override
			public boolean isColourProfileById() { return true; }
			@Override
			public boolean isDrawClusters() { return true; }
		},
		/** Coloured by depth with clusters */
		COLOURED_BY_DEPTH_WITH_CLUSTERS {
			@Override
			public String getName() { return "Coloured by depth with clusters"; }
			@Override
			public boolean isColourProfileByDepth() { return true; }
			@Override
			public boolean isDrawClusters() { return true; }
		},
		/** Coloured by order with clusters */
		COLOURED_BY_ORDER_WITH_CLUSTERS {
			@Override
			public String getName() { return "Coloured by order with clusters"; }
			@Override
			public boolean isColourProfileByOrder() { return true; }
			@Override
			public boolean isDrawClusters() { return true; }
		},
		/** Off */
		OFF {
			@Override
			public String getName() { return "Off"; }
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

        /**
         * Gets the plot mode from the ordinal.
         *
         * @param ordinal
         *            the ordinal
         * @return the plot mode
         */
        public static PlotMode get(int ordinal)
        {
            if (ordinal < 0 || ordinal >= values().length)
                ordinal = 0;
            return values()[ordinal];
        }
    }

    /**
     * Options for plotting the OPTICS results
     */
    public enum OutlineMode
    {
        //@formatter:off
		/** Coloured by cluster */
		COLOURED_BY_CLUSTER {
			@Override
			public String getName() { return "Coloured by cluster"; }
		},
		/** Coloured by depth */
		COLOURED_BY_DEPTH {
			@Override
			public String getName() { return "Coloured by depth"; }
			@Override
			public boolean isColourByDepth() { return true; }
		},
		/** Off */
		OFF {
			@Override
			public String getName() { return "Off"; }
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

        /**
         * Gets the outline mode from the ordinal.
         *
         * @param ordinal
         *            the ordinal
         * @return the outline mode
         */
        public static OutlineMode get(int ordinal)
        {
            if (ordinal < 0 || ordinal >= values().length)
                ordinal = 0;
            return values()[ordinal];
        }
    }

    /**
     * Options for plotting the OPTICS results
     */
    public enum SpanningTreeMode
    {
        //@formatter:off
		/** Coloured by cluster */
		COLOURED_BY_CLUSTER {
			@Override
			public String getName() { return "Coloured by cluster"; }
		},
		/** Coloured by depth */
		COLOURED_BY_DEPTH {
			@Override
			public String getName() { return "Coloured by depth"; }
		},
		/** Coloured by order */
		COLOURED_BY_ORDER {
			@Override
			public String getName() { return "Coloured by order"; }
		},
		/** Coloured by LoOP */
		COLOURED_BY_LOOP {
			@Override
			public String getName() { return "Coloured by LoOP"; }
		},
		/** Off */
		OFF {
			@Override
			public String getName() { return "Off"; }
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

        @Override
        public String toString()
        {
            return getName();
        }

        /**
         * Gets the spanning tree mode from the ordinal.
         *
         * @param ordinal
         *            the ordinal
         * @return the spanning tree mode
         */
        public static SpanningTreeMode get(int ordinal)
        {
            if (ordinal < 0 || ordinal >= values().length)
                ordinal = 0;
            return values()[ordinal];
        }
    }

    /**
     * Options for sorting the table
     */
    public enum TableSortMode
    {
        //@formatter:off
		/** Sort by Id */
		ID {
			@Override
			public String getName() { return "Id"; }
		},
		/** Sort by size */
		SIZE {
			@Override
			public String getName() { return "Size"; }
		},
		/** Sort by level */
		LEVEL {
			@Override
			public String getName() { return "Level"; }
		},
		/** Sort by area */
		AREA {
			@Override
			public String getName() { return "Area"; }
		},
		/** Sort by density */
		DENSITY {
			@Override
			public String getName() { return "Density"; }
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

        /**
         * Gets the table sort mode from the ordinal.
         *
         * @param ordinal
         *            the ordinal
         * @return the table sort mode
         */
        public static TableSortMode get(int ordinal)
        {
            if (ordinal < 0 || ordinal >= values().length)
                ordinal = 0;
            return values()[ordinal];
        }
    }

    private static final String TITLE_OPTICS = "OPTICS";
    private static final String TITLE_DBSCAN = "DBSCAN";

    private static LUT clusterLut;
    private static LUT valueLut;
    private static LUT clusterDepthLut;
    private static LUT clusterOrderLut;
    private static LUT loopLut;
    static
    {
        valueLut = LUTHelper.createLUT(LutColour.FIRE);

        // Need to be able to see all colours against white (plot) or black (image) background
        final LUT fireGlow = LUTHelper.createLUT(LutColour.FIRE_GLOW, true);
        // Clusters are scrambled so use a LUT with colours that are visible easily visible on black/white.
        // Note: The colours returned by LUTHelper.getColorModel() can be close to black.
        clusterLut = LUTHelper.createLUT(LutColour.PIMP_LIGHT, true);
        clusterDepthLut = fireGlow;
        clusterOrderLut = fireGlow;

        // This is only used on the image so can include white
        loopLut = LUTHelper.createLUT(LutColour.FIRE_LIGHT, true);
    }

    /**
     * Event generated in the GUI when clusters are selected
     */
    private abstract class ClusterSelectedEvent
    {
        int source;
        int[] clusters;

        ClusterSelectedEvent(int source)
        {
            this.source = source;
        }

        /**
         * Gets the source generating the selection.
         *
         * @return the source
         */
        int getSource()
        {
            return source;
        }

        /**
         * Gets the clusters.
         *
         * @return the clusters
         */
        int[] getClusters()
        {
            if (clusters == null)
                clusters = computeClusters();
            return clusters;
        }

        /**
         * Compute clusters.
         *
         * @return the clusters
         */
        abstract int[] computeClusters();
    }

    /**
     * Interface for any class that can respond to cluster selected events.
     */
    private interface ClusterSelectedHandler
    {
        void clusterSelected(ClusterSelectedEvent e);
    }

    /**
     * Compute which clusters were selected
     */
    private class ClusterSelectedEventWorker extends WorkflowWorker<ClusterSelectedEvent, int[]>
    {
        @Override
        public boolean equalSettings(ClusterSelectedEvent current, ClusterSelectedEvent previous)
        {
            // Return false to always run doWork
            return false;
        }

        @Override
        public boolean equalResults(int[] current, int[] previous)
        {
            // We ignore this
            return true;
        }

        @Override
        public Pair<ClusterSelectedEvent, int[]> doWork(Pair<ClusterSelectedEvent, int[]> work)
        {
            final int[] clusters = work.a.getClusters();
            if (IJ.debugMode)
                IJ.log("ClusterSelected: " + Arrays.toString(clusters));
            return new Pair<>(work.a, clusters);
        }
    }

    /**
     * Relay the selected clusters to all the handlers
     */
    private class ClusterSelectedWorker extends WorkflowWorker<ClusterSelectedEvent, int[]>
    {
        TurboList<ClusterSelectedHandler> handlers = new TurboList<>();

        @Override
        public boolean equalSettings(ClusterSelectedEvent current, ClusterSelectedEvent previous)
        {
            // We ignore this
            return true;
        }

        @Override
        public boolean equalResults(int[] current, int[] previous)
        {
            return (Arrays.equals(current, previous));
        }

        @Override
        public Pair<ClusterSelectedEvent, int[]> doWork(Pair<ClusterSelectedEvent, int[]> work)
        {
            for (final ClusterSelectedHandler h : handlers)
                h.clusterSelected(work.a);
            return work;
        }

        void addHandler(ClusterSelectedHandler h)
        {
            handlers.add(h);
        }
    }

    // Stack to handle events that selected certain clusters
    private Workflow<ClusterSelectedEvent, int[]> eventWorkflow = null;
    // The worker that will relay all the selected clusters
    private ClusterSelectedWorker clusterSelectedWorker;

    private String TITLE;

    private OpticsSettings.Builder inputSettings;

    private boolean extraOptions, preview, debug;

    // Stack to which the work is first added
    private final Workflow<OpticsSettings, Settings> workflow = new Workflow<>();

    private static int WORKER_ID = 0;

    private abstract class BaseWorker extends WorkflowWorker<OpticsSettings, Settings>
    {
        final int id;

        BaseWorker()
        {
            id = WORKER_ID++;
            // When constructing the workflow automatically add any workers
            // that can handle cluster selections
            if (this instanceof ClusterSelectedHandler)
                clusterSelectedWorker.addHandler((ClusterSelectedHandler) this);
        }

        @Override
        public boolean equalResults(Settings current, Settings previous)
        {
            if (current == null)
                return previous == null;
            return current.equals(previous);
        }
    }

    private class InputWorker extends BaseWorker
    {
        @Override
        public boolean equalSettings(OpticsSettings current, OpticsSettings previous)
        {
            // Nothing in the settings effects if we have to create a new OPTICS manager
            return true;
        }

        @Override
        public Pair<OpticsSettings, Settings> doWork(Pair<OpticsSettings, Settings> work)
        {
            // The first item should be the memory peak results
            final OpticsSettings settings = work.a;
            final Settings resultList = work.b;
            final MemoryPeakResults results = (MemoryPeakResults) resultList.get(0);
            // Convert results to coordinates
            final StandardResultProcedure p = new StandardResultProcedure(results, DistanceUnit.PIXEL);
            p.getXY();
            final Rectangle bounds = results.getBounds(true);
            final OPTICSManager opticsManager = new OPTICSManager(p.x, p.y, bounds);
            opticsManager.setTracker(new IJTrackProgress());
            opticsManager.setOptions(Option.CACHE);
            return new Pair<>(settings, new Settings(results, opticsManager));
        }
    }

    /**
     * Encapsulate the clustering result and provide cached access to all the desired properties via synchronised blocks
     * around the clustering result.
     */
    private static class CachedClusteringResult
    {
        // To allow detection of stale cached data
        static int ID;
        final int id;

        final boolean isOPTICS;
        final ClusteringResult clusteringResult;

        // All data that any worker may want to access.
        // This only need be computed once for each new clustering result.
        int max;
        int[] clusters;
        int[] topClusters;
        double[] profile;
        int[] order;
        int[] predecessor;
        ArrayList<OPTICSCluster> allClusters;
        Rectangle2D[] bounds;
        ConvexHull[] hulls;
        int[] size;
        int[] level;

        CachedClusteringResult(ClusteringResult clusteringResult)
        {
            id = ++ID;
            this.clusteringResult = clusteringResult;
            if (clusteringResult != null)
                isOPTICS = clusteringResult instanceof OPTICSResult;
            else
                isOPTICS = false;
        }

        /**
         * Checks if this is the current clustering result. If new results have been created then the cached results are
         * effectively stale and should not be trusted.
         * <p>
         * This should be used by any worker that keeps a copy of the cached results, e.g. when responding to
         * ClusterSelectedEvents there is no point responding when the represented results are not current so avoiding
         * cluster ID mismatches.
         *
         * @return true, if is current
         */
        boolean isCurrent()
        {
            return id == ID;
        }

        /**
         * Gets the OPTICS result.
         *
         * @return the OPTICS result
         */
        OPTICSResult getOPTICSResult()
        {
            return (isOPTICS) ? (OPTICSResult) clusteringResult : null;
        }

        /**
         * Gets the DBSCAN result.
         *
         * @return the DBSCAN result
         */
        DBSCANResult getDBSCANResult()
        {
            return (isOPTICS) ? null : (DBSCANResult) clusteringResult;
        }

        /**
         * Checks if is a valid clustering result.
         *
         * @return true, if is valid
         */
        boolean isValid()
        {
            return clusteringResult != null;
        }

        int[] getClusters()
        {
            if (clusters == null)
                synchronized (clusteringResult)
                {
                    if (clusters == null)
                    {
                        clusters = clusteringResult.getClusters();
                        max = Maths.max(clusters);
                        if (isOPTICS)
                            topClusters = ((OPTICSResult) clusteringResult).getTopLevelClusters(false);
                    }
                }
            return clusters;
        }

        int getMaxClusterId()
        {
            getClusters();
            return max;
        }

        int[] getTopClusters()
        {
            if (isOPTICS)
                getClusters();
            return topClusters;
        }

        double[] getProfile(double nmPerPixel)
        {
            if (isOPTICS && profile == null)
                synchronized (clusteringResult)
                {
                    // Check it has not already been created by another thread
                    if (profile == null)
                    {
                        profile = ((OPTICSResult) clusteringResult).getReachabilityDistanceProfile(true);
                        if (nmPerPixel != 1)
                            for (int i = 0; i < profile.length; i++)
                                profile[i] *= nmPerPixel;
                    }
                }
            return profile;
        }

        int[] getOrder()
        {
            if (isOPTICS && order == null)
                synchronized (clusteringResult)
                {
                    // Check it has not already been created by another thread
                    if (order == null)
                        order = ((OPTICSResult) clusteringResult).getOrder();
                }
            return order;
        }

        int[] getPredecessor()
        {
            if (isOPTICS && predecessor == null)
                synchronized (clusteringResult)
                {
                    // Check it has not already been created by another thread
                    if (predecessor == null)
                        predecessor = ((OPTICSResult) clusteringResult).getPredecessor();
                }
            return predecessor;
        }

        ArrayList<OPTICSCluster> getAllClusters()
        {
            if (isOPTICS && allClusters == null)
                synchronized (clusteringResult)
                {
                    // Check it has not already been created by another thread
                    if (allClusters == null)
                        allClusters = ((OPTICSResult) clusteringResult).getAllClusters();
                }
            return allClusters;
        }

        /**
         * Gets the bounds. The bounds are stored for each cluster id. Index 0 is null as this is not a cluster ID.
         *
         * @return the bounds
         */
        Rectangle2D[] getBounds()
        {
            if (bounds == null)
                synchronized (clusteringResult)
                {
                    // Check it has not already been created by another thread
                    if (bounds == null)
                    {
                        clusteringResult.computeConvexHulls();
                        bounds = new Rectangle2D[getMaxClusterId() + 1];
                        hulls = new ConvexHull[bounds.length];
                        for (int c = 1; c <= max; c++)
                        {
                            bounds[c] = clusteringResult.getBounds(c);
                            hulls[c] = clusteringResult.getConvexHull(c);
                        }
                    }
                }
            return bounds;
        }

        /**
         * Gets the hulls. The hulls are stored for each cluster id. Index 0 is null as this is not a cluster ID.
         *
         * @return the hulls
         */
        ConvexHull[] getHulls()
        {
            getBounds();
            return hulls;
        }

        int[] getSize()
        {
            if (size == null)
                synchronized (clusteringResult)
                {
                    // Check it has not already been created by another thread
                    if (size == null)
                    {
                        size = new int[getMaxClusterId() + 1];
                        level = new int[size.length];
                        if (isOPTICS)
                            // We need to account for hierarchical clusters
                            for (final OPTICSCluster c : getAllClusters())
                            {
                                level[c.getClusterId()] = c.getLevel();
                                size[c.getClusterId()] = c.size();
                            }
                        else
                            // Flat clustering (i.e. no levels)
                            for (int i = 0; i < clusters.length; i++)
                                size[clusters[i]]++;
                    }
                }
            return size;
        }

        int[] getLevel()
        {
            getSize();
            return level;
        }

        int[] lastClusterIds;
        int[] parents;

        public synchronized int[] getParents(int[] clusterIds)
        {
            // This can be cached if the ids are the same
            if (Arrays.equals(lastClusterIds, clusterIds))
                return parents;

            lastClusterIds = clusterIds;
            synchronized (clusteringResult)
            {
                return parents = clusteringResult.getParents(clusterIds);
            }
        }
    }

    private class OpticsWorker extends BaseWorker
    {
        @Override
        public boolean equalSettings(OpticsSettings current, OpticsSettings previous)
        {
            if (current.getMinPoints() != previous.getMinPoints())
                return false;
            if (current.getOpticsMode() != previous.getOpticsMode())
                return false;
            if (current.getOpticsMode() == OpticsMode.OPTICS.ordinal())
            {
                if (current.getGeneratingDistance() != previous.getGeneratingDistance())
                    return false;
            }
            else
            {
                if (current.getNumberOfSplitSets() != previous.getNumberOfSplitSets())
                    return false;
                if (extraOptions)
                {
                    if (current.getUseRandomVectors() != previous.getUseRandomVectors())
                        return false;
                    if (current.getSaveApproximateSets() != previous.getSaveApproximateSets())
                        return false;
                    if (current.getSampleMode() != previous.getSampleMode())
                        return false;
                }
            }
            return true;
        }

        @Override
        public Pair<OpticsSettings, Settings> doWork(Pair<OpticsSettings, Settings> work)
        {
            final OpticsSettings settings = work.a;
            final Settings resultList = work.b;

            // The first item should be the memory peak results
            final MemoryPeakResults results = (MemoryPeakResults) resultList.get(0);
            // The second item should be the OPTICS manager
            final OPTICSManager opticsManager = (OPTICSManager) resultList.get(1);

            final int minPts = settings.getMinPoints();

            OPTICSResult opticsResult;
            if (settings.getOpticsMode() == OpticsMode.FAST_OPTICS.ordinal())
            {
                final int n = settings.getNumberOfSplitSets();
                // Q. Should these be options
                boolean useRandomVectors = false;
                boolean saveApproximateSets = false;
                SampleMode sampleMode = SampleMode.RANDOM;
                if (extraOptions)
                {
                    useRandomVectors = settings.getUseRandomVectors();
                    saveApproximateSets = settings.getSaveApproximateSets();
                    sampleMode = SampleMode.get(settings.getSampleMode());
                }
                synchronized (opticsManager)
                {
                    opticsManager.setNumberOfThreads(Prefs.getThreads());
                    opticsResult = opticsManager.fastOptics(minPts, n, n, useRandomVectors, saveApproximateSets,
                            sampleMode);
                }
            }
            else
            {
                double distance = settings.getGeneratingDistance();
                if (distance > 0)
                {
                    // Convert generating distance to pixels
                    final double nmPerPixel = getNmPerPixel(results);
                    if (nmPerPixel != 1)
                    {
                        final double newDistance = distance / nmPerPixel;
                        Utils.log(TITLE + ": Converting generating distance %s nm to %s pixels",
                                Utils.rounded(distance), Utils.rounded(newDistance));
                        distance = newDistance;
                    }
                }
                else
                {
                    final double nmPerPixel = getNmPerPixel(results);
                    if (nmPerPixel != 1)
                        Utils.log(TITLE + ": Default generating distance %s nm",
                                Utils.rounded(opticsManager.computeGeneratingDistance(minPts) * nmPerPixel));
                }

                synchronized (opticsManager)
                {
                    opticsResult = opticsManager.optics((float) distance, minPts);
                }
            }
            // It may be null if cancelled. However return null Work will close down the next thread
            return new Pair<>(settings, new Settings(results, opticsManager, new CachedClusteringResult(opticsResult)));
        }
    }

    private class OpticsClusterWorker extends BaseWorker
    {
        @Override
        public boolean equalSettings(OpticsSettings current, OpticsSettings previous)
        {
            if (current.getClusteringMode() != previous.getClusteringMode())
                return false;
            if (current.getClusteringMode() == ClusteringMode.XI.ordinal())
            {
                if (current.getXi() != previous.getXi())
                    return false;
                if (current.getTopLevel() != previous.getTopLevel())
                    return false;
                if (current.getUpperLimit() != previous.getUpperLimit())
                    return false;
                if (current.getLowerLimit() != previous.getLowerLimit())
                    return false;
            }
            else
            {
                if (current.getClusteringDistance() != previous.getClusteringDistance())
                    return false;
                if (current.getCore() != previous.getCore())
                    return false;
            }
            return true;
        }

        @Override
        public Pair<OpticsSettings, Settings> doWork(Pair<OpticsSettings, Settings> work)
        {
            final OpticsSettings settings = work.a;
            final Settings resultList = work.b;

            final MemoryPeakResults results = (MemoryPeakResults) resultList.get(0);
            final OPTICSManager opticsManager = (OPTICSManager) resultList.get(1);
            CachedClusteringResult clusteringResult = (CachedClusteringResult) resultList.get(2);
            // It may be invalid if cancelled.
            if (clusteringResult.isValid())
            {
                final OPTICSResult opticsResult = clusteringResult.getOPTICSResult();

                int nClusters = 0;
                synchronized (opticsResult)
                {
                    final double nmPerPixel = getNmPerPixel(results);

                    if (settings.getClusteringMode() == ClusteringMode.XI.ordinal())
                    {
                        int options = (settings.getTopLevel()) ? OPTICSResult.XI_OPTION_TOP_LEVEL : 0;
                        // Always include these as they are ignored if invalid
                        options |= OPTICSResult.XI_OPTION_UPPER_LIMIT | OPTICSResult.XI_OPTION_LOWER_LIMIT;
                        opticsResult.setUpperLimit(settings.getUpperLimit() / nmPerPixel);
                        opticsResult.setLowerLimit(settings.getLowerLimit() / nmPerPixel);
                        opticsResult.extractClusters(settings.getXi(), options);
                    }
                    else
                    {
                        double distance;
                        if (settings.getOpticsMode() == OpticsMode.FAST_OPTICS.ordinal())
                        {
                            if (settings.getClusteringDistance() > 0)
                                distance = settings.getClusteringDistance();
                            else
                            {
                                distance = opticsManager.computeGeneratingDistance(settings.getMinPoints()) *
                                        nmPerPixel;
                                if (nmPerPixel != 1)
                                    Utils.log(TITLE + ": Default clustering distance %s nm", Utils.rounded(distance));
                            }
                        }
                        else
                        {
                            // Ensure that the distance is valid
                            distance = opticsResult.generatingDistance * nmPerPixel;
                            if (settings.getClusteringDistance() > 0)
                                distance = Math.min(settings.getClusteringDistance(), distance);
                        }

                        if (nmPerPixel != 1)
                        {
                            final double newDistance = distance / nmPerPixel;
                            Utils.log(TITLE + ": Converting clustering distance %s nm to %s pixels",
                                    Utils.rounded(distance), Utils.rounded(newDistance));
                            distance = newDistance;
                        }

                        opticsResult.extractDBSCANClustering((float) distance, settings.getCore());
                    }
                    nClusters = opticsResult.getNumberOfClusters();
                    // We must scramble after extracting the clusters since the cluster Ids have been rewritten
                    scrambleClusters(opticsResult);
                }

                Utils.log("Clustering mode: %s = %s", settings.getClusteringMode(),
                        TextUtils.pleural(nClusters, "Cluster"));

                // We created a new clustering so create a new WorkerResult
                clusteringResult = new CachedClusteringResult(opticsResult);
                return new Pair<>(settings, new Settings(results, opticsManager, clusteringResult));
            }
            return work;
        }
    }

    private class ResultsWorker extends BaseWorker
    {
        @Override
        public boolean equalSettings(OpticsSettings current, OpticsSettings previous)
        {
            // Only depends on if the clustering results are new. This is triggered
            // in the default comparison of the Settings object.
            return true;
        }

        @Override
        public Pair<OpticsSettings, Settings> doWork(Pair<OpticsSettings, Settings> work)
        {
            final Settings resultList = work.b;
            // The result is in position 2.
            final CachedClusteringResult clusteringResult = (CachedClusteringResult) resultList.get(2);
            if (!clusteringResult.isValid())
                // Only log here so it happens once
                IJ.log(TITLE + ": No results to display");
            return work;
        }
    }

    private class ClusterResult
    {
        final int n1, n2;
        final int[] c1, c2;

        ClusterResult(int[] clusters, int[] topClusters)
        {
            // The original set of clusters does not need to be compacted
            n1 = Maths.max(clusters) + 1;
            this.c1 = clusters;
            if (topClusters != null)
            {
                // The top clusters from OPTICS may contain non-sequential integers
                n2 = RandIndex.compact(topClusters);
                this.c2 = topClusters;
            }
            else
            {
                n2 = 0;
                c2 = null;
            }
        }
    }

    private class RandIndexWorker extends BaseWorker
    {
        Queue<ClusterResult> queue = new LinkedList<>();

        @Override
        public boolean equalSettings(OpticsSettings current, OpticsSettings previous)
        {
            if (!current.getInputOption().equals(previous.getInputOption()))
            {
                // We only cache results for the same set of raw results, i.e. the input coordinates.
                queue.clear();
                return false;
            }

            return true;
        }

        @Override
        public Pair<OpticsSettings, Settings> doWork(Pair<OpticsSettings, Settings> work)
        {
            final Settings resultList = work.b;
            final CachedClusteringResult clusteringResult = (CachedClusteringResult) resultList.get(2);
            if (!clusteringResult.isValid())
                return work;

            final int[] clusters = clusteringResult.getClusters();
            final int[] topClusters = clusteringResult.getTopClusters();

            final ClusterResult current = new ClusterResult(clusters, topClusters);

            // Compare to previous results
            if (!queue.isEmpty())
            {
                int i = -queue.size();
                final StringBuilder sb = new StringBuilder();
                sb.append("Cluster comparison: RandIndex (AdjustedRandIndex)\n");
                for (final Iterator<ClusterResult> it = queue.iterator(); it.hasNext();)
                {
                    final ClusterResult previous = it.next();
                    sb.append("[").append(i++).append("] ");
                    compare(sb, "Clusters", current.c1, current.n1, previous.c1, previous.n1);
                    if (current.c2 != null)
                    {
                        sb.append(" : ");
                        compare(sb, "Top-level clusters", current.c2, current.n2, previous.c2, previous.n2);
                    }
                    sb.append('\n');
                }
                IJ.log(sb.toString());
            }

            queue.add(current);

            // Limit size
            if (queue.size() > 2)
                queue.poll();

            return work;
        }

        private void compare(StringBuilder sb, String title, int[] set1, int n1, int[] set2, int n2)
        {
            final RandIndex ri = new RandIndex();
            ri.compute(set1, n1, set2, n2);
            final double r = ri.getRandIndex();
            final double ari = ri.getAdjustedRandIndex();
            sb.append(title);
            sb.append(" ");
            sb.append(Utils.rounded(r));
            sb.append(" (");
            sb.append(Utils.rounded(ari));
            sb.append(")");

        }
    }

    private class MemoryResultsWorker extends BaseWorker
    {
        @Override
        public boolean equalSettings(OpticsSettings current, OpticsSettings previous)
        {
            // Only depends on if the clustering results are new. This is triggered
            // in the default comparison of the Settings object.
            return true;
        }

        @Override
        public Pair<OpticsSettings, Settings> doWork(Pair<OpticsSettings, Settings> work)
        {
            final Settings resultList = work.b;
            final MemoryPeakResults results = (MemoryPeakResults) resultList.get(0);
            //OPTICSManager opticsManager = (OPTICSManager) resultList.get(1);
            final CachedClusteringResult clusteringResult = (CachedClusteringResult) resultList.get(2);
            if (clusteringResult.isValid())
            {
                final int[] clusters = clusteringResult.getClusters();
                final int max = clusteringResult.getMaxClusterId();

                // Save the clusters to memory
                final Trace[] traces = new Trace[max + 1];
                for (int i = 0; i <= max; i++)
                {
                    traces[i] = new Trace();
                    traces[i].setId(i);
                }
                final Counter counter = new Counter();
                results.forEach(new PeakResultProcedure()
                {
                    @Override
                    public void execute(PeakResult result)
                    {
                        traces[clusters[counter.getAndIncrement()]].add(result);
                    }
                });
                TraceMolecules.saveResults(results, traces, TITLE);
            }

            // We have not created anything new so return the current object
            return work;
        }
    }

    private class ReachabilityResultsWorker extends BaseWorker implements MouseListener, ClusterSelectedHandler
    {
        PlotCanvas lastCanvas;
        CachedClusteringResult clusteringResult = null;

        @Override
        public boolean equalSettings(OpticsSettings current, OpticsSettings previous)
        {
            if (current.getPlotMode() != previous.getPlotMode())
                return false;
            if (lastCanvas != null)
                if (!lastCanvas.isValid())
                    return false;
            return true;
        }

        @Override
        protected void newResults()
        {
            clusteringResult = null;
        }

        @SuppressWarnings("null")
        @Override
        public Pair<OpticsSettings, Settings> doWork(Pair<OpticsSettings, Settings> work)
        {
            final OpticsSettings settings = work.a;
            final Settings resultList = work.b;
            final CachedClusteringResult clusteringResult = (CachedClusteringResult) resultList.get(2);
            if (!clusteringResult.isValid())
                return work;

            final MemoryPeakResults results = (MemoryPeakResults) resultList.get(0);
            final double nmPerPixel = getNmPerPixel(results);

            // Draw the reachability profile
            final PlotMode mode = PlotMode.get(settings.getPlotMode());
            if (mode != PlotMode.OFF)
            {
                final double[] profile = clusteringResult.getProfile(nmPerPixel);
                String units = " (px)";
                if (nmPerPixel != 1)
                    units = " (nm)";

                final double[] order = SimpleArrayUtils.newArray(profile.length, 1.0, 1.0);
                final String title = TITLE + " Reachability Distance";
                final Plot2 plot = new Plot2(title, "Order", "Reachability" + units);
                final double[] limits = Maths.limits(profile);
                // plot to zero
                limits[0] = 0;

                ArrayList<OPTICSCluster> clusters = null;
                LUT lut = clusterLut;
                int maxClusterId = 0;
                int maxLevel = 0;

                if (mode.requiresClusters())
                {
                    clusters = clusteringResult.getAllClusters();
                    for (final OPTICSCluster cluster : clusters)
                        if (maxLevel < cluster.getLevel())
                            maxLevel = cluster.getLevel();
                    maxClusterId = clusteringResult.getMaxClusterId();
                }

                if (settings.getOpticsMode() == OpticsMode.FAST_OPTICS.ordinal())
                {
                    // The profile may be very high. Compute the outliers and remove.
                    final Percentile p = new Percentile();
                    p.setData(profile);
                    double max;
                    final boolean useIQR = true;
                    if (useIQR)
                    {
                        final double lq = p.evaluate(25);
                        final double uq = p.evaluate(75);
                        max = (uq - lq) * 2 + uq;
                    }
                    else
                        // Remove top 2%
                        max = p.evaluate(98);
                    if (limits[1] > max)
                        limits[1] = max;
                }
                else
                    // Show extra at the top
                    limits[1] *= 1.05;

                // Draw the clusters using lines underneath
                if (mode.isDrawClusters() && maxClusterId > 0)
                {
                    // Get a good distance to start the lines, and the separation
                    // Make the clusters fill 1/3 of the plot.
                    final double range = 0.5 * limits[1];
                    final double separation = range / (maxLevel + 2);
                    final double start = -separation;

                    final LUTMapper mapper = new LUTHelper.NonZeroLUTMapper(1, maxClusterId);
                    for (final OPTICSCluster cluster : clusters)
                    {
                        final int level = cluster.getLevel();
                        final float y = (float) (start - (maxLevel - level) * separation);
                        final Color c = mapper.getColour(lut, cluster.getClusterId());
                        plot.setColor(c);
                        //plot.drawLine(cluster.start, y, cluster.end, y);
                        // Create as a line. This allows the plot to reset the range to the full data set
                        final float[] xx = new float[] { cluster.start, cluster.end };
                        final float[] yy = new float[] { y, y };
                        plot.addPoints(xx, yy, Plot.LINE);
                    }

                    // Update the limits if we are plotting lines underneath for the clusters
                    limits[0] = -range; //start - (maxLevel + 1) * separation;
                }

                // Pad order by 0.5 so we can see the ends of the plot
                plot.setLimits(0.5, order.length + 0.5, limits[0], limits[1]);

                // Create the colour for each point on the line:
                // We draw lines between from and to of the same colour
                final int[] profileColourFrom = new int[profile.length];
                final int[] profileColourTo = new int[profile.length];

                //plot.setColor(Color.black);
                //plot.addPoints(order, profile, Plot.LINE);

                // Create a colour to match the LUT of the image
                LUTMapper mapper = null;

                // Colour the reachability plot line if it is in a cluster. Use a default colour
                if (mode.isColourProfile())
                {
                    if (mode.isColourProfileByOrder())
                    {
                        lut = clusterOrderLut;
                        mapper = new LUTHelper.NonZeroLUTMapper(1, profileColourTo.length - 1);
                        for (int i = 1; i < profileColourTo.length; i++)
                            profileColourFrom[i - 1] = profileColourTo[i] = mapper.map(i);
                        // Ensure we correctly get colours for each value
                        mapper = new LUTHelper.DefaultLUTMapper(0, 255);
                    }
                    else
                    {
                        // Do all clusters so rank by level
                        Collections.sort(clusters, new Comparator<OPTICSCluster>()
                        {
                            @Override
                            public int compare(OPTICSCluster o1, OPTICSCluster o2)
                            {
                                return o1.getLevel() - o2.getLevel();
                            }
                        });

                        final boolean useLevel = mode.isColourProfileByDepth();
                        if (useLevel)
                            lut = clusterDepthLut;

                        for (final OPTICSCluster cluster : clusters)
                        {
                            final int value = (useLevel) ? cluster.getLevel() + 1 : cluster.getClusterId();
                            Arrays.fill(profileColourFrom, cluster.start, cluster.end, value);
                            Arrays.fill(profileColourTo, cluster.start + 1, cluster.end + 1, value);
                        }
                    }
                }
                else if (mode.isHighlightProfile())
                    for (final OPTICSCluster cluster : clusters)
                    {
                        // Only do top level clusters
                        if (cluster.getLevel() != 0)
                            continue;
                        final int value = 1;
                        Arrays.fill(profileColourFrom, cluster.start, cluster.end, value);
                        Arrays.fill(profileColourTo, cluster.start + 1, cluster.end + 1, value);
                    }

                // Now draw the line
                final int maxColour = Maths.max(profileColourTo);
                if (mapper == null)
                    // Make zero black
                    mapper = new LUTHelper.NonZeroLUTMapper(1, maxColour);

                // Cache all the colours
                final Color[] colors = new Color[maxColour + 1];
                if (mode.isColourProfile())
                    for (int c = mapper.getMin(); c <= maxColour; c++)
                        colors[c] = mapper.getColour(lut, c);
                else if (maxColour == 1)
                    colors[1] = Color.BLUE;
                if (colors[0] == null)
                    colors[0] = Color.BLACK;

                // We draw lines between from and to of the same colour
                int from = 0;
                int to = 1;
                final int limit = profileColourTo.length - 1;
                while (to < profileColourTo.length)
                {
                    while (to < limit && profileColourFrom[from] == profileColourTo[to + 1])
                        to++;

                    // Draw the line on the plot
                    final double[] order1 = Arrays.copyOfRange(order, from, to + 1);
                    final double[] profile1 = Arrays.copyOfRange(profile, from, to + 1);
                    plot.setColor(colors[profileColourFrom[from]]);
                    plot.addPoints(order1, profile1, Plot.LINE);
                    from = to++;
                }

                // Draw the final line
                if (from != limit)
                {
                    to = limit;
                    final double[] order1 = Arrays.copyOfRange(order, from, to);
                    final double[] profile1 = Arrays.copyOfRange(profile, from, to);
                    plot.setColor(colors[profileColourFrom[from]]);
                    plot.addPoints(order1, profile1, Plot.LINE);
                }

                // Add the clustering distance limits
                double distance = -1, distance2 = -1;
                if (inputSettings.getClusteringMode() == ClusteringMode.DBSCAN.ordinal())
                {
                    if (settings.getOpticsMode() == OpticsMode.FAST_OPTICS.ordinal())
                    {
                        if (settings.getClusteringDistance() > 0)
                            distance = settings.getClusteringDistance();
                        else
                        {
                            final OPTICSManager opticsManager = (OPTICSManager) resultList.get(1);
                            distance = opticsManager.computeGeneratingDistance(settings.getMinPoints()) * nmPerPixel;
                        }
                    }
                    else
                    {
                        // Ensure that the distance is valid
                        distance = clusteringResult.getOPTICSResult().generatingDistance * nmPerPixel;
                        if (settings.getClusteringDistance() > 0)
                            distance = Math.min(settings.getClusteringDistance(), distance);
                    }

                    if (distance > limits[1])
                        limits[1] = distance * 1.05;
                }
                else // Assume Optics Xi
                {
                    if (settings.getUpperLimit() > 0)
                        distance = settings.getUpperLimit();
                    if (settings.getLowerLimit() > 0)
                        distance2 = settings.getLowerLimit();
                }

                if (distance > -1)
                {
                    plot.setColor(Color.red);
                    plot.drawLine(1, distance, order.length, distance);
                }
                if (distance2 > -1)
                {
                    plot.setColor(Color.magenta);
                    plot.drawLine(1, distance2, order.length, distance2);
                }

                // Preserve current limits (but not y-min so the clusters profile can be redrawn)
                final PlotWindow pw = Utils.display(title, plot,
                        Utils.PRESERVE_X_MIN | Utils.PRESERVE_X_MAX | Utils.PRESERVE_Y_MAX);
                if (Utils.isNewWindow())
                {
                    if (lastCanvas != null)
                    {
                        lastCanvas.removeMouseListener(this);
                        lastCanvas = null;
                    }
                    lastCanvas = (PlotCanvas) pw.getCanvas();
                    lastCanvas.addMouseListener(this);
                }
                this.clusteringResult = clusteringResult;
            }
            else
            {
                // We could close an existing plot here.
                // However we leave it as the user may wish to keep it for something.
            }

            // We have not created anything new so return the current object
            return work;
        }

        @Override
        public void mouseClicked(MouseEvent e)
        {
            // Ignore
        }

        double startX;

        @Override
        public void mousePressed(final MouseEvent e)
        {
            if (!eventWorkflow.isRunning())
            {
                if (lastCanvas != null)
                {
                    lastCanvas.removeMouseListener(this);
                    lastCanvas = null;
                }
                return;
            }

            startX = getX(e);
        }

        private double getX(MouseEvent e)
        {
            final PlotCanvas pc = lastCanvas;
            if (pc == null)
                return Double.NaN;
            final Plot plot = pc.getPlot();
            return plot.descaleX(e.getX());
        }

        @Override
        public void mouseReleased(MouseEvent e)
        {
            if (!inputSettings.getOpticsEventSettingsOrBuilder().getPlotCreateSelection())
                return;
            final double endX = getX(e);
            if (Double.isNaN(startX) || Double.isNaN(endX))
                return;

            eventWorkflow.run(new ClusterSelectedEvent(id)
            {
                @Override
                int[] computeClusters()
                {
                    final CachedClusteringResult clusteringResult = ReachabilityResultsWorker.this.clusteringResult;
                    if (clusteringResult == null || !clusteringResult.isCurrent())
                        return null;
                    // Allow drag both ways
                    int start, end;
                    if (endX < startX)
                    {
                        start = (int) endX;
                        end = (int) startX;
                    }
                    else
                    {
                        start = (int) startX;
                        end = (int) endX;
                    }
                    // Ignore the hierarchy as we only want to find the parent clusters
                    return clusteringResult.getOPTICSResult().getClustersFromOrder(start, end, false);
                }
            });
        }

        @Override
        public void mouseEntered(MouseEvent e)
        {
            // Ignore
        }

        @Override
        public void mouseExited(MouseEvent e)
        {
            // Ignore
        }

        @Override
        public void clusterSelected(ClusterSelectedEvent e)
        {
            // Ignore events generated by the plot
            if (e.getSource() == id)
                return;
            if (!inputSettings.getOpticsEventSettingsOrBuilder().getPlotShowSelection())
                return;

            final PlotCanvas pc = lastCanvas;
            if (pc == null)
                return;

            final int[] selectedClusters = e.getClusters();
            final int[] parents = clusteringResult.getParents(selectedClusters);
            if (parents == null || parents.length == 0)
                return;

            // Move the profile to the cover the range of the selected clusters
            final int[] order = clusteringResult.getOrder();

            int min = Integer.MAX_VALUE;
            int max = 0;
            for (final int i : parents)
            {
                final int o = order[i];
                if (min > o)
                    min = o;
                if (max < o)
                    max = o;
            }

            // Find the range of the profile
            double minR = Double.POSITIVE_INFINITY, maxR = 0;

            // The scale should not matter as the result is cached
            final double[] profile = clusteringResult.getProfile(Double.NaN);
            for (int i = min; i < max; i++)
            {
                final double r = profile[i];
                if (minR > r)
                    minR = r;
                if (maxR < r)
                    maxR = r;
            }

            // Pad?
            final double padR = Math.max(0.5, (maxR - minR) * 0.05);
            maxR += padR;
            minR -= padR;

            // TODO - Optionally add y-range of the clusters drawn underneath

            final Plot plot = pc.getPlot();

            // Pad order by 0.5 so we can see the ends of the range
            final int x = (int) plot.scaleXtoPxl(min - 0.5);
            final int endX = (int) Math.ceil(plot.scaleXtoPxl(max + 0.5));
            final int y = (int) plot.scaleYtoPxl(maxR);
            final int endY = (int) Math.ceil(plot.scaleYtoPxl(minR));
            //System.out.printf("%d,%d to %d,%d\n", x, y, endX, endY);

            // Setting an ROI and calling zoom will reset the view window
            // and then kill the ROI
            pc.getImage().setRoi(new Roi(x, y, endX - x, endY - y));
            pc.zoom("to");
        }
    }

    private class OrderProvider
    {
        int getOrder(int i)
        {
            return i;
        }
    }

    private class RealOrderProvider extends OrderProvider
    {
        final int[] order;

        RealOrderProvider(int[] order)
        {
            this.order = order;
        }

        @Override
        int getOrder(int i)
        {
            return order[i];
        }
    }

    /**
     * Map the input value as an index to an output value
     */
    public class ValueLUTMapper extends LUTHelper.NullLUTMapper
    {
        /** The values. */
        float[] values;

        /**
         * Instantiates a new value LUT mapper.
         *
         * @param values
         *            the values
         */
        public ValueLUTMapper(float[] values)
        {
            this.values = values;
        }

        @Override
        public float mapf(float value)
        {
            return values[(int) value];
        }
    }

    private class ImageResultsWorker extends BaseWorker implements MouseListener, ClusterSelectedHandler
    {
        IJImagePeakResults image = null;
        int lastOutlineMode = -1;
        Overlay outline = null;
        int lastSpanningTreeMode = -1;
        Overlay spanningTree = null;
        double lastLambda = 0;
        int lastMinPoints;
        float[] loop = null;

        // For detecting the cluster from mouse click
        DetectionGrid grid = null;
        double[] area = null;
        CachedClusteringResult clusteringResult = null;

        @Override
        public boolean equalSettings(OpticsSettings current, OpticsSettings previous)
        {
            if (current.getImageScale() != previous.getImageScale())
            {
                // Clear all the cached results
                clearCache(true);
                return false;
            }
            boolean result = true;
            if (image != null && !image.getImagePlus().isVisible())
                result = false;

            if (current.getOutlineMode() != previous.getOutlineMode())
            {
                if (OutlineMode.get(current.getOutlineMode()).isOutline() &&
                        current.getOutlineMode() != lastOutlineMode)
                    outline = null;
                result = false;
            }
            if (current.getSpanningTreeMode() != previous.getSpanningTreeMode())
            {
                if (SpanningTreeMode.get(current.getSpanningTreeMode()).isSpanningTree() &&
                        current.getSpanningTreeMode() != lastSpanningTreeMode)
                    spanningTree = null;
                result = false;
            }
            if (current.getImageMode() != previous.getImageMode() ||
                    getDisplayFlags(current) != getDisplayFlags(previous))
            {
                // We can only cache the image if the display mode is the same
                image = null;
                result = false;
            }
            if (requiresLoop(current) &&
                    (current.getMinPoints() != lastMinPoints || (extraOptions && current.getLambda() != lastLambda)))
            {
                // We can only cache the loop values if the minPts is the same
                loop = null;
                if (current.getImageMode() == ImageMode.LOOP.ordinal())
                    // We must rebuild the image
                    image = null;
                if (current.getSpanningTreeMode() == SpanningTreeMode.COLOURED_BY_LOOP.ordinal())
                    // We must rebuild the outline
                    outline = null;
                result = false;
            }
            return result;
        }

        private boolean requiresLoop(OpticsSettings settings)
        {
            return settings.getImageMode() == ImageMode.LOOP.ordinal() ||
                    settings.getSpanningTreeMode() == SpanningTreeMode.COLOURED_BY_LOOP.ordinal();
        }

        @Override
        protected void newResults()
        {
            // We can keep the image but should clear the overlays
            clearCache(false);
        }

        private void clearCache(boolean clearImage)
        {
            // Clear cache
            if (image != null)
            {
                image.getImagePlus().setOverlay(null);
                image.getImagePlus().killRoi();
            }

            if (clearImage)
                image = null;
            lastOutlineMode = -1;
            outline = null;
            lastSpanningTreeMode = -1;
            spanningTree = null;
            grid = null;
            clusteringResult = null;
        }

        @SuppressWarnings("null")
        @Override
        public Pair<OpticsSettings, Settings> doWork(Pair<OpticsSettings, Settings> work)
        {
            final OpticsSettings settings = work.a;
            final Settings resultList = work.b;
            final MemoryPeakResults results = (MemoryPeakResults) resultList.get(0);
            final OPTICSManager opticsManager = (OPTICSManager) resultList.get(1);
            final CachedClusteringResult clusteringResult = (CachedClusteringResult) resultList.get(2);

            if (!clusteringResult.isValid())
            {
                clearCache(true);
                return new Pair<>(settings, new Settings(results, opticsManager, clusteringResult, image));
            }

            final ImageMode mode = ImageMode.get(settings.getImageMode());
            final StandardResultProcedure sp = new StandardResultProcedure(results, DistanceUnit.PIXEL);

            if (settings.getImageScale() > 0)
            {
                // Check if the image should be redrawn based on the clusters
                if (mode.isRequiresClusters())
                    image = null;

                if (image != null && !image.getImagePlus().isVisible())
                    image = null;

                if (grid == null)
                {
                    final int max = clusteringResult.getMaxClusterId();
                    area = SimpleArrayUtils.newDoubleArray(max + 1, -1);
                    // We need to ignore the first entry as this is not a cluster
                    final Rectangle2D[] clusterBounds = new Rectangle2D[max];
                    System.arraycopy(clusteringResult.getBounds(), 1, clusterBounds, 0, max);
                    grid = new BinarySearchDetectionGrid(clusterBounds);
                    this.clusteringResult = clusteringResult;
                }

                if (image == null)
                {
                    // Display the results ...

                    final Rectangle bounds = results.getBounds();
                    image = new IJImagePeakResults(results.getName() + " " + TITLE, bounds,
                            (float) settings.getImageScale());
                    // Options to control rendering
                    image.copySettings(results);
                    image.setDisplayFlags(getDisplayFlags(settings));
                    image.setLiveImage(false);
                    image.begin();
                    final ImagePlus imp = image.getImagePlus();
                    imp.setOverlay(null);
                    imp.killRoi();

                    // Allow clicking to select a cluster
                    imp.getCanvas().addMouseListener(this);

                    if (mode != ImageMode.NONE)
                    {
                        float[] map = null; // Used to map clusters to a display value
                        int[] order = null;

                        if (clusteringResult.isOPTICS)
                            if (mode == ImageMode.CLUSTER_ORDER)
                                order = clusteringResult.getOrder();
                            else if (mode == ImageMode.CLUSTER_DEPTH)
                            {
                                final ArrayList<OPTICSCluster> allClusters = clusteringResult.getAllClusters();
                                map = new float[clusteringResult.getMaxClusterId() + 1];
                                for (final OPTICSCluster c : allClusters)
                                    map[c.getClusterId()] = c.getLevel() + 1;
                            }
                        createLoopData(settings, opticsManager);

                        // Draw each cluster in a new colour
                        LUT lut = valueLut;
                        LUTMapper mapper = new LUTHelper.NullLUTMapper();
                        if (mode.isMapped())
                            switch (mode)
                            {
                                //@formatter:off
								case CLUSTER_ORDER: lut = clusterOrderLut; break;
								case CLUSTER_ID:    lut = clusterLut; break;
								case CLUSTER_DEPTH:
									lut = clusterDepthLut;
									mapper = new ValueLUTMapper(map);
									break;
								case LOOP:
									lut = loopLut;
									mapper = new ValueLUTMapper(loop);
									break;
								default:
									throw new NotImplementedException();
								//@formatter:on
                            }
                        image.getImagePlus().getProcessor().setColorModel(lut);

                        // Add in a single batch
                        sp.getIXY();
                        final OrderProvider op = (order == null) ? new OrderProvider() : new RealOrderProvider(order);
                        final int[] clusters = clusteringResult.getClusters();
                        for (int i = sp.size(); i-- > 0;)
                            sp.intensity[i] = mapper.mapf(mode.getValue(sp.intensity[i], clusters[i], op.getOrder(i)));
                        image.add(sp.x, sp.y, sp.intensity);
                    }
                    image.end();
                    if (mode.isMapped())
                    {
                        // Convert already mapped image to 8-bit (so the values are fixed)
                        //imp.setProcessor(imp.getProcessor().convertToByteProcessor(false));
                    }
                    imp.getWindow().toFront();
                }
            }
            else
            {
                // We could close an image here.
                // However we leave it as the user may wish to keep it for something.
            }

            // Note: If the image scale is set to zero then the image cache will be cleared and the image will be null.
            // This will prevent computing an overlay even if the 'outline' setting is enabled.

            if (image != null)
            {
                final ImagePlus imp = image.getImagePlus();
                Overlay overlay = null;

                final OutlineMode outlineMode = OutlineMode.get(settings.getOutlineMode());
                if (outlineMode.isOutline())
                {
                    if (outline == null)
                    {
                        lastOutlineMode = settings.getOutlineMode();

                        final int max = clusteringResult.getMaxClusterId();
                        int max2 = max;
                        final int[] map = SimpleArrayUtils.newArray(max + 1, 0, 1);

                        LUT lut = clusterLut;

                        if (clusteringResult.isOPTICS)
                            if (outlineMode.isColourByDepth())
                            {
                                lut = clusterDepthLut;
                                final ArrayList<OPTICSCluster> allClusters = clusteringResult.getAllClusters();
                                Arrays.fill(map, 0);
                                for (final OPTICSCluster c : allClusters)
                                    map[c.getClusterId()] = c.getLevel() + 1;
                                max2 = Maths.max(map);
                            }

                        outline = new Overlay();

                        // Create a colour to match the LUT of the image
                        final LUTMapper mapper = new LUTHelper.NonZeroLUTMapper(1, max2);

                        // Cache all the colours
                        final Color[] colors = new Color[max2 + 1];
                        for (int c = 1; c <= max2; c++)
                            colors[c] = mapper.getColour(lut, c);

                        // Extract the ConvexHull of each cluster
                        final ConvexHull[] hulls = clusteringResult.getHulls();
                        for (int c = 1; c <= max; c++)
                        {
                            final ConvexHull hull = hulls[c];
                            if (hull != null)
                            {
                                final Roi roi = createRoi(hull, true);
                                roi.setStrokeColor(colors[map[c]]);
                                // TODO: Options to set a fill colour?
                                outline.add(roi);
                            }
                        }
                    }
                    overlay = outline;
                }

                if (clusteringResult.isOPTICS && SpanningTreeMode.get(settings.getSpanningTreeMode()).isSpanningTree())
                {
                    if (spanningTree == null)
                    {
                        lastSpanningTreeMode = settings.getSpanningTreeMode();

                        final int[] predecessor = clusteringResult.getPredecessor();
                        final int[] clusters = clusteringResult.getClusters();
                        //int[] topLevelClusters = clusteringResult.getTopClusters();
                        final int max = clusteringResult.getMaxClusterId();
                        int max2 = max;
                        final int[] map = SimpleArrayUtils.newArray(max + 1, 0, 1);

                        LUT lut = clusterLut;

                        if (lastSpanningTreeMode == SpanningTreeMode.COLOURED_BY_ORDER.ordinal())
                            lut = clusterOrderLut;
                        else if (lastSpanningTreeMode == SpanningTreeMode.COLOURED_BY_DEPTH.ordinal() && max == max2)
                        {
                            lut = clusterDepthLut;
                            final ArrayList<OPTICSCluster> allClusters = clusteringResult.getAllClusters();
                            Arrays.fill(map, 0);
                            for (final OPTICSCluster c : allClusters)
                                map[c.getClusterId()] = c.getLevel() + 1;
                            max2 = Maths.max(map);
                        }
                        else if (lastSpanningTreeMode == SpanningTreeMode.COLOURED_BY_LOOP.ordinal())
                            lut = loopLut;

                        // Get the coordinates
                        if (sp.x == null)
                            sp.getXY();

                        spanningTree = new Overlay();

                        // Cache all the colours
                        Color[] colors;
                        // Create a colour to match the LUT of the image
                        LUTMapper mapper;

                        boolean useMap = false, useLoop = false;
                        int[] order = null;
                        if (lastSpanningTreeMode == SpanningTreeMode.COLOURED_BY_ORDER.ordinal())
                        {
                            // We will use the order for the colour
                            order = clusteringResult.getOrder();
                            mapper = new LUTHelper.DefaultLUTMapper(0, 255);
                            colors = new Color[256];
                            for (int c = 1; c < colors.length; c++)
                                colors[c] = mapper.getColour(lut, c);
                            mapper = new LUTHelper.NonZeroLUTMapper(1, clusters.length);
                        }
                        else if (lastSpanningTreeMode == SpanningTreeMode.COLOURED_BY_LOOP.ordinal())
                        {
                            // We will use the LoOP for the colour
                            useLoop = true;
                            mapper = new LUTHelper.DefaultLUTMapper(0, 255);
                            colors = new Color[256];
                            for (int c = 1; c < colors.length; c++)
                                colors[c] = mapper.getColour(lut, c);
                            mapper = new LUTHelper.NonZeroLUTMapper(0, 1);
                        }
                        else
                        {
                            // Alternative is to colour by cluster Id/Depth using a map
                            useMap = true;
                            mapper = new LUTHelper.NonZeroLUTMapper(1, max2);
                            colors = new Color[max2 + 1];
                            for (int c = 1; c <= max2; c++)
                                colors[c] = mapper.getColour(lut, c);
                        }

                        createLoopData(settings, opticsManager);

                        for (int i = 1; i < predecessor.length; i++)
                        {
                            if (clusters[i] == 0 || predecessor[i] < 0)
                                continue;

                            final int j = predecessor[i];

                            final float xi = image.mapX(sp.x[i]);
                            final float yi = image.mapY(sp.y[i]);
                            final float xj = image.mapX(sp.x[j]);
                            final float yj = image.mapY(sp.y[j]);

                            final Line roi = new Line(xi, yi, xj, yj);
                            if (useMap)
                                roi.setStrokeColor(colors[map[clusters[i]]]);
                            else if (useLoop)
                                roi.setStrokeColor(colors[mapper.map(loop[i])]);
                            else
                                roi.setStrokeColor(colors[mapper.map(order[i])]);
                            spanningTree.add(roi);
                        }
                    }
                    if (overlay == null)
                        overlay = spanningTree;
                    else
                    {
                        // Merge the two
                        overlay = new Overlay();
                        for (int i = outline.size(); i-- > 0;)
                            overlay.add(outline.get(i));
                        for (int i = spanningTree.size(); i-- > 0;)
                            overlay.add(spanningTree.get(i));
                    }
                }

                imp.setOverlay(overlay);
            }

            return new Pair<>(settings, new Settings(results, opticsManager, clusteringResult, image));
        }

        private Roi createRoi(ConvexHull hull, boolean forcePolygon)
        {
            // Convert the Hull to the correct image scale.
            final float[] x2 = hull.x.clone();
            final float[] y2 = hull.y.clone();
            for (int i = 0; i < x2.length; i++)
            {
                x2[i] = image.mapX(x2[i]);
                y2[i] = image.mapY(y2[i]);
            }
            // Note: The hull can be a single point or a line
            if (!forcePolygon)
            {
                if (x2.length == 1)
                    return new PointRoi(x2[0], y2[0]);
                if (x2.length == 2)
                    return new Line(x2[0], y2[0], x2[1], y2[1]);
            }
            return new PolygonRoi(x2, y2, Roi.POLYGON);
        }

        private Roi createRoi(Rectangle2D bounds, int size)
        {
            if (bounds.getWidth() == 0 && bounds.getHeight() == 0)
                return new PointRoi(bounds.getX(), bounds.getY());
            // It may be a cluster of size 2 so we should create a line for these.
            if (size == 2)
                return new Line(bounds.getX(), bounds.getY(), bounds.getMaxX(), bounds.getMaxY());
            return new Roi(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
        }

        private void createLoopData(OpticsSettings settings, OPTICSManager opticsManager)
        {
            if (requiresLoop(settings) && loop == null)
            {
                synchronized (opticsManager)
                {
                    lastLambda = (extraOptions) ? settings.getLambda() : 3;
                    lastMinPoints = settings.getMinPoints();
                    loop = opticsManager.loop(lastMinPoints, lastLambda, true);
                }
                final float[] limits = Maths.limits(loop);
                Utils.log("LoOP range: %s - %s", Utils.rounded(limits[0]), Utils.rounded(limits[1]));
            }
        }

        private int getDisplayFlags(OpticsSettings inputSettings)
        {
            int displayFlags = 0;
            final ImageMode imageMode = ImageMode.get(inputSettings.getImageMode());
            if (imageMode.canBeWeighted())
            {
                if (inputSettings.getWeighted())
                    displayFlags |= IJImagePeakResults.DISPLAY_WEIGHTED;
                if (inputSettings.getEqualised())
                    displayFlags |= IJImagePeakResults.DISPLAY_EQUALIZED;
            }

            if (imageMode == ImageMode.CLUSTER_ID || imageMode == ImageMode.CLUSTER_DEPTH ||
                    imageMode == ImageMode.CLUSTER_ORDER || imageMode == ImageMode.LOOP)
                displayFlags = IJImagePeakResults.DISPLAY_MAX;

            if (imageMode.isMapped())
            {
                displayFlags |= IJImagePeakResults.DISPLAY_MAPPED;
                if (imageMode == ImageMode.LOOP)
                    displayFlags |= IJImagePeakResults.DISPLAY_MAP_ZERO;
            }
            return displayFlags;
        }

        @Override
        public void mouseClicked(MouseEvent e)
        {
            if (!eventWorkflow.isRunning())
            {
                if (image != null)
                {
                    final ImagePlus imp = image.getImagePlus();
                    if (imp.getCanvas() != null)
                        imp.getCanvas().removeMouseListener(this);
                }
                return;
            }

            if (!inputSettings.getOpticsEventSettingsOrBuilder().getImageCreateSelection())
                return;

            if (image == null)
                return;
            final ImagePlus imp = image.getImagePlus();
            final ImageCanvas ic = imp.getCanvas();
            final double cx = ic.offScreenXD(e.getX());
            final double cy = ic.offScreenYD(e.getY());

            // Convert to pixel coordinates using the scale
            final float x = (float) (cx / image.getScale());
            final float y = (float) (cy / image.getScale());

            eventWorkflow.run(new ClusterSelectedEvent(id)
            {
                @Override
                int[] computeClusters()
                {
                    //System.out.printf("Compute cluster @ %.2f,%.2f\n", x, y);

                    // Find the regions that could have been clicked
                    if (grid == null)
                        return null;
                    final CachedClusteringResult clusteringResult = ImageResultsWorker.this.clusteringResult;
                    if (clusteringResult == null || !clusteringResult.isCurrent())
                        return null;

                    int[] candidates = grid.find(x, y);
                    if (candidates.length == 0)
                        return null;

                    // Return the smallest region clicked for which we have an area.

                    // Since collision detection is costly first rank by area (which
                    // is faster and can be precomputed)
                    final TIntArrayList ids = new TIntArrayList(candidates.length);
                    final TDoubleArrayList area = new TDoubleArrayList(candidates.length);
                    final ConvexHull[] hulls = clusteringResult.getHulls();
                    for (final int index : candidates)
                    {
                        final int clusterId = index + 1;
                        final ConvexHull hull = hulls[clusterId];
                        if (hull != null)
                        {
                            ids.add(clusterId);
                            area.add(getArea(clusterId, hull));
                        }
                    }

                    if (ids.isEmpty())
                        return null;

                    // Sort ascending
                    candidates = ids.toArray();
                    Sort.sortArrays(candidates, area.toArray(), true);
                    for (final int clusterId : candidates)
                        if (hulls[clusterId].contains(x, y))
                            return new int[] { clusterId };

                    return null;
                }
            });
        }

        protected double getArea(int clusterId, ConvexHull hull)
        {
            if (area[clusterId] == -1)
                area[clusterId] = hull.getArea();
            return area[clusterId];
        }

        @Override
        public void mousePressed(MouseEvent e)
        {
            // Ignore
        }

        @Override
        public void mouseReleased(MouseEvent e)
        {
            // Ignore
        }

        @Override
        public void mouseEntered(MouseEvent e)
        {
            // Ignore
        }

        @Override
        public void mouseExited(MouseEvent e)
        {
            // Ignore
        }

        @Override
        public void clusterSelected(ClusterSelectedEvent e)
        {
            if (image == null)
                return;
            if (!inputSettings.getOpticsEventSettingsOrBuilder().getImageShowSelection())
                return;
            final ImagePlus imp = image.getImagePlus();
            if (!imp.isVisible())
                return;

            final CachedClusteringResult clusteringResult = ImageResultsWorker.this.clusteringResult;
            if (clusteringResult == null || !clusteringResult.isCurrent())
                return;

            final int[] clusters = e.getClusters();
            Roi roi = null;
            final ConvexHull[] hulls = clusteringResult.getHulls();

            if (clusters != null && clusters.length > 0)
            {
                final TurboList<Roi> rois = new TurboList<>(clusters.length);
                for (final int clusterId : clusters)
                {
                    final ConvexHull hull = hulls[clusterId];
                    if (hull != null)
                        rois.add(createRoi(hull, false));
                    else
                        rois.add(createRoi(clusteringResult.getBounds()[clusterId],
                                clusteringResult.getSize()[clusterId]));
                }
                if (rois.size() == 1)
                    roi = rois.getf(0);
                else if (rois.size() > 1)
                    // If all are points then create a multi-point ROI.
                    // This is useful to see where the tiny clusters are.
                    if (allPoints(rois))
                    {
                        final float[] x = new float[rois.size()];
                        final float[] y = new float[x.length];
                        for (int i = 0; i < rois.size(); i++)
                        {
                            final Rectangle2D.Double b = rois.getf(i).getFloatBounds();
                            x[i] = (float) b.getX();
                            y[i] = (float) b.getY();
                        }
                        roi = new PointRoi(x, y);
                    }
                    else
                    {
                        // We cannot handle combining points and polygons unless
                        // we draw in the overlay. So for now the tiny shape will
                        // be invisible.

                        // Combine into a shape
                        ShapeRoi shapeRoi = null;
                        for (final Roi r : rois)
                            if (r instanceof PolygonRoi)
                            {
                                final ShapeRoi next = new ShapeRoi(getShape((PolygonRoi) r));
                                if (shapeRoi == null)
                                    shapeRoi = next;
                                else
                                    shapeRoi.or(next);
                            }

                        //ShapeRoi shapeRoi = new ShapeRoi(rois.getf(0));
                        //for (int i = 1; i < rois.size(); i++)
                        //	shapeRoi.or(new ShapeRoi(rois.getf(i)));
                        roi = shapeRoi;
                    }
            }
            imp.setRoi(roi);

            if (roi != null)
            {
                final ImageCanvas ic = imp.getCanvas();
                final Rectangle source = ic.getSrcRect();
                final Rectangle target = roi.getBounds();
                if (!source.contains(target))
                {
                    // Shift to centre
                    final int cx1 = target.x + target.width / 2;
                    final int cy1 = target.y + target.height / 2;
                    final int cx2 = source.x + source.width / 2;
                    final int cy2 = source.y + source.height / 2;
                    final int shiftx = cx1 - cx2;
                    final int shifty = cy1 - cy2;
                    source.x += shiftx;
                    source.y += shifty;

                    if (target.width > source.width)
                    {
                        // Reduce magnification to fit
                        final int pad = target.width - source.width;
                        source.width += pad;
                        source.x -= pad / 2;
                        // Shift
                        //source.x = target.x;
                    }
                    if (target.height > source.height)
                    {
                        // Reduce magnification to fit
                        final int pad = target.height - source.height;
                        source.height += pad;
                        source.y -= pad / 2;
                        // Shift
                        //source.y = target.y;
                    }

                    ic.setSourceRect(source);
                }
            }
        }

        private Shape getShape(PolygonRoi r)
        {
            final Path2D.Float path = new Path2D.Float();
            final FloatPolygon p = r.getFloatPolygon();
            path.moveTo(p.xpoints[0], p.ypoints[0]);
            for (int i = 1; i < p.xpoints.length; i++)
                path.lineTo(p.xpoints[i], p.ypoints[i]);
            return path;
        }

        private boolean allPoints(TurboList<Roi> rois)
        {
            for (final Roi r : rois)
                if (!(r instanceof PointRoi))
                    return false;
            return true;
        }
    }

    private class TableResult
    {
        int id;
        int size;
        int level;
        double area, density;
        //ConvexHull hull;
        Rectangle2D bounds;
        String text;
        double toUnit = 0;

        TableResult(int id, int size, int level, ConvexHull hull, Rectangle2D bounds)
        {
            this.id = id;
            this.size = size;
            this.level = level;
            //this.hull = hull;
            this.bounds = bounds;

            if (hull != null)
            {
                area = hull.getArea();

                if (area != 0)
                    density = size / area;
                else
                    //System.out.printf("Weird: %d\n%s\n%s\n", size, Arrays.toString(hull.x), Arrays.toString(hull.y));
                    density = Double.MAX_VALUE; // Just used for sorting
            }
        }

        public String getTableText(double toUnit)
        {
            if (text == null || this.toUnit != toUnit)
            {
                this.toUnit = toUnit;
                final StringBuilder sb = new StringBuilder();
                // "Level\tId\tSize\tArea\tDensity\tBounds"
                sb.append(level).append('\t');
                sb.append(id).append('\t');
                sb.append(size).append('\t');
                if (area > 0)
                {
                    final double area = this.area * toUnit * toUnit;
                    sb.append(Maths.round(area)).append('\t');
                    sb.append(Maths.round(size / area)).append('\t');
                }
                else
                    sb.append("0\t\t");
                sb.append('(').append(Maths.round(toUnit * bounds.getX())).append(',');
                sb.append(Maths.round(toUnit * bounds.getY())).append(") ");
                sb.append(Maths.round(toUnit * bounds.getWidth())).append('x');
                sb.append(Maths.round(toUnit * bounds.getHeight()));
                text = sb.toString();
            }
            return text;
        }
    }

    /**
     * Base result comparator allows chaining comparisons together
     */
    private abstract static class BaseTableResultComparator implements Comparator<TableResult>
    {
        BaseTableResultComparator next;
        boolean reverse;

        public BaseTableResultComparator(BaseTableResultComparator next, boolean reverse)
        {
            this.next = next;
            this.reverse = reverse;

            // Remove instances of the same comparator
            BaseTableResultComparator previous = this;
            while (previous.next != null)
                if (previous.next.getClass() == this.getClass())
                    // Skip this to remove it
                    previous.next = previous.next.next;
                else
                    // Move forward
                    previous = previous.next;
        }

        @Override
        public int compare(TableResult o1, TableResult o2)
        {
            final int result = compareColumn(o1, o2);
            if (result != 0)
                return (reverse) ? -result : result;
            return (next != null) ? next.compare(o1, o2) : 0;
        }

        public abstract int compareColumn(TableResult o1, TableResult o2);
    }

    private static class IdTableResultComparator extends BaseTableResultComparator
    {
        public IdTableResultComparator(BaseTableResultComparator next, boolean reverse)
        {
            super(next, reverse);
        }

        @Override
        public int compareColumn(TableResult o1, TableResult o2)
        {
            return compareInt(o1.id, o2.id);
        }
    }

    private static class SizeTableResultComparator extends BaseTableResultComparator
    {
        public SizeTableResultComparator(BaseTableResultComparator next, boolean reverse)
        {
            super(next, reverse);
        }

        @Override
        public int compareColumn(TableResult o1, TableResult o2)
        {
            return compareInt(o1.size, o2.size);
        }
    }

    private static class LevelTableResultComparator extends BaseTableResultComparator
    {
        public LevelTableResultComparator(BaseTableResultComparator next, boolean reverse)
        {
            super(next, reverse);
        }

        @Override
        public int compareColumn(TableResult o1, TableResult o2)
        {
            return compareInt(o1.level, o2.level);
        }
    }

    /**
     * Compare the integers. Copied from Java 1.7.
     *
     * @param x
     *            the x
     * @param y
     *            the y
     * @return the result
     */
    private static int compareInt(int x, int y)
    {
        return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }

    private static class AreaTableResultComparator extends BaseTableResultComparator
    {
        public AreaTableResultComparator(BaseTableResultComparator next, boolean reverse)
        {
            super(next, reverse);
        }

        @Override
        public int compareColumn(TableResult o1, TableResult o2)
        {
            return Double.compare(o1.area, o2.area);
        }
    }

    private static class DensityTableResultComparator extends BaseTableResultComparator
    {
        public DensityTableResultComparator(BaseTableResultComparator next, boolean reverse)
        {
            super(next, reverse);
        }

        @Override
        public int compareColumn(TableResult o1, TableResult o2)
        {
            return Double.compare(o1.density, o2.density);
        }
    }

    private class TableResultsWorker extends BaseWorker implements MouseListener, ClusterSelectedHandler
    {
        TurboList<TableResult> tableResults;
        TextWindow2 tw;
        Rectangle location;
        BaseTableResultComparator previous = null;

        @Override
        public boolean equalSettings(OpticsSettings current, OpticsSettings previous)
        {
            if (current.getShowTable() != previous.getShowTable())
                return false;
            if (current.getShowTable())
            {
                if (tw != null && !tw.isVisible())
                    return false;
                if (current.getTableReverseSort() != previous.getTableReverseSort())
                    return false;
                if (current.getTableSortMode() != previous.getTableSortMode())
                    return false;
            }
            return true;
        }

        @Override
        protected void newResults()
        {
            // Clear cache
            tableResults = null;

            // This should not matter so keep the same sort
            //previous = null;
        }

        @Override
        public Pair<OpticsSettings, Settings> doWork(Pair<OpticsSettings, Settings> work)
        {
            final OpticsSettings settings = work.a;
            final Settings resultList = work.b;
            final MemoryPeakResults results = (MemoryPeakResults) resultList.get(0);
            final CachedClusteringResult clusteringResult = (CachedClusteringResult) resultList.get(2);
            if (!clusteringResult.isValid() || !settings.getShowTable())
            {
                // Hide the table
                if (tw != null)
                {
                    location = tw.getBounds();
                    tw.close();
                    tw = null;
                }
            }
            else
            {
                if (tableResults == null)
                {
                    final ConvexHull[] hulls = clusteringResult.getHulls();
                    final Rectangle2D[] bounds = clusteringResult.getBounds();

                    // Get the cluster sizes and level
                    final int[] size = clusteringResult.getSize();
                    final int[] level = clusteringResult.getLevel();

                    tableResults = new TurboList<>(size.length);
                    for (int c = 1; c < size.length; c++)
                        tableResults.add(new TableResult(c, size[c], level[c], hulls[c], bounds[c]));
                }

                // TODO: Allow user to change the distance units.
                // Note that all clustering is currently done in pixels.
                final DistanceUnit unit = DistanceUnit.NM;
                final String name = UnitHelper.getShortName(unit);
                final String headings = String.format("Level\tId\tSize\tArea (%s^2)\tDensity (%s^-2)\tBounds (%s)",
                        name, name, name);
                final double toUnit = results.getDistanceConverter(unit).convert(1);

                if (tw == null || !tw.isVisible())
                {
                    tw = new TextWindow2(TITLE + " Clusters", headings, "", 800, 400);

                    // Add a mouse listener to allow double click on a cluster to draw
                    // a polygon ROI of the convex hull, or point ROI if size <=2
                    final TextPanel tp = tw.getTextPanel();
                    tp.addMouseListener(this);

                    if (location != null)
                        tw.setBounds(location);
                    tw.setVisible(true);
                }
                else
                    tw.getTextPanel().setColumnHeadings(headings);

                final BufferedTextWindow bw = new BufferedTextWindow(tw);
                bw.setIncrement(Integer.MAX_VALUE);

                sort(settings);
                for (final TableResult r : tableResults)
                    bw.append(r.getTableText(toUnit));
                bw.flush();

                tw.getTextPanel().scrollToTop();
            }

            // We have not created anything new so return the current object
            return work;
        }

        private void sort(OpticsSettings settings)
        {
            tableResults.sort(createComparator(settings));
        }

        private Comparator<? super TableResult> createComparator(OpticsSettings settings)
        {
            switch (TableSortMode.get(settings.getTableSortMode()))
            {
                case DENSITY:
                    previous = new DensityTableResultComparator(previous, settings.getTableReverseSort());
                    break;
                case AREA:
                    previous = new AreaTableResultComparator(previous, settings.getTableReverseSort());
                    break;
                case LEVEL:
                    previous = new LevelTableResultComparator(previous, settings.getTableReverseSort());
                    break;
                case SIZE:
                    previous = new SizeTableResultComparator(previous, settings.getTableReverseSort());
                    break;
                case ID:
                default:
                    previous = new IdTableResultComparator(previous, settings.getTableReverseSort());
            }
            return previous;
        }

        @Override
        public void mouseClicked(MouseEvent e)
        {
            // Ignore
        }

        @Override
        public void mousePressed(MouseEvent e)
        {
            if (!eventWorkflow.isRunning())
            {
                if (tw != null)
                {
                    final TextPanel tp = tw.getTextPanel();
                    tp.removeMouseListener(this);
                }
                return;
            }

            if (!inputSettings.getOpticsEventSettingsOrBuilder().getTableCreateSelection())
                return;

            eventWorkflow.run(new ClusterSelectedEvent(id)
            {
                @Override
                int[] computeClusters()
                {
                    if (tw == null)
                        return null;
                    final TextPanel textPanel = tw.getTextPanel();
                    int index = textPanel.getSelectionStart();
                    if (index < 0)
                        return null;
                    final int index2 = textPanel.getSelectionEnd();
                    final TIntArrayList clusters = new TIntArrayList(index2 - index + 1);
                    while (index <= index2)
                    {
                        final String line = textPanel.getLine(index);
                        index++;
                        final int i = line.indexOf('\t');
                        final int j = line.indexOf('\t', i + 1);
                        if (i >= 0 && i < j)
                        {
                            final int id = Integer.parseInt(line.substring(i + 1, j));
                            clusters.add(id);
                        }
                    }
                    return clusters.toArray();
                }
            });
        }

        @Override
        public void mouseReleased(MouseEvent e)
        {
            // Ignore
        }

        @Override
        public void mouseEntered(MouseEvent e)
        {
            // Ignore
        }

        @Override
        public void mouseExited(MouseEvent e)
        {
            // Ignore
        }

        @Override
        public void clusterSelected(ClusterSelectedEvent e)
        {
            if (tw == null || e.getSource() == id)
                return;
            if (!inputSettings.getOpticsEventSettingsOrBuilder().getTableShowSelection())
                return;
            final TextPanel textPanel = tw.getTextPanel();
            int startLine = -1, endLine = -1;
            final int[] clusters = e.getClusters();
            if (clusters == null || clusters.length == 0)
                textPanel.resetSelection();
            else
                // Find the clusters.
                // Assume that the panel is showing the current results.
                for (int i = 0; i < tableResults.size(); i++)
                {
                    final TableResult r = tableResults.getf(i);
                    if (r.id == clusters[0])
                    {
                        // We can only handle selecting continuous lines so
                        // for now just select the first cluster.
                        startLine = endLine = i;
                        break;
                    }
                }
            if (startLine != -1)
                textPanel.setSelection(startLine, endLine);
        }
    }

    private class ClusterSelectedTableWorker extends BaseWorker implements ClusterSelectedHandler
    {
        MemoryPeakResults results;
        CachedClusteringResult clusteringResult;

        boolean display = true;
        IJTablePeakResults table;
        TextWindow tw;
        Rectangle bounds;

        @Override
        public boolean equalSettings(OpticsSettings current, OpticsSettings previous)
        {
            if (current.getOpticsEventSettings().getShowSelectionTable() != previous.getOpticsEventSettings()
                    .getShowSelectionTable())
                return false;
            return true;
        }

        @Override
        protected void newResults()
        {
            // Clear cache
            results = null;
            clusteringResult = null;
        }

        @Override
        public Pair<OpticsSettings, Settings> doWork(Pair<OpticsSettings, Settings> work)
        {
            //OpticsSettings settings = work.s;
            final Settings resultList = work.b;
            final MemoryPeakResults newResults = (MemoryPeakResults) resultList.get(0);
            clusteringResult = (CachedClusteringResult) resultList.get(2);

            // Make display of this table optional and show/hide it
            display = work.a.getOpticsEventSettings().getShowSelectionTable();

            // Optionally hide the window using
            if (display)
            {
                // Check for a closed table
                saveOldLocation();

                // If new results then copy the settings (for calibration)
                if (newResults != results && table != null)
                    table.copySettings(newResults);

                // Clear the table
                if (tw != null)
                    tw.getTextPanel().clear();
            }
            else if (tw != null)
            {
                bounds = tw.getBounds();
                tw.close();
                tw = null;
            }
            results = newResults;

            // We have not created anything new so return the current object
            return work;
        }

        private void saveOldLocation()
        {
            if (tw != null && !tw.isShowing())
            {
                bounds = tw.getBounds();
                tw = null;
            }
        }

        @Override
        public void clusterSelected(ClusterSelectedEvent e)
        {
            saveOldLocation();
            if (!display)
                return;
            if (results == null)
                return;

            final int[] selectedClusters = e.getClusters();
            final int[] parents = clusteringResult.getParents(selectedClusters);
            if (parents == null || parents.length == 0)
            {
                if (table != null)
                    table.clear();
                return;
            }

            // Create the table if needed
            if (tw == null)
            {
                table = new IJTablePeakResults(false, TITLE, true);
                table.setTableTitle(TITLE + " Selected Clusters");
                table.copySettings(results);
                table.setDistanceUnit(DistanceUnit.NM);
                table.setHideSourceText(true);
                table.setShowId(true);
                table.begin();
                tw = table.getResultsWindow();
                if (bounds == null)
                {
                    // Position under the Clusters window
                    final String tableTitle = TITLE + " Clusters";
                    for (final Frame f : WindowManager.getNonImageWindows())
                        if (f != null && tableTitle.equals(f.getTitle()))
                        {
                            // Cascade
                            bounds = tw.getBounds();
                            bounds.x = f.getX() + 30;
                            bounds.y = f.getY() + 30;
                            break;
                        }
                }
                if (bounds != null && table.isNewWindow())
                    tw.setBounds(bounds);
            }
            else
                // Just clear the table
                table.clear();

            final int[] clusters = clusteringResult.getClusters();

            for (final int i : parents)
            {
                final PeakResult p = results.get(i);
                final IdPeakResult r = new IdPeakResult(p.getFrame(), p.getOrigX(), p.getOrigY(), p.getOrigValue(),
                        p.getError(), p.getNoise(), p.getMeanIntensity(), p.getParameters(), p.getParameterDeviations(),
                        clusters[i]);
                table.add(r);
            }

            table.flush();
            tw.getTextPanel().scrollToTop();
        }
    }

    private class KNNWorker extends BaseWorker
    {
        double[] profile = null;

        @Override
        public boolean equalSettings(OpticsSettings current, OpticsSettings previous)
        {
            if (current.getMinPoints() != previous.getMinPoints())
            {
                newResults();
                return false;
            }
            if (current.getSamples() != previous.getSamples() ||
                    current.getSampleFraction() != previous.getSampleFraction())
            {
                newResults();
                return false;
            }
            if (current.getFractionNoise() != previous.getFractionNoise())
                return false;
            if (clusteringDistanceChange(current.getClusteringDistance(), previous.getClusteringDistance()))
                return false;

            return true;
        }

        @Override
        protected void newResults()
        {
            // Clear cache
            profile = null;
        }

        @Override
        public Pair<OpticsSettings, Settings> doWork(Pair<OpticsSettings, Settings> work)
        {
            OpticsSettings settings = work.a;
            final Settings resultList = work.b;
            // The first item should be the memory peak results
            final MemoryPeakResults results = (MemoryPeakResults) resultList.get(0);
            // The second item should be the OPTICS manager
            final OPTICSManager opticsManager = (OPTICSManager) resultList.get(1);

            final int minPts = settings.getMinPoints();
            final int k = minPts - 1; // Since min points includes the actual point
            final double fractionNoise = settings.getFractionNoise();

            final double nmPerPixel = getNmPerPixel(results);

            // Flag indicating that the scale can be kept on a new plot
            int preserve = Utils.PRESERVE_ALL;

            // Create a profile of the K-Nearest Neighbour distances
            if (profile == null)
            {
                preserve = 0;
                synchronized (opticsManager)
                {
                    int samples = settings.getSamples();
                    if (samples > 1 || settings.getSampleFraction() > 0)
                        // Ensure we take a reasonable amount of samples (min=100)
                        samples = Maths.max(100, samples,
                                (int) Math.ceil(opticsManager.getSize() * settings.getSampleFraction()));
                    final float[] d = opticsManager.nearestNeighbourDistance(k, samples, true);
                    profile = new double[d.length];
                    for (int i = d.length; i-- > 0;)
                        profile[i] = d[i];
                }
                Arrays.sort(profile);
                SimpleArrayUtils.reverse(profile);
                if (nmPerPixel != 1)
                    for (int i = 0; i < profile.length; i++)
                        profile[i] *= nmPerPixel;
            }

            final String units = (nmPerPixel != 1) ? " (nm)" : " (px)";

            final double[] order = SimpleArrayUtils.newArray(profile.length, 1.0, 1.0);
            final String title = TITLE + " KNN Distance";
            final Plot2 plot = new Plot2(title, "Sample", k + "-NN Distance" + units);
            final double[] limits = new double[] { profile[profile.length - 1], profile[0] };

            plot.setLimits(1, order.length, limits[0], limits[1] * 1.05);

            plot.setColor(Color.black);
            plot.addPoints(order, profile, Plot.LINE);

            // Add the DBSCAN clustering distance
            double distance = settings.getClusteringDistance();
            if (distance > 0)
            {
                plot.setColor(Color.red);
                plot.drawLine(1, distance, order.length, distance);
            }

            // Find the clustering distance using a % noise in the KNN distance samples
            distance = findClusteringDistance(profile, fractionNoise);
            plot.setColor(Color.blue);
            plot.drawDottedLine(1, distance, order.length, distance, 2);

            Utils.display(title, plot, preserve);

            if (settings.getClusteringDistance() == 0)
            {
                // Set this distance into the settings if there is no clustering distance
                // Use a negative value to show it is an auto-distance
                settings = settings.toBuilder().setClusteringDistance(-distance).build();

                // Updated settings
                return new Pair<>(settings, resultList);
            }
            // We have not created anything new so return the current object
            return work;
        }
    }

    private static boolean clusteringDistanceChange(double newD, double oldD)
    {
        // The input distance can never be below zero due to the use of abs.
        // If the auto-distance changes then we want to rerun DBSCAN so remove this check.
        //if (newD <= 0 && oldD <= 0)
        //	// Auto-distance
        //	return false;

        return newD != oldD;
    }

    private static void scrambleClusters(ClusteringResult result)
    {
        // Scramble to ensure adjacent clusters have different Ids.
        // Same seed for consistency (e.g. in macros on the same data).
        result.scrambleClusters(new Well19937c(1999));
    }

    /**
     * Find the clustering distance using a sorted profile of the KNN distance.
     *
     * @param profile
     *            the profile (sorted high to low)
     * @param fractionNoise
     *            the fraction noise
     * @return the clustering distance
     */
    public static double findClusteringDistance(double[] profile, double fractionNoise)
    {
        // Return the next distance after the fraction has been achieved
        final int n = Maths.clip(0, profile.length - 1, (int) Math.ceil(profile.length * fractionNoise));
        return profile[n];
    }

    private class DBSCANWorker extends BaseWorker
    {
        @Override
        public boolean equalSettings(OpticsSettings current, OpticsSettings previous)
        {
            if (current.getMinPoints() != previous.getMinPoints())
                return false;
            if (clusteringDistanceChange(current.getClusteringDistance(), previous.getClusteringDistance()))
                return false;
            return true;
        }

        @Override
        public Pair<OpticsSettings, Settings> doWork(Pair<OpticsSettings, Settings> work)
        {
            final OpticsSettings settings = work.a;
            final Settings resultList = work.b;
            // The first item should be the memory peak results
            final MemoryPeakResults results = (MemoryPeakResults) resultList.get(0);
            // The second item should be the OPTICS manager
            final OPTICSManager opticsManager = (OPTICSManager) resultList.get(1);

            double clusteringDistance = Math.abs(settings.getClusteringDistance());
            final int minPts = settings.getMinPoints();
            if (clusteringDistance > 0)
            {
                // Convert clustering distance to pixels
                final double nmPerPixel = getNmPerPixel(results);
                if (nmPerPixel != 1)
                {
                    final double newGeneratingDistance = clusteringDistance / nmPerPixel;
                    Utils.log(TITLE + ": Converting clustering distance %s nm to %s pixels",
                            Utils.rounded(clusteringDistance), Utils.rounded(newGeneratingDistance));
                    clusteringDistance = newGeneratingDistance;
                }
            }
            else
            {
                // Note: This should not happen since the clustering distance is set using the KNN distance samples

                final double nmPerPixel = getNmPerPixel(results);
                if (nmPerPixel != 1)
                    Utils.log(TITLE + ": Default clustering distance %s nm",
                            Utils.rounded(opticsManager.computeGeneratingDistance(minPts) * nmPerPixel));
            }

            DBSCANResult dbscanResult;
            synchronized (opticsManager)
            {
                dbscanResult = opticsManager.dbscan((float) clusteringDistance, minPts);
                // Scramble only needs to be done once as the cluster Ids are not re-allocated when extracting the clusters
                scrambleClusters(dbscanResult);
            }
            // It may be null if cancelled. However return null Work will close down the next thread
            return new Pair<>(settings, new Settings(results, opticsManager, new CachedClusteringResult(dbscanResult)));
        }
    }

    private class DBSCANClusterWorker extends BaseWorker
    {
        @Override
        public boolean equalSettings(OpticsSettings current, OpticsSettings previous)
        {
            if (current.getCore() != previous.getCore())
                return false;
            return true;
        }

        @Override
        public Pair<OpticsSettings, Settings> doWork(Pair<OpticsSettings, Settings> work)
        {
            final OpticsSettings settings = work.a;
            final Settings resultList = work.b;
            final MemoryPeakResults results = (MemoryPeakResults) resultList.get(0);
            final OPTICSManager opticsManager = (OPTICSManager) resultList.get(1);
            final CachedClusteringResult clusteringResult = (CachedClusteringResult) resultList.get(2);
            // It may be null if cancelled.
            if (clusteringResult.isValid())
            {
                final DBSCANResult dbscanResult = clusteringResult.getDBSCANResult();
                synchronized (dbscanResult)
                {
                    dbscanResult.extractClusters(settings.getCore());
                }
                // We created a new clustering
                return new Pair<>(settings,
                        new Settings(results, opticsManager, new CachedClusteringResult(dbscanResult)));
            }
            return work;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void run(String arg)
    {
        SMLMUsageTracker.recordPlugin(this.getClass(), arg);

        if (MemoryPeakResults.isMemoryEmpty())
        {
            IJ.error(TITLE, "No localisations in memory");
            return;
        }

        extraOptions = Utils.isExtraOptions();

        inputSettings = SettingsManager.readOpticsSettings(0).toBuilder();

        IJ.showStatus("");

        if ("dbscan".equals(arg))
            runDBSCAN();
        else
            runOPTICS();

        IJ.showStatus(TITLE + " finished");

        // Update the settings
        SettingsManager.writeSettings(inputSettings.build());
    }

    private void runDBSCAN()
    {
        TITLE = TITLE_DBSCAN;

        createEventWorkflow();

        // Create the workflow
        workflow.add(new InputWorker());
        workflow.add(new KNNWorker());
        workflow.add(new DBSCANWorker());
        final int previous = workflow.add(new DBSCANClusterWorker());
        // The following can operate in parallel
        workflow.add(new ResultsWorker(), previous);
        workflow.add(new RandIndexWorker(), previous);
        workflow.add(new MemoryResultsWorker(), previous);
        workflow.add(new ImageResultsWorker(), previous);
        workflow.add(new TableResultsWorker(), previous);
        workflow.add(new ClusterSelectedTableWorker(), previous);

        workflow.start();

        final boolean cancelled = !showDialog(true);

        shutdownWorkflows(cancelled);
    }

    private void createEventWorkflow()
    {
        eventWorkflow = new Workflow<>();
        eventWorkflow.add(new ClusterSelectedEventWorker());
        eventWorkflow.add(clusterSelectedWorker = new ClusterSelectedWorker());
        eventWorkflow.start();
    }

    private void shutdownWorkflows(boolean cancelled)
    {
        workflow.shutdown(cancelled);
        eventWorkflow.shutdown(cancelled);
    }

    private void runOPTICS()
    {
        TITLE = TITLE_OPTICS;

        createEventWorkflow();

        // Create the workflow
        workflow.add(new InputWorker());
        workflow.add(new OpticsWorker());
        final int previous = workflow.add(new OpticsClusterWorker());
        // The following can operate in parallel
        workflow.add(new ResultsWorker(), previous);
        workflow.add(new RandIndexWorker(), previous);
        workflow.add(new MemoryResultsWorker(), previous);
        workflow.add(new ReachabilityResultsWorker(), previous);
        workflow.add(new ImageResultsWorker(), previous);
        workflow.add(new TableResultsWorker(), previous);
        workflow.add(new ClusterSelectedTableWorker(), previous);

        workflow.start();

        final boolean cancelled = !showDialog(false);

        shutdownWorkflows(cancelled);
    }

    /**
     * Gets the nm per pixel.
     *
     * @param results
     *            the results
     * @return the nm per pixel
     */
    public double getNmPerPixel(MemoryPeakResults results)
    {
        if (results.hasCalibration() && results.getCalibrationReader().hasNmPerPixel())
            return results.getCalibrationReader().getNmPerPixel();
        return 1;
    }

    private Object[] imageModeArray;
    private Object[] outlineModeArray;

    private boolean showDialog(boolean isDBSCAN)
    {
        logReferences(isDBSCAN);

        final NonBlockingExtendedGenericDialog gd = new NonBlockingExtendedGenericDialog(TITLE);
        gd.addHelp(About.HELP_URL);

        ResultsManager.addInput(gd, inputSettings.getInputOption(), InputSource.MEMORY);

        //globalSettings = SettingsManager.loadSettings();
        //settings = globalSettings.getClusteringSettings();

        if (isDBSCAN)
            gd.addMessage("--- Nearest-Neighbour Analysis ---");
        else
            gd.addMessage("--- " + TITLE + " ---");
        gd.addNumericField("Min_points", inputSettings.getMinPoints(), 0);
        if (isDBSCAN)
        {
            // Add fields to auto-compute the clustering distance from the K-nearest neighbour distance profile
            gd.addSlider("Noise (%)", 0, 50, inputSettings.getFractionNoise() * 100);
            gd.addNumericField("Samples", inputSettings.getSamples(), 0);
            gd.addSlider("Sample_fraction (%)", 0, 15, inputSettings.getSampleFraction() * 100);
        }
        else
        {
            final String[] opticsModes = SettingsManager.getNames((Object[]) OpticsMode.values());
            gd.addChoice("OPTICS_mode", opticsModes, inputSettings.getOpticsMode(), new OptionListener<Integer>()
            {
                @Override
                public boolean collectOptions(Integer value)
                {
                    inputSettings.setOpticsMode(value);
                    final boolean result = collectOptions(false);
                    return result;
                }

                @Override
                public boolean collectOptions()
                {
                    return collectOptions(true);
                }

                private boolean collectOptions(boolean silent)
                {
                    final OpticsMode mode = OpticsMode.get(inputSettings.getOpticsMode());
                    final ExtendedGenericDialog egd = new ExtendedGenericDialog(mode.toString() + " options");
                    final OpticsSettings oldSettings = inputSettings.build();
                    if (mode == OpticsMode.FAST_OPTICS)
                    {
                        egd.addMessage(TextUtils.wrap(
                                "The number of splits to compute (if below 1 it will be auto-computed using the size of the data)",
                                80));
                        egd.addNumericField("Number_of_splits", inputSettings.getNumberOfSplitSets(), 0);
                        if (extraOptions)
                        {
                            egd.addCheckbox("Random_vectors", inputSettings.getUseRandomVectors());
                            egd.addCheckbox("Approx_sets", inputSettings.getSaveApproximateSets());
                            final String[] sampleModes = SettingsManager.getNames((Object[]) SampleMode.values());
                            egd.addChoice("Sample_mode", sampleModes, inputSettings.getSampleMode());
                        }
                        egd.setSilent(silent);
                        egd.showDialog(true, gd);
                        if (egd.wasCanceled())
                            return false;
                        inputSettings.setNumberOfSplitSets((int) Math.abs(egd.getNextNumber()));
                        if (extraOptions)
                        {
                            inputSettings.setUseRandomVectors(egd.getNextBoolean());
                            inputSettings.setSaveApproximateSets(egd.getNextBoolean());
                            inputSettings.setSampleMode(egd.getNextChoiceIndex());
                        }
                    }
                    else // OPTICS
                    {
                        egd.addNumericField("Generating_distance", inputSettings.getGeneratingDistance(), 2, 6, "nm");
                        egd.showDialog(true, gd);
                        if (egd.wasCanceled())
                            return false;
                        inputSettings.setGeneratingDistance(Math.abs(egd.getNextNumber()));
                    }
                    // Return true if new settings
                    return !inputSettings.build().equals(oldSettings);
                }
            });
        }
        gd.addMessage("--- Clustering ---");
        if (isDBSCAN)
        {
            gd.addNumericField("Clustering_distance", inputSettings.getClusteringDistance(), 2, 6, "nm");
            gd.addCheckbox("Core_points", inputSettings.getCore());
        }
        else
        {
            final String[] clusteringModes = SettingsManager.getNames((Object[]) ClusteringMode.values());
            gd.addChoice("Clustering_mode", clusteringModes, inputSettings.getClusteringMode(),
                    new OptionListener<Integer>()
                    {

                        @Override
                        public boolean collectOptions(Integer value)
                        {
                            inputSettings.setClusteringMode(value);
                            final boolean result = collectOptions(false);
                            return result;
                        }

                        @Override
                        public boolean collectOptions()
                        {
                            return collectOptions(true);
                        }

                        private boolean collectOptions(boolean silent)
                        {
                            final ClusteringMode mode = ClusteringMode.get(inputSettings.getClusteringMode());
                            final ExtendedGenericDialog egd = new ExtendedGenericDialog(mode.toString() + " options");
                            final OpticsSettings oldSettings = inputSettings.build();
                            if (mode == ClusteringMode.XI)
                            {
                                egd.addMessage(
                                        "Xi controls the change in reachability (profile steepness) to define a cluster");
                                egd.addNumericField("Xi", inputSettings.getXi(), 4);
                                egd.addCheckbox("Top_clusters", inputSettings.getTopLevel());
                                egd.addNumericField("Upper_limit", inputSettings.getUpperLimit(), 4, 10, "nm");
                                egd.addNumericField("Lower_limit", inputSettings.getLowerLimit(), 4, 10, "nm");
                                egd.setSilent(silent);
                                egd.showDialog(true, gd);
                                if (egd.wasCanceled())
                                    return false;
                                inputSettings.setXi(Math.abs(egd.getNextNumber()));
                                inputSettings.setTopLevel(egd.getNextBoolean());
                                inputSettings.setUpperLimit(Math.abs(egd.getNextNumber()));
                                inputSettings.setLowerLimit(Math.abs(egd.getNextNumber()));
                            }
                            else // DBSCAN
                            {
                                egd.addMessage(ClusteringMode.DBSCAN.toString() + " options:");
                                egd.addNumericField("Clustering_distance", inputSettings.getClusteringDistance(), 4, 10,
                                        "nm");
                                egd.addCheckbox("Core_points", inputSettings.getCore());
                                egd.setSilent(silent);
                                egd.showDialog(true, gd);
                                if (egd.wasCanceled())
                                    return false;
                                inputSettings.setClusteringDistance(Math.abs(egd.getNextNumber()));
                                inputSettings.setCore(egd.getNextBoolean());
                            }
                            // Return true if new settings
                            return !inputSettings.build().equals(oldSettings);
                        }
                    });
        }
        gd.addMessage("--- Table ---");
        gd.addCheckbox("Show_table", inputSettings.getShowTable(), new OptionListener<Boolean>()
        {
            @Override
            public boolean collectOptions(Boolean value)
            {
                inputSettings.setShowTable(value);
                final boolean result = collectOptions(false);
                return result;
            }

            @Override
            public boolean collectOptions()
            {
                return collectOptions(true);
            }

            private boolean collectOptions(boolean silent)
            {
                if (!inputSettings.getShowTable())
                    return false;
                final ExtendedGenericDialog egd = new ExtendedGenericDialog("Table options");
                final OpticsSettings oldSettings = inputSettings.build();
                final String[] modes = SettingsManager.getNames((Object[]) TableSortMode.values());
                egd.addChoice("Table_sort_mode", modes, inputSettings.getTableSortMode());
                egd.addCheckbox("Table_reverse_sort", inputSettings.getTableReverseSort());
                egd.setSilent(silent);
                egd.showDialog(true, gd);
                if (egd.wasCanceled())
                    return false;
                inputSettings.setTableSortMode(egd.getNextChoiceIndex());
                inputSettings.setTableReverseSort(egd.getNextBoolean());
                // Return true if new settings
                return !inputSettings.build().equals(oldSettings);
            }
        });

        gd.addMessage("--- Image ---");
        gd.addSlider("Image_scale", 0, 15, inputSettings.getImageScale());
        final TreeSet<ImageMode> imageModeSet = new TreeSet<>();
        imageModeSet.addAll(Arrays.asList(ImageMode.values()));
        if (isDBSCAN)
        {
            imageModeSet.remove(ImageMode.CLUSTER_DEPTH);
            imageModeSet.remove(ImageMode.CLUSTER_ORDER);
        }
        imageModeArray = imageModeSet.toArray();
        final String[] imageModes = SettingsManager.getNames(imageModeArray);
        gd.addChoice("Image_mode", imageModes, ImageMode.get(inputSettings.getImageMode()).toString(),
                new OptionListener<Integer>()
                {
                    @Override
                    public boolean collectOptions(Integer value)
                    {
                        inputSettings.setImageMode(((ImageMode) imageModeArray[value]).ordinal());
                        final boolean result = collectOptions(false);
                        return result;
                    }

                    @Override
                    public boolean collectOptions()
                    {
                        return collectOptions(true);
                    }

                    private boolean collectOptions(boolean silent)
                    {
                        final ImageMode mode = ImageMode.get(inputSettings.getImageMode());
                        final ExtendedGenericDialog egd = new ExtendedGenericDialog(mode.toString() + " options");
                        if (mode.canBeWeighted())
                        {
                            egd.addCheckbox("Weighted", inputSettings.getWeighted());
                            egd.addCheckbox("Equalised", inputSettings.getEqualised());
                        }
                        if (mode == ImageMode.LOOP && extraOptions)
                            egd.addNumericField("LoOP_lambda", inputSettings.getLambda(), 4);
                        if (!egd.hasFields())
                            return false;
                        egd.setSilent(silent);
                        egd.showDialog(true, gd);
                        if (egd.wasCanceled())
                            return false;
                        final OpticsSettings oldSettings = inputSettings.build();
                        if (mode.canBeWeighted())
                        {
                            inputSettings.setWeighted(egd.getNextBoolean());
                            inputSettings.setEqualised(egd.getNextBoolean());
                        }
                        if (mode == ImageMode.LOOP && extraOptions)
                            inputSettings.setLambda(Math.abs(gd.getNextNumber()));
                        // Return true if new settings
                        return !inputSettings.build().equals(oldSettings);
                    }
                });

        final TreeSet<OutlineMode> outlineModeSet = new TreeSet<>();
        outlineModeSet.addAll(Arrays.asList(OutlineMode.values()));
        if (isDBSCAN)
            outlineModeSet.remove(OutlineMode.COLOURED_BY_DEPTH);
        outlineModeArray = outlineModeSet.toArray();
        final String[] outlineModes = SettingsManager.getNames(outlineModeArray);
        gd.addChoice("Outline", outlineModes, OutlineMode.get(inputSettings.getOutlineMode()).toString());

        if (!isDBSCAN)
        {
            final String[] spanningTreeModes = SettingsManager.getNames((Object[]) SpanningTreeMode.values());
            gd.addChoice("Spanning_tree", spanningTreeModes, inputSettings.getSpanningTreeMode());

            gd.addMessage("--- Reachability Plot ---");
            final String[] plotModes = SettingsManager.getNames((Object[]) PlotMode.values());
            gd.addChoice("Plot_mode", plotModes, inputSettings.getPlotMode());
        }

        // Everything is done within the dialog listener
        final BaseDialogListener listener = (isDBSCAN) ? new DBSCANDialogListener(gd) : new OPTICSDialogListener(gd);
        gd.addDialogListener(listener);
        gd.addOptionCollectedListener(listener);

        // Start disabled so the user can choose settings to update
        if (Utils.isShowGenericDialog())
            gd.addCheckbox("Preview", false, listener);
        else
            gd.addCheckbox("Preview", false);

        if (extraOptions)
            gd.addCheckbox("Debug", false);

        gd.showDialog();

        if (gd.wasCanceled())
            return false;

        // The dialog was OK'd so run if work was staged in the workflow.
        if (workflow.isStaged())
            workflow.runStaged();

        // Record the options for macros since the NonBlocking dialog does not
        if (Recorder.record)
        {
            Recorder.recordOption("Min_points", Integer.toString(inputSettings.getMinPoints()));
            if (isDBSCAN)
            {
                // Add fields to auto-compute the clustering distance from the K-nearest neighbour distance profile
                Recorder.recordOption("Noise", Double.toString(inputSettings.getFractionNoise() * 100));
                Recorder.recordOption("Samples", Double.toString(inputSettings.getSamples()));
                Recorder.recordOption("Sample_fraction", Double.toString(inputSettings.getSampleFraction() * 100));
                Recorder.recordOption("Clustering_distance", Double.toString(inputSettings.getClusteringDistance()));
            }
            else
            {
                Recorder.recordOption("OPTICS_mode", OpticsMode.get(inputSettings.getOpticsMode()).toString());
                Recorder.recordOption("Number_of_splits", Integer.toString(inputSettings.getNumberOfSplitSets()));
                if (extraOptions)
                {
                    if (inputSettings.getUseRandomVectors())
                        Recorder.recordOption("Random_vectors");
                    if (inputSettings.getSaveApproximateSets())
                        Recorder.recordOption("Approx_sets");
                    Recorder.recordOption("Sample_mode", SampleMode.get(inputSettings.getSampleMode()).toString());
                }
                Recorder.recordOption("Generating_distance", Double.toString(inputSettings.getGeneratingDistance()));
            }
            if (isDBSCAN)
            {
                if (inputSettings.getCore())
                    Recorder.recordOption("Core_points");
            }
            else
            {
                Recorder.recordOption("Clustering_mode",
                        ClusteringMode.get(inputSettings.getClusteringMode()).toString());
                Recorder.recordOption("Xi", Double.toString(inputSettings.getXi()));
                if (inputSettings.getTopLevel())
                    Recorder.recordOption("Top_clusters");
                Recorder.recordOption("Upper_limit", Double.toString(inputSettings.getUpperLimit()));
                Recorder.recordOption("Lower_limit", Double.toString(inputSettings.getLowerLimit()));
                Recorder.recordOption("Clustering_distance", Double.toString(inputSettings.getClusteringDistance()));
                if (inputSettings.getCore())
                    Recorder.recordOption("Core_points");
            }
            if (inputSettings.getShowTable())
            {
                Recorder.recordOption("Show_table");
                Recorder.recordOption("Table_sort_mode",
                        TableSortMode.get(inputSettings.getTableSortMode()).toString());
                if (inputSettings.getTableReverseSort())
                    Recorder.recordOption("Table_reverse_sort");
            }

            Recorder.recordOption("Image_scale", Double.toString(inputSettings.getImageScale()));
            Recorder.recordOption("Image_mode", ImageMode.get(inputSettings.getImageMode()).toString());

            if (inputSettings.getWeighted())
                Recorder.recordOption("Weighted");
            if (inputSettings.getEqualised())
                Recorder.recordOption("Equalised");
            if (extraOptions)
                Recorder.recordOption("LoOP_lambda", Double.toString(inputSettings.getLambda()));
            Recorder.recordOption("Outline", OutlineMode.get(inputSettings.getOutlineMode()).toString());

            if (!isDBSCAN)
            {
                Recorder.recordOption("Spanning_tree",
                        SpanningTreeMode.get(inputSettings.getSpanningTreeMode()).toString());
                Recorder.recordOption("Plot_mode", PlotMode.get(inputSettings.getPlotMode()).toString());
            }

            if (debug)
                Recorder.recordOption("Debug");
        }

        return true;
    }

    private static byte logged = 0;
    private static final byte LOG_DBSCAN = 0x01;
    private static final byte LOG_OPTICS = 0x02;
    private static final byte LOG_LOOP = 0x04;

    private static void logReferences(boolean isDBSCAN)
    {
        final int width = 80;
        final StringBuilder sb = new StringBuilder();
        if (isDBSCAN && (logged & LOG_DBSCAN) != LOG_DBSCAN)
        {
            logged |= LOG_DBSCAN;
            sb.append("DBSCAN: ");
            sb.append(TextUtils.wrap(
                    "Ester, et al (1996). 'A density-based algorithm for discovering clusters in large spatial databases with noise'. Proceedings of the Second International Conference on Knowledge Discovery and Data Mining (KDD-96). AAAI Press. pp. 226–231.",
                    width)).append('\n');
        }
        else if ((logged & LOG_OPTICS) != LOG_OPTICS)
        {
            logged |= LOG_OPTICS;
            sb.append("OPTICS: ");
            sb.append(TextUtils.wrap(
                    "Kriegel, et al (2011). 'Density-based clustering'. Wiley Interdisciplinary Reviews: Data Mining and Knowledge Discovery. 1 (3): 231–240.",
                    width)).append('\n');
            sb.append("FastOPTICS: ");
            sb.append(TextUtils.wrap(
                    "Schneider, et al (2013). 'Fast parameterless density-based clustering via random projections'. 22nd ACM International Conference on Information and Knowledge Management(CIKM). ACM. pp. 861-866.",
                    width)).append('\n');
        }
        if ((logged & LOG_LOOP) != LOG_LOOP)
        {
            logged |= LOG_LOOP;
            sb.append("LoOP: ");
            sb.append(TextUtils.wrap(
                    "Kriegel, et al (2009). 'LoOP: Local Outlier Probabilities'. 18th ACM International Conference on Information and knowledge management(CIKM). ACM. pp. 1649-1652.",
                    width)).append('\n');
        }
        if (sb.length() > 0)
            IJ.log(sb.toString());
    }

    private abstract class BaseDialogListener
            implements DialogListener, OptionCollectedListener, OptionListener<Boolean>
    {
        final GenericDialog gd;
        MemoryPeakResults results = null;
        String input = null;

        BaseDialogListener(GenericDialog gd)
        {
            this.gd = gd;
        }

        @Override
        public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
        {
            //		if (e == null)
            //		{
            //			// This happens when the dialog is first shown and can be ignored.
            //			// It also happens when called from within a macro. In this case we should run.
            //			if (!Utils.isMacro())
            //				return true;
            //		}

            if (debug)
                System.out.println("dialogItemChanged: " + e);

            // A previous run may have been cancelled so we have to handle this.
            if (Utils.isInterrupted())
            {
                if (Utils.isMacro())
                    return true;

                // Q. Should we ask if the user wants to restart?
                IJ.resetEscape();
            }

            inputSettings.setInputOption(ResultsManager.getInputSource(gd));

            // Load the results.
            if (results == null || !inputSettings.getInputOption().equals(input))
            {
                input = inputSettings.getInputOption();
                // TODO - update the plugin to handle any data, not just pixels
                results = ResultsManager.loadInputResults(inputSettings.getInputOption(), true, DistanceUnit.PIXEL,
                        null);
            }
            if (results == null || results.size() == 0)
            {
                IJ.error(TITLE, "No results could be loaded");
                return false;
            }

            if (!readSettings(gd))
                return false;

            // If the change was from a checkbox or selection box then we do not have to delay
            boolean delay = true;
            if (e != null && e.getSource() != null)
            {
                final Object source = e.getSource();
                if (source instanceof Checkbox || source instanceof Choice)
                    delay = false;
            }

            createWork(delay);

            return true;
        }

        private void createWork(boolean delay)
        {
            // Clone so that the workflow has it's own unique reference
            final OpticsSettings settings = inputSettings.build();
            final Settings baseResults = new Settings(results);
            if (preview)
            {
                // Run the settings
                if (debug)
                    System.out.println("Adding work");
                if (!delay)
                    workflow.stopPreview();
                workflow.run(settings, baseResults);
                workflow.startPreview();
            }
            else
            {
                workflow.stopPreview();
                // Stage the work but do not run
                workflow.stage(settings, baseResults);
            }
        }

        /** {@inheritDoc} */
        @Override
        public void optionCollected(OptionCollectedEvent e)
        {
            // This occurs when any of the additional options have changed.
            // We just add the work with no delay.
            createWork(false);
        }

        abstract boolean readSettings(GenericDialog gd);

        @Override
        public boolean collectOptions(Boolean value)
        {
            // Collect the options for the events
            final OpticsEventSettings old = inputSettings.getOpticsEventSettings();
            final ExtendedGenericDialog egd = new ExtendedGenericDialog(TITLE + " Preview Settings");
            final boolean record = Recorder.record;
            Recorder.record = false;
            egd.addMessage("Configure the interactive preview");
            egd.addCheckbox("Show_selected_clusters_table", old.getShowSelectionTable());
            egd.addCheckbox("Table_create_selection", old.getTableCreateSelection());
            egd.addCheckbox("Table_show_selection", old.getTableShowSelection());
            egd.addCheckbox("Image_create_selection", old.getImageCreateSelection());
            egd.addCheckbox("Image_show_selection", old.getImageShowSelection());
            egd.addCheckbox("Plot_create_selection", old.getPlotCreateSelection());
            egd.addCheckbox("Plot_show_selection", old.getPlotShowSelection());
            egd.showDialog(false, gd);
            if (egd.wasCanceled())
            {
                Recorder.record = record;
                return false;
            }
            final OpticsEventSettings.Builder b = inputSettings.getOpticsEventSettingsBuilder();
            b.setShowSelectionTable(egd.getNextBoolean());
            b.setTableCreateSelection(egd.getNextBoolean());
            b.setTableShowSelection(egd.getNextBoolean());
            b.setImageCreateSelection(egd.getNextBoolean());
            b.setImageShowSelection(egd.getNextBoolean());
            b.setPlotCreateSelection(egd.getNextBoolean());
            b.setPlotShowSelection(egd.getNextBoolean());
            Recorder.record = record;
            return !old.equals(b.build());
        }

        @Override
        public boolean collectOptions()
        {
            // We do not care about collecting settings for the event options in macros
            return false;
        }
    }

    private class OPTICSDialogListener extends BaseDialogListener
    {
        OPTICSDialogListener(GenericDialog gd)
        {
            super(gd);
        }

        @Override
        boolean readSettings(GenericDialog gd)
        {
            inputSettings.setMinPoints((int) Math.abs(gd.getNextNumber()));
            inputSettings.setOpticsMode(gd.getNextChoiceIndex());
            inputSettings.setClusteringMode(gd.getNextChoiceIndex());
            inputSettings.setShowTable(gd.getNextBoolean());
            inputSettings.setImageScale(Math.abs(gd.getNextNumber()));
            inputSettings.setImageMode(((ImageMode) imageModeArray[gd.getNextChoiceIndex()]).ordinal());
            inputSettings.setOutlineMode(((OutlineMode) outlineModeArray[gd.getNextChoiceIndex()]).ordinal());
            inputSettings.setSpanningTreeMode(gd.getNextChoiceIndex());
            inputSettings.setPlotMode(gd.getNextChoiceIndex());
            preview = gd.getNextBoolean();
            if (extraOptions)
                debug = gd.getNextBoolean();

            if (gd.invalidNumber())
                return false;

            ((ExtendedGenericDialog) gd).collectOptions(); // For macros

            // Check arguments
            try
            {
                Parameters.isAboveZero("Xi", inputSettings.getXi());
                Parameters.isBelow("Xi", inputSettings.getXi(), 1);
                if (inputSettings.getUpperLimit() > 0)
                    Parameters.isAbove("Upper limit", inputSettings.getUpperLimit(), inputSettings.getLowerLimit());
            }
            catch (final IllegalArgumentException ex)
            {
                Utils.log(TITLE + ": " + ex.getMessage());
                return false;
            }

            return true;
        }
    }

    private class DBSCANDialogListener extends BaseDialogListener
    {
        DBSCANDialogListener(GenericDialog gd)
        {
            super(gd);
        }

        @Override
        boolean readSettings(GenericDialog gd)
        {
            inputSettings.setMinPoints((int) Math.abs(gd.getNextNumber()));
            inputSettings.setFractionNoise(Math.abs(gd.getNextNumber() / 100));
            inputSettings.setSamples((int) Math.abs(gd.getNextNumber()));
            inputSettings.setSampleFraction(Math.abs(gd.getNextNumber() / 100));
            inputSettings.setClusteringDistance(Math.abs(gd.getNextNumber()));
            inputSettings.setCore(gd.getNextBoolean());
            inputSettings.setShowTable(gd.getNextBoolean());
            inputSettings.setImageScale(Math.abs(gd.getNextNumber()));
            inputSettings.setImageMode(((ImageMode) imageModeArray[gd.getNextChoiceIndex()]).ordinal());
            inputSettings.setOutlineMode(((OutlineMode) outlineModeArray[gd.getNextChoiceIndex()]).ordinal());
            preview = gd.getNextBoolean();
            if (extraOptions)
                debug = gd.getNextBoolean();

            if (gd.invalidNumber())
                return false;

            ((ExtendedGenericDialog) gd).collectOptions(); // For macros

            return true;
        }
    }
}
