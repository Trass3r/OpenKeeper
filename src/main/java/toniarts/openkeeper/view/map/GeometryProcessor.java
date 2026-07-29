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
    private static final int POSITION_SCALE = 10;

    // --- AO constants (from AmbientOcclusionUtils) ---
    private static final float AO_STRENGTH = 0.55f;
    private static final float EDGE_THRESHOLD = 0.2f;
    private static final float MIN_AO = 0.25f;

    /** User-data key marking geometries that have already been processed. */
    private static final String PROCESSED_KEY = "geomProcessed";

    private GeometryProcessor() {
        // Utility class
    }

    // ================================================================
    //  Public API
    // ================================================================

    /**
     * Applies noise + floor AO in a single pass.
     *
     * @param spatial the spatial whose geometry to process
     * @param N  north neighbor is occupied
     * @param NE northeast neighbor is occupied
     * @param E  east neighbor is occupied
     * @param SE southeast neighbor is occupied
     * @param S  south neighbor is occupied
     * @param SW southwest neighbor is occupied
     * @param W  west neighbor is occupied
     * @param NW northwest neighbor is occupied
     */
    public static void applyFloorNoiseAndAO(Spatial spatial,
            boolean N, boolean NE, boolean E, boolean SE,
            boolean S, boolean SW, boolean W, boolean NW) {

        AOComputer aoFn = (worldX, worldY, worldZ, localX, localY, localZ) ->
                computeFloorAO(worldX, worldZ, N, NE, E, SE, S, SW, W, NW);

        processSpatial(spatial, aoFn);
    }

    /**
     * Applies noise + simple wall AO (bottom-row darkening only, no edge info).
     */
    public static void applySimpleWallNoiseAndAO(Spatial spatial) {
        applyWallNoiseAndAO(spatial, false, false, false, false);
    }

    /**
     * Applies noise + full wall AO (bottom-row + edge-column darkening).
     *
     * @param spatial       the wall spatial
     * @param isWestEdge    neighbor in the wall's west direction is solid
     * @param isEastEdge    neighbor in the wall's east direction is solid
     * @param isCornerStart wall forms a room corner on the start side
     * @param isCornerEnd   wall forms a room corner on the end side
     */
    public static void applyWallNoiseAndAO(Spatial spatial,
            boolean isWestEdge, boolean isEastEdge,
            boolean isCornerStart, boolean isCornerEnd) {

        // Wall AO needs a bottom-threshold computed from the geometry's
        // local Y range.  This must be computed per-geometry.
        spatial.depthFirstTraversal(new SceneGraphVisitor() {
            @Override
            public void visit(Spatial child) {
                if (!(child instanceof Geometry geom)) {
                    return;
                }
                if (Boolean.TRUE.equals(geom.getUserData(PROCESSED_KEY))) {
                    return;
                }

                // Pre-scan: find the local Y range for bottom-threshold
                Mesh mesh = geom.getMesh();
                VertexBuffer posBuf = mesh.getBuffer(Type.Position);
                if (posBuf == null) {
                    return;
                }
                FloatBuffer posData = (FloatBuffer) posBuf.getDataReadOnly();
                posData.rewind();
                int n = mesh.getVertexCount();
                float minY = Float.MAX_VALUE;
                float maxY = -Float.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    float y = posData.get(i * 3 + 1);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
                float heightRange = maxY - minY;
                float bottomThreshold = minY + heightRange * 0.15f;

                AOComputer aoFn = (worldX, worldY, worldZ, localX, localY, localZ) ->
                        computeWallAO(worldX, localY, bottomThreshold,
                                isWestEdge, isEastEdge, isCornerStart, isCornerEnd);

                processGeometry(geom, aoFn);
            }
        });
    }

    // ================================================================
    //  Single-pass vertex processing
    // ================================================================

    /**
     * Traverses a spatial tree and applies noise+AO to every geometry.
     */
    private static void processSpatial(Spatial spatial, AOComputer aoFn) {
        spatial.depthFirstTraversal(new SceneGraphVisitor() {
            @Override
            public void visit(Spatial child) {
                if (!(child instanceof Geometry geom)) {
                    return;
                }
                processGeometry(geom, aoFn);
            }
        });
    }

    /**
     * Walks a single geometry's vertex buffer once, applying noise
     * displacement and AO color in the same loop.
     */
    private static void processGeometry(Geometry geom, AOComputer aoFn) {
        if (Boolean.TRUE.equals(geom.getUserData(PROCESSED_KEY))) {
            return;
        }
        geom.setUserData(PROCESSED_KEY, true);

        Transform worldTransform = geom.getWorldTransform();

        // Clone mesh + position buffer — models are shared across instances
        Mesh mesh = geom.getMesh().clone();
        VertexBuffer posVB = mesh.getBuffer(Type.Position).clone();
        FloatBuffer positions = (FloatBuffer) posVB.getData();
        positions.rewind();
        int vertexCount = mesh.getVertexCount();

        // Inverse world rotation (no translation) for converting the noise
        // vector from world space back to model-local space.
        Transform invWorld = worldTransform.clone();
        invWorld.setTranslation(0, 0, 0);
        invWorld = invWorld.invert();

        // Output buffers
        float[] newPositions = new float[vertexCount * 3];
        float[] colors = new float[vertexCount * 4];

        // Reusable vectors to avoid allocation in the hot loop
        Vector3f localPos = new Vector3f();
        Vector3f worldPos = new Vector3f();
        Vector3f noise = new Vector3f();
        Vector3f invNoise = new Vector3f();

        int pi = 0; // position index in flat arrays
        while (positions.hasRemaining()) {
            // Read local position
            localPos.x = positions.get();
            localPos.y = positions.get();
            localPos.z = positions.get();

            // Local → world (pre-displacement)
            worldTransform.transformVector(localPos, worldPos);

            // Compute world-space noise
            noise.set(getNoiseForWorldPos(worldPos));

            // Rotate noise back to local space for displacement
            invWorld.transformVector(noise, invNoise);

            // Displaced local position
            float dx = localPos.x + invNoise.x;
            float dy = localPos.y + invNoise.y;
            float dz = localPos.z + invNoise.z;
            newPositions[pi]     = dx;
            newPositions[pi + 1] = dy;
            newPositions[pi + 2] = dz;

            // AO from displaced world position
            // (worldPos + noise gives the displaced world position without
            // needing another transform)
            float ao = aoFn.computeAO(
                    worldPos.x + noise.x, worldPos.y + noise.y, worldPos.z + noise.z,
                    localPos.x, localPos.y, localPos.z);

            int ci = (pi / 3) * 4;
            colors[ci]     = ao;
            colors[ci + 1] = ao;
            colors[ci + 2] = ao;
            colors[ci + 3] = 1.0f;

            pi += 3;
        }

        // Replace position and color buffers on the cloned mesh
        mesh.clearBuffer(Type.Position);
        mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(newPositions));
        mesh.setBuffer(Type.Color, 4, BufferUtils.createFloatBuffer(colors));
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
     * @param x world-space X of the displaced vertex
     * @param z world-space Z of the displaced vertex
     */
    static float computeFloorAO(float x, float z,
            boolean N, boolean NE, boolean E, boolean SE,
            boolean S, boolean SW, boolean W, boolean NW) {

        float halfTile = 0.5f;
        float edgeThreshold = halfTile * EDGE_THRESHOLD;

        boolean nearNorth = z < -(halfTile - edgeThreshold);
        boolean nearSouth = z > (halfTile - edgeThreshold);
        boolean nearWest  = x < -(halfTile - edgeThreshold);
        boolean nearEast  = x > (halfTile - edgeThreshold);

        if (!nearNorth && !nearSouth && !nearWest && !nearEast) {
            return 1.0f;
        }

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

        if (sampleCount == 0) {
            return 1.0f;
        }

        float avgOcclusion = occlusion / sampleCount;
        return Math.max(MIN_AO, 1.0f - avgOcclusion);
    }

    /**
     * @param worldX        world-space X (along-wall) of displaced vertex
     * @param localY        local-space Y (height) for bottom-row detection
     * @param bottomThreshold  vertices with localY ≤ this are "bottom row"
     */
    static float computeWallAO(float worldX, float localY, float bottomThreshold,
            boolean isWestEdge, boolean isEastEdge,
            boolean isCornerStart, boolean isCornerEnd) {

        float halfTile = 0.5f;
        float edgeThreshold = halfTile * EDGE_THRESHOLD;

        boolean nearWest  = worldX < -(halfTile - edgeThreshold);
        boolean nearEast  = worldX > (halfTile - edgeThreshold);
        boolean nearBottom = localY <= bottomThreshold;

        if (!nearWest && !nearEast && !nearBottom) {
            return 1.0f;
        }

        float occlusion = 0.0f;
        int sampleCount = 0;

        if (nearBottom) {
            occlusion += AO_STRENGTH * 0.7f;
            sampleCount++;
        }
        if (nearWest) {
            if (isWestEdge)   occlusion += AO_STRENGTH;
            if (isCornerStart) occlusion += AO_STRENGTH * 0.5f;
            sampleCount++;
        }
        if (nearEast) {
            if (isEastEdge)  occlusion += AO_STRENGTH;
            if (isCornerEnd)  occlusion += AO_STRENGTH * 0.5f;
            sampleCount++;
        }

        if (sampleCount == 0) {
            return 1.0f;
        }

        float avgOcclusion = occlusion / sampleCount;
        return Math.max(MIN_AO, 1.0f - avgOcclusion);
    }

    // ================================================================
    //  Inner types
    // ================================================================

    /**
     * Computes the AO factor for a single vertex given its world-space
     * (displaced) and local-space positions.
     */
    @FunctionalInterface
    private interface AOComputer {
        float computeAO(float worldX, float worldY, float worldZ,
                        float localX, float localY, float localZ);
    }
}
