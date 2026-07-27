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
import com.jme3.math.Vector3f;
import com.jme3.scene.*;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import toniarts.openkeeper.game.map.IMapDataInformation;
import toniarts.openkeeper.game.map.IMapTileInformation;
import toniarts.openkeeper.tools.convert.map.KwdFile;
import toniarts.openkeeper.tools.convert.map.Terrain;
import toniarts.openkeeper.utils.WorldUtils;

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
    private static final float AO_STRENGTH = 1f;

    /** Threshold for classifying a vertex as being at a tile edge (fraction of TILE_WIDTH) */
    private static final float EDGE_THRESHOLD = 0.2f;

    /** Minimum AO value (prevent absolute black) */
    private static final float MIN_AO = 0.25f;

    private static float[] vertices = new float[0];
    private static byte[]  colors   = new byte[0];

    private AmbientOcclusionUtils() {
        // Utility class
    }

    // --- Shared vertex-AO application ---

    /**
     * Computes the AO factor for a single vertex given its world-space position.
     *
     * @param worldX world-space X
     * @param worldY world-space Y
     * @param worldZ world-space Z
     * @return AO factor 0..1 (0 = fully dark, 1 = fully lit)
     */
    @FunctionalInterface
    private interface VertexAoFunction {
        float computeAo(float worldX, float worldY, float worldZ);
    }

    /**
     * Traverses a spatial's geometries and writes a per-vertex AO color buffer
     * using the supplied {@link VertexAoFunction}.
     */
    private static void applyVertexAO(Spatial spatial, VertexAoFunction aoFunc) {
        spatial.depthFirstTraversal(child -> {
            if (!(child instanceof Geometry geom))
                return;

            Mesh mesh = geom.getMesh();
            VertexBuffer posBuf = mesh.getBuffer(Type.Position);
            if (posBuf == null)
                return;

            // Clone mesh — can't modify shared mesh
            mesh = mesh.clone();

            // Read position data via FloatBuffer
            final int vertexCount = mesh.getVertexCount();
            final int vertexCountX3 = vertexCount * 3;
            if (vertices.length < vertexCountX3)
                vertices = new float[vertexCountX3];
            if (colors.length < vertexCountX3)
                colors = new byte[vertexCountX3];
            var posData = (FloatBuffer) posBuf.getData(); // TODO: how to make it adapt to the actual data type?
            posData.rewind();
            posData.get(vertices, 0, vertexCountX3);
            // java.util.Arrays.stream(vertices).map(v -> String.format(java.util.Locale.ROOT, "[%f, %f, %f]", v.x, v.y, v.z)).collect(java.util.stream.Collectors.joining(", ", "[", "]"))

            // Apply the local/world transform of this geometry
            var worldTransform = geom.getWorldTransform();
            var pos = new Vector3f();
            for (int i = 0; i < vertexCount; ++i) {
                pos.set(vertices[i * 3], vertices[i * 3 + 1], vertices[i * 3 + 2]);
                var worldPos = worldTransform.transformVector(pos, pos);
                byte ao = (byte) (aoFunc.computeAo(worldPos.x, worldPos.y, worldPos.z) * 255.0f);

                colors[i * 3]     = ao;
                colors[i * 3 + 1] = ao;
                colors[i * 3 + 2] = ao;
            }

            var colByteBuf = BufferUtils.createByteBuffer(vertexCountX3);
            colByteBuf.put(colors, 0, vertexCountX3).flip();
            var colBuf = new VertexBuffer(Type.Color);
            colBuf.setName("AO");
            colBuf.setupData(VertexBuffer.Usage.Static, 3, VertexBuffer.Format.UnsignedByte, colByteBuf);
            colBuf.setNormalized(true);
            mesh.clearBuffer(Type.Color);
            mesh.setBuffer(colBuf);

            // Detach from batch node if grouped (clone changes mesh identity)
            if (geom.isGrouped())
                geom.unassociateFromGroupNode();
            geom.setMesh(mesh);

            // Enable vertex color in the material
            Material mat = geom.getMaterial();
            if (mat != null && mat.getMaterialDef().getMaterialParam("UseVertexColor") != null)
                mat.setBoolean("UseVertexColor", true);
        });
    }

    // --- Shared occlusion math ---

    /**
     * Finalizes the accumulated occlusion samples into a 0..1 AO factor.
     *
     * @param occlusion   sum of occlusion contributions (0..sampleCount * AO_STRENGTH)
     * @param sampleCount number of samples taken
     * @return AO factor (1.0 = fully lit, closer to 0 = darker), clamped at {@link #MIN_AO}
     */
    private static float finalizeOcclusion(float occlusion, int sampleCount) {
        if (sampleCount == 0)
            return 1.0f;
        return Math.max(MIN_AO, 1.0f - occlusion / sampleCount);
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
        applyVertexAO(spatial, (worldX, worldY, worldZ) -> computeFloorAO(worldX, worldZ, N, NE, E, SE, S, SW, W, NW));
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
    public static void applyFloorAO(Spatial spatial, IMapDataInformation<IMapTileInformation> mapData, int x, int y, Terrain terrain, KwdFile kwdFile) {

        boolean N  = isNeighborOccupied(mapData, kwdFile, x    , y - 1, terrain);
        boolean NE = isNeighborOccupied(mapData, kwdFile, x + 1, y - 1, terrain);
        boolean E  = isNeighborOccupied(mapData, kwdFile, x + 1, y,     terrain);
        boolean SE = isNeighborOccupied(mapData, kwdFile, x + 1, y + 1, terrain);
        boolean S  = isNeighborOccupied(mapData, kwdFile, x    , y + 1, terrain);
        boolean SW = isNeighborOccupied(mapData, kwdFile, x - 1, y + 1, terrain);
        boolean W  = isNeighborOccupied(mapData, kwdFile, x - 1, y,     terrain);
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
        boolean nearSouth = z >  (halfTile - edgeThreshold);
        boolean nearWest  = x < -(halfTile - edgeThreshold);
        boolean nearEast  = x >  (halfTile - edgeThreshold);

        // Center or near-center vertices: always fully lit
        if (!nearNorth && !nearSouth && !nearWest && !nearEast) {
            return 1.0f;
        }

        float occlusion = 0.0f;
        int sampleCount = 0;

        // Corner vertices sample 3 neighbors
        if (nearNorth && nearWest) {
            if (N)  occlusion += AO_STRENGTH;
            if (W)  occlusion += AO_STRENGTH;
            if (NW) occlusion += AO_STRENGTH;
            sampleCount = 3;
        } else if (nearNorth && nearEast) {
            if (N)  occlusion += AO_STRENGTH;
            if (E)  occlusion += AO_STRENGTH;
            if (NE) occlusion += AO_STRENGTH;
            sampleCount = 3;
        } else if (nearSouth && nearWest) {
            if (S)  occlusion += AO_STRENGTH;
            if (W)  occlusion += AO_STRENGTH;
            if (SW) occlusion += AO_STRENGTH;
            sampleCount = 3;
        } else if (nearSouth && nearEast) {
            if (S)  occlusion += AO_STRENGTH;
            if (E)  occlusion += AO_STRENGTH;
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

        return finalizeOcclusion(occlusion, sampleCount);
    }

    // --- Wall AO ---

    /**
     * Applies per-vertex ambient occlusion to a wall piece based on corner
     * adjacency. Must be called <b>before</b> the wall spatial is rotated, so that
     * the wall's local X axis corresponds to its horizontal direction.
     * <p>
     * Wall AO has two components:
     * <ul>
     * <li>Vertical: bottom vertices touching the ground get darker</li>
     * <li>Horizontal: vertices near the left/right end of the wall get darker when
     * a perpendicular wall meets at that corner (i.e. the diagonal tile in the room
     * side is SOLID)</li>
     * </ul>
     *
     * @param wall the wall spatial (not yet rotated)
     * @param isLeftCorner true if the diagonal tile at the left end of this wall is
     * SOLID, forming an inner corner with a perpendicular wall
     * @param isRightCorner true if the diagonal tile at the right end of this wall
     * is SOLID, forming an inner corner with a perpendicular wall
     */
    public static void applyWallAO(Spatial wall, boolean isLeftCorner, boolean isRightCorner) {
        // walls are 4 rows, if more than 25% then it's more like 37.5% due to interpolation
        final float bottomThreshold = WorldUtils.FLOOR_HEIGHT + WorldUtils.TILE_HEIGHT * 0.20f;
        applyVertexAO(wall, (worldX, worldY, worldZ) -> computeWallAO(worldX, worldY, bottomThreshold, isLeftCorner, isRightCorner));
    }

    /**
     * Computes wall AO for a vertex given its wall-local position.
     * X = horizontal along-wall position, Y = height.
     */
    static float computeWallAO(float x, float y, float bottomThreshold,
            boolean isLeftCorner, boolean isRightCorner) {

        float halfTile = 0.5f;
        float edgeThreshold = halfTile * EDGE_THRESHOLD;

        boolean nearLeft = x < -(halfTile - edgeThreshold);
        boolean nearRight = x > (halfTile - edgeThreshold);
        boolean nearBottom = y <= bottomThreshold;

        // Interior vertices: fully lit
        if (!nearLeft && !nearRight && !nearBottom)
            return 1.0f;

        float occlusion = 0.0f;
        int sampleCount = 0;

        // Bottom row always gets some darkening (touches floor)
        if (nearBottom) {
            occlusion += AO_STRENGTH;
            sampleCount++;
        }

        // Left edge — darken if a perpendicular wall meets at this corner
        if (nearLeft) {
            if (isLeftCorner)
                occlusion += AO_STRENGTH;
            sampleCount++;
        }

        // Right edge — darken if a perpendicular wall meets at this corner
        if (nearRight) {
            if (isRightCorner)
                occlusion += AO_STRENGTH;
            sampleCount++;
        }

        if (sampleCount == 0)
            return 1.0f;

        return finalizeOcclusion(occlusion, sampleCount);
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
        spatial.depthFirstTraversal(child -> {
            if (!(child instanceof Geometry geom))
                return;

                Mesh mesh = geom.getMesh();
            if (mesh == null || mesh.getBuffer(Type.Color) == null)
                return;
            Material mat = geom.getMaterial();
            if (mat != null && mat.getMaterialDef().getMaterialParam("UseVertexColor") != null)
                mat.setBoolean("UseVertexColor", true);
        });
    }

    // --- Helpers ---

    /**
     * Checks if a neighbor tile is "occupied" — i.e., same terrain or solid.
     */
    private static boolean isNeighborOccupied(IMapDataInformation<IMapTileInformation> mapData, KwdFile kwdFile, int x, int y, Terrain terrain) {
        IMapTileInformation tile = mapData.getTile(x, y);
        if (tile == null)
            return false;

        Terrain neighborTerrain = kwdFile.getTerrain(tile.getTerrainId());
        Terrain bridgeTerrain = kwdFile.getTerrainBridge(tile.getBridgeTerrainType(), neighborTerrain);

        boolean sameTile = (tile.getTerrainId() == terrain.getTerrainId()
                || (bridgeTerrain != null
                && bridgeTerrain.getTerrainId() == terrain.getTerrainId()));
        boolean solid = neighborTerrain.getFlags().contains(Terrain.TerrainFlag.SOLID);

        return /*sameTile &&*/ solid; // TODO: need to check the bridge tile case to if it's needed
    }
}
