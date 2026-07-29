/*
 * Copyright (C) 2026 OpenKeeper
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

import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying that {@link AmbientOcclusionUtils#applyWallAO}
 * and {@link AmbientOcclusionUtils#applyFloorAO} correctly write per-vertex
 * AO values into the geometry's color buffer.
 *
 * <p>These tests create synthetic meshes (no OpenGL required), apply AO, and
 * read back the vertex color buffers to assert expected brightness values.
 *
 * <p>Wall model convention (see {@link toniarts.openkeeper.utils.WorldUtils}):
 * <ul>
 *   <li>Local Y = height above floor; {@code FLOOR_HEIGHT = 1.0},
 *       {@code TILE_HEIGHT = 1.0}</li>
 *   <li>Bottom threshold = {@code 1.0 + 1.0 * 0.20 = 1.2}</li>
 *   <li>Wall vertices span Y ≈ 1.0 (bottom) to 2.0 (top)</li>
 *   <li>Local X = horizontal along the wall, ±0.5 are the edges</li>
 * </ul>
 */
class AmbientOcclusionIntegrationTest {

    // ── Wall AO integration tests ──────────────────────────────────────────

    @Test
    void wallAo_bothCorners_darkensCornerVertices() {
        // Create a wall mesh with vertices at key positions.
        // Y=1.0 = bottom row, Y=1.5 = above bottom threshold (1.2)
        // X=±0.5 = edges, X=0.0 = center
        Mesh mesh = createWallMesh(new float[] {
            -0.5f, 1.0f, 0f,  // v0: bottom-left
             0.5f, 1.0f, 0f,  // v1: bottom-right
            -0.5f, 1.5f, 0f,  // v2: top-left
             0.5f, 1.5f, 0f,  // v3: top-right
             0.0f, 1.0f, 0f,  // v4: bottom-center
             0.0f, 1.5f, 0f,  // v5: top-center (interior)
        });

        Geometry geom = new Geometry("wall", mesh);
        AmbientOcclusionUtils.applyWallAO(geom, true, true);

        byte[] colors = readColorBytes(geom);

        // v0: bottom-left → nearBottom + nearLeft + isLeftCorner
        //     occlusion=1+0.5=1.5, samples=2 → 1-1.5/2=0.25 → 63
        assertColorEquals("v0 bottom-left corner", 0.25f, colors, 0);

        // v1: bottom-right → nearBottom + nearRight + isRightCorner
        //     occlusion=1+0.5=1.5, samples=2 → 0.25 → 63
        assertColorEquals("v1 bottom-right corner", 0.25f, colors, 1);

        // v2: top-left → nearLeft + isLeftCorner (y=1.5 > 1.2, not nearBottom)
        //     occlusion=1, samples=1 → 1-1=0 → clamped to MIN_AO=0.25
        assertColorEquals("v2 top-left corner", 0.25f, colors, 2);

        // v3: top-right → nearRight + isRightCorner
        //     occlusion=1, samples=1 → 0.25
        assertColorEquals("v3 top-right corner", 0.25f, colors, 3);

        // v4: bottom-center → nearBottom only
        //     occlusion=1, samples=1 → 1-1=0 → clamped to MIN_AO=0.25 → 63
        assertColorEquals("v4 bottom-center", 0.25f, colors, 4);

        // v5: top-center → interior (not near anything) → 1.0 → 255
        assertColorEquals("v5 top-center interior", 1.0f, colors, 5);
    }

    @Test
    void wallAo_noCorners_onlyBottomDarkened() {
        Mesh mesh = createWallMesh(new float[] {
            -0.5f, 1.0f, 0f,  // v0: bottom-left
             0.5f, 1.0f, 0f,  // v1: bottom-right
            -0.5f, 1.5f, 0f,  // v2: top-left
             0.5f, 1.5f, 0f,  // v3: top-right
             0.0f, 1.0f, 0f,  // v4: bottom-center (no edge proximity)
        });

        Geometry geom = new Geometry("wall", mesh);
        AmbientOcclusionUtils.applyWallAO(geom, false, false);

        byte[] colors = readColorBytes(geom);

        // Bottom-edge vertices have BOTH nearBottom AND nearLeft/nearRight.
        // Even without corner flags, the edge sampleCount dilutes the avg:
        //   occlusion=1 (bottom), sampleCount=2 → 1-1/2 = 0.5
        assertColorEquals("v0 bottom-left (no corner)", 0.5f, colors, 0);
        assertColorEquals("v1 bottom-right (no corner)", 0.5f, colors, 1);
        // Top-edge vertices: nearLeft/nearRight without corner → fully lit
        assertColorEquals("v2 top-left (no corner)", 1.0f, colors, 2);
        assertColorEquals("v3 top-right (no corner)", 1.0f, colors, 3);
        // Bottom-center: only nearBottom → occlusion=1, sampleCount=1 → 0.25
        assertColorEquals("v4 bottom-center", 0.25f, colors, 4);
    }

