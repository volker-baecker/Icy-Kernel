/*
 * Copyright 2010-2015 Institut Pasteur.
 * 
 * This file is part of Icy.
 * 
 * Icy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Icy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Icy. If not, see <http://www.gnu.org/licenses/>.
 */
package plugins.kernel.roi.roi4d;

import icy.canvas.IcyCanvas;
import icy.canvas.IcyCanvas2D;
import icy.canvas.IcyCanvas3D;
import icy.painter.OverlayEvent;
import icy.painter.OverlayListener;
import icy.roi.BooleanMask2D;
import icy.roi.ROI;
import icy.roi.ROI3D;
import icy.roi.ROI4D;
import icy.roi.ROIEvent;
import icy.roi.ROIListener;
import icy.sequence.Sequence;
import icy.system.IcyExceptionHandler;
import icy.type.point.Point5D;
import icy.type.rectangle.Rectangle3D;
import icy.type.rectangle.Rectangle4D;
import icy.util.StringUtil;
import icy.util.XMLUtil;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Abstract class defining a generic 4D ROI as a stack of individual 3D ROI slices.
 * 
 * @author Alexandre Dufour
 * @author Stephane Dallongeville
 * @param <R>
 *        the type of 3D ROI for each slice of this 4D ROI
 */
public class ROI4DStack<R extends ROI3D> extends ROI4D implements ROIListener, OverlayListener, Iterable<R>
{
    public static final String PROPERTY_USECHILDCOLOR = "useChildColor";

    protected final TreeMap<Integer, R> slices = new TreeMap<Integer, R>();

    protected final Class<R> roiClass;
    protected boolean useChildColor;
    protected Semaphore modifyingSlice;
    protected double translateT;

    /**
     * Creates a new 4D ROI based on the given 3D ROI type.
     */
    public ROI4DStack(Class<R> roiClass)
    {
        super();

        this.roiClass = roiClass;
        useChildColor = false;
        modifyingSlice = new Semaphore(1);
        translateT = 0d;
    }

    @Override
    protected ROIPainter createPainter()
    {
        return new ROI4DStackPainter();
    }

    /**
     * Create a new empty 3D ROI slice.
     */
    protected R createSlice()
    {
        try
        {
            return roiClass.newInstance();
        }
        catch (Exception e)
        {
            IcyExceptionHandler.showErrorMessage(e, true, true);
            return null;
        }
    }

    /**
     * Returns <code>true</code> if the ROI directly uses the 3D slice color draw property and <code>false</code> if it
     * uses the global 4D ROI color draw property.
     */
    public boolean getUseChildColor()
    {
        return useChildColor;
    }

    /**
     * Set to <code>true</code> if you want to directly use the 3D slice color draw property and <code>false</code> to
     * keep the global 4D ROI color draw property.
     * 
     * @see #setColor(int, Color)
     */
    public void setUseChildColor(boolean value)
    {
        if (useChildColor != value)
        {
            useChildColor = value;
            propertyChanged(PROPERTY_USECHILDCOLOR);
            // need to redraw it
            getOverlay().painterChanged();
        }
    }

    /**
     * Set the painter color for the specified ROI slice.
     * 
     * @see #setUseChildColor(boolean)
     */
    public void setColor(int t, Color value)
    {
        final ROI3D slice = getSlice(t);

        modifyingSlice.acquireUninterruptibly();
        try
        {
            if (slice != null)
                slice.setColor(value);
        }
        finally
        {
            modifyingSlice.release();
        }
    }

    @Override
    public void setColor(Color value)
    {
        beginUpdate();
        try
        {
            super.setColor(value);

            if (!getUseChildColor())
            {
                modifyingSlice.acquireUninterruptibly();
                try
                {
                    for (R slice : slices.values())
                        slice.setColor(value);
                }
                finally
                {
                    modifyingSlice.release();
                }
            }
        }
        finally
        {
            endUpdate();
        }
    }

    @Override
    public void setOpacity(float value)
    {
        beginUpdate();
        try
        {
            super.setOpacity(value);

            modifyingSlice.acquireUninterruptibly();
            try
            {
                for (R slice : slices.values())
                    slice.setOpacity(value);
            }
            finally
            {
                modifyingSlice.release();
            }
        }
        finally
        {
            endUpdate();
        }
    }

