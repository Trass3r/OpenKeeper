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
 * This processor clones the mesh once and computes noise displacement
 * and AO in one pass, with AO using the <em>displaced</em> vertex positions
 * for noise-aware occlusion.
 * <p>
 * Must be called AFTER the spatial has been positioned via
 * {@code AssetUtils.translateToTile()} so that
 * {@link Geometry#getWorldTransform()} returns correct world coordinates.
 */
public final class GeometryProcessor {

    // --- Noise constants ---
    static final float NOISE_AMPLITUDE = 0.075f;

    // --- AO constants ---
    private static final float AO_STRENGTH = 1f;
    static final float EDGE_THRESHOLD = 0.2f;
    static final float MIN_AO = 0.25f;

    /**
     * Scales depth-below-surface into AO darkening.
     * A vertex 0.15 units below the surface reaches MIN_AO (0.25).
     */
    private static final float DEPTH_SCALE = 1f / NOISE_AMPLITUDE / 2;

    /** Height range (local Y) over which wall bottom darkening fades out. */
    static final float BOTTOM_RANGE = 0.15f;

    /** User-data key for per-wall-piece corner flags. */
    static final String WALL_CORNER_START_KEY = "wallCornerStart";
    static final String WALL_CORNER_END_KEY = "wallCornerEnd";

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
                // TODO: also consider claimed-ness (though that's technically not AO!)? not claimed SOLID block leads to very dark wall and floor next to it?
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

        AOComputer aoFn = (dx, dy, dz, dwx, dwy, dwz, minY, maxY, maxZ) ->
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
            if (!(child instanceof Geometry geom))
                return;

            if (Boolean.TRUE.equals(geom.getUserData(PROCESSED_KEY)))
                return;

            AOComputer aoFn = (dx, dy, dz, dwx, dwy, dwz, minY, maxY, maxZ) ->
                    computeWallAO(dx, dy, dz, minY, maxZ, isCornerStart, isCornerEnd);

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
            if (!(child instanceof Geometry geom))
                return;
            processGeometry(geom, aoFn);
        });
    }

    /**
     * Processes a single geometry in one pass:
     * <ol>
     * <li>Scan undisplaced vertices for surface bounds.</li>
     * <li>For each vertex: compute world-space position (including
     *     translation), displace in world space, transform the result
     *     back to local via the full inverse transform, then compute
     *     per-vertex occlusion.</li>
     * </ol>
     * <p>
     * Displacement in world space ensures that two tiles sharing a
     * vertex (e.g. wall pieces meeting at a corner) apply the
     * identical displacement regardless of their individual rotations
     * — no gaps at tile boundaries.
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

        // Scan UNDISPLACED vertices for surface bounds.
        // These are identical for same-type tiles, so the surface reference
        // is consistent across adjacent tiles (no seams).
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < vertexCount; i++) {
            float y = vertices[i * 3 + 1];
            float z = vertices[i * 3 + 2];
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        // Full inverse world transform (including translation).  Using
        // the full inverse lets us displace in world space and convert
        // the result back to a rotation-consistent local position.
        Transform fullInvWorld = worldTransform.invert();

        var newPositions = new float[vertexCountX3];

        // Reusable vectors to avoid allocation in the hot loop
        var localPos = new Vector3f();
        var worldPos = new Vector3f();
        var displacedWorld = new Vector3f();

        for (int i = 0; i < vertexCount; ++i) {
            localPos.set(vertices[i * 3], vertices[i * 3 + 1], vertices[i * 3 + 2]);

            // World position (transformVector includes translation).
            // Noise therefore varies uniquely per world location, while
            // shared-edge vertices round to the same grid cell → seam-free.
            worldTransform.transformVector(localPos, worldPos);

            var noise = getGridNoise(worldPos);

            // Displace in world space — two tiles sharing a world-space
            // vertex apply the identical displacement regardless of the
            // tiles' rotations.
            displacedWorld.set(worldPos.x + noise.x,
                               worldPos.y + noise.y,
                               worldPos.z + noise.z);

            // Transform back to local via full inverse for storage
            fullInvWorld.transformVector(displacedWorld, localPos);

            float dx = localPos.x;
            float dy = localPos.y;
            float dz = localPos.z;
            newPositions[i * 3]     = dx;
            newPositions[i * 3 + 1] = dy;
            newPositions[i * 3 + 2] = dz;

            // AO uses displaced world position + undisplaced surface bounds.
            // computeFloorAO gets tile-relative coords (dwx - tileX, …).
            // computeWallAO gets local coords (dx, dy, dz).
            float occlusion = aoFn.computeAO(dx, dy, dz,
                    displacedWorld.x, displacedWorld.y, displacedWorld.z,
                    minY, maxY, maxZ);
            byte ao = (byte) ((1.0f - occlusion) * 255f);
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

    /**
     * Fast 3D displacement handling 5x5 to 3x3 seams without inner if-else branching.
     */
    public static Vector3f getGridNoise(Vector3f v) {
        int ix = Math.round(v.x * 4.0f);
        int iy = Math.round(v.y * 4.0f);
        int iz = Math.round(v.z * 4.0f);

        boolean xOdd = (ix % 2 != 0);
        boolean zOdd = (iz % 2 != 0);

        // Edge vertex on a T-junction between 5x5 and 3x3 meshes
        if (xOdd ^ zOdd) {
            // If xOdd is true, step X by 1 and Z by 0. If false, step X by 0 and Z by 1.
            int dx = xOdd ? 1 : 0;
            int dz = zOdd ? 1 : 0;

            var neighborA = sampleLattice(ix - dx, iy, iz - dz, NOISE_AMPLITUDE);
            var neighborB = sampleLattice(ix + dx, iy, iz + dz, NOISE_AMPLITUDE);

            return new Vector3f(
                (neighborA.x + neighborB.x) * 0.5f,
                (neighborA.y + neighborB.y) * 0.5f,
                (neighborA.z + neighborB.z) * 0.5f
            );
        }

        // Interior fine vertex or standard coarse vertex
        return sampleLattice(ix, iy, iz, NOISE_AMPLITUDE);
    }

    private static Vector3f sampleLattice(int ix, int iy, int iz, float intensity) {
        final int PRIME_X = 73856093;
        final int PRIME_Y = 19349663;
        final int PRIME_Z = 83492791;
        int hx = (ix * PRIME_X) ^ (iy * PRIME_Y) ^ (iz * PRIME_Z);
        int hy = (ix * PRIME_Y) ^ (iy * PRIME_Z) ^ (iz * PRIME_X);
        int hz = (ix * PRIME_Z) ^ (iy * PRIME_X) ^ (iz * PRIME_Y);

        hx = ((hx >>> 16) ^ hx) * 0x45d9f3b;
        hy = ((hy >>> 16) ^ hy) * 0x45d9f3b;
        hz = ((hz >>> 16) ^ hz) * 0x45d9f3b;

        float scale = intensity / Integer.MAX_VALUE;
        return new Vector3f(hx * scale, hy * scale, hz * scale);
    }

    // ================================================================
    //  AO computation
    // ================================================================

    /**
     * Computes floor/top occlusion from inter-tile edge neighbours and
     * intra-tile depth below the undisplaced surface.
     *
     * @param x    tile-relative world X of the displaced vertex (center at 0)
     * @param z    tile-relative world Z of the displaced vertex
     * @param dy   displaced local Y (after noise)
     * @param maxY highest <em>undisplaced</em> local Y = surface reference
     * @return occlusion in {@code [0, 1 - MIN_AO]} (0 = fully lit)
     */
    static float computeFloorAO(float x, float z, float dy, float maxY,
            boolean N, boolean NE, boolean E, boolean SE,
            boolean S, boolean SW, boolean W, boolean NW) {

        float maxOcclusion = 1.0f - MIN_AO;

        // --- Inter-tile edge occlusion ---
        float halfTile = 0.5f;
        float edgeThreshold = halfTile * EDGE_THRESHOLD;

        boolean nearNorth = z < -(halfTile - edgeThreshold);
        boolean nearSouth = z > (halfTile - edgeThreshold);
        boolean nearWest  = x < -(halfTile - edgeThreshold);
        boolean nearEast  = x > (halfTile - edgeThreshold);

        float edgeOcclusion = 0.0f;

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
                edgeOcclusion = Math.min(maxOcclusion, occlusion / sampleCount);
            }
        }

        // --- Intra-tile depth occlusion ---
        // Depth below the undisplaced surface: noise pushing vertices
        // down creates recesses (darker); noise pushing up puts the vertex
        // above the reference plane (depth=0, stays lit).
        float depth = maxY - dy;
        float depthOcclusion = Math.min(maxOcclusion, (depth + NOISE_AMPLITUDE) * DEPTH_SCALE);

        edgeOcclusion = 0; // TODO: remove
        // Combine: occl = 1 - (1-edge)*(1-depth), clamped to maxOcclusion
        return Math.min(maxOcclusion,
                1.0f - (1.0f - edgeOcclusion) * (1.0f - depthOcclusion));
    }

    /**
     * Computes wall occlusion from inter-tile corners and intra-tile
     * surface-relative depth (bottom row + wall-face recess).
     *
     * @param worldX  tile-local X (along-wall) of displaced vertex
     * @param dy      displaced local Y (height)
     * @param dz      displaced local Z (depth into wall)
     * @param minY    lowest <em>undisplaced</em> local Y (wall bottom)
     * @param maxZ    highest <em>undisplaced</em> local Z (wall front face)
     * @return occlusion in {@code [0, 1 - MIN_AO]} (0 = fully lit)
     */
    static float computeWallAO(float worldX, float dy, float dz, float minY, float maxZ, boolean isCornerStart, boolean isCornerEnd) {

        float maxOcclusion = 1.0f - MIN_AO;

        // --- Inter-tile corner occlusion ---
        float halfTile = 0.5f;
        float edgeThreshold = halfTile * EDGE_THRESHOLD;

        boolean nearWest = worldX < -(halfTile - edgeThreshold);
        boolean nearEast = worldX > (halfTile - edgeThreshold);

        float edgeOcclusion = 0.0f;

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
                edgeOcclusion = Math.min(maxOcclusion, occlusion / sampleCount);
            }
        }

        // --- Intra-tile depth occlusion ---
        // Bottom: vertices near minY (touching floor) are darker.
        // Proportional falloff over BOTTOM_RANGE.
        float bottomRatio = Math.max(0f, Math.min(1f, (dy - minY) / BOTTOM_RANGE));
        float bottomOcclusion = 1.0f - bottomRatio;

        // Recess: vertices behind the front face (dz < maxZ) are darker.
        float recess = Math.max(0f, maxZ - dz);
        float recessOcclusion = Math.min(maxOcclusion, recess * DEPTH_SCALE);

        // Combine: occl = 1 - (1-edge)*(1-bottom)*(1-recess), clamped
        return Math.min(maxOcclusion, 1.0f - (1.0f - edgeOcclusion) * (1.0f - bottomOcclusion) * (1.0f - recessOcclusion));
    }

    // ================================================================
    //  Inner types
    // ================================================================

    /**
     * Computes the occlusion factor for a single vertex.
     *
     * @param dx   displaced local X
     * @param dy   displaced local Y
     * @param dz   displaced local Z
     * @param dwx  displaced world X
     * @param dwy  displaced world Y
     * @param dwz  displaced world Z
     * @param minY lowest <em>undisplaced</em> local Y in the geometry
     * @param maxY highest <em>undisplaced</em> local Y (surface for floors)
     * @param maxZ highest <em>undisplaced</em> local Z (front face for walls)
     * @return occlusion in {@code [0, 1 - MIN_AO]} where 0 = no occlusion (fully lit)
     */
    @FunctionalInterface
    private interface AOComputer {
        float computeAO(float dx, float dy, float dz,
                        float dwx, float dwy, float dwz,
                        float minY, float maxY, float maxZ);
    }
}
