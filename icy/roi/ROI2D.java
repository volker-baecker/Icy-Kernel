/*
 * Copyright 2010-2013 Institut Pasteur.
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
package icy.roi;

import icy.canvas.Canvas3D;
import icy.canvas.IcyCanvas;
import icy.common.EventHierarchicalChecker;
import icy.roi.roi2d.ROI2DArea;
import icy.roi.roi2d.ROI2DShape;
import icy.type.rectangle.Rectangle5D;
import icy.util.EventUtil;
import icy.util.ShapeUtil.ShapeOperation;
import icy.util.XMLUtil;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Node;

public abstract class ROI2D extends ROI
{
    /**
     * Return ROI2D of ROI list.
     */
    public static List<ROI2D> getROI2DList(List<ROI> rois)
    {
        final List<ROI2D> result = new ArrayList<ROI2D>();

        for (ROI roi : rois)
            if (roi instanceof ROI2D)
                result.add((ROI2D) roi);

        return result;
    }

    /**
     * @deprecated Use {@link ROI2D#getROI2DList(List)} instead.
     */
    @Deprecated
    public static ArrayList<ROI2D> getROI2DList(ArrayList<ROI> rois)
    {
        final ArrayList<ROI2D> result = new ArrayList<ROI2D>();

        for (ROI roi : rois)
            if (roi instanceof ROI2D)
                result.add((ROI2D) roi);

        return result;
    }

    /**
     * @deprecated Use {@link ROI2D#getROI2DList(List)} instead.
     */
    @Deprecated
    public static ROI2D[] getROI2DList(ROI[] rois)
    {
        final ArrayList<ROI2D> result = new ArrayList<ROI2D>();

        for (ROI roi : rois)
            if (roi instanceof ROI2D)
                result.add((ROI2D) roi);

        return result.toArray(new ROI2D[result.size()]);
    }

    /**
     * @deprecated Use {@link ROIUtil#merge(List, icy.util.ShapeUtil.BooleanOperator)} instead.
     */
    @Deprecated
    public static ROI2D merge(ROI2D[] rois, ShapeOperation operation)
    {
        final List<ROI> list = new ArrayList<ROI>();

        for (ROI2D roi2d : rois)
            list.add(roi2d);

        return (ROI2D) ROIUtil.merge(list, operation.getBooleanOperator());
    }

    /**
     * @deprecated Use {@link ROI2D#subtract(ROI2D, ROI2D)} instead
     */
    @Deprecated
    public static ROI2D substract(ROI2D roi1, ROI2D roi2)
    {
        return subtract(roi1, roi2);
    }

    /**
     * Subtract the content of the roi2 from the roi1 and return the result as a new {@link ROI2D}.
     * 
     * @return {@link ROI2D} representing the result of subtraction.
     */
    public static ROI2D subtract(ROI2D roi1, ROI2D roi2)
    {
        if ((roi1 instanceof ROI2DShape) && (roi2 instanceof ROI2DShape))
            return ROI2DShape.subtract((ROI2DShape) roi1, (ROI2DShape) roi2);

        // use ROI2DArea
        final ROI2DArea result = new ROI2DArea(BooleanMask2D.getSubtractionMask(roi1.getBooleanMask(),
                roi2.getBooleanMask()));

        result.setName("Substraction");

        return result;
    }

    public abstract class ROI2DPainter extends ROIPainter
    {
        protected Point2D startDragMousePosition;
        protected Point2D startDragROIPosition;

        public ROI2DPainter()
        {
            super();

            startDragMousePosition = null;
            startDragROIPosition = null;
        }

        protected boolean updateFocus(InputEvent e, Point2D imagePoint, IcyCanvas canvas)
        {
            final boolean focused = isOver(canvas, imagePoint);

            setFocused(focused);

            return focused;
        }

        protected boolean updateSelect(InputEvent e, Point2D imagePoint, IcyCanvas canvas)
        {
            final boolean selectedPoint = hasSelectedPoint();

            // union selection
            if (EventUtil.isShiftDown(e))
            {
                if (isFocused())
                {
                    // only if not already selected
                    if (!isSelected())
                    {
                        setSelected(true);
                        return true;
                    }
                }
            }
            else if (EventUtil.isControlDown(e))
            // switch selection
            {
                // inverse state
                if (isFocused())
                {
                    setSelected(!isSelected());
                    return true;
                }
            }
            else
            // exclusive selection
            {
                // we stay selected when we click on control points
                final boolean newSelected = isFocused() || selectedPoint;

                if (newSelected)
                {
                    // only if not already selected
                    if (!isSelected())
                    {
                        canvas.getSequence().setSelectedROI(newSelected ? ROI2D.this : null);
                        return true;
                    }
                }
            }

            return false;
        }

        protected boolean updateDrag(InputEvent e, Point2D imagePoint, IcyCanvas canvas)
        {
            // not dragging --> exit
            if (startDragMousePosition == null)
                return false;

            double dx = imagePoint.getX() - startDragMousePosition.getX();
            double dy = imagePoint.getY() - startDragMousePosition.getY();

            // shift action --> limit to one direction
            if (EventUtil.isShiftDown(e))
            {
                // X drag
                if (Math.abs(dx) > Math.abs(dy))
                    dy = 0;
                // Y drag
                else
                    dx = 0;
            }

            // set new position
            setPosition(new Point2D.Double(startDragROIPosition.getX() + dx, startDragROIPosition.getY() + dy));

            return true;
        }

        @Override
        public void keyReleased(KeyEvent e, Point2D imagePoint, IcyCanvas canvas)
        {
            // do parent stuff
            super.keyReleased(e, imagePoint, canvas);

            if (isActiveFor(canvas))
            {
                // check we can do the action
                if (!(canvas instanceof Canvas3D) && (imagePoint != null))
                {
                    // just for the shift key state change
                    if (!isReadOnly())
                        updateDrag(e, imagePoint, canvas);
                }
            }
        }

        @Override
        public void mousePressed(MouseEvent e, Point2D imagePoint, IcyCanvas canvas)
        {
            // do parent stuff
            super.mousePressed(e, imagePoint, canvas);

            if (isActiveFor(canvas))
            {
                // check we can do the action
                if (!(canvas instanceof Canvas3D) && (imagePoint != null))
                {
                    // not yet consumed...
                    if (!e.isConsumed())
                    {
                        ROI2D.this.beginUpdate();
                        try
                        {
                            // left button action
                            if (EventUtil.isLeftMouseButton(e))
                            {
                                // roi focused (mouse over ROI bounds) ?
                                if (isFocused())
                                {
                                    // update selection
                                    updateSelect(e, imagePoint, canvas);
                                    // always consume (to enable dragging)
                                    e.consume();
                                }
                                // roi selected and no point selected ?
                                else if (isSelected() && !hasSelectedPoint())
                                {
                                    if (!isReadOnly())
                                    {
                                        // try to add point first
                                        if (addPointAt(imagePoint, EventUtil.isControlDown(e)))
                                            e.consume();
                                        // else we update selection
                                        else if (updateSelect(e, imagePoint, canvas))
                                            e.consume();
                                    }
                                    else
                                    {
                                        // just update selection
                                        if (updateSelect(e, imagePoint, canvas))
                                            e.consume();
                                    }
                                }
                                else
                                {
                                    // update selection
                                    if (updateSelect(e, imagePoint, canvas))
                                        e.consume();
                                }
                            }
                        }
                        finally
                        {
                            ROI2D.this.endUpdate();
                        }
                    }
                }
            }
        }

        @Override
        public void mouseReleased(MouseEvent e, Point2D imagePoint, IcyCanvas canvas)
        {
            // do parent stuff
            super.mouseReleased(e, imagePoint, canvas);

            startDragMousePosition = null;
        }

        @Override
        public void mouseDrag(MouseEvent e, Point2D imagePoint, IcyCanvas canvas)
        {
            // do parent stuff
            super.mouseDrag(e, imagePoint, canvas);

            if (isActiveFor(canvas))
            {
                // check we can do the action
                if (!(canvas instanceof Canvas3D) && (imagePoint != null))
                {
                    // not yet consumed and ROI editable...
                    if (!e.isConsumed() && !isReadOnly())
                    {
                        ROI2D.this.beginUpdate();
                        try
                        {
                            // left button action
                            if (EventUtil.isLeftMouseButton(e))
                            {
                                // roi focused ?
                                if (isFocused())
                                {
                                    // start drag position
                                    if (startDragMousePosition == null)
                                    {
                                        startDragMousePosition = imagePoint;
                                        startDragROIPosition = getPosition2D();
                                    }

                                    updateDrag(e, imagePoint, canvas);

                                    // consume event
                                    e.consume();
                                }
                            }
                        }
                        finally
                        {
                            ROI2D.this.endUpdate();
                        }
                    }
                }
            }
        }

        @Override
        public void mouseMove(MouseEvent e, Point2D imagePoint, IcyCanvas canvas)
        {
            // do parent stuff
            super.mouseMove(e, imagePoint, canvas);

            if (isActiveFor(canvas))
            {
                // check we can do the action
                if (!(canvas instanceof Canvas3D) && (imagePoint != null))
                {
                    // update focus
                    if (!e.isConsumed())
                    {
                        if (updateFocus(e, imagePoint, canvas))
                            e.consume();
                    }
                }
            }
        }
    }

    public static final String ID_Z = "z";
    public static final String ID_T = "t";
    public static final String ID_C = "c";

    /**
     * z coordinate attachment
     */
    protected int z;
    /**
     * t coordinate attachment
     */
    protected int t;
    /**
     * c coordinate attachment
     */
    protected int c;

    public ROI2D()
    {
        super();

        // by default we consider no specific Z, T and C attachment
        z = -1;
        t = -1;
        c = -1;
    }

    @Override
    final public int getDimension()
    {
        return 2;
    }

    /**
     * @return the z
     */
    public int getZ()
    {
        return z;
    }

    /**
     * @param value
     *        the z to set
     */
    public void setZ(int value)
    {
        if (z != value)
        {
            z = value;
            roiChanged();
        }
    }

    /**
     * @return the t
     */
    public int getT()
    {
        return t;
    }

    /**
     * @param value
     *        the t to set
     */
    public void setT(int value)
    {
        if (t != value)
        {
            t = value;
            roiChanged();
        }
    }

    /**
     * @return the c
     */
    public int getC()
    {
        return c;
    }

    /**
     * @param value
     *        the c to set
     */
    public void setC(int value)
    {
        if (c != value)
        {
            c = value;
            roiChanged();
        }
    }

    @Override
    public boolean isActiveFor(IcyCanvas canvas)
    {
        return isActiveFor(canvas.getPositionZ(), canvas.getPositionT(), canvas.getPositionC());
    }

    /**
     * Return true if the ROI is active for the specified Z, T, C coordinates
     */
    public boolean isActiveFor(int z, int t, int c)
    {
        return ((getZ() == -1) || (z == -1) || (getZ() == z)) && ((getT() == -1) || (t == -1) || (getT() == t))
                && ((getC() == -1) || (c == -1) || (getC() == c));
    }

    /**
     * Return true if this ROI support adding new point
     */
    public abstract boolean canAddPoint();

    /**
     * Return true if this ROI support removing point
     */
    public abstract boolean canRemovePoint();

    /**
     * Add a new point at specified position (used to build ROI)
     */
    public abstract boolean addPointAt(Point2D pos, boolean ctrl);

    /**
     * Remove point at specified position (used to build ROI)
     */
    public abstract boolean removePointAt(IcyCanvas canvas, Point2D imagePoint);

    /**
     * Remove selected point at specified position (used to build ROI)
     */
    protected abstract boolean removeSelectedPoint(IcyCanvas canvas, Point2D imagePoint);

    /**
     * Return true if the ROI has a current selected point
     */
    public abstract boolean hasSelectedPoint();

    /**
     * return true if specified point coordinates overlap the ROI<br>
     * Edge overlap only, used for roi manipulation
     */
    public boolean isOver(IcyCanvas canvas, Point2D p)
    {
        return isOver(canvas, p.getX(), p.getY());
    }

    /**
     * return true if specified point coordinates overlap the ROI<br>
     * Edge overlap only, used for roi manipulation
     */
    public abstract boolean isOver(IcyCanvas canvas, double x, double y);

    /**
     * return true if specified point coordinates overlap a ROI (control) point<br>
     * used for roi manipulation
     */
    public boolean isOverPoint(IcyCanvas canvas, Point2D p)
    {
        return isOverPoint(canvas, p.getX(), p.getY());
    }

    /**
     * Return true if specified point coordinates overlap a ROI control point (used for roi
     * manipulation).
     */
    public abstract boolean isOverPoint(IcyCanvas canvas, double x, double y);

    /**
     * Tests if a specified {@link Point2D} is inside the ROI.
     * 
     * @param p
     *        the specified <code>Point2D</code> to be tested
     * @return <code>true</code> if the specified <code>Point2D</code> is inside the boundary of the
     *         <code>ROI</code>; <code>false</code> otherwise.
     */
    public boolean contains(Point2D p)
    {
        return contains(p.getX(), p.getY());
    }

    /**
     * Tests if the interior of the <code>ROI</code> entirely contains the specified
     * <code>Rectangle2D</code>. The {@code ROI.contains()} method allows a implementation to
     * conservatively return {@code false} when:
     * <ul>
     * <li>the <code>intersect</code> method returns <code>true</code> and
     * <li>the calculations to determine whether or not the <code>ROI</code> entirely contains the
     * <code>Rectangle2D</code> are prohibitively expensive.
     * </ul>
     * This means that for some ROIs this method might return {@code false} even though the
     * {@code ROI} contains the {@code Rectangle2D}.
     * 
     * @param r
     *        The specified <code>Rectangle2D</code>
     * @return <code>true</code> if the interior of the <code>ROI</code> entirely contains the
     *         <code>Rectangle2D</code>; <code>false</code> otherwise or, if the <code>ROI</code>
     *         contains the <code>Rectangle2D</code> and the <code>intersects</code> method returns
     *         <code>true</code> and the containment calculations would be too expensive to perform.
     * @see #contains(double, double, double, double)
     */
    public boolean contains(Rectangle2D r)
    {
        return contains(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    /**
     * Tests if the specified coordinates are inside the <code>ROI</code>.
     * 
     * @param x
     *        the specified X coordinate to be tested
     * @param y
     *        the specified Y coordinate to be tested
     * @return <code>true</code> if the specified coordinates are inside the <code>ROI</code>
     *         boundary; <code>false</code> otherwise.
     */
    public abstract boolean contains(double x, double y);

    /**
     * Tests if the <code>ROI</code> entirely contains the specified rectangular area. All
     * coordinates that lie inside the rectangular area must lie within the <code>ROI</code> for the
     * entire rectangular area to be considered contained within the <code>ROI</code>.
     * <p>
     * The {@code ROI.contains()} method allows a {@code ROI} implementation to conservatively
     * return {@code false} when:
     * <ul>
     * <li>the <code>intersect</code> method returns <code>true</code> and
     * <li>the calculations to determine whether or not the <code>ROI</code> entirely contains the
     * rectangular area are prohibitively expensive.
     * </ul>
     * This means that for some {@code ROIs} this method might return {@code false} even though the
     * {@code ROI} contains the rectangular area.
     * 
     * @param x
     *        the X coordinate of the upper-left corner of the specified rectangular area
     * @param y
     *        the Y coordinate of the upper-left corner of the specified rectangular area
     * @param w
     *        the width of the specified rectangular area
     * @param h
     *        the height of the specified rectangular area
     * @return <code>true</code> if the interior of the <code>ROI</code> entirely contains the
     *         specified rectangular area; <code>false</code> otherwise or, if the <code>ROI</code>
     *         contains the rectangular area and the <code>intersects</code> method returns
     *         <code>true</code> and the containment calculations would be too expensive to perform.
     */
    public abstract boolean contains(double x, double y, double w, double h);

    @Override
    public boolean contains(double x, double y, double z, double t, double c)
    {
        final boolean cok;
        final boolean zok;
        final boolean tok;

        if (getZ() == -1)
            zok = true;
        else
            zok = (z > getZ()) && (z < (getZ() + 1d));
        if (getT() == -1)
            tok = true;
        else
            tok = (t > getT()) && (t < (getT() + 1d));
        if (getC() == -1)
            cok = true;
        else
            cok = (c >= getC()) && (c < (getC() + 1d));

        return contains(x, y) && cok && zok && tok;
    }

    @Override
    public boolean contains(double x, double y, double z, double t, double c, double sizeX, double sizeY, double sizeZ,
            double sizeT, double sizeC)
    {
        final boolean zok;
        final boolean tok;
        final boolean cok;

        if (getZ() == -1)
            zok = true;
        else
            zok = (z >= getZ()) && ((z + sizeZ) <= (getZ() + 1d));
        if (getT() == -1)
            tok = true;
        else
            tok = (t >= getT()) && ((t + sizeT) <= (getT() + 1d));
        if (getC() == -1)
            cok = true;
        else
            cok = (c >= getC()) && ((c + sizeC) <= (getC() + 1d));

        return contains(x, y, sizeX, sizeY) && zok && tok && cok;
    }

    /**
     * Tests if the interior of the <code>ROI</code> intersects the interior of a specified
     * <code>Rectangle2D</code>. The {@code ROI.intersects()} method allows a {@code ROI}
     * implementation to conservatively return {@code true} when:
     * <ul>
     * <li>there is a high probability that the <code>Rectangle2D</code> and the <code>ROI</code>
     * intersect, but
     * <li>the calculations to accurately determine this intersection are prohibitively expensive.
     * </ul>
     * This means that for some {@code ROIs} this method might return {@code true} even though the
     * {@code Rectangle2D} does not intersect the {@code ROI}.
     * 
     * @param r
     *        the specified <code>Rectangle2D</code>
     * @return <code>true</code> if the interior of the <code>ROI</code> and the interior of the
     *         specified <code>Rectangle2D</code> intersect, or are both highly likely to intersect
     *         and intersection calculations would be too expensive to perform; <code>false</code>
     *         otherwise.
     * @see #intersects(double, double, double, double)
     */
    public boolean intersects(Rectangle2D r)
    {
        return intersects(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    /**
     * Tests if the interior of the <code>ROI</code> intersects the interior of a specified
     * rectangular area. The rectangular area is considered to intersect the <code>ROI</code> if any
     * point is contained in both the interior of the <code>ROI</code> and the specified rectangular
     * area.
     * <p>
     * The {@code ROI.intersects()} method allows a {@code ROI} implementation to conservatively
     * return {@code true} when:
     * <ul>
     * <li>there is a high probability that the rectangular area and the <code>ROI</code> intersect,
     * but
     * <li>the calculations to accurately determine this intersection are prohibitively expensive.
     * </ul>
     * This means that for some {@code ROIs} this method might return {@code true} even though the
     * rectangular area does not intersect the {@code ROI}.
     * 
     * @param x
     *        the X coordinate of the upper-left corner of the specified rectangular area
     * @param y
     *        the Y coordinate of the upper-left corner of the specified rectangular area
     * @param w
     *        the width of the specified rectangular area
     * @param h
     *        the height of the specified rectangular area
     * @return <code>true</code> if the interior of the <code>ROI</code> and the interior of the
     *         rectangular area intersect, or are both highly likely to intersect and intersection
     *         calculations would be too expensive to perform; <code>false</code> otherwise.
     */
    public abstract boolean intersects(double x, double y, double w, double h);

    @Override
    public boolean intersects(double x, double y, double z, double t, double c, double sizeX, double sizeY,
            double sizeZ, double sizeT, double sizeC)
    {
        // easy discard
        if ((sizeX == 0d) || (sizeY == 0d) || (sizeZ == 0d) || (sizeT == 0d) || (sizeC == 0d))
            return false;

        final boolean zok;
        final boolean tok;
        final boolean cok;

        if (getZ() == -1)
            zok = true;
        else
            zok = ((z + sizeZ) > getZ()) && (z < (getZ() + 1d));
        if (getT() == -1)
            tok = true;
        else
            tok = ((t + sizeT) > getT()) && (t < (getT() + 1d));
        if (getC() == -1)
            cok = true;
        else
            cok = ((c + sizeC) > getC()) && (c < (getC() + 1d));

        return intersects(x, y, sizeX, sizeY) && zok && tok && cok;
    }

    /**
     * Calculate and returns the 2D bounding box of the <code>ROI</code>.<br>
     * This method is used by {@link #getBounds2D()} which should try to cache the result as the
     * bounding box calculation can take some computation time for complex ROI.
     */
    public abstract Rectangle2D computeBounds2D();

    @Override
    public Rectangle5D computeBounds5D()
    {
        final Rectangle2D bounds2D = computeBounds2D();
        final Rectangle5D.Double result = new Rectangle5D.Double(bounds2D.getX(), bounds2D.getY(), 0d, 0d, 0d,
                bounds2D.getWidth(), bounds2D.getHeight(), 0d, 0d, 0d);

        if (getZ() == -1)
        {
            result.z = Double.NEGATIVE_INFINITY;
            result.sizeZ = Double.POSITIVE_INFINITY;
        }
        else
        {
            result.z = getZ();
            result.sizeZ = 1d;
        }
        if (getT() == -1)
        {
            result.t = Double.NEGATIVE_INFINITY;
            result.sizeT = Double.POSITIVE_INFINITY;
        }
        else
        {
            result.t = getT();
            result.sizeT = 1d;
        }
        if (getC() == -1)
        {
            result.c = Double.NEGATIVE_INFINITY;
            result.sizeC = Double.POSITIVE_INFINITY;
        }
        else
        {
            result.c = getC();
            result.sizeC = 1d;
        }

        return result;
    }

    /**
     * Returns an integer {@link Rectangle} that completely encloses the <code>ROI</code>. Note that
     * there is no guarantee that the returned <code>Rectangle</code> is the smallest bounding box
     * that encloses the <code>ROI</code>, only that the <code>ROI</code> lies entirely within the
     * indicated <code>Rectangle</code>. The returned <code>Rectangle</code> might also fail to
     * completely enclose the <code>ROI</code> if the <code>ROI</code> overflows the limited range
     * of the integer data type. The <code>getBounds2D</code> method generally returns a tighter
     * bounding box due to its greater flexibility in representation.
     * 
     * @return an integer <code>Rectangle</code> that completely encloses the <code>ROI</code>.
     */
    public Rectangle getBounds()
    {
        return getBounds2D().getBounds();
    }

    /**
     * Returns a high precision and more accurate bounding box of the <code>ROI</code> than the
     * <code>getBounds</code> method. Note that there is no guarantee that the returned
     * {@link Rectangle2D} is the smallest bounding box that encloses the <code>ROI</code>, only
     * that the <code>ROI</code> lies entirely within the indicated <code>Rectangle2D</code>. The
     * bounding box returned by this method is usually tighter than that returned by the
     * <code>getBounds</code> method and never fails due to overflow problems since the return value
     * can be an instance of the <code>Rectangle2D</code> that uses double precision values to store
     * the dimensions.
     * 
     * @return an instance of <code>Rectangle2D</code> that is a high-precision bounding box of the
     *         <code>ROI</code>.
     */
    public Rectangle2D getBounds2D()
    {
        final Rectangle5D bounds = getBounds5D();
        return new Rectangle2D.Double(bounds.getX(), bounds.getY(), bounds.getSizeX(), bounds.getSizeY());
    }

    /**
     * Returns the upper left point of the ROI bounds.<br>
     * Equivalent to :<br>
     * <code>getBounds().getLocation()</code>
     * 
     * @see #getBounds()
     */
    public Point getPosition()
    {
        return getBounds().getLocation();
    }

    /**
     * Returns the ROI position which normally correspond to the <i>minimum</i> point of the ROI
     * bounds:<br>
     * <code>new Point2D.Double(getBounds2D().getX(), getBounds2D().getY())</code>
     * 
     * @see #getBounds2D()
     */
    public Point2D getPosition2D()
    {
        final Rectangle2D r = getBounds2D();
        return new Point2D.Double(r.getX(), r.getY());
    }

    @Override
    public boolean[] getBooleanMask(int x, int y, int width, int height, int z, int t, int c, boolean inclusive)
    {
        // not on the correct C, Z, T position
        if ((getC() != -1) && (getC() != c))
            return null;
        if ((getZ() != -1) && (getZ() != z))
            return null;
        if ((getT() != -1) && (getT() != t))
            return null;

        return getBooleanMask(x, y, width, height, inclusive);
    }

    /**
     * Get the boolean bitmap mask for the specified rectangular area of the roi.<br>
     * if the pixel (x,y) is contained in the roi then result[(y * w) + x] = true<br>
     * if the pixel (x,y) is not contained in the roi then result[(y * w) + x] = false
     * 
     * @param x
     *        the X coordinate of the upper-left corner of the specified rectangular area
     * @param y
     *        the Y coordinate of the upper-left corner of the specified rectangular area
     * @param w
     *        the width of the specified rectangular area
     * @param h
     *        the height of the specified rectangular area
     * @param inclusive
     *        If true then all partially contained (intersected) pixels are included in the mask.
     * @return the boolean bitmap mask
     */
    public boolean[] getBooleanMask(int x, int y, int w, int h, boolean inclusive)
    {
        final boolean[] result = new boolean[w * h];

        // simple and basic implementation, override it to have better performance
        int offset = 0;
        for (int j = 0; j < h; j++)
        {
            for (int i = 0; i < w; i++)
            {
                result[offset] = contains(x + i, y + j, 1, 1);
                if (inclusive)
                    result[offset] |= intersects(x + i, y + j, 1, 1);
                offset++;
            }
        }

        return result;
    }

    /**
     * Get the boolean bitmap mask for the specified rectangular area of the roi.<br>
     * if the pixel (x,y) is contained in the roi then result[(y * w) + x] = true<br>
     * if the pixel (x,y) is not contained in the roi then result[(y * w) + x] = false
     * 
     * @param x
     *        the X coordinate of the upper-left corner of the specified rectangular area
     * @param y
     *        the Y coordinate of the upper-left corner of the specified rectangular area
     * @param w
     *        the width of the specified rectangular area
     * @param h
     *        the height of the specified rectangular area
     * @return the bitmap mask
     */
    public boolean[] getBooleanMask(int x, int y, int w, int h)
    {
        return getBooleanMask(x, y, w, h, false);
    }

    /**
     * Get the boolean bitmap mask for the specified rectangular area of the roi.<br>
     * if the pixel (x,y) is contained in the roi then result[(y * w) + x] = true<br>
     * if the pixel (x,y) is not contained in the roi then result[(y * w) + x] = false
     * 
     * @param rect
     *        area we want to retrieve the boolean mask
     * @param inclusive
     *        If true then all partially contained (intersected) pixels are included in the mask.
     */
    public boolean[] getBooleanMask(Rectangle rect, boolean inclusive)
    {
        return getBooleanMask(rect.x, rect.y, rect.width, rect.height, inclusive);
    }

    /**
     * Get the boolean bitmap mask for the specified rectangular area of the roi.<br>
     * if the pixel (x,y) is contained in the roi then result[(y * w) + x] = true<br>
     * if the pixel (x,y) is not contained in the roi then result[(y * w) + x] = false
     * 
     * @param rect
     *        area we want to retrieve the boolean mask
     */
    public boolean[] getBooleanMask(Rectangle rect)
    {
        return getBooleanMask(rect, false);
    }

    @Override
    public BooleanMask2D getBooleanMask(int z, int t, int c, boolean inclusive)
    {
        final Rectangle bounds = getBounds2D().getBounds();
        return new BooleanMask2D(bounds, getBooleanMask(bounds.x, bounds.y, bounds.width, bounds.height, z, t, c,
                inclusive));
    }

    /**
     * Get the {@link BooleanMask2D} object representing the roi.<br>
     * It contains the rectangle mask bounds and the associated boolean array mask.<br>
     * if the pixel (x,y) is contained in the roi then result.mask[(y * w) + x] = true<br>
     * if the pixel (x,y) is not contained in the roi then result.mask[(y * w) + x] = false
     * 
     * @param inclusive
     *        If true then all partially contained (intersected) pixels are included in the mask.
     */
    public BooleanMask2D getBooleanMask(boolean inclusive)
    {
        final Rectangle bounds = getBounds2D().getBounds();
        return new BooleanMask2D(bounds, getBooleanMask(bounds, inclusive));
    }

    /**
     * Get the {@link BooleanMask2D} object representing the roi.<br>
     * It contains the rectangle mask bounds and the associated boolean array mask.<br>
     * if the pixel (x,y) is contained in the roi then result.mask[(y * w) + x] = true<br>
     * if the pixel (x,y) is not contained in the roi then result.mask[(y * w) + x] = false
     */
    public BooleanMask2D getBooleanMask()
    {
        return getBooleanMask(false);
    }

    /**
     * @deprecated Use {@link #getBooleanMask(boolean)} instead.
     */
    @Deprecated
    public BooleanMask2D getAsBooleanMask(boolean inclusive)
    {
        return getBooleanMask(inclusive);
    }

    /**
     * @deprecated Use {@link #getBooleanMask(Rectangle, boolean)} instead.
     */
    @Deprecated
    public boolean[] getAsBooleanMask(Rectangle rect, boolean inclusive)
    {
        return getBooleanMask(rect, inclusive);
    }

    /**
     * @deprecated Use {@link #getBooleanMask(int, int, int, int, boolean)} instead.
     */
    @Deprecated
    public boolean[] getAsBooleanMask(int x, int y, int w, int h, boolean inclusive)
    {
        return getBooleanMask(x, y, w, h, inclusive);
    }

    /**
     * @deprecated Use {@link #getBooleanMask(boolean)} instead.
     */
    @Deprecated
    public BooleanMask2D getAsBooleanMask()
    {
        return getBooleanMask();
    }

    /**
     * @deprecated Use {@link #getBooleanMask(boolean)} instead.
     */
    @Deprecated
    public boolean[] getAsBooleanMask(Rectangle rect)
    {
        return getBooleanMask(rect);
    }

    /**
     * @deprecated Use {@link #getBooleanMask(boolean)} instead.
     */
    @Deprecated
    public boolean[] getAsBooleanMask(int x, int y, int w, int h)
    {
        return getBooleanMask(x, y, w, h);
    }

    /**
     * Translate the ROI position by the specified delta X <code>dx</code> and Delta Y
     * <code>dy</code>
     */
    public abstract void translate(double dx, double dy);

    /**
     * Set the ROI position.<br>
     * This is equivalent to :<br>
     * <code>translate(newPosition - getPosition2D())</code>
     */
    public void setPosition(Point2D newPosition)
    {
        final Point2D oldPos = getPosition2D();
        translate(newPosition.getX() - oldPos.getX(), newPosition.getY() - oldPos.getY());
    }

    /*
     * Generic implementation for ROI2D using the BooleanMask object so
     * the result is just an approximation.
     * Override to optimize for specific ROI.
     */
    @Override
    public double getPerimeter()
    {
        // approximation by using number of point of the edge of boolean mask
        return getBooleanMask(true).getEdgePoints().length;
    }

    /*
     * Generic implementation for ROI2D using the BooleanMask object so
     * the result is just an approximation.
     * Override to optimize for specific ROI.
     */
    @Override
    public double getVolume()
    {
        // approximation by using number of point of boolean mask
        return getBooleanMask(true).getPoints().length;
    }

    @Override
    public boolean loadFromXML(Node node)
    {
        beginUpdate();
        try
        {
            if (!super.loadFromXML(node))
                return false;

            setZ(XMLUtil.getElementIntValue(node, ID_Z, -1));
            setT(XMLUtil.getElementIntValue(node, ID_T, -1));
            setC(XMLUtil.getElementIntValue(node, ID_C, -1));
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

        XMLUtil.setElementIntValue(node, ID_Z, getZ());
        XMLUtil.setElementIntValue(node, ID_T, getT());
        XMLUtil.setElementIntValue(node, ID_C, getC());

        return true;
    }

    @Override
    public void onChanged(EventHierarchicalChecker object)
    {
        final ROIEvent event = (ROIEvent) object;

        // do here global process on ROI change
        switch (event.getType())
        {
            case ROI_CHANGED:
                // refresh bounds
                cachedBounds = computeBounds5D();
                break;
        }

        super.onChanged(object);
    }
}
