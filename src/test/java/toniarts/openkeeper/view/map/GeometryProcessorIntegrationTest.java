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
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying that {@link GeometryProcessor} writes per-vertex
 * AO colors into the geometry's color buffer.
 *
 * <p>These tests create synthetic meshes (no OpenGL required), run the
 * noise+AO processing, and read back the vertex color buffers. Because the
 * processor <em>also displaces vertices with deterministic noise</em>, exact
 * byte values are not asserted — instead we assert robust qualitative
 * properties (corners darker than interiors, bottom rows darker than top
 * rows) with margins that hold regardless of the noise displacement.
 *
 * <p>Color buffer convention: value = {@code (1 - occlusion) * 255}, i.e.
 * {@code 255} = fully lit, {@code 63} = darkest (occlusion clamped at
 * {@code MAX_OCCLUSION}).
 *
 * <p>Wall model convention (see {@link toniarts.openkeeper.utils.WorldUtils}):
 * <ul>
 *   <li>Local Y = height above floor; {@code FLOOR_HEIGHT = 1.0},
 *       {@code TILE_HEIGHT = 1.0}</li>
 *   <li>Wall vertices span Y ≈ 1.0 (bottom) to 1.5 (top)</li>
 *   <li>Local X = horizontal along the wall, ±0.5 are the edges</li>
 * </ul>
 */
class GeometryProcessorIntegrationTest {

    // -- Source constants under test --------------------------------------------
    // Referenced (not duplicated) from GeometryProcessor so a tuning change can't
    // silently desync the derived thresholds below.
    private static final float MAX_OCCLUSION = 1.0f - GeometryProcessor.MIN_AO; // mirrors computeFloorAO/computeWallAO
    private static final float NOISE_AMPLITUDE = GeometryProcessor.NOISE_AMPLITUDE;
    private static final float BOTTOM_RANGE = GeometryProcessor.BOTTOM_RANGE;

    // -- Brightness thresholds -------------------------------------------------
    // Darkest byte the processor writes: occlusion clamps at MAX_OCCLUSION
    // (= 1 - MIN_AO = 0.75) → (1 - 0.75) * 255 = 63.75 → byte 63. The small
    // margins below absorb noise displacement and byte rounding.
    private static final int DARKEST_BYTE = (int) ((1.0f - MAX_OCCLUSION) * 255f);
    // Corner/bottom vertices (occlusion = MAX_OCCLUSION) → darkest byte + 7.
    private static final int DARK_MAX = DARKEST_BYTE + 7;
    // Interior top vertices: only recess can darken (≤ 0.5, an identity of
    // NOISE_AMPLITUDE × DEPTH_SCALE in the source) → brightness ≥ ~127, minus
    // 17 of noise margin. Kept literal: the 0.5 bound can't drift.
    private static final int LIT_MIN = 110;
    // Deep recess below the surface (depth occlusion saturated) → darkest byte + 1.
    private static final int DEEP_RECESS_MAX = DARKEST_BYTE + 1;
    // Bottom row: noise lifts the bottom vertex by up to NOISE_AMPLITUDE →
    // bottomRatio ≤ NOISE_AMPLITUDE / BOTTOM_RANGE = 0.5 → brightness ≤ 127.5
    // → byte 127, +3 of noise margin.
    private static final int BOTTOM_ROW_MAX = (int) ((1.0f - NOISE_AMPLITUDE / BOTTOM_RANGE) * 255f) + 3;

    // ── Wall AO integration tests ──────────────────────────────────────────

    @Test
    void wallAo_bothCorners_darkensCornerVertices() {
        Mesh mesh = createMesh(new float[] {
            -0.5f, 1.0f, 0f,  // v0: bottom-left
             0.5f, 1.0f, 0f,  // v1: bottom-right
            -0.5f, 1.5f, 0f,  // v2: top-left
             0.5f, 1.5f, 0f,  // v3: top-right
             0.0f, 1.0f, 0f,  // v4: bottom-center
             0.0f, 1.5f, 0f,  // v5: top-center (interior)
        });

        Geometry geom = new Geometry("wall", mesh);
        GeometryProcessor.applyWallNoiseAndAO(geom, true, true);

        byte[] colors = readColorBytes(geom);

        // Corners (both ends flagged) are always max-dark, regardless of noise
        assertColorInRange("v0 bottom-left corner", colors, 0, 0, DARK_MAX);
        assertColorInRange("v1 bottom-right corner", colors, 1, 0, DARK_MAX);
        assertColorInRange("v2 top-left corner", colors, 2, 0, DARK_MAX);
        assertColorInRange("v3 top-right corner", colors, 3, 0, DARK_MAX);

        // Interior top-center is never corner-darkened (occlusion ≤ 0.5)
        assertColorInRange("v5 top-center interior", colors, 5, LIT_MIN, 255);

        // Corners strictly darker than the interior
        assertBrightnessLessThan("v2 corner darker than v5 interior", colors, 2, 5);
    }

