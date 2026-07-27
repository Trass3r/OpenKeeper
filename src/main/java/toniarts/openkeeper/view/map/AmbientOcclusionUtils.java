/*
 * Copyright (C) 2014-2026 OpenKeeper
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

import com.jme3.material.Material;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import toniarts.openkeeper.game.map.IMapDataInformation;
import toniarts.openkeeper.game.map.IMapTileInformation;
import toniarts.openkeeper.tools.convert.map.KwdFile;
import toniarts.openkeeper.tools.convert.map.Terrain;

/**
 * Injects per-vertex ambient occlusion (fake lighting) into KMF-loaded terrain
 * models by adding a vertex color buffer. Darkens vertices near solid/occupied
 * neighboring tiles to create the illusion of ambient occlusion, similar to how
 * the original Dungeon Keeper II did it.
 * <p>
 * The AO factor for each vertex is determined by its position relative to the
 * tile center: corner vertices sample 3 neighbors, edge vertices sample 1,
 * center vertices are always fully lit.
 *
 * @see <a href="https://github.com/tonihele/OpenKeeper/issues/479">Issue #479</a>
 */
public final class AmbientOcclusionUtils {

    /** How much each occupied neighbor darkens an adjacent vertex (0=no darkening, 1=full black) */
    private static final float AO_STRENGTH = 0.55f;

    /** Threshold for classifying a vertex as being at a tile edge (fraction of TILE_WIDTH) */
    private static final float EDGE_THRESHOLD = 0.2f;

    /** Minimum AO value (prevent absolute black) */
    private static final float MIN_AO = 0.25f;

    private AmbientOcclusionUtils() {
        // Utility class
    }

    // --- Floor / Top AO ---

    /**
     * Applies per-vertex ambient occlusion to a floor or top tile spatial
     * based on the 8-neighbor occupancy pattern.
     *
     * @param spatial the spatial to modify (node containing geometry pieces)
     * @param N  north neighbor is occupied
     * @param NE northeast neighbor is occupied
     * @param E  east neighbor is occupied
     * @param SE southeast neighbor is occupied
     * @param S  south neighbor is occupied
     * @param SW southwest neighbor is occupied
     * @param W  west neighbor is occupied
     * @param NW northwest neighbor is occupied
     */
    public static void applyFloorAO(Spatial spatial,
            boolean N, boolean NE, boolean E, boolean SE,
            boolean S, boolean SW, boolean W, boolean NW) {

        spatial.depthFirstTraversal(new SceneGraphVisitor() {
            @Override
            public void visit(Spatial child) {
                if (!(child instanceof Geometry geom)) {
                    return;
                }

                Mesh mesh = geom.getMesh();
                VertexBuffer posBuf = mesh.getBuffer(Type.Position);
                if (posBuf == null) {
                    return;
                }

                // Clone mesh — can't modify shared mesh
                mesh = mesh.clone();

                // Read position data via FloatBuffer
                FloatBuffer posData = (FloatBuffer) posBuf.getDataReadOnly();
                posData.rewind();
                int vertexCount = mesh.getVertexCount();
                float[] posArray = new float[vertexCount * 3];
                posData.get(posArray);

                // Remove old color buffer if present
                mesh.clearBuffer(Type.Color);

                // Apply the local/world transform of this geometry
                Transform worldTransform = geom.getWorldTransform();

                float[] colors = new float[vertexCount * 4];

                for (int i = 0; i < vertexCount; i++) {
                    int pi = i * 3;
                    Vector3f pos = new Vector3f(posArray[pi], posArray[pi + 1], posArray[pi + 2]);

                    // Transform to world-space (tile-local, since spatial parent
                    // is at tile center before TranslateToTile)
                    Vector3f worldPos = worldTransform.transformVector(pos, null);

                    float ao = computeFloorAO(worldPos.x, worldPos.z,
                            N, NE, E, SE, S, SW, W, NW);

                    int ci = i * 4;
                    colors[ci] = ao;
                    colors[ci + 1] = ao;
                    colors[ci + 2] = ao;
                    colors[ci + 3] = 1.0f;
                }

                mesh.setBuffer(Type.Color, 4, BufferUtils.createFloatBuffer(colors));
                mesh.updateBound();

                // Detach from batch node if grouped (clone changes mesh identity)
                if (geom.isGrouped()) {
                    geom.unassociateFromGroupNode();
                }
                geom.setMesh(mesh);

                // Enable vertex color in the material
                Material mat = geom.getMaterial();
                if (mat != null && mat.getMaterialDef().getMaterialParam("UseVertexColor") != null) {
                    mat.setBoolean("UseVertexColor", true);
                }
            }
        });
    }

