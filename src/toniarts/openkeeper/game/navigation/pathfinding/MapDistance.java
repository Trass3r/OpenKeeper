/*
 * Copyright (C) 2014-2016 OpenKeeper
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
package toniarts.openkeeper.game.navigation.pathfinding;

import com.badlogic.gdx.ai.pfa.Heuristic;
import toniarts.openkeeper.game.map.IMapTileInformation;
import toniarts.openkeeper.utils.WorldUtils;

/**
 * Calculates distance between nodes
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public final class MapDistance implements Heuristic<IMapTileInformation> {

    @Override
    public float estimate(IMapTileInformation node, IMapTileInformation endNode) {
        return WorldUtils.calculateDistance(node.getLocation(), endNode.getLocation());
    }

}
