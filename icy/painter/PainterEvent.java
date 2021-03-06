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
package icy.painter;

import icy.common.CollapsibleEvent;

/**
 * @deprecated Use {@link Overlay} classes instead.
 */
@Deprecated
public class PainterEvent implements CollapsibleEvent
{
    public enum PainterEventType
    {
        PAINTER_CHANGED;
    }

    private final Painter source;
    private final PainterEventType type;

    public PainterEvent(Painter source, PainterEventType type)
    {
        this.source = source;
        this.type = type;
    }

    /**
     * @return the source
     */
    public Painter getSource()
    {
        return source;
    }

    /**
     * @return the type
     */
    public PainterEventType getType()
    {
        return type;
    }

    @Override
    public boolean collapse(CollapsibleEvent event)
    {
        if (equals(event))
        {
            // nothing to do here
            return true;
        }

        return false;
    }

    @Override
    public int hashCode()
    {
        return source.hashCode() ^ type.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof PainterEvent)
        {
            final PainterEvent e = (PainterEvent) obj;

            return (source == e.getSource()) && (type == e.getType());
        }

        return super.equals(obj);
    }
}