    /**
     * Applies floor AO to a single-piece (non-quad) tile spatial by looking up
     * neighbor occupancy from the map data.
     *
     * @param spatial the spatial to modify
     * @param mapData the map data for neighbor lookup
     * @param x       tile X coordinate
     * @param y       tile Y coordinate
     * @param terrain the terrain of this tile
     * @param kwdFile needed for terrain lookups
     */
    public static void applyFloorAO(Spatial spatial,
            IMapDataInformation<IMapTileInformation> mapData,
            int x, int y, Terrain terrain, KwdFile kwdFile) {

        boolean N = isNeighborOccupied(mapData, kwdFile, x, y - 1, terrain);
        boolean NE = isNeighborOccupied(mapData, kwdFile, x + 1, y - 1, terrain);
        boolean E = isNeighborOccupied(mapData, kwdFile, x + 1, y, terrain);
        boolean SE = isNeighborOccupied(mapData, kwdFile, x + 1, y + 1, terrain);
        boolean S = isNeighborOccupied(mapData, kwdFile, x, y + 1, terrain);
        boolean SW = isNeighborOccupied(mapData, kwdFile, x - 1, y + 1, terrain);
        boolean W = isNeighborOccupied(mapData, kwdFile, x - 1, y, terrain);
        boolean NW = isNeighborOccupied(mapData, kwdFile, x - 1, y - 1, terrain);

        applyFloorAO(spatial, N, NE, E, SE, S, SW, W, NW);
    }

    /**
     * Computes the AO factor for a vertex at tile-local position (x, z),
     * where tile center is (0, 0) and tile bounds are ±0.5.
     */
    static float computeFloorAO(float x, float z,
            boolean N, boolean NE, boolean E, boolean SE,
            boolean S, boolean SW, boolean W, boolean NW) {

        float halfTile = 0.5f;
        float edgeThreshold = halfTile * EDGE_THRESHOLD; // ~0.1 units

        boolean nearNorth = z < -(halfTile - edgeThreshold);
        boolean nearSouth = z > (halfTile - edgeThreshold);
        boolean nearWest = x < -(halfTile - edgeThreshold);
        boolean nearEast = x > (halfTile - edgeThreshold);

        // Center or near-center vertices: always fully lit
        if (!nearNorth && !nearSouth && !nearWest && !nearEast) {
            return 1.0f;
        }

        float occlusion = 0.0f;
        int sampleCount = 0;

        // Corner vertices sample 3 neighbors
        if (nearNorth && nearWest) {
            if (N) occlusion += AO_STRENGTH;
            if (W) occlusion += AO_STRENGTH;
            if (NW) occlusion += AO_STRENGTH;
            sampleCount = 3;
        } else if (nearNorth && nearEast) {
            if (N) occlusion += AO_STRENGTH;
            if (E) occlusion += AO_STRENGTH;
            if (NE) occlusion += AO_STRENGTH;
            sampleCount = 3;
        } else if (nearSouth && nearWest) {
            if (S) occlusion += AO_STRENGTH;
            if (W) occlusion += AO_STRENGTH;
            if (SW) occlusion += AO_STRENGTH;
            sampleCount = 3;
        } else if (nearSouth && nearEast) {
            if (S) occlusion += AO_STRENGTH;
            if (E) occlusion += AO_STRENGTH;
            if (SE) occlusion += AO_STRENGTH;
            sampleCount = 3;
        }
        // Edge vertices sample 1 neighbor
        else if (nearNorth) {
            if (N) occlusion += AO_STRENGTH;
            sampleCount = 1;
        } else if (nearSouth) {
            if (S) occlusion += AO_STRENGTH;
            sampleCount = 1;
        } else if (nearWest) {
            if (W) occlusion += AO_STRENGTH;
            sampleCount = 1;
        } else if (nearEast) {
            if (E) occlusion += AO_STRENGTH;
            sampleCount = 1;
        }

        if (sampleCount == 0) {
            return 1.0f;
        }

        float avgOcclusion = occlusion / sampleCount;
        return Math.max(MIN_AO, 1.0f - avgOcclusion);
    }

