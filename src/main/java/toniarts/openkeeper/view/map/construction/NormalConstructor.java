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
package toniarts.openkeeper.view.map.construction;

import com.jme3.asset.AssetManager;
import com.jme3.scene.BatchNode;
import com.jme3.scene.Spatial;
import toniarts.openkeeper.utils.Point;
import toniarts.openkeeper.utils.AssetUtils;
import toniarts.openkeeper.common.RoomInstance;
import toniarts.openkeeper.tools.convert.map.ArtResource;
import toniarts.openkeeper.view.map.TileNeighborhood;
import toniarts.openkeeper.view.map.GeometryProcessor;

/**
 * Constructs "normal" rooms
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public class NormalConstructor extends RoomConstructor {

    public NormalConstructor(AssetManager assetManager, RoomInstance roomInstance,
            TileNeighborhood[][] neighborhoods) {
        super(assetManager, roomInstance, neighborhoods);
    }

    @Override
    protected BatchNode constructFloor() {
        BatchNode root = new BatchNode();
        ArtResource artResource = roomInstance.getRoom().getCompleteResource();
        String modelName = artResource.getName();

        // Normal rooms
        for (int x = 0; x < map.length; x++) {
            for (int y = 0; y < map[x].length; y++) {

                // Skip non-room tiles
                if (!roomInstance.getCoordinatesAsMatrix()[x][y]) {
                    continue;
                }

                // There are 4 different floor pieces
                Spatial part;

                // Figure out which piece by seeing the neighbours from TileNeighborhood
                int wx = start.x + x;
                int wy = start.y + y;
                TileNeighborhood n = neighborhoods[wx][wy];
                boolean N  = n.hasSameN();
                boolean NE = n.hasSameNE();
                boolean E  = n.hasSameE();
                boolean SE = n.hasSameSE();
                boolean S  = n.hasSameS();
                boolean SW = n.hasSameSW();
                boolean W  = n.hasSameW();
                boolean NW = n.hasSameNW();

                // If we are completely covered, use a big tile
                if (N && NE && E && SE && S && SW && W && NW && useBigFloorTile(x, y)) {
                    part = AssetUtils.loadModel(assetManager, modelName + "9", artResource);
                } else {
                    part = QuadConstructor.constructQuad(assetManager, modelName, artResource, N, NE, E, SE, S, SW, W, NW);
                }
                AssetUtils.translateToTile(part, new Point(x, y));

                // Apply noise+AO per-tile. Room floors are non-solid, so only
                // solid neighbors (solidMask) occlude (issue #479).
                GeometryProcessor.applyFloorNoiseAndAO(part, n,
                        GeometryProcessor.SurfaceLayer.FLOOR);

                root.attachChild(part);
            }
        }

        // Set the transform and scale to our scale and 0 the transform
        AssetUtils.translateToTile(root, start);

        return root;
    }

}