    @Test
    void wallAo_noCorners_onlyBottomDarkened() {
        Mesh mesh = createMesh(new float[] {
            -0.5f, 1.0f, 0f,  // v0: bottom-left
             0.5f, 1.0f, 0f,  // v1: bottom-right
            -0.5f, 1.5f, 0f,  // v2: top-left
             0.5f, 1.5f, 0f,  // v3: top-right
             0.0f, 1.0f, 0f,  // v4: bottom-center
        });

        Geometry geom = new Geometry("wall", mesh);
        GeometryProcessor.applyWallNoiseAndAO(geom, false, false);

        byte[] colors = readColorBytes(geom);

        // Bottom row: bottom darkening guarantees occlusion ≥ 0.5 → ≤ 127
        assertColorInRange("v4 bottom-center", colors, 4, 0, BOTTOM_ROW_MAX);
        // Top row without corners: only recess can darken (≤ 0.5) → stays bright
        assertColorInRange("v2 top-left (no corner)", colors, 2, LIT_MIN, 255);
        assertColorInRange("v3 top-right (no corner)", colors, 3, LIT_MIN, 255);
        // Bottom is never brighter than the same-x top vertex
        assertBrightnessLessThanOrEqual("v4 bottom ≤ v2 top", colors, 4, 2);
    }

    @Test
    void wallAo_leftCornerOnly_asymmetricDarkening() {
        Mesh mesh = createMesh(new float[] {
            -0.5f, 1.0f, 0f,  // v0: bottom-left
             0.5f, 1.0f, 0f,  // v1: bottom-right
            -0.5f, 1.5f, 0f,  // v2: top-left
             0.5f, 1.5f, 0f,  // v3: top-right
        });

        Geometry geom = new Geometry("wall", mesh);
        GeometryProcessor.applyWallNoiseAndAO(geom, true, false);

        byte[] colors = readColorBytes(geom);

        // Left end corner-darkened, right end stays lit
        assertColorInRange("v2 top-left (left corner)", colors, 2, 0, DARK_MAX);
        assertColorInRange("v3 top-right (no corner)", colors, 3, LIT_MIN, 255);
        assertBrightnessLessThan("v2 corner darker than v3", colors, 2, 3);
    }

    @Test
    void wallAo_userDataCornerFlags_driveDarkening() {
        // The node entry point reads the WALL_CORNER_* user data from each
        // child (as MapViewController sets them).
        // Each child must span bottom-to-top rows so the top vertex is above
        // the bottom darkening range (minY is scanned from the mesh itself).
        Node root = new Node("wall-node");

        Geometry left = new Geometry("left", createMesh(new float[] {
            -0.5f, 1.0f, 0f,  // v0: bottom-left
            -0.5f, 1.5f, 0f,  // v1: top-left
        }));
        // Keys are read from GeometryProcessor's constants so a rename in the
        // source propagates here automatically (MapViewController writes the
        // same keys).
        left.setUserData(GeometryProcessor.WALL_CORNER_START_KEY, true);
        left.setUserData(GeometryProcessor.WALL_CORNER_END_KEY, false);

        Geometry right = new Geometry("right", createMesh(new float[] {
             0.5f, 1.0f, 0f,  // v0: bottom-right
             0.5f, 1.5f, 0f,  // v1: top-right
        }));

        root.attachChild(left);
        root.attachChild(right);

        GeometryProcessor.applyWallNoiseAndAO(root);

        assertColorInRange("left child top-left corner", readColorBytes(left), 1, 0, DARK_MAX);
        assertColorInRange("right child top-right no corner", readColorBytes(right), 1, LIT_MIN, 255);
    }

    // ── Surface layer AO integration tests (FLOOR / WATER / SOLID_TOP) ─────

    @ParameterizedTest(name = "surfaceLayerAo_writesColorBuffer_deepRecessIsMaxDark [{0}]")
    @MethodSource("surfaceLayerArguments")
    void surfaceLayerAo_writesColorBuffer_deepRecessIsMaxDark(
            GeometryProcessor.SurfaceLayer layer, TileNeighborhood neighborhood) {
        // Surface mesh: surface at y=0, plus one vertex well below it.
        // maxY (undisplaced) = 0; the deep vertex has depth ≈ 1.0 which always
        // saturates the depth occlusion to 0.75 regardless of noise.
        // The neighbourhood is currently inert for all three layers (edge
        // occlusion is disabled in computeFloorAO and SOLID_TOP forces its
        // flags off), but each layer gets its representative neighbourhood.
        Mesh mesh = createMesh(surfaceWithDeepRecessVertices());

        Geometry geom = new Geometry(layer.name(), mesh);
        GeometryProcessor.applyFloorNoiseAndAO(geom, neighborhood, layer);

        byte[] colors = readColorBytes(geom);

        // Deep recess is always at max darkness
        assertColorInRange("v5 deep recess", colors, 5, 0, DEEP_RECESS_MAX);
        // The deep vertex is never brighter than a surface vertex (surface
        // spans [63, 255] depending on noise; deep is pinned at 63)
        assertBrightnessLessThanOrEqual("v5 deep ≤ v4 surface", colors, 5, 4);
    }

