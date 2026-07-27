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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link AmbientOcclusionUtils} per-vertex AO computation.
 * <p>
 * Key constants (from source):
 * <ul>
 *   <li>{@code AO_STRENGTH = 1.0}</li>
 *   <li>{@code EDGE_THRESHOLD = 0.2}</li>
 *   <li>{@code MIN_AO = 0.25}</li>
 *   <li>Effective edge threshold = {@code halfTile * EDGE_THRESHOLD = 0.5 * 0.2 = 0.1}</li>
 *   <li>"Near" an edge when {@code |x| > 0.4} or {@code |z| > 0.4} (floor), or {@code |x| > 0.4} (wall)</li>
 * </ul>
 */
class AmbientOcclusionUtilsTest {

    // --- computeFloorAO -------------------------------------------------------

    @Test
    void floorCenter_alwaysFullyLit() {
        // (0,0) is dead center — no edge proximity
        assertEquals(1.0f, AmbientOcclusionUtils.computeFloorAO(
                0f, 0f,
                true, true, true, true, true, true, true, true));
    }

    @Test
    void floorCenter_nearButWithinThreshold_fullyLit() {
        // x=-0.4 is at the threshold boundary (not "near" because condition is <, not <=)
        assertEquals(1.0f, AmbientOcclusionUtils.computeFloorAO(
                -0.4f, 0.4f,
                true, true, true, true, true, true, true, true));
    }

    @Test
    void floorNorthEdge_withOccupiedNeighbor_clampedToMinAo() {
        // z=-0.41 → nearNorth, N=true → occlusion=1, sampleCount=1 → 1-1/1=0 → clamped to MIN_AO
        assertEquals(0.25f, AmbientOcclusionUtils.computeFloorAO(
                0f, -0.41f,
                true, false, false, false, false, false, false, false),
                0.0001f);
    }

    @Test
    void floorNorthEdge_withUnoccupiedNeighbor_fullyLit() {
        // z=-0.41 → nearNorth, N=false → occlusion=0, sampleCount=1 → 1-0/1=1
        assertEquals(1.0f, AmbientOcclusionUtils.computeFloorAO(
                0f, -0.41f,
                false, false, false, false, false, false, false, false),
                0.0001f);
    }

    @Test
    void floorSouthEdge_withOccupiedNeighbor_clampedToMinAo() {
        // z=0.41 → nearSouth, S=true
        assertEquals(0.25f, AmbientOcclusionUtils.computeFloorAO(
                0f, 0.41f,
                false, false, false, false, true, false, false, false),
                0.0001f);
    }

    @Test
    void floorWestEdge_withOccupiedNeighbor_clampedToMinAo() {
        // x=-0.41 → nearWest, W=true
        assertEquals(0.25f, AmbientOcclusionUtils.computeFloorAO(
                -0.41f, 0f,
                false, false, false, false, false, false, true, false),
                0.0001f);
    }

    @Test
    void floorEastEdge_withUnoccupiedNeighbor_fullyLit() {
        // x=0.41 → nearEast, E=false
        assertEquals(1.0f, AmbientOcclusionUtils.computeFloorAO(
                0.41f, 0f,
                false, false, false, false, false, false, false, false),
                0.0001f);
    }

    @Test
    void floorNWCorner_allThreeOccupied_clampedToMinAo() {
        // x=-0.41, z=-0.41 → nearNorth+nearWest, N=W=NW=true → occlusion=3, sampleCount=3 → clamped
        assertEquals(0.25f, AmbientOcclusionUtils.computeFloorAO(
                -0.41f, -0.41f,
                true, false, false, false, false, false, true, true),
                0.0001f);
    }

    @Test
    void floorNWCorner_oneOccupied_partialDarkening() {
        // N=true, W=false, NW=false → occlusion=1, sampleCount=3 → 1-1/3 = 2/3
        assertEquals(2.0f / 3.0f, AmbientOcclusionUtils.computeFloorAO(
                -0.41f, -0.41f,
                true, false, false, false, false, false, false, false),
                0.0001f);
    }

    @Test
    void floorNWCorner_noneOccupied_fullyLit() {
        // all false → occlusion=0, sampleCount=3 → 1-0/3 = 1
        assertEquals(1.0f, AmbientOcclusionUtils.computeFloorAO(
                -0.41f, -0.41f,
                false, false, false, false, false, false, false, false),
                0.0001f);
    }

