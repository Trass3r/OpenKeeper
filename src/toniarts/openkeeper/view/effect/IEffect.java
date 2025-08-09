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
package toniarts.openkeeper.view.effect;

import toniarts.openkeeper.tools.convert.map.ArtResource;

/**
 *
 * @author ArchDemon
 */
public interface IEffect {
    String getName();
    ArtResource getArtResource();

    float getAirFriction();
    float getElasticity();
    float getMass();

    float getMinSpeedXy();
    float getMaxSpeedXy();
    float getMinSpeedYz();
    float getMaxSpeedYz();

    float getMinScale();
    float getMaxScale();

    int getMinHp();
    int getMaxHp();
}