    // --- Wall AO ---

    /**
     * Applies simple wall AO — darkens the bottom row of vertices
     * (which touch the adjacent ground tile).
     *
     * @param wall the wall spatial to modify
     */
    public static void applySimpleWallAO(Spatial wall) {
        applyWallAO(wall, false, false, false, false);
    }

    /**
     * Applies per-vertex ambient occlusion to a wall piece.
     * Wall AO has two components:
     * <ul>
     *   <li>Horizontal (column-based): edge columns touching adjacent solid tiles get darker</li>
     *   <li>Vertical: bottom vertices touching the ground get darker</li>
     * </ul>
     *
     * @param wall          the wall spatial
     * @param isWestEdge    true if the neighbor in the wall's west direction is solid
     * @param isEastEdge    true if the neighbor in the wall's east direction is solid
     * @param isCornerStart true if this wall forms a room corner edge on the start side
     * @param isCornerEnd   true if this wall forms a room corner edge on the end side
     */
    public static void applyWallAO(Spatial wall,
            boolean isWestEdge, boolean isEastEdge,
            boolean isCornerStart, boolean isCornerEnd) {

        wall.depthFirstTraversal(new SceneGraphVisitor() {
            @Override
            public void visit(Spatial child) {
                if (!(child instanceof Geometry geom)) {
                    return;
                }

                Mesh mesh = geom.getMesh();
                VertexBuffer posBuf = mesh.getBuffer(Type.Position);
                if (posBuf == null) {
                    return;
                }

                mesh = mesh.clone();

                // Read position data via FloatBuffer
                FloatBuffer posData = (FloatBuffer) posBuf.getDataReadOnly();
                posData.rewind();
                int vertexCount = mesh.getVertexCount();
                float[] posArray = new float[vertexCount * 3];
                posData.get(posArray);

                mesh.clearBuffer(Type.Color);

                Transform worldTransform = geom.getWorldTransform();

                // Find min/max height for bottom row detection
                float minY = Float.MAX_VALUE;
                float maxY = -Float.MAX_VALUE;
                for (int i = 0; i < vertexCount; i++) {
                    float y = posArray[i * 3 + 1];
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
                float heightRange = maxY - minY;
                float bottomThreshold = minY + heightRange * 0.15f; // bottom ~15%

                float[] colors = new float[vertexCount * 4];

                for (int i = 0; i < vertexCount; i++) {
                    int pi = i * 3;
                    Vector3f pos = new Vector3f(posArray[pi], posArray[pi + 1], posArray[pi + 2]);

                    Vector3f worldPos = worldTransform.transformVector(pos, null);

                    float ao = computeWallAO(worldPos.x, posArray[pi + 1], bottomThreshold,
                            isWestEdge, isEastEdge, isCornerStart, isCornerEnd);

                    int ci = i * 4;
                    colors[ci] = ao;
                    colors[ci + 1] = ao;
                    colors[ci + 2] = ao;
                    colors[ci + 3] = 1.0f;
                }

                mesh.setBuffer(Type.Color, 4, BufferUtils.createFloatBuffer(colors));
                mesh.updateBound();

                if (geom.isGrouped()) {
                    geom.unassociateFromGroupNode();
                }
                geom.setMesh(mesh);

                Material mat = geom.getMaterial();
                if (mat != null && mat.getMaterialDef().getMaterialParam("UseVertexColor") != null) {
                    mat.setBoolean("UseVertexColor", true);
                }
            }
        });
    }

    /**
     * Computes wall AO for a vertex given its wall-local position.
     * X = horizontal along-wall position, Y = height.
     */
    static float computeWallAO(float x, float y, float bottomThreshold,
            boolean isWestEdge, boolean isEastEdge,
            boolean isCornerStart, boolean isCornerEnd) {

        float halfTile = 0.5f;
        float edgeThreshold = halfTile * EDGE_THRESHOLD;

        boolean nearWest = x < -(halfTile - edgeThreshold);
        boolean nearEast = x > (halfTile - edgeThreshold);
        boolean nearBottom = y <= bottomThreshold;

        // Interior vertices: fully lit
        if (!nearWest && !nearEast && !nearBottom) {
            return 1.0f;
        }

        float occlusion = 0.0f;
        int sampleCount = 0;

        // Bottom row always gets some darkening (touches floor)
        if (nearBottom) {
            occlusion += AO_STRENGTH * 0.7f;
            sampleCount++;
        }

        // West edge column
        if (nearWest) {
            if (isWestEdge) occlusion += AO_STRENGTH;
            if (isCornerStart) occlusion += AO_STRENGTH * 0.5f;
            sampleCount++;
        }

        // East edge column
        if (nearEast) {
            if (isEastEdge) occlusion += AO_STRENGTH;
            if (isCornerEnd) occlusion += AO_STRENGTH * 0.5f;
            sampleCount++;
        }

        if (sampleCount == 0) {
            return 1.0f;
        }

        float avgOcclusion = occlusion / sampleCount;
        return Math.max(MIN_AO, 1.0f - avgOcclusion);
    }

    // --- Post-hoc material fix ---

    /**
     * Re-enables {@code UseVertexColor} on any geometry that already has a
     * vertex color buffer (e.g. after a material swap by
     * {@code setRandomTexture}).
     *
     * @param spatial the spatial whose geometries to check
     */
    public static void enableVertexColorOnExistingColorBuffer(Spatial spatial) {
        spatial.depthFirstTraversal(new SceneGraphVisitor() {
            @Override
            public void visit(Spatial child) {
                if (!(child instanceof Geometry geom)) {
                    return;
                }
                Mesh mesh = geom.getMesh();
                if (mesh == null || mesh.getBuffer(Type.Color) == null) {
                    return;
                }
                Material mat = geom.getMaterial();
                if (mat != null && mat.getMaterialDef().getMaterialParam("UseVertexColor") != null) {
                    mat.setBoolean("UseVertexColor", true);
                }
            }
        });
    }

    // --- Helpers ---

    /**
     * Checks if a neighbor tile is "occupied" — i.e., same terrain or solid.
     */
    private static boolean isNeighborOccupied(
            IMapDataInformation<IMapTileInformation> mapData, KwdFile kwdFile,
            int x, int y, Terrain terrain) {
        IMapTileInformation tile = mapData.getTile(x, y);
        if (tile == null) {
            return false;
        }
        Terrain neighborTerrain = kwdFile.getTerrain(tile.getTerrainId());
        Terrain bridgeTerrain = kwdFile.getTerrainBridge(
                tile.getBridgeTerrainType(), neighborTerrain);

        boolean sameTile = (tile.getTerrainId() == terrain.getTerrainId()
                || (bridgeTerrain != null
                && bridgeTerrain.getTerrainId() == terrain.getTerrainId()));
        boolean solid = neighborTerrain.getFlags().contains(Terrain.TerrainFlag.SOLID);

        return sameTile || solid;
    }
}
