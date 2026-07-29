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
import com.jme3.scene.*;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;

/**
 * Applies world-space-consistent geometry noise displacement <em>and</em>
 * per-vertex ambient occlusion in a single vertex-buffer walk.
 * <p>
 * Previously {@link VertexNoiseMaker} and {@link AmbientOcclusionUtils}
 * traversed each geometry twice (two mesh clones, two full vertex loops).
 * This processor clones the mesh once and computes noise displacement
 * and AO in one pass, with AO using the <em>displaced</em> vertex positions
 * for noise-aware occlusion.
 * <p>
 * Must be called AFTER the spatial has been positioned via
 * {@code AssetUtils.translateToTile()} so that
 * {@link Geometry#getWorldTransform()} returns correct world coordinates.
 *
 * @see AmbientOcclusionUtils  (kept for compatibility with existing AO-only code paths)
 * @see VertexNoiseMaker       (kept for stand-alone noise use cases)
 */
public final class GeometryProcessor {

    // --- Noise constants (from VertexNoiseMaker) ---
    private static final float NOISE_AMPLITUDE = 0.075f;

    // --- AO constants (from AmbientOcclusionUtils) ---
    private static final float AO_STRENGTH = 1f;
    private static final float EDGE_THRESHOLD = 0.2f;
    private static final float MIN_AO = 0.25f;

    /**
     * Scales depth-below-surface into AO darkening.
     * A vertex 0.15 units below the surface reaches MIN_AO (0.25).
     */
    private static final float DEPTH_SCALE = 1f / NOISE_AMPLITUDE / 2;

    /** Height range (local Y) over which wall bottom darkening fades out. */
    private static final float BOTTOM_RANGE = 0.15f;

    /** User-data key for per-wall-piece corner flags. */
    private static final String WALL_CORNER_START_KEY = "wallCornerStart";
    private static final String WALL_CORNER_END_KEY = "wallCornerEnd";

    /** User-data key marking geometries that have already been processed. */
    private static final String PROCESSED_KEY = "geomProcessed";

    // cache to minimize allocations
    private static float[] vertices = new float[0];
    private static byte[]  colors   = new byte[0];

    private GeometryProcessor() {
        // Utility class
    }

    // ================================================================
    //  Public API
    // ================================================================

    /**
     * Which surface layer a tile represents — determines the AO neighbour
     * occlusion rules. Replaces the 8 unpacked booleans that every call
     * site was reconstructing from {@link TileNeighborhood}.
     */
    public enum SurfaceLayer {
        /**
         * Non-solid floor tiles. Only solid neighbours occlude
         * (issue #479).
         */
        FLOOR,
        /**
         * Solid top tiles (rock, reinforced wall, etc.). Occlusion only
         * at material boundaries — a solid neighbour of a <em>different</em>
         * terrain type. Identical solid tiles are flush (no gap).
         */
        SOLID_TOP,
        /**
         * Water / lava tiles. Same-terrain neighbours create darker
         * edges (contiguous water body look).
         */
        WATER
    }

    /**
     * Applies noise + floor AO in a single pass.
     *
     * @param spatial the spatial whose geometry to process.
     *                Must be translated to its world tile position
     *                before calling so getWorldTranslation() is valid.
     * @param n       pre-computed 8-neighbour info for this tile
     * @param layer   surface type — determines occlusion rules
     */
    public static void applyFloorNoiseAndAO(Spatial spatial,
            TileNeighborhood n, SurfaceLayer layer) {

        // Resolve the 8 AO booleans from the neighbourhood masks
        // according to the surface-layer rules.
        boolean N, NE, E, SE, S, SW, W, NW;
        switch (layer) {
            case WATER -> {
                // should be inverted but looks much better
                N  = n.hasSameN();  NE = n.hasSameNE();
                E  = n.hasSameE();  SE = n.hasSameSE();
                S  = n.hasSameS();  SW = n.hasSameSW();
                W  = n.hasSameW();  NW = n.hasSameNW();
            }
            case SOLID_TOP -> {
                if (true) {
                    N = false; NE = false; E = false; SE = false; S = false; SW = false; W = false; NW = false;
                    break; // TODO: tops shouldn't really be occluded by neighbours, though it has some appeal as kind of fog of war
                }
                N  = !n.solidN(); NE = !n.solidNE();
                E  = !n.solidE(); SE = !n.solidSE();
                S  = !n.solidS(); SW = !n.solidSW();
                W  = !n.solidW(); NW = !n.solidNW();
            }
            case FLOOR -> {
                N  = n.solidN();  NE = n.solidNE();
                E  = n.solidE();  SE = n.solidSE();
                S  = n.solidS();  SW = n.solidSW();
                W  = n.solidW();  NW = n.solidNW();
            }
            default -> throw new AssertionError("Unknown SurfaceLayer: " + layer);
        }

        // Tile-relative world coordinates are rotation-independent,
        // unlike local coords which swap axes on rotated pieces.
        float tileX = spatial.getWorldTranslation().x;
        float tileZ = spatial.getWorldTranslation().z;

        AOComputer aoFn = (dx, dy, dz, dwx, dwy, dwz, ox, oy, oz, minY, maxY, maxZ) ->
                computeFloorAO(dwx - tileX, dwz - tileZ, dy, maxY, N, NE, E, SE, S, SW, W, NW);
        processSpatial(spatial, aoFn);
    }

