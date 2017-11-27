package gdsc.smlm.results;

import java.awt.Rectangle;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.annotations.XStreamOmitField;
import com.thoughtworks.xstream.io.xml.DomDriver;

import gdsc.smlm.ij.utils.ImageConverter;

/*----------------------------------------------------------------------------- 
 * GDSC SMLM Software
 * 
 * Copyright (C) 2013 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

/**
 * Abstract base class for the image source for peak results.
 */
public abstract class ImageSource
{
	private String name;
	@XStreamOmitField
	private int startFrame;
	@XStreamOmitField
	private int endFrame;
	protected int xOrigin;
	protected int yOrigin;
	protected int width;
	protected int height;
	protected int frames;

	/**
	 * Create the image source
	 * 
	 * @param name
	 *            The name of the image source
	 */
	public ImageSource(String name)
	{
		setName(name);
	}

	/**
	 * Opens the source
	 */
	public boolean open()
	{
		startFrame = endFrame = 0;
		return openSource();
	}

	/**
	 * Opens the source
	 */
	protected abstract boolean openSource();

	/**
	 * Closes the source
	 */
	public abstract void close();

	/**
	 * Gets the x origin of the image frame. This may be non-zero to specify a crop of an image frame.
	 * <p>
	 * Note that the origin is ignored by the method {@link #next(Rectangle)} and {@link #get(int, Rectangle)} as these
	 * use a rectangle relative to the image source origin.
	 *
	 * @return the x origin
	 */
	public int getXOrigin()
	{
		return xOrigin;
	}

	/**
	 * Gets the y origin of the image frame. This may be non-zero to specify a crop of an image frame.
	 * <p>
	 * Note that the origin is ignored by the method {@link #next(Rectangle)} and {@link #get(int, Rectangle)} as these
	 * use a rectangle relative to the image source origin.
	 *
	 * @return the y origin
	 */
	public int getYOrigin()
	{
		return yOrigin;
	}

	/**
	 * Get the width of the image frame. The frame returned by {@link #next()} will be equal to width * height.
	 * 
	 * @return
	 */
	public int getWidth()
	{
		return width;
	}

	/**
	 * Get the height of the image frame. The frame returned by {@link #next()} will be equal to width * height.
	 * 
	 * @return
	 */
	public int getHeight()
	{
		return height;
	}

	/**
	 * Get the number of frames that can be extracted from the image source with calls to {@link #next()}
	 * 
	 * @return The total number of frames
	 */
	public int getFrames()
	{
		return frames;
	}

	/**
	 * Get the start frame number of the source returned by the last call to {@link #get(int)} or {@link #next()}.
	 * 
	 * @return The start frame number of the latest block of data
	 */
	public int getStartFrameNumber()
	{
		return startFrame;
	}

	/**
	 * Get the end frame number of the source returned by the last call to {@link #get(int)} or {@link #next()}.
	 * <p>
	 * This may be larger than the result returned by {@link #getFrames()} if the ImageSource is selecting a subset of
	 * the possible frames.
	 * 
	 * @return The end frame number of the latest block of data
	 */
	public int getEndFrameNumber()
	{
		return endFrame;
	}

	/**
	 * Set the current frame number(s) of the source returned by the last call to {@link #get(int)} or {@link #next()}.
	 * <p>
	 * This should be called by subclasses that perform more complex frame manipulation than just getting a single
	 * frame.
	 * 
	 * @param startFrame
	 *            the start frame of the current block of data
	 * @param endFrame
	 *            the end frame of the current block of data
	 */
	protected void setFrameNumber(int startFrame, int endFrame)
	{
		this.startFrame = startFrame;
		this.endFrame = endFrame;
	}

	/**
	 * Get the next frame. Return null if the frame is not available and set the current frame to
	 * zero. The data is is packed in yx order: index = y * width + x;
	 * <p>
	 * Provides serial access to the data after a successful call to {@link #openSource()}
	 * 
	 * @return the next frame (or null if at the end)
	 */
	public float[] next()
	{
		return next(null);
	}

	/**
	 * Get the next frame. Return null if the frame is not available and set the current frame to
	 * zero. The data is is packed in yx order: index = y * width + x;
	 * <p>
	 * Provides serial access to the data after a successful call to {@link #openSource()}
	 * <p>
	 * Note: The bounds are relative to the image source origin so that bounds.x + bounds.width must be less or equal to
	 * than {@link #getWidth()}, similarly for height.
	 * 
	 * @param bounds
	 *            The bounding limits of the frame to extract
	 * @return the next frame (or null if at the end)
	 */
	public float[] next(Rectangle bounds)
	{
		if (!checkBounds(bounds))
			bounds = null;
		startFrame = endFrame = (startFrame + 1);
		Object pixels = nextRawFrame();
		if (pixels != null)
		{
			return ImageConverter.getData(pixels, getWidth(), getHeight(), bounds, null);
		}
		startFrame = endFrame = 0;
		return null;
	}

	/**
	 * Get the next frame of raw pixels. Return null if the frame is not available and set the current frame to
	 * zero. The data is is packed in yx order: index = y * width + x;
	 * <p>
	 * Provides serial access to the data after a successful call to {@link #openSource()}
	 * 
	 * @return the next frame (or null if at the end)
	 */
	public Object nextRaw()
	{
		startFrame = endFrame = (startFrame + 1);
		Object data = nextRawFrame();
		if (data == null)
			startFrame = endFrame = 0;
		return data;
	}