    private static Stream<Arguments> surfaceLayerArguments() {
        return Stream.of(
                // FLOOR: solid neighbours occlude (issue #479)
                Arguments.of(GeometryProcessor.SurfaceLayer.FLOOR, allSolidNeighborhood()),
                // WATER: contiguous body — same-terrain neighbours
                Arguments.of(GeometryProcessor.SurfaceLayer.WATER, contiguousWaterNeighborhood()),
                // SOLID_TOP: solid tiles; neighbour flags currently forced off
                Arguments.of(GeometryProcessor.SurfaceLayer.SOLID_TOP, allSolidNeighborhood()));
    }

    @ParameterizedTest(name = "surfaceLayer_neighborMasking_currentlyInert [{0}]")
    @MethodSource("inertNeighborMaskingArguments")
    void surfaceLayer_neighborMasking_currentlyInert(GeometryProcessor.SurfaceLayer layer,
            TileNeighborhood neighborhoodA, TileNeighborhood neighborhoodB, String message) {
        Geometry geomA = new Geometry("neighborhood-a", createMesh(surfaceWithDeepRecessVertices()));
        Geometry geomB = new Geometry("neighborhood-b", createMesh(surfaceWithDeepRecessVertices()));

        GeometryProcessor.applyFloorNoiseAndAO(geomA, neighborhoodA, layer);
        GeometryProcessor.applyFloorNoiseAndAO(geomB, neighborhoodB, layer);

        // Identical geometry + identical noise → identical depth occlusion;
        // the neighbour masks currently change nothing
        assertArrayEquals(readColorBytes(geomA), readColorBytes(geomB), message);
    }

    private static Stream<Arguments> inertNeighborMaskingArguments() {
        return Stream.of(
                // FLOOR resolves neighbour occlusion from the solid mask, but edge
                // occlusion is disabled in computeFloorAO (edgeOcclusion = 0;
                // TODO: remove) — once implemented, solid neighbours should darken
                // the floor edges and this test must be updated.
                Arguments.of(GeometryProcessor.SurfaceLayer.FLOOR,
                        allSolidNeighborhood(),
                        isolatedNeighborhood(),
                        "FLOOR solid-neighbour masking currently has no effect "
                        + "(edge occlusion disabled in computeFloorAO)"),
                // WATER must resolve neighbour occlusion from the same-terrain mask
                // (contiguous water body) rather than the solid mask. Edge occlusion
                // is disabled in computeFloorAO (edgeOcclusion = 0; TODO: remove) —
                // once implemented, the contiguous body should darken its edges and
                // this test must be updated.
                Arguments.of(GeometryProcessor.SurfaceLayer.WATER,
                        contiguousWaterNeighborhood(),
                        waterInSolidNeighborhood(),
                        "same-terrain neighbour masking currently has no effect "
                        + "(edge occlusion disabled in computeFloorAO)"),
                // SOLID_TOP resolves to all-false neighbour flags (see the if(true)
                // block in applyFloorNoiseAndAO — tops aren't occluded by neighbours
                // yet). When that TODO is implemented, tops should only darken at
                // material boundaries (a solid neighbour of a different terrain type)
                // and this test must be updated.
                Arguments.of(GeometryProcessor.SurfaceLayer.SOLID_TOP,
                        allSolidNeighborhood(),
                        isolatedNeighborhood(),
                        "SOLID_TOP neighbour flags currently have no effect "
                        + "(flags forced off + edge occlusion disabled)"));
    }

    // ── Noise displacement ──────────────────────────────────────────────────