    @Test
    void floorNECorner_partialDarkening() {
        // N=false, E=true, NE=true → occlusion=2, sampleCount=3 → 1-2/3 = 1/3 (above MIN_AO)
        assertEquals(1.0f / 3.0f, AmbientOcclusionUtils.computeFloorAO(
                0.41f, -0.41f,
                false, true, true, false, false, false, false, false),
                0.0001f);
    }

    @Test
    void floorSECorner_threeOccupied_clamped() {
        // S=true, E=true, SE=true → occlusion=3, sampleCount=3 → clamped
        assertEquals(0.25f, AmbientOcclusionUtils.computeFloorAO(
                0.41f, 0.41f,
                false, false, true, true, true, false, false, false),
                0.0001f);
    }

    @Test
    void floorSWCorner_threeOccupied_clamped() {
        assertEquals(0.25f, AmbientOcclusionUtils.computeFloorAO(
                -0.41f, 0.41f,
                false, false, false, false, true, true, true, false),
                0.0001f);
    }

    @Test
    void floorMinAoClamping_appliedWhenAvgBelowThreshold() {
        // North edge, N=true → 1-1/1=0 → MIN_AO=0.25
        float ao = AmbientOcclusionUtils.computeFloorAO(
                0f, -0.41f,
                true, false, false, false, false, false, false, false);
        assertEquals(0.25f, ao, 0.0001f);
    }

    @Test
    void floorEdgeDoesNotOvershadowCorner() {
        // z=-0.41 (nearNorth) with N=true but x also -0.2 (not nearWest)
        // Should be treated as edge (sampleCount=1), not corner
        assertEquals(0.25f, AmbientOcclusionUtils.computeFloorAO(
                -0.2f, -0.41f,
                true, false, false, false, false, false, true, false),
                0.0001f);
    }

    // --- computeWallAO --------------------------------------------------------

    private static final float BOTTOM = 10.0f; // arbitrary bottomThreshold for testing

    @Test
    void wallInterior_fullyLit() {
        // x=0 is center, y=20 is well above bottom
        assertEquals(1.0f, AmbientOcclusionUtils.computeWallAO(
                0f, 20f, BOTTOM,
                true, true),
                0.0001f);
    }

    @Test
    void wallBottomOnly_clampedToMinAo() {
        // y=BOTTOM → nearBottom, occlusion=1, sampleCount=1 → 0 → MIN_AO
        assertEquals(0.25f, AmbientOcclusionUtils.computeWallAO(
                0f, BOTTOM, BOTTOM,
                false, false),
                0.0001f);
    }

    @Test
    void wallLeftCorner_partialDarkening() {
        // x=-0.41 → nearLeft, isLeftCorner=true → occlusion=0.5, sampleCount=1 → 1-0.5/1 = 0.5
        assertEquals(0.5f, AmbientOcclusionUtils.computeWallAO(
                -0.41f, BOTTOM + 1f, BOTTOM,
                true, false),
                0.0001f);
    }

    @Test
    void wallLeftEdge_withoutCorner_fullyLit() {
        // x=-0.41 → nearLeft, isLeftCorner=false → occlusion=0, sampleCount=1 → 1
        assertEquals(1.0f, AmbientOcclusionUtils.computeWallAO(
                -0.41f, BOTTOM + 1f, BOTTOM,
                false, false),
                0.0001f);
    }

    @Test
    void wallRightCorner_partialDarkening() {
        // x=0.41 → nearRight, isRightCorner=true → occlusion=0.5, sampleCount=1 → 1-0.5/1 = 0.5
        assertEquals(0.5f, AmbientOcclusionUtils.computeWallAO(
                0.41f, BOTTOM + 1f, BOTTOM,
                false, true),
                0.0001f);
    }

    @Test
    void wallBottomAndLeft_withLeftCorner_clampedToMinAo() {
        // nearBottom + nearLeft, isLeftCorner: occlusion=1+0.5=1.5, sampleCount=2
        // 1-1.5/2 = 1-0.75 = 0.25 → MIN_AO
        assertEquals(0.25f, AmbientOcclusionUtils.computeWallAO(
                -0.41f, BOTTOM, BOTTOM,
                true, false),
                0.0001f);
    }