    @Test
    void wallAo_leftCornerOnly_asymmetricDarkening() {
        Mesh mesh = createWallMesh(new float[] {
            -0.5f, 1.0f, 0f,  // v0: bottom-left
             0.5f, 1.0f, 0f,  // v1: bottom-right
            -0.5f, 1.5f, 0f,  // v2: top-left
             0.5f, 1.5f, 0f,  // v3: top-right
        });

        Geometry geom = new Geometry("wall", mesh);
        AmbientOcclusionUtils.applyWallAO(geom, true, false);

        byte[] colors = readColorBytes(geom);

        // Left side darkened, right side stays lit (above bottom) or half-lit (bottom)
        assertColorEquals("v0 bottom-left (left corner)", 0.25f, colors, 0);
        // v1: bottom-right, no corner → nearBottom+nearRight → occlusion=1, sampleCount=2 → 0.5
        assertColorEquals("v1 bottom-right (no right corner)", 0.5f, colors, 1);
        assertColorEquals("v2 top-left (left corner, above bottom)", 0.25f, colors, 2);
        assertColorEquals("v3 top-right (no corner, above bottom)", 1.0f, colors, 3);
    }

    @Test
    void wallAo_verticesAtEdgeThreshold_notDarkened() {
        // x=-0.4 is exactly at the edge threshold — should NOT be "near"
        Mesh mesh = createWallMesh(new float[] {
            -0.4f, 1.5f, 0f,  // at left threshold, above bottom
             0.4f, 1.5f, 0f,  // at right threshold, above bottom
        });

        Geometry geom = new Geometry("wall", mesh);
        AmbientOcclusionUtils.applyWallAO(geom, true, true);

        byte[] colors = readColorBytes(geom);

        // Both should be fully lit — threshold is exclusive
        assertColorEquals("left threshold", 1.0f, colors, 0);
        assertColorEquals("right threshold", 1.0f, colors, 1);
    }

    // ── Floor AO integration tests ─────────────────────────────────────────

    @Test
    void floorAo_allNeighborsOccupied_clampsAllCorners() {
        // Floor mesh: vertices at center and four corners
        // Tile-local coords: center at (0,0), corners at (±0.5, ±0.5)
        // With all 8 neighbors occupied
        Mesh mesh = createFloorMesh(new float[] {
            -0.5f, 0f, -0.5f,  // v0: NW corner
             0.5f, 0f, -0.5f,  // v1: NE corner
            -0.5f, 0f,  0.5f,  // v2: SW corner
             0.5f, 0f,  0.5f,  // v3: SE corner
             0.0f, 0f,  0.0f,  // v4: center
        });

        Geometry geom = new Geometry("floor", mesh);
        AmbientOcclusionUtils.applyFloorAO(geom,
                true, true, true, true,  // N, NE, E, SE
                true, true, true, true); // S, SW, W, NW

        byte[] colors = readColorBytes(geom);

        // All corners: 3 neighbors occupied → occlusion=3, samples=3 → 1-3/3=0 → MIN_AO=0.25
        assertColorEquals("NW corner all occupied", 0.25f, colors, 0);
        assertColorEquals("NE corner all occupied", 0.25f, colors, 1);
        assertColorEquals("SW corner all occupied", 0.25f, colors, 2);
        assertColorEquals("SE corner all occupied", 0.25f, colors, 3);
        // Center always fully lit
        assertColorEquals("center", 1.0f, colors, 4);
    }