    @Test
    void noiseDisplacement_movesVertices() {
        Mesh mesh = createMesh(new float[] {
            -0.5f, 0f, -0.5f,
             0.5f, 0f, -0.5f,
             0.0f, 0f,  0.0f,
        });
        float[] original = readPositions(mesh);

        Geometry geom = new Geometry("floor", mesh);
        GeometryProcessor.applyFloorNoiseAndAO(geom, allSolidNeighborhood(),
                GeometryProcessor.SurfaceLayer.FLOOR);

        float[] displaced = readPositions(geom.getMesh());

        // Deterministic grid noise displaces at least one vertex
        boolean anyMoved = false;
        for (int i = 0; i < original.length; i++) {
            if (Math.abs(original[i] - displaced[i]) > 1e-4f) {
                anyMoved = true;
                break;
            }
        }
        assertTrue(anyMoved, "Noise displacement should move at least one vertex");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static TileNeighborhood allSolidNeighborhood() {
        // All 8 neighbors solid and share the terrain (all mask bits set)
        return new TileNeighborhood((byte) 0xFF, (byte) 0xFF);
    }

    private static TileNeighborhood contiguousWaterNeighborhood() {
        // Water tile surrounded by water: all 8 neighbours share the terrain,
        // none are solid.
        return new TileNeighborhood((byte) 0xFF, (byte) 0x00);
    }

    private static TileNeighborhood isolatedNeighborhood() {
        // No same-terrain and no solid neighbours.
        return new TileNeighborhood((byte) 0x00, (byte) 0x00);
    }

    private static TileNeighborhood waterInSolidNeighborhood() {
        // No same-terrain neighbours, all neighbours are solid rock.
        return new TileNeighborhood((byte) 0x00, (byte) 0xFF);
    }

    /**
     * Tile-local surface at y=0 with one vertex well below it (deep recess).
     * maxY (undisplaced) = 0.
     */
    private static float[] surfaceWithDeepRecessVertices() {
        return new float[] {
            -0.5f, 0f, -0.5f,  // v0: NW corner (surface)
             0.5f, 0f, -0.5f,  // v1: NE corner (surface)
            -0.5f, 0f,  0.5f,  // v2: SW corner (surface)
             0.5f, 0f,  0.5f,  // v3: SE corner (surface)
             0.0f, 0f,  0.0f,  // v4: center (surface)
             0.0f, -1.0f, 0.0f, // v5: deep recess
        };
    }

    /**
     * Creates a Mesh with the given vertex positions.
     * The positions array is {@code [x0,y0,z0, x1,y1,z1, ...]}.
     */
    private static Mesh createMesh(float[] positions) {
        Mesh mesh = new Mesh();
        mesh.setBuffer(Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        return mesh;
    }

    /**
     * Reads the 3-component unsigned-byte color buffer from the geometry's
     * mesh as individual R/G/B channels (all three should be equal for
     * grayscale AO).
     */
    private static byte[] readColorBytes(Geometry geom) {
        VertexBuffer colBuf = geom.getMesh().getBuffer(Type.Color);
        assertNotNull(colBuf, "Mesh should have a Color buffer after AO application");
        assertEquals(3, colBuf.getNumComponents(), "Color buffer should be RGB (3 components)");
        assertTrue(colBuf.isNormalized(), "AO color buffer should be normalized");

        ByteBuffer data = (ByteBuffer) colBuf.getData();
        data.rewind();
        int vertexCount = geom.getMesh().getVertexCount();
        byte[] result = new byte[vertexCount * 3];
        data.get(result);
        return result;
    }

    private static float[] readPositions(Mesh mesh) {
        FloatBuffer data = (FloatBuffer) mesh.getBuffer(Type.Position).getData();
        data.rewind();
        float[] result = new float[data.remaining()];
        data.get(result);
        return result;
    }

    private static int brightness(byte[] colors, int vi) {
        return colors[vi * 3] & 0xFF;
    }

    /**
     * Asserts that all three RGB channels of vertex {@code vi} lie in
     * {@code [minBrightness, maxBrightness]}.
     */
    private static void assertColorInRange(String label, byte[] colors,
            int vi, int minBrightness, int maxBrightness) {
        int i = vi * 3;
        for (int c = 0; c < 3; c++) {
            int value = colors[i + c] & 0xFF;
            int channel = c; // effectively final for the lambda
            assertTrue(value >= minBrightness && value <= maxBrightness,
                    () -> String.format("%s: channel %d value %d outside [%d, %d]",
                            label, channel, value, minBrightness, maxBrightness));
        }
    }

    private static void assertBrightnessLessThan(String label, byte[] colors, int darkerVi, int brighterVi) {
        int darker = brightness(colors, darkerVi);
        int brighter = brightness(colors, brighterVi);
        assertTrue(darker < brighter,
                () -> String.format("%s: expected darker=%d < brighter=%d", label, darker, brighter));
    }

    private static void assertBrightnessLessThanOrEqual(String label, byte[] colors, int darkerVi, int brighterVi) {
        int darker = brightness(colors, darkerVi);
        int brighter = brightness(colors, brighterVi);
        assertTrue(darker <= brighter,
                () -> String.format("%s: expected darker=%d <= brighter=%d", label, darker, brighter));
    }
}
