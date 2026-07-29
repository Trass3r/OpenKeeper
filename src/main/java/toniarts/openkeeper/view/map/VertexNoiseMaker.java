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

import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.FloatBuffer;

/**
 * Applies world-space-consistent 3D hash noise displacement to geometry
 * vertices. Uses the model's world transform so that adjacent tiles sharing
 * the same vertex get identical displacements.
 * <p>
 * Based on the noise experiment in the {@code wip} branch (commits
 * {@code df338a71} and {@code c57c0d0e}).
 * <p>
 * <b>Important:</b> This must be called AFTER the spatial has been positioned
 * via {@code AssetUtils.translateToTile()} so that
 * {@link Geometry#getWorldTransform()} returns the correct world position.
 * Ambient occlusion should be applied AFTER noise so it sees the displaced
 * vertex positions.
 */
public final class VertexNoiseMaker {

    private static final Logger logger = System.getLogger(VertexNoiseMaker.class.getName());

    /** Maximum displacement magnitude in world units. */
    private static final float NOISE_AMPLITUDE = 0.075f;

    /** Grid granularity for the noise function (higher = finer detail). */
    private static final int POSITION_SCALE = 10;

    /** User-data key marking geometries that have already had noise applied. */
    private static final String NOISE_APPLIED_KEY = "noiseApplied";

    private VertexNoiseMaker() {
        // Utility class
    }

    /**
     * Applies noise displacement to all {@link Geometry} children of the
     * given spatial. Skips geometries already marked with
     * {@code noiseApplied} user data.
     * <p>
     * Each geometry's mesh is cloned (because models are shared across
     * multiple tile instances) and unassociated from its group node if
     * batched (modified meshes cannot live in a {@code BatchNode}).
     *
     * @param spatial the root spatial whose geometry subtree to noise
     */
    public static void applyNoiseToSpatial(Spatial spatial) {
        spatial.depthFirstTraversal(new SceneGraphVisitor() {
            @Override
            public void visit(Spatial child) {
                if (!(child instanceof Geometry geom)) {
                    return;
                }
                applyNoiseToGeometry(geom);
            }
        });
    }

    /**
     * Applies noise displacement to a single geometry.
     */
    private static void applyNoiseToGeometry(Geometry geom) {
        if (Boolean.TRUE.equals(geom.getUserData(NOISE_APPLIED_KEY))) {
            return;
        }
        geom.setUserData(NOISE_APPLIED_KEY, true);

        Transform worldTransform = geom.getWorldTransform();

        // Clone mesh — models are shared; noise-modified meshes are unique
        Mesh mesh = geom.getMesh().clone();
        VertexBuffer posVB = mesh.getBuffer(Type.Position).clone();
        FloatBuffer positions = (FloatBuffer) posVB.getData();
        positions.rewind();

        // Inverse world rotation (no translation) for converting noise back
        // to model space.  The tile translation is the world position origin;
        // we only need to rotate the noise vector back so it aligns with the
        // local vertex frame.
        Transform invWorld = worldTransform.clone();
        invWorld.setTranslation(0, 0, 0);
        invWorld = invWorld.invert();

        Vector3f vertex = new Vector3f();
        Vector3f worldVertex = new Vector3f();
        Vector3f noise = new Vector3f();
        Vector3f invNoise = new Vector3f();

        while (positions.hasRemaining()) {
            vertex.x = positions.get();
            vertex.y = positions.get();
            vertex.z = positions.get();

            // Vertex → world space
            worldTransform.transformVector(vertex, worldVertex);

            // Compute world-space noise at this position
            noise.set(getNoiseForWorldPos(worldVertex));

            // Rotate noise back to model space
            invWorld.transformVector(noise, invNoise);

            // Write displaced vertex back
            positions.position(positions.position() - 3);
            positions.put(vertex.x + invNoise.x);
            positions.put(vertex.y + invNoise.y);
            positions.put(vertex.z + invNoise.z);
        }

        mesh.clearBuffer(Type.Position);
        mesh.setBuffer(posVB);
        mesh.updateBound();

        // Modified meshes cannot be batched
        if (geom.isGrouped()) {
            geom.unassociateFromGroupNode();
        }
        geom.setMesh(mesh);
    }

    /**
     * Generates a 3D noise vector at the given world position.
     * The noise is deterministic and grid-aligned via
     * {@link #POSITION_SCALE}, so adjacent tiles sharing world-coordinate
     * positions produce consistent displacements.
     */
    private static Vector3f getNoiseForWorldPos(Vector3f worldPos) {
        int x = Math.round(worldPos.x * POSITION_SCALE);
        int y = Math.round(worldPos.y * POSITION_SCALE);
        int z = Math.round(worldPos.z * POSITION_SCALE);

        // Three independent hash values using large distinct prime offsets
        // for the three displacement axes.
        return new Vector3f(
                hash3D(x, y, z) * NOISE_AMPLITUDE,
                hash3D(x + 104729, y + 104729, z + 104729) * NOISE_AMPLITUDE,
                hash3D(x + 15485863, y + 15485863, z + 15485863) * NOISE_AMPLITUDE);
    }

    /**
     * Wang hash — fast, good distribution, no visible grid patterns.
     */
    private static float hash3D(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        // Map to [-1, 1]
        return (float) ((h & 0xFFFFFFFFL) / 4294967296.0 * 2.0 - 1.0);
    }
}