    @Test
    void floorAo_onlyNorthOccupied_asymmetricDarkening() {
        // Floor mesh with corners, edges, and center.
        // Only the north neighbor is SOLID.
        Mesh mesh = createFloorMesh(new float[] {
            -0.5f, 0f, -0.5f,  // v0: NW corner → nearNorth + nearWest (samples 3)
             0.5f, 0f, -0.5f,  // v1: NE corner → nearNorth + nearEast (samples 3)
            -0.5f, 0f,  0.5f,  // v2: SW corner → nearSouth + nearWest (samples 3)
             0.5f, 0f,  0.5f,  // v3: SE corner → nearSouth + nearEast (samples 3)
             0.0f, 0f, -0.5f,  // v4: north edge center → nearNorth only (samples 1)
             0.0f, 0f,  0.5f,  // v5: south edge center → nearSouth only (samples 1)
             0.0f, 0f,  0.0f,  // v6: center → interior
        });

        Geometry geom = new Geometry("floor", mesh);
        AmbientOcclusionUtils.applyFloorAO(geom,
                true,  false, false, false,  // N, NE, E, SE — only N true
                false, false, false, false); // S, SW, W, NW

        byte[] colors = readColorBytes(geom);

        // v0 NW corner: N=true, W=false, NW=false → occlusion=1, samples=3 → 1-1/3 = 0.667
        assertColorEquals("v0 NW corner", 0.667f, colors, 0);
        // v1 NE corner: N=true, E=false, NE=false → occlusion=1, samples=3 → 0.667
        assertColorEquals("v1 NE corner", 0.667f, colors, 1);
        // v2 SW corner: all false → occlusion=0, samples=3 → 1.0
        assertColorEquals("v2 SW corner (unoccupied)", 1.0f, colors, 2);
        // v3 SE corner: all false → occlusion=0, samples=3 → 1.0
        assertColorEquals("v3 SE corner (unoccupied)", 1.0f, colors, 3);
        // v4 north edge center: N=true → occlusion=1, samples=1 → 0 → MIN_AO
        assertColorEquals("v4 north edge center", 0.25f, colors, 4);
        // v5 south edge center: S=false → occlusion=0, samples=1 → 1.0
        assertColorEquals("v5 south edge center (unoccupied)", 1.0f, colors, 5);
        // v6 center: interior → 1.0
        assertColorEquals("v6 center", 1.0f, colors, 6);
    }

    @Test
    void floorAo_noNeighborsOccupied_allFullyLit() {
        Mesh mesh = createFloorMesh(new float[] {
            -0.5f, 0f, -0.5f,
             0.5f, 0f, -0.5f,
             0.0f, 0f,  0.0f,
        });

        Geometry geom = new Geometry("floor", mesh);
        AmbientOcclusionUtils.applyFloorAO(geom,
                false, false, false, false,
                false, false, false, false);

        byte[] colors = readColorBytes(geom);

        assertColorEquals("NW corner no neighbors", 1.0f, colors, 0);
        assertColorEquals("NE corner no neighbors", 1.0f, colors, 1);
        assertColorEquals("center no neighbors", 1.0f, colors, 2);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Creates a wall Mesh with the given vertex positions.
     * The positions array is {@code [x0,y0,z0, x1,y1,z1, ...]}.
     */
    private static Mesh createWallMesh(float[] positions) {
        Mesh mesh = new Mesh();
        mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        return mesh;
    }

    /**
     * Creates a floor Mesh with the given vertex positions.
     * Same as wall but with distinct naming for clarity.
     */
    private static Mesh createFloorMesh(float[] positions) {
        return createWallMesh(positions);
    }

    /**
     * Reads the 3-component unsigned-byte color buffer from the geometry's
     * mesh as individual R channel values (all 3 components should be equal
     * for grayscale AO).
     *
     * @return array of AO brightness values (0-255) for each vertex
     */
    private static byte[] readColorBytes(Geometry geom) {
        VertexBuffer colBuf = geom.getMesh().getBuffer(Type.Color);
        assertNotNull(colBuf, "Mesh should have a Color buffer after AO application");
        assertEquals(3, colBuf.getNumComponents(), "Color buffer should be RGB (3 components)");

        ByteBuffer data = (ByteBuffer) colBuf.getData();
        data.rewind();
        int vertexCount = geom.getMesh().getVertexCount();
        byte[] result = new byte[vertexCount * 3];
        data.get(result);
        return result;
    }

    /**
     * Asserts that the R-channel at vertex index {@code vi} has the expected
     * AO brightness. All three (RGB) channels should be identical.
     */
    private static void assertColorEquals(String label, float expectedAo,
            byte[] colors, int vi) {
        int i = vi * 3;
        int expectedByte = (int) (expectedAo * 255.0f);

        // Allow ±1 tolerance for float→byte rounding
        int diffR = Math.abs((colors[i] & 0xFF) - expectedByte);
        int diffG = Math.abs((colors[i + 1] & 0xFF) - expectedByte);
        int diffB = Math.abs((colors[i + 2] & 0xFF) - expectedByte);

        assertTrue(diffR <= 1,
            () -> String.format("%s: expected R=%d (%.2f AO), got %d",
                    label, expectedByte, expectedAo, colors[i] & 0xFF));
        assertTrue(diffG <= 1,
            () -> String.format("%s: expected G=%d (%.2f AO), got %d",
                    label, expectedByte, expectedAo, colors[i + 1] & 0xFF));
        assertTrue(diffB <= 1,
            () -> String.format("%s: expected B=%d (%.2f AO), got %d",
                    label, expectedByte, expectedAo, colors[i + 2] & 0xFF));
    }
}
