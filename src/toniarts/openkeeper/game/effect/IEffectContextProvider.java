/*
 * Copyright (C) 2014-2015 OpenKeeper
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
package toniarts.openkeeper.game.effect;

import java.awt.Point;
import toniarts.openkeeper.world.TileData;

/**
 * Provides context information needed by effects without depending on WorldState.
 * This interface abstracts away the world system dependencies.
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public interface IEffectContextProvider {
    
    /**
     * Gets tile data for a given point
     * @param point the point to get tile data for
     * @return the tile data, or null if not found
     */
    TileData getTileData(Point point);
}