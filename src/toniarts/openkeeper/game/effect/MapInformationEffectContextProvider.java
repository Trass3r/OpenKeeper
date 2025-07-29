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
import toniarts.openkeeper.game.map.IMapInformation;
import toniarts.openkeeper.game.map.IMapTileInformation;
import toniarts.openkeeper.tools.convert.map.Terrain;

/**
 * Implementation of IEffectContextProvider that uses the modern
 * game system's MapInformation to provide terrain context.
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public class MapInformationEffectContextProvider implements IEffectContextProvider {
    
    private final IMapInformation<? extends IMapTileInformation> mapInformation;
    
    public MapInformationEffectContextProvider(IMapInformation<? extends IMapTileInformation> mapInformation) {
        this.mapInformation = mapInformation;
    }
    
    @Override
    public TerrainType getTerrainType(Point point) {
        if (mapInformation == null || mapInformation.getMapData() == null) {
            return TerrainType.UNKNOWN;
        }
        
        IMapTileInformation tile = mapInformation.getMapData().getTile(point);
        if (tile == null) {
            return TerrainType.UNKNOWN;
        }
        
        Terrain terrain = mapInformation.getTerrain(tile);
        if (terrain == null) {
            return TerrainType.UNKNOWN;
        }
        
        if (terrain.getFlags().contains(Terrain.TerrainFlag.LAVA)) {
            return TerrainType.LAVA;
        } else if (terrain.getFlags().contains(Terrain.TerrainFlag.WATER)) {
            return TerrainType.WATER;
        } else {
            return TerrainType.SOLID;
        }
    }
}