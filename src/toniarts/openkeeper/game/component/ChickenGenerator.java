/*
 * Copyright (C) 2014-2019 OpenKeeper
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
package toniarts.openkeeper.game.component;

import com.simsilica.es.EntityComponent;

/**
 * Just a tagging component for chicken generators
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public final class ChickenGenerator implements EntityComponent {

    /**
     * Last entity spawn time, in game time
     */
    public double lastSpawnTime;

    public ChickenGenerator() {
        // For serialization
    }

    public ChickenGenerator(double lastSpawnTime) {
        this.lastSpawnTime = lastSpawnTime;
    }

}