    /**
     * Applies noise + wall AO per child of the spatial. Reads
     * {@code wallCornerStart} / {@code wallCornerEnd} Boolean user data
     * from each direct child to determine corner flags.
     * <p>
     * Must be called AFTER {@code translateToTile()} so world transforms
     * are correct.
     */
    public static void applyWallNoiseAndAO(Spatial spatial) {
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                Boolean cs = child.getUserData(WALL_CORNER_START_KEY);
                Boolean ce = child.getUserData(WALL_CORNER_END_KEY);
                boolean isCornerStart = cs != null && cs;
                boolean isCornerEnd   = ce != null && ce;
                applyWallNoiseAndAO(child, isCornerStart, isCornerEnd);
            }
        } else {
            Boolean cs = spatial.getUserData(WALL_CORNER_START_KEY);
            Boolean ce = spatial.getUserData(WALL_CORNER_END_KEY);
            applyWallNoiseAndAO(spatial,
                    cs != null && cs, ce != null && ce);
        }
    }

    /**
     * Applies noise + wall AO (bottom-row + corner darkening).
     *
     * @param spatial       the wall spatial
     * @param isCornerStart wall forms a room corner on the start side
     * @param isCornerEnd   wall forms a room corner on the end side
     */
    public static void applyWallNoiseAndAO(Spatial spatial,
            boolean isCornerStart, boolean isCornerEnd) {

        spatial.depthFirstTraversal(child -> {
            if (!(child instanceof Geometry geom)) {
                return;
            }
            if (Boolean.TRUE.equals(geom.getUserData(PROCESSED_KEY))) {
                return;
            }

            AOComputer aoFn = (dx, dy, dz, dwx, dwy, dwz, ox, oy, oz, minY, maxY, maxZ) ->
                    computeWallAO(dx, dy, dz, minY, maxZ,
                            isCornerStart, isCornerEnd);

            processGeometry(geom, aoFn);
        });
    }

    // ================================================================
    //  Single-pass vertex processing
    // ================================================================

    /**
     * Traverses a spatial tree and applies noise+AO to every geometry.
     */
    private static void processSpatial(Spatial spatial, AOComputer aoFn) {
        spatial.depthFirstTraversal(child -> {
            if (!(child instanceof Geometry geom)) {
                return;
            }
            processGeometry(geom, aoFn);
        });
    }

    /**
     * Walks a single geometry's vertex buffer once, applying noise
     * displacement and AO color in the same loop.
     */
    private static void processGeometry(Geometry geom, AOComputer aoFn) {
        if (Boolean.TRUE.equals(geom.getUserData(PROCESSED_KEY)))
            return;

        geom.setUserData(PROCESSED_KEY, true);

        Transform worldTransform = geom.getWorldTransform();

        // Clone mesh + position buffer — models are shared across instances
        Mesh mesh = geom.getMesh().clone();
        VertexBuffer posVB = mesh.getBuffer(Type.Position).clone();

        final int vertexCount = mesh.getVertexCount();
        final int vertexCountX3 = vertexCount * 3;
        if (vertices.length < vertexCountX3)
            vertices = new float[vertexCountX3];
        if (colors.length < vertexCountX3)
            colors = new byte[vertexCountX3];
        var posData = (FloatBuffer)posVB.getData(); // TODO: how to make it adapt to the actual data type?
        posData.rewind();
        posData.get(vertices, 0, vertexCountX3);
        // java.util.Arrays.stream(vertices).map(v -> String.format(java.util.Locale.ROOT, "[%f, %f, %f]", v.x, v.y, v.z)).collect(java.util.stream.Collectors.joining(", ", "[", "]"))

        // Inverse world rotation (no translation) for converting the noise
        // vector from world space back to model-local space.
        Transform invWorld = worldTransform.clone();
        invWorld.setTranslation(0, 0, 0);
        invWorld = invWorld.invert();

        // Scan undisplaced vertices for surface bounds.
        // maxY = highest point = surface for floors; minY = lowest = bottom for walls.
        // maxZ = front face for walls (recess depth reference).
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < vertexCount; i++) {
            float y = vertices[i * 3 + 1];
            float z = vertices[i * 3 + 2];
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        // Output buffers
        var newPositions = new float[vertexCount * 3];

        // Reusable vectors to avoid allocation in the hot loop
        var localPos = new Vector3f();
        var worldPos = new Vector3f();
        var invNoise = new Vector3f();
        for (int i = 0; i < vertexCount; ++i) {
            localPos.set(vertices[i * 3], vertices[i * 3 + 1], vertices[i * 3 + 2]);

            // Local → world (pre-displacement)
            worldTransform.transformVector(localPos, worldPos);

            // Compute world-space noise
            var noise = getNoiseForWorldPos(worldPos);

            // Rotate noise back to local space for displacement
            invWorld.transformVector(noise, invNoise);

            // Displaced local position
            float dx = localPos.x + invNoise.x;
            float dy = localPos.y + invNoise.y;
            float dz = localPos.z + invNoise.z;
            newPositions[i * 3]     = dx;
            newPositions[i * 3 + 1] = dy;
            newPositions[i * 3 + 2] = dz;

            // Displaced world position (for rotation-independent AO)
            float dwx = worldPos.x + noise.x;
            float dwy = worldPos.y + noise.y;
            float dwz = worldPos.z + noise.z;

            // AO: pass both displaced local (for wall along-axis / depth) and
            // displaced world (for floor tile-relative cardinal directions),
            // plus surface bounds for intra-tile height/depth AO.
            byte ao = (byte)(aoFn.computeAO(dx, dy, dz, dwx, dwy, dwz,
                    localPos.x, localPos.y, localPos.z, minY, maxY, maxZ) * 255f);
            colors[i * 3]     = ao;
            colors[i * 3 + 1] = ao;
            colors[i * 3 + 2] = ao;
        }

        // Replace position and color buffers on the cloned mesh
        mesh.clearBuffer(Type.Position);
        mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(newPositions));

        var colByteBuf = BufferUtils.createByteBuffer(vertexCountX3);
        colByteBuf.put(colors, 0, vertexCountX3).flip();
        var colBuf = new VertexBuffer(Type.Color);
        colBuf.setName("AO");
        colBuf.setupData(VertexBuffer.Usage.Static, 3, VertexBuffer.Format.UnsignedByte, colByteBuf);
        colBuf.setNormalized(true);
        mesh.clearBuffer(Type.Color);
        mesh.setBuffer(colBuf);

        mesh.updateBound();

        // Modified meshes cannot live in BatchNode
        if (geom.isGrouped()) {
            geom.unassociateFromGroupNode();
        }
        geom.setMesh(mesh);

        // Enable vertex color on the material
        Material mat = geom.getMaterial();
        if (mat != null && mat.getMaterialDef().getMaterialParam("UseVertexColor") != null) {
            mat.setBoolean("UseVertexColor", true);
        }
    }

    // ================================================================
    //  Noise function
    // ================================================================

    /**
     * Generates a 3D noise vector at the given world position.
     * Deterministic and grid-aligned so adjacent tiles get consistent
     * displacements at shared vertex positions.
     */
    private static Vector3f getNoiseForWorldPos(Vector3f worldPos) {
        final int POSITION_SCALE = 10;
        int x = Math.round(worldPos.x * POSITION_SCALE);
        int y = Math.round(worldPos.y * POSITION_SCALE);
        int z = Math.round(worldPos.z * POSITION_SCALE);

        return new Vector3f(
                hash3D(x, y, z) * NOISE_AMPLITUDE,
                hash3D(x + 104729, y + 104729, z + 104729) * NOISE_AMPLITUDE,
                hash3D(x + 15485863, y + 15485863, z + 15485863) * NOISE_AMPLITUDE);
    }

    private static float hash3D(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        return (float) ((h & 0xFFFFFFFFL) / 4294967296.0 * 2.0 - 1.0);
    }

    // ================================================================
    //  AO computation (from AmbientOcclusionUtils)
    // ================================================================

    /**
     * Computes floor/top AO with inter-tile edge occlusion and intra-tile
     * surface-relative depth darkening.
     *
     * @param x    tile-relative world X of the displaced vertex (center at 0)
     * @param z    tile-relative world Z of the displaced vertex
     * @param dy   displaced local Y (height — noise IS applied)
     * @param maxY highest undisplaced local Y = surface level
     */
    static float computeFloorAO(float x, float z, float dy, float maxY,
            boolean N, boolean NE, boolean E, boolean SE,
            boolean S, boolean SW, boolean W, boolean NW) {

        // --- Inter-tile edge AO (existing logic) ---
        float halfTile = 0.5f;
        float edgeThreshold = halfTile * EDGE_THRESHOLD;

        boolean nearNorth = z < -(halfTile - edgeThreshold);
        boolean nearSouth = z > (halfTile - edgeThreshold);
        boolean nearWest  = x < -(halfTile - edgeThreshold);
        boolean nearEast  = x > (halfTile - edgeThreshold);

        float edgeAO = 1.0f;

        if (nearNorth || nearSouth || nearWest || nearEast) {
            float occlusion = 0.0f;
            int sampleCount = 0;

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
            } else if (nearNorth) {
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

            if (sampleCount > 0) {
                edgeAO = Math.max(MIN_AO, 1.0f - occlusion / sampleCount);
            }
        }

        // --- Intra-tile surface-relative depth AO ---
        // Vertices below the surface (dy < maxY) are darker — crevices and
        // noise-displaced pits get natural self-shadowing.
        float depth = Math.max(0f, maxY - dy);
        float surfaceAO = Math.max(MIN_AO, 1.0f - depth * DEPTH_SCALE);

        return Math.max(MIN_AO, edgeAO * surfaceAO);
    }

    /**
     * Computes wall AO with inter-tile corner occlusion and intra-tile
     * surface-relative depth darkening (bottom row + wall-face recess).
     *
     * @param worldX       tile-local X (along-wall) of displaced vertex
     * @param dy           displaced local Y (height)
     * @param dz           displaced local Z (depth into wall)
     * @param minY         lowest undisplaced local Y (wall bottom)
     * @param maxZ         highest undisplaced local Z (wall front face)
     */
    static float computeWallAO(float worldX, float dy, float dz,
            float minY, float maxZ,
            boolean isCornerStart, boolean isCornerEnd) {

        // --- Inter-tile corner/edge AO ---
        float halfTile = 0.5f;
        float edgeThreshold = halfTile * EDGE_THRESHOLD;

        boolean nearWest = worldX < -(halfTile - edgeThreshold);
        boolean nearEast = worldX > (halfTile - edgeThreshold);

        float edgeAO = 1.0f;

        if (nearWest || nearEast) {
            float occlusion = 0.0f;
            int sampleCount = 0;

            if (nearWest) {
                if (isCornerStart) occlusion += AO_STRENGTH;
                sampleCount++;
            }
            if (nearEast) {
                if (isCornerEnd) occlusion += AO_STRENGTH;
                sampleCount++;
            }

            if (sampleCount > 0) {
                edgeAO = Math.max(MIN_AO, 1.0f - occlusion / sampleCount);
            }
        }

        // --- Intra-tile surface-relative AO ---
        // Bottom row: vertices near minY are darker (touching floor).
        // Proportional falloff over BOTTOM_RANGE replaces old binary check.
        float bottomRatio = Math.max(0f, Math.min(1f, (dy - minY) / BOTTOM_RANGE));
        float bottomAO = Math.max(MIN_AO, bottomRatio);

        // Wall-face recess: vertices behind the front face (dz < maxZ) are
        // darker — recessed into the wall.
        float recess = Math.max(0f, maxZ - dz);
        float recessAO = Math.max(MIN_AO, 1.0f - recess * DEPTH_SCALE);

        return Math.max(MIN_AO, edgeAO * bottomAO * recessAO);
    }

    // ================================================================
    //  Inner types
    // ================================================================

    /**
     * Computes the AO factor for a single vertex.
     *
     * @param dx   displaced local X
     * @param dy   displaced local Y
     * @param dz   displaced local Z
     * @param dwx  displaced world X
     * @param dwy  displaced world Y
     * @param dwz  displaced world Z
     * @param ox   original (undisplaced) local X
     * @param oy   original (undisplaced) local Y
     * @param oz   original (undisplaced) local Z
     * @param minY lowest undisplaced local Y in the geometry
     * @param maxY highest undisplaced local Y in the geometry (surface for floors)
     * @param maxZ highest undisplaced local Z in the geometry (front face for walls)
     */
    @FunctionalInterface
    private interface AOComputer {
        float computeAO(float dx, float dy, float dz,
                        float dwx, float dwy, float dwz,
                        float ox, float oy, float oz,
                        float minY, float maxY, float maxZ);
    }
}
