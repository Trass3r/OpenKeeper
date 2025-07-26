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
package toniarts.openkeeper.view.map;

import com.jme3.asset.AssetManager;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import toniarts.openkeeper.common.RoomInstance;
import toniarts.openkeeper.tools.convert.map.KwdFile;
import toniarts.openkeeper.view.map.construction.DoubleQuadConstructor;
import toniarts.openkeeper.view.map.construction.FiveByFiveRotatedConstructor;
import toniarts.openkeeper.view.map.construction.HeroGateConstructor;
import toniarts.openkeeper.view.map.construction.HeroGateFrontEndConstructor;
import toniarts.openkeeper.view.map.construction.HeroGateThreeByOneConstructor;
import toniarts.openkeeper.view.map.construction.HeroGateTwoByTwoConstructor;
import toniarts.openkeeper.view.map.construction.NormalConstructor;
import toniarts.openkeeper.view.map.construction.QuadConstructor;
import toniarts.openkeeper.view.map.construction.RoomConstructor;
import toniarts.openkeeper.view.map.construction.ThreeByThreeConstructor;
import toniarts.openkeeper.view.map.construction.room.CombatPitConstructor;
import toniarts.openkeeper.view.map.construction.room.PrisonConstructor;
import toniarts.openkeeper.view.map.construction.room.StoneBridgeConstructor;
import toniarts.openkeeper.view.map.construction.room.TempleConstructor;
import toniarts.openkeeper.view.map.construction.room.WorkshopConstructor;

/**
 * A factory class you can use to build buildings
 *
 * @author ArchDemon
 */
public final class RoomFactory {

    private static final Logger logger = System.getLogger(RoomFactory.class.getName());

    private RoomFactory() {
        // Nope
    }

    // TODO: Implement room construction using new architecture - world.effect.EffectManagerState was removed
    // Need to create new room construction system compatible with controller architecture
    public static RoomConstructor constructRoom(RoomInstance roomInstance, AssetManager assetManager, KwdFile kwdFile) {
        // Method body commented out - replace with new system
        return null;
    }
}