    @Override
    public void setStroke(double value)
    {
        beginUpdate();
        try
        {
            super.setStroke(value);

            modifyingSlice.acquireUninterruptibly();
            try
            {
                for (R slice : slices.values())
                    slice.setStroke(value);
            }
            finally
            {
                modifyingSlice.release();
            }
        }
        finally
        {
            endUpdate();
        }
    }

    @Override
    public void setCreating(boolean value)
    {
        beginUpdate();
        try
        {
            super.setCreating(value);

            modifyingSlice.acquireUninterruptibly();
            try
            {
                for (R slice : slices.values())
                    slice.setCreating(value);
            }
            finally
            {
                modifyingSlice.release();
            }
        }
        finally
        {
            endUpdate();
        }
    }

    @Override
    public void setReadOnly(boolean value)
    {
        beginUpdate();
        try
        {
            super.setReadOnly(value);

            modifyingSlice.acquireUninterruptibly();
            try
            {
                for (R slice : slices.values())
                    slice.setReadOnly(value);
            }
            finally
            {
                modifyingSlice.release();
            }
        }
        finally
        {
            endUpdate();
        }
    }

    @Override
    public void setFocused(boolean value)
    {
        beginUpdate();
        try
        {
            super.setFocused(value);

            modifyingSlice.acquireUninterruptibly();
            try
            {
                for (R slice : slices.values())
                    slice.setFocused(value);
            }
            finally
            {
                modifyingSlice.release();
            }
        }
        finally
        {
            endUpdate();
        }
    }

    @Override
    public void setSelected(boolean value)
    {
        beginUpdate();
        try
        {
            super.setSelected(value);

            modifyingSlice.acquireUninterruptibly();
            try
            {
                for (R slice : slices.values())
                    slice.setSelected(value);
            }
            finally
            {
                modifyingSlice.release();
            }
        }
        finally
        {
            endUpdate();
        }
    }

    @Override
    public void setC(int value)
    {
        beginUpdate();
        try
        {
            super.setC(value);

            modifyingSlice.acquireUninterruptibly();
            try
            {
                for (R slice : slices.values())
                    slice.setC(value);
            }
            finally
            {
                modifyingSlice.release();
            }
        }
        finally
        {
            endUpdate();
        }
    }

    /**
     * Returns <code>true</code> if the ROI stack is empty.
     */
    public boolean isEmpty()
    {
        return slices.isEmpty();
    }

    /**
     * @return The size of this ROI stack along T.<br>
     *         Note that the returned value indicates the difference between upper and lower bounds
     *         of this ROI, but doesn't guarantee that all slices in-between exist ( {@link #getSlice(int)} may still
     *         return <code>null</code>.<br>
     */
    public int getSizeT()
    {
        if (slices.isEmpty())
            return 0;

        return (slices.lastKey().intValue() - slices.firstKey().intValue()) + 1;
    }

    /**
     * Returns the ROI slice at given T position.
     */
    public R getSlice(int t)
    {
        return slices.get(Integer.valueOf(t));
    }

    /**
     * Returns the ROI slice at given T position.
     */
    public R getSlice(int t, boolean createIfNull)
    {
        R result = getSlice(t);

        if ((result == null) && createIfNull)
        {
            result = createSlice();
            if (result != null)
                setSlice(t, result);
        }

        return result;
    }

    /**
     * Sets the slice for the given T position.
     */
    public void setSlice(int t, R roi3d)
    {
        if (roi3d == null)
            throw new IllegalArgumentException("Cannot set an empty slice in a 4D ROI");

        // set T and C position
        roi3d.setT(t);
        roi3d.setC(getC());
        // listen events from this ROI and its overlay
        roi3d.addListener(this);
        roi3d.getOverlay().addOverlayListener(this);

        slices.put(Integer.valueOf(t), roi3d);

        // notify ROI changed
        roiChanged(true);
    }

    /**
     * Removes slice at the given T position and returns it.
     */
    public R removeSlice(int t)
    {
        // remove the current slice (if any)
        final R result = slices.remove(Integer.valueOf(t));

        // remove listeners
        if (result != null)
        {
            result.removeListener(this);
            result.getOverlay().removeOverlayListener(this);
        }

        // notify ROI changed
        roiChanged(true);

        return result;
    }

    /**
     * Removes all slices.
     */
    public void clear()
    {
        // nothing to do
        if (isEmpty())
            return;

        for (R slice : slices.values())
        {
            slice.removeListener(this);
            slice.getOverlay().removeOverlayListener(this);
        }

        slices.clear();
        roiChanged(true);
    }