	/**
	 * Get the next frame of raw pixels. The data is is packed in yx order: index = y * width + x;
	 * <p>
	 * Must be implemented by sub-classes.
	 *
	 * @return the next frame (or null if at the end)
	 */
	protected abstract Object nextRawFrame();

	/**
	 * Get a specific frame from the results. Return null if the frame is not available and set the current frame to
	 * zero.
	 * <p>
	 * Provides random access to the data after a successful call to {@link #openSource()}. This operation may be
	 * significantly slower than using {@link #next()} to read all the data.
	 * 
	 * @param frame
	 * @return the frame (or null)
	 */
	public float[] get(int frame)
	{
		return get(frame, null);
	}

	/**
	 * Get a specific frame from the results. Return null if the frame is not available and set the current frame to
	 * zero.
	 * <p>
	 * Provides random access to the data after a successful call to {@link #openSource()}. This operation may be
	 * significantly slower than using {@link #next()} to read all the data.
	 * <p>
	 * Note: The bounds are relative to the image source origin so that bounds.x + bounds.width must be less or equal to
	 * than {@link #getWidth()}, similarly for height.
	 * 
	 * @param frame
	 * @param bounds
	 *            The bounding limits of the frame to extract
	 * @return the frame (or null)
	 */
	public float[] get(int frame, Rectangle bounds)
	{
		if (!checkBounds(bounds))
			bounds = null;
		startFrame = endFrame = frame;
		Object pixels = getRawFrame(frame);
		if (pixels != null)
		{
			return ImageConverter.getData(pixels, getWidth(), getHeight(), bounds, null);
		}
		startFrame = endFrame = 0;
		return null;
	}

	/**
	 * Get a specific frame of raw pixels from the results. Return null if the frame is not available and set the
	 * current frame to zero.
	 * <p>
	 * Provides random access to the data after a successful call to {@link #openSource()}. This operation may be
	 * significantly slower than using {@link #next()} to read all the data.
	 * 
	 * @param frame
	 * @return the frame (or null)
	 */
	public Object getRaw(int frame)
	{
		startFrame = endFrame = frame;
		Object data = getRawFrame(frame);
		if (data == null)
			startFrame = endFrame = 0;
		return data;
	}

	/**
	 * Get a specific frame of raw pixels from the results. Return null if the frame is not available.
	 * <p>
	 * Must be implemented by sub-classes.
	 *
	 * @param frame
	 *            the frame
	 * @return The frame data
	 */
	protected abstract Object getRawFrame(int frame);

	/**
	 * Get the name of the results source
	 * 
	 * @return
	 */
	public String getName()
	{
		return name;
	}

	/**
	 * Get the parent source upon which this source is based. The default is to return null.
	 * 
	 * @return The parent source
	 */
	public ImageSource getParent()
	{
		return null;
	}

	/**
	 * Get the original source for the data provided. The default is to return this object.
	 * 
	 * @return The original source
	 */
	public ImageSource getOriginal()
	{
		return this;
	}

	/**
	 * Set the name of the results source
	 * 
	 * @param name
	 */
	public void setName(String name)
	{
		if (name != null && name.length() > 0)
			this.name = name;
		else
			this.name = "";
	}

	/**
	 * Return true if the frame is within the limits of the image source.
	 * <p>
	 * Note that the {@link #get(int)} method may still return null. This method can be used to determine if the
	 * {@link #get(int)} method has skipped data, e.g. if interlaced, or if the data has actually ended.
	 * 
	 * @param frame
	 * @return true if valid
	 */
	public abstract boolean isValid(int frame);

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		// Over-ride this to produce a nicer output description of the results source
		return String.format("%s [%d,%d:%dx%dx%d]", name, xOrigin, yOrigin, getWidth(), getHeight(), frames);
	}

	/**
	 * @return An XML representation of this object
	 */
	public String toXML()
	{
		XStream xs = new XStream(new DomDriver());
		try
		{
			xs.autodetectAnnotations(true);
			return xs.toXML(this);
		}
		catch (XStreamException ex)
		{
			//ex.printStackTrace();
		}
		return "";
	}

	public static ImageSource fromXML(String xml)
	{
		XStream xs = new XStream(new DomDriver());
		try
		{
			xs.autodetectAnnotations(true);
			return (ImageSource) xs.fromXML(xml);
		}
		catch (ClassCastException ex)
		{
			ex.printStackTrace();
		}
		catch (XStreamException ex)
		{
			ex.printStackTrace();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		return null;
	}

	/**
	 * Check if the bounds fit inside the image.
	 *
	 * @param bounds
	 *            the bounds
	 * @return True if the bounds are not null and are within the image, false if null or the bounds fit the image
	 *         exactly
	 * @throws RuntimeException
	 *             if the bounds do not fit in the image
	 */
	public boolean checkBounds(Rectangle bounds)
	{
		return checkBounds(getWidth(), getHeight(), bounds);
	}

	/**
	 * Check if the bounds fit inside the image.
	 * 
	 * @param width
	 * @param height
	 * @param bounds
	 * @throws RuntimeException
	 *             if the bounds do not fit in the image
	 * @return True if the bounds are not null and are within the image, false if null or the bounds fit the image
	 *         exactly
	 */
	public static boolean checkBounds(int width, int height, Rectangle bounds)
	{
		if (bounds != null)
		{
			final int maxx = bounds.x + bounds.width;
			final int maxy = bounds.y + bounds.height;
			if (bounds.x < 0 || maxx > width || bounds.y < 0 || maxy > height)
				throw new RuntimeException("The bounds do not fit within the image");
			return bounds.x != 0 || bounds.y != 0 || bounds.width != width || bounds.height != height;
		}
		return false;
	}
}
