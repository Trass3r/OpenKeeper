/*
 * Copyright (C) 2014-2025 OpenKeeper
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenKeeper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenKeeper.  If not, see <http://www.gnu.org/licenses/>.
 */
package toniarts.openkeeper.game.listener;

import java.util.Objects;
import toniarts.openkeeper.utils.Point;

/**
 * Represents a specific change to a map tile with context about what changed.
 * This allows for optimized updates instead of brute-force tile recreation.
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public final class MapTileChange {
    
    private final Point location;
    private final MapChangeType changeType;
    private final Object oldValue;
    private final Object newValue;
    
    public MapTileChange(Point location, MapChangeType changeType) {
        this(location, changeType, null, null);
    }
    
    public MapTileChange(Point location, MapChangeType changeType, Object oldValue, Object newValue) {
        this.location = Objects.requireNonNull(location, "Location cannot be null");
        this.changeType = Objects.requireNonNull(changeType, "Change type cannot be null");
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
    
    public Point getLocation() {
        return location;
    }
    
    public MapChangeType getChangeType() {
        return changeType;
    }
    
    public Object getOldValue() {
        return oldValue;
    }
    
    public Object getNewValue() {
        return newValue;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MapTileChange that = (MapTileChange) obj;
        return Objects.equals(location, that.location) &&
               changeType == that.changeType;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(location, changeType);
    }
    
    @Override
    public String toString() {
        return String.format("MapTileChange{location=%s, type=%s, old=%s, new=%s}", 
                           location, changeType, oldValue, newValue);
    }
}