    /**
     * Called when a ROI slice has changed.
     */
    protected void sliceChanged(ROIEvent event)
    {
        if (modifyingSlice.availablePermits() <= 0)
            return;

        final ROI source = event.getSource();

        switch (event.getType())
        {
            case ROI_CHANGED:
                // position change of a slice can change global bounds --> transform to 'content changed' event type
                roiChanged(true);
//                roiChanged(StringUtil.equals(event.getPropertyName(), ROI_CHANGED_ALL));
                break;

            case FOCUS_CHANGED:
                setFocused(source.isFocused());
                break;

            case SELECTION_CHANGED:
                setSelected(source.isSelected());
                break;

            case PROPERTY_CHANGED:
                final String propertyName = event.getPropertyName();

                if ((propertyName == null) || propertyName.equals(PROPERTY_READONLY))
                    setReadOnly(source.isReadOnly());
                if ((propertyName == null) || propertyName.equals(PROPERTY_CREATING))
                    setCreating(source.isCreating());
                break;
        }
    }

    /**
     * Called when a ROI slice overlay has changed.
     */
    protected void sliceOverlayChanged(OverlayEvent event)
    {
        switch (event.getType())
        {
            case PAINTER_CHANGED:
                // forward the event to ROI stack overlay
                getOverlay().painterChanged();
                break;

            case PROPERTY_CHANGED:
                // forward the event to ROI stack overlay
                getOverlay().propertyChanged(event.getPropertyName());
                break;
        }
    }

    @Override
    public Rectangle4D computeBounds4D()
    {
        Rectangle3D xyzBounds = null;

        for (R slice : slices.values())
        {
            final Rectangle3D bnd3d = slice.getBounds3D();

            // only add non empty bounds
            if (!bnd3d.isEmpty())
            {
                if (xyzBounds == null)
                    xyzBounds = (Rectangle3D) bnd3d.clone();
                else
                    xyzBounds.add(bnd3d);
            }
        }

        // create empty 3D bounds
        if (xyzBounds == null)
            xyzBounds = new Rectangle3D.Double();

        final int t;
        final int sizeT;

        if (!slices.isEmpty())
        {
            t = slices.firstKey().intValue();
            sizeT = getSizeT();
        }
        else
        {
            t = 0;
            sizeT = 0;
        }

        return new Rectangle4D.Double(xyzBounds.getX(), xyzBounds.getY(), xyzBounds.getZ(), t, xyzBounds.getSizeX(),
                xyzBounds.getSizeY(), xyzBounds.getSizeZ(), sizeT);
    }

    @Override
    public boolean contains(double x, double y, double z, double t)
    {
        final R roi3d = getSlice((int) Math.floor(t));

        if (roi3d != null)
            return roi3d.contains(x, y, z);

        return false;
    }

    @Override
    public boolean contains(double x, double y, double z, double t, double sizeX, double sizeY, double sizeZ,
            double sizeT)
    {
        final Rectangle4D bounds = getBounds4D();

        // easy discard
        if (!bounds.contains(x, y, z, t, sizeX, sizeY, sizeZ, sizeT))
            return false;

        final int lim = (int) Math.floor(t + sizeT);
        for (int tc = (int) Math.floor(t); tc < lim; tc++)
        {
            final R roi3d = getSlice(tc);
            if ((roi3d == null) || !roi3d.contains(x, y, z, sizeX, sizeY, sizeZ))
                return false;
        }

        return true;
    }

    @Override
    public boolean intersects(double x, double y, double z, double t, double sizeX, double sizeY, double sizeZ,
            double sizeT)
    {
        final Rectangle4D bounds = getBounds4D();

        // easy discard
        if (!bounds.intersects(x, y, z, t, sizeX, sizeY, sizeZ, sizeT))
            return false;

        final int lim = (int) Math.floor(t + sizeT);
        for (int tc = (int) Math.floor(t); tc < lim; tc++)
        {
            final R roi3d = getSlice(tc);
            if ((roi3d != null) && roi3d.intersects(x, y, z, sizeX, sizeY, sizeZ))
                return true;
        }

        return false;
    }

    @Override
    public boolean hasSelectedPoint()
    {
        // default
        return false;
    }

