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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-math tests for {@link GeometryProcessor} per-vertex occlusion.
 * <p>
 * Key constants are defined in {@link GeometryProcessor}; this test reads
 * {@code NOISE_AMPLITUDE}, {@code MIN_AO}, {@code EDGE_THRESHOLD} and
 * {@code BOTTOM_RANGE} directly from the source (see the constants below)
 * instead of duplicating their values. For reference:
 * <ul>
 *   <li>{@code AO_STRENGTH = 1.0}</li>
 *   <li>{@code EDGE_THRESHOLD} → effective edge threshold = {@code 0.5 * EDGE_THRESHOLD}
 *       (a vertex is "near" an edge when {@code |coord| > 0.4})</li>
 *   <li>{@code MIN_AO} → max occlusion {@code 1 - MIN_AO}
 *       (the {@code MAX_OCCLUSION} constant below)</li>
 *   <li>{@code DEPTH_SCALE = 1 / NOISE_AMPLITUDE / 2}</li>
 *   <li>{@code BOTTOM_RANGE} (wall bottom darkening fade-out range)</li>
 * </ul>
 * <p>
 * Unlike the previous AO implementation, the processor returns
 * <em>occlusion</em> ({@code 0} = fully lit, up to {@code MAX_OCCLUSION} = darkest),
 * not an AO factor. Floor occlusion is currently driven by depth below the
 * surface; inter-tile neighbour occlusion is disabled in the source
 * ({@code edgeOcclusion = 0; // TODO: remove}).
 */
class GeometryProcessorTest {

    private static final float DELTA = 0.0001f;

    // Source constants under test — referenced (not duplicated) from
    // GeometryProcessor so a tuning change can't silently desync this test.
    private static final float NOISE_AMPLITUDE = GeometryProcessor.NOISE_AMPLITUDE;
    private static final float MAX_OCCLUSION = 1.0f - GeometryProcessor.MIN_AO; // mirrors computeFloorAO/computeWallAO
    private static final float EDGE_THRESHOLD = GeometryProcessor.EDGE_THRESHOLD;
    private static final float BOTTOM_RANGE = GeometryProcessor.BOTTOM_RANGE;

    // ── computeFloorAO ───────────────────────────────────────────────────────
    // Signature: computeFloorAO(x, z, dy, maxY, N, NE, E, SE, S, SW, W, NW)
    // depth = maxY - dy; depthOcclusion = min(MAX_OCCLUSION, (depth + NOISE_AMPLITUDE) * (1 / NOISE_AMPLITUDE / 2))

    @Test
    void floorVertexAtSurface_halfOccluded() {
        // dy == maxY → depth = 0 → (0 + NOISE_AMPLITUDE) * 6.6667 = 0.5
        assertEquals(0.5f, GeometryProcessor.computeFloorAO(
                0f, 0f, 0f, 0f,
                true, true, true, true, true, true, true, true), DELTA);
    }

    @Test
    void floorVertexRaisedByMaxNoise_fullyLit() {
        // dy = maxY + NOISE_AMPLITUDE → depth = -NOISE_AMPLITUDE → (NOISE_AMPLITUDE - NOISE_AMPLITUDE) * 6.6667 = 0
        assertEquals(0f, GeometryProcessor.computeFloorAO(
                0f, 0f, NOISE_AMPLITUDE, 0f,
                true, true, true, true, true, true, true, true), DELTA);
    }

    @Test
    void floorVertexDepressedByMaxNoise_darkest() {
        // dy = maxY - NOISE_AMPLITUDE → depth = NOISE_AMPLITUDE → (NOISE_AMPLITUDE + NOISE_AMPLITUDE) * 6.6667 = 1 → clamped to MAX_OCCLUSION
        assertEquals(MAX_OCCLUSION, GeometryProcessor.computeFloorAO(
                0f, 0f, -NOISE_AMPLITUDE, 0f,
                true, true, true, true, true, true, true, true), DELTA);
    }

    @Test
    void floorVertexDeeplyRecessed_clampedToMaxOcclusion() {
        // dy = maxY - 1 → depth = 1 → way past MAX_OCCLUSION
        assertEquals(MAX_OCCLUSION, GeometryProcessor.computeFloorAO(
                0f, 0f, -1f, 0f,
                true, true, true, true, true, true, true, true), DELTA);
    }

    @Test
    void floorOcclusionGrowsMonotonicallyWithDepth() {
        float shallow = GeometryProcessor.computeFloorAO(
                0f, 0f, -0.01f, 0f,
                false, false, false, false, false, false, false, false);
        float deeper = GeometryProcessor.computeFloorAO(
                0f, 0f, -0.03f, 0f,
                false, false, false, false, false, false, false, false);
        assertEquals(0.5667f, shallow, 0.001f); // (0.01 + NOISE_AMPLITUDE) * 6.6667
        assertEquals(0.7f, deeper, 0.001f);     // (0.03 + NOISE_AMPLITUDE) * 6.6667
    }

    @Test
    void floor_neighborMasking_currentlyInert() {
        // Inter-tile edge occlusion is zeroed out in the source (TODO: remove).
        // Both masks must currently produce the same depth-only result.
        float allSolid = GeometryProcessor.computeFloorAO(
                0f, 0f, 0f, 0f,
                true, true, true, true, true, true, true, true);
        float noneSolid = GeometryProcessor.computeFloorAO(
                0f, 0f, 0f, 0f,
                false, false, false, false, false, false, false, false);
        assertEquals(allSolid, noneSolid, DELTA);
    }

    // ── computeWallAO ───────────────────────────────────────────────────────
    // Signature: computeWallAO(worldX, dy, dz, minY, maxZ, isCornerStart, isCornerEnd)
    // bottomRatio = clamp((dy - minY) / BOTTOM_RANGE); recess = max(0, maxZ - dz)

    private static final float WALL_MIN_Y = 0f;
    private static final float WALL_MAX_Z = 0f;

    // A vertex is "near" an edge when |coord| > halfTile - halfTile * EDGE_THRESHOLD,
    // i.e. past 0.5 - 0.5 * EDGE_THRESHOLD. NEAR_EDGE (0.41) sits just past that
    // boundary; EDGE_BOUNDARY (0.4) is exactly on it (the source uses <, not <=).
    private static final float EDGE_BOUNDARY = 0.5f - 0.5f * EDGE_THRESHOLD;
    private static final float NEAR_EDGE = EDGE_BOUNDARY + 0.01f;

    /** dy high enough that the bottom darkening has fully faded out. */
    private static float wallTopDy() {
        return WALL_MIN_Y + 0.5f;
    }

    @Test
    void wallInterior_fullyLit() {
        // x=0 (no corner), well above bottom, at the front face
        assertEquals(0f, GeometryProcessor.computeWallAO(
                0f, wallTopDy(), WALL_MAX_Z, WALL_MIN_Y, WALL_MAX_Z,
                true, true), DELTA);
    }

    @Test
    void wallBottom_darkest() {
        // dy == minY → bottomRatio = 0 → bottom occlusion = 1 → clamped to MAX_OCCLUSION
        assertEquals(MAX_OCCLUSION, GeometryProcessor.computeWallAO(
                0f, WALL_MIN_Y, WALL_MAX_Z, WALL_MIN_Y, WALL_MAX_Z,
                false, false), DELTA);
    }

    @Test
    void wallLeftCorner_darkest() {
        // x=-NEAR_EDGE → near left edge, isCornerStart=true → edge occlusion 1 → clamped to MAX_OCCLUSION
        assertEquals(MAX_OCCLUSION, GeometryProcessor.computeWallAO(
                -NEAR_EDGE, wallTopDy(), WALL_MAX_Z, WALL_MIN_Y, WALL_MAX_Z,
                true, false), DELTA);
    }

    @Test
    void wallLeftEdge_withoutCorner_fullyLit() {
        assertEquals(0f, GeometryProcessor.computeWallAO(
                -NEAR_EDGE, wallTopDy(), WALL_MAX_Z, WALL_MIN_Y, WALL_MAX_Z,
                false, false), DELTA);
    }

    @Test
    void wallRightCorner_darkest() {
        assertEquals(MAX_OCCLUSION, GeometryProcessor.computeWallAO(
                NEAR_EDGE, wallTopDy(), WALL_MAX_Z, WALL_MIN_Y, WALL_MAX_Z,
                false, true), DELTA);
    }

    @Test
    void wallBottomAndLeftCorner_clamped() {
        // bottom (1) + corner (1) → combined 1 → clamped to MAX_OCCLUSION
        assertEquals(MAX_OCCLUSION, GeometryProcessor.computeWallAO(
                -NEAR_EDGE, WALL_MIN_Y, WALL_MAX_Z, WALL_MIN_Y, WALL_MAX_Z,
                true, false), DELTA);
    }

    @Test
    void wallBottomWithNoCorners_darkest() {
        // bottom alone already saturates: (1 - (1-0)*(1-1)*(1-0)) = 1 → MAX_OCCLUSION
        assertEquals(MAX_OCCLUSION, GeometryProcessor.computeWallAO(
                -NEAR_EDGE, WALL_MIN_Y, WALL_MAX_Z, WALL_MIN_Y, WALL_MAX_Z,
                false, false), DELTA);
    }

    @Test
    void wallBottomWithRightCorner_clamped() {
        assertEquals(MAX_OCCLUSION, GeometryProcessor.computeWallAO(
                NEAR_EDGE, WALL_MIN_Y, WALL_MAX_Z, WALL_MIN_Y, WALL_MAX_Z,
                false, true), DELTA);
    }

    @Test
    void wallBottomWithLeftCorner_clamped() {
        // x=-NEAR_EDGE is only near the left edge, so isCornerEnd never contributes
        assertEquals(MAX_OCCLUSION, GeometryProcessor.computeWallAO(
                -NEAR_EDGE, WALL_MIN_Y, WALL_MAX_Z, WALL_MIN_Y, WALL_MAX_Z,
                true, true), DELTA);
    }

    @Test
    void wallAtLeftThreshold_notTriggered() {
        // x=-EDGE_BOUNDARY is exactly at the threshold, NOT "near" (condition is <, not <=)
        assertEquals(0f, GeometryProcessor.computeWallAO(
                -EDGE_BOUNDARY, wallTopDy(), WALL_MAX_Z, WALL_MIN_Y, WALL_MAX_Z,
                true, false), DELTA);
    }

    @Test
    void wallBottomPartial_fadesLinearly() {
        // dy = minY + BOTTOM_RANGE / 2 → bottomRatio = 0.5 → bottom occlusion = 0.5
        assertEquals(0.5f, GeometryProcessor.computeWallAO(
                0f, WALL_MIN_Y + BOTTOM_RANGE * 0.5f, WALL_MAX_Z, WALL_MIN_Y, WALL_MAX_Z,
                false, false), DELTA);
    }

    @Test
    void wallRecessed_partialOcclusion() {
        // dz = maxZ - NOISE_AMPLITUDE → recess = NOISE_AMPLITUDE → recess occlusion = 0.5
        assertEquals(0.5f, GeometryProcessor.computeWallAO(
                0f, wallTopDy(), WALL_MAX_Z - NOISE_AMPLITUDE, WALL_MIN_Y, WALL_MAX_Z,
                false, false), DELTA);
    }

    @Test
    void wallDeeplyRecessed_clamped() {
        // dz far behind the front face → recess occlusion saturates at MAX_OCCLUSION
        assertEquals(MAX_OCCLUSION, GeometryProcessor.computeWallAO(
                0f, wallTopDy(), WALL_MAX_Z - 0.2f, WALL_MIN_Y, WALL_MAX_Z,
                false, false), DELTA);
    }
}