    @Test
    void wallBottomAndLeft_withNoCorners_partialDarkening() {
        // nearBottom + nearLeft, no corners: occlusion=1+0=1, sampleCount=2 → 1-1/2=0.5
        assertEquals(0.5f, AmbientOcclusionUtils.computeWallAO(
                -0.41f, BOTTOM, BOTTOM,
                false, false),
                0.0001f);
    }

    @Test
    void wallBottomAndRight_withRightCorner_clampedToMinAo() {
        assertEquals(0.25f, AmbientOcclusionUtils.computeWallAO(
                0.41f, BOTTOM, BOTTOM,
                false, true),
                0.0001f);
    }

    @Test
    void wallBottomLeft_withBothCorners_clampedToMinAo() {
        // nearBottom + nearLeft (nearRight is false since x=-0.41), isLeftCorner → occlusion=1+0.5=1.5, sampleCount=2 → 0.25
        assertEquals(0.25f, AmbientOcclusionUtils.computeWallAO(
                -0.41f, BOTTOM, BOTTOM,
                true, true),
                0.0001f);
    }

    @Test
    void wallAtLeftThreshold_notTriggered() {
        // x=-0.4 is exactly at threshold, NOT "near" (condition is <, not <=)
        assertEquals(1.0f, AmbientOcclusionUtils.computeWallAO(
                -0.4f, BOTTOM + 1f, BOTTOM,
                true, false),
                0.0001f);
    }

    @Test
    void wallBottomThreshold_inclusive() {
        // y == bottomThreshold, condition is <= so this IS nearBottom
        assertEquals(0.25f, AmbientOcclusionUtils.computeWallAO(
                0f, BOTTOM, BOTTOM,
                false, false),
                0.0001f);
    }

    // --- Parameterized boundary tests -----------------------------------------

    @ParameterizedTest
    @CsvSource({
        // Floor: 4 cardinal edges with occupied neighbor → all clamp to MIN_AO
        "floor,  0,   -0.41,  N,   0.25",
        "floor,  0,    0.41,  S,   0.25",
        "floor, -0.41, 0,     W,   0.25",
        "floor,  0.41, 0,     E,   0.25",
        // Floor: 4 edges with unoccupied neighbor → fully lit
        "floor,  0,   -0.41,  none, 1.0",
        "floor,  0,    0.41,  none, 1.0",
        "floor, -0.41, 0,     none, 1.0",
        "floor,  0.41, 0,     none, 1.0",
        // Floor: corners with all 3 occupied → clamp
        "floor, -0.41, -0.41,  NW,  0.25",
        "floor,  0.41, -0.41,  NE,  0.25",
        "floor,  0.41,  0.41,  SE,  0.25",
        "floor, -0.41,  0.41,  SW,  0.25",
        // Wall: bottom only, no corner flags
        "wall,   0,     10.0,  bottom,          0.25",
        // Wall: bottom+left with corner → clamp
        "wall,  -0.41,  10.0,  bottom+left+corner, 0.25",
        // Wall: bottom+left with no corner → half dark
        "wall,  -0.41,  10.0,  bottom+left+plain,0.5",
    })
    void parameterizedAo(String type, float x, float zy, String scenario, float expected) {
        float ao;
        if ("floor".equals(type)) {
            boolean N  = scenario.contains("N") || scenario.contains("NW") || scenario.contains("NE");
            boolean NE = scenario.contains("NE");
            boolean E  = scenario.contains("E") || scenario.contains("NE") || scenario.contains("SE");
            boolean SE = scenario.contains("SE");
            boolean S  = scenario.contains("S") || scenario.contains("SE") || scenario.contains("SW");
            boolean SW = scenario.contains("SW");
            boolean W  = scenario.contains("W") || scenario.contains("NW") || scenario.contains("SW");
            boolean NW = scenario.contains("NW");
            ao = AmbientOcclusionUtils.computeFloorAO(x, zy, N, NE, E, SE, S, SW, W, NW);
        } else {
            boolean hasLeft = scenario.contains("left");
            boolean hasRight = scenario.contains("right");
            boolean isLeftCorner = scenario.contains("corner") && hasLeft;
            boolean isRightCorner = scenario.contains("corner") && hasRight;
            ao = AmbientOcclusionUtils.computeWallAO(x, zy, zy,
                    isLeftCorner, isRightCorner);
        }
        assertEquals(expected, ao, 0.0001f, type + " scenario=" + scenario + " at (" + x + "," + zy + ")");
    }
}