    // default approximated implementation for ROI4DStack
    @Override
    public double computeNumberOfContourPoints()
    {
        // 4D contour points = first slice points + inter slices contour points + last slice points
        double result = 0;

        if (slices.size() <= 2)
        {
            for (R slice : slices.values())
                result += slice.getNumberOfPoints();
        }
        else
        {
            final Entry<Integer, R> firstEntry = slices.firstEntry();
            final Entry<Integer, R> lastEntry = slices.lastEntry();
            final Integer firstKey = firstEntry.getKey();
            final Integer lastKey = lastEntry.getKey();

            result = firstEntry.getValue().getNumberOfPoints();

            for (R slice : slices.subMap(firstKey, false, lastKey, false).values())
                result += slice.getNumberOfContourPoints();

            result += lastEntry.getValue().getNumberOfPoints();
        }

        return result;
    }

    @Override
    public double computeNumberOfPoints()
    {
        double volume = 0;

        for (R slice : slices.values())
            volume += slice.getNumberOfPoints();

        return volume;
    }

    @Override
    public boolean canTranslate()
    {
        // only need to test the first entry
        if (!slices.isEmpty())
            return slices.firstEntry().getValue().canTranslate();

        return false;
    }

    /**
     * Translate the stack of specified T position.
     */
    public void translate(int t)
    {
        // easy optimizations
        if ((t == 0) || isEmpty())
            return;

        final Map<Integer, R> map = new HashMap<Integer, R>(slices);

        slices.clear();
        for (Entry<Integer, R> entry : map.entrySet())
        {
            final R roi = entry.getValue();
            final int newT = roi.getT() + t;

            // only positive value accepted
            if (newT >= 0)
            {
                roi.setT(newT);
                slices.put(Integer.valueOf(newT), roi);
            }
        }

        // notify ROI changed
        roiChanged(false);
    }

    @Override
    public void translate(double dx, double dy, double dz, double dt)
    {
        beginUpdate();
        try
        {
            translateT += dt;
            // convert to integer
            final int dti = (int) translateT;
            // keep trace of not used floating part
            translateT -= dti;

            translate(dti);

            modifyingSlice.acquireUninterruptibly();
            try
            {
                for (R slice : slices.values())
                    slice.translate(dx, dy, dz);
            }
            finally
            {
                modifyingSlice.release();
            }

            // notify ROI changed because we modified slice 'internally'
            if ((dx != 0d) || (dy != 0d) || (dz != 0d))
                roiChanged(false);
        }
        finally
        {
            endUpdate();
        }
    }

    @Override
    public boolean[] getBooleanMask2D(int x, int y, int width, int height, int z, int t, boolean inclusive)
    {
        final R roi3d = getSlice(t);

        if (roi3d != null)
            return roi3d.getBooleanMask2D(x, y, width, height, z, inclusive);

        return new boolean[width * height];
    }

    @Override
    public BooleanMask2D getBooleanMask2D(int z, int t, boolean inclusive)
    {
        final R roi3d = getSlice(t);

        if (roi3d != null)
            return roi3d.getBooleanMask2D(z, inclusive);

        return new BooleanMask2D(new Rectangle(), new boolean[0]);
    }

    // called when one of the slice ROI changed
    @Override
    public void roiChanged(ROIEvent event)
    {
        // propagate children change event
        sliceChanged(event);
    }

    // called when one of the slice ROI overlay changed
    @Override
    public void overlayChanged(OverlayEvent event)
    {
        // propagate children overlay change event
        sliceOverlayChanged(event);
    }

    @Override
    public Iterator<R> iterator()
    {
        return slices.values().iterator();
    }

    @Override
    public boolean loadFromXML(Node node)
    {
        beginUpdate();
        try
        {
            if (!super.loadFromXML(node))
                return false;

            // we don't need to save the 3D ROI class as the parent class already do it
            clear();

            for (Element e : XMLUtil.getElements(node, "slice"))
            {
                // faster than using complete XML serialization
                final R slice = createSlice();

                // error while reloading the ROI from XML
                if ((slice == null) || !slice.loadFromXML(e))
                    return false;

                setSlice(slice.getT(), slice);
            }
        }
        finally
        {
            endUpdate();
        }

        return true;
    }

    @Override
    public boolean saveToXML(Node node)
    {
        if (!super.saveToXML(node))
            return false;

        for (R slice : slices.values())
        {
            Element sliceNode = XMLUtil.addElement(node, "slice");

            if (!slice.saveToXML(sliceNode))
                return false;
        }

        return true;
    }

    public class ROI4DStackPainter extends ROIPainter
    {
        R getSliceForCanvas(IcyCanvas canvas)
        {
            final int t = canvas.getPositionT();

            if (t >= 0)
                return getSlice(t);

            return null;
        }

        @Override
        public void paint(Graphics2D g, Sequence sequence, IcyCanvas canvas)
        {
            if (isActiveFor(canvas))
            {
                if (canvas instanceof IcyCanvas3D)
                {
                    // TODO

                }
                else if (canvas instanceof IcyCanvas2D)
                {
                    // forward event to current slice
                    final R slice = getSliceForCanvas(canvas);

                    if (slice != null)
                        slice.getOverlay().paint(g, sequence, canvas);
                }
            }
        }

        @Override
        public void keyPressed(KeyEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
        {
            // send event to parent first
            super.keyPressed(e, imagePoint, canvas);

            // then send it to active slice
            if (isActiveFor(canvas))
            {
                // forward event to current slice
                final R slice = getSliceForCanvas(canvas);

                if (slice != null)
                    slice.getOverlay().keyPressed(e, imagePoint, canvas);
            }
        }

        @Override
        public void keyReleased(KeyEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
        {
            // send event to parent first
            super.keyReleased(e, imagePoint, canvas);

            // then send it to active slice
            if (isActiveFor(canvas))
            {
                // forward event to current slice
                final R slice = getSliceForCanvas(canvas);

                if (slice != null)
                    slice.getOverlay().keyReleased(e, imagePoint, canvas);
            }
        }

        @Override
        public void mouseEntered(MouseEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
        {
            // send event to parent first
            super.mouseEntered(e, imagePoint, canvas);

            // then send it to active slice
            if (isActiveFor(canvas))
            {
                // forward event to current slice
                final R slice = getSliceForCanvas(canvas);

                if (slice != null)
                    slice.getOverlay().mouseEntered(e, imagePoint, canvas);
            }
        }

        @Override
        public void mouseExited(MouseEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
        {
            // send event to parent first
            super.mouseExited(e, imagePoint, canvas);

            // then send it to active slice
            if (isActiveFor(canvas))
            {
                // forward event to current slice
                final R slice = getSliceForCanvas(canvas);

                if (slice != null)
                    slice.getOverlay().mouseExited(e, imagePoint, canvas);
            }
        }

        @Override
        public void mouseMove(MouseEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
        {
            // send event to parent first
            super.mouseMove(e, imagePoint, canvas);

            // then send it to active slice
            if (isActiveFor(canvas))
            {
                // forward event to current slice
                final R slice = getSliceForCanvas(canvas);

                if (slice != null)
                    slice.getOverlay().mouseMove(e, imagePoint, canvas);
            }
        }

        @Override
        public void mouseDrag(MouseEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
        {
            // send event to parent first
            super.mouseDrag(e, imagePoint, canvas);

            // then send it to active slice
            if (isActiveFor(canvas))
            {
                // forward event to current slice
                final R slice = getSliceForCanvas(canvas);

                if (slice != null)
                    slice.getOverlay().mouseDrag(e, imagePoint, canvas);
            }
        }

        @Override
        public void mousePressed(MouseEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
        {
            // send event to parent first
            super.mousePressed(e, imagePoint, canvas);

            // then send it to active slice
            if (isActiveFor(canvas))
            {
                // forward event to current slice
                final R slice = getSliceForCanvas(canvas);

                if (slice != null)
                    slice.getOverlay().mousePressed(e, imagePoint, canvas);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
        {
            // send event to parent first
            super.mouseReleased(e, imagePoint, canvas);

            // then send it to active slice
            if (isActiveFor(canvas))
            {
                // forward event to current slice
                final R slice = getSliceForCanvas(canvas);

                if (slice != null)
                    slice.getOverlay().mouseReleased(e, imagePoint, canvas);
            }
        }

        @Override
        public void mouseClick(MouseEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
        {
            // send event to parent first
            super.mouseClick(e, imagePoint, canvas);

            // then send it to active slice
            if (isActiveFor(canvas))
            {
                // forward event to current slice
                final R slice = getSliceForCanvas(canvas);

                if (slice != null)
                    slice.getOverlay().mouseClick(e, imagePoint, canvas);
            }
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e, Point5D.Double imagePoint, IcyCanvas canvas)
        {
            // send event to parent first
            super.mouseWheelMoved(e, imagePoint, canvas);

            // then send it to active slice
            if (isActiveFor(canvas))
            {
                // forward event to current slice
                final R slice = getSliceForCanvas(canvas);

                if (slice != null)
                    slice.getOverlay().mouseWheelMoved(e, imagePoint, canvas);
            }
        }
    }
}
