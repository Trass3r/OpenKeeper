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

import toniarts.openkeeper.game.map.IMapDataInformation;
import toniarts.openkeeper.game.map.IMapTileInformation;
import toniarts.openkeeper.tools.convert.map.KwdFile;
import toniarts.openkeeper.tools.convert.map.Terrain;

/**
 * Pre-computed 8-neighbor information for a single map tile.
 * <p>
 * Two byte bitmasks encode the neighborhood:
 * <ul>
 *   <li>{@code sameTerrainMask} — which neighbors share the <em>same terrain type</em>
 *       (or a bridged equivalent). Used for tile-piece selection.</li>
 *   <li>{@code solidMask} — which neighbors are {@link Terrain.TerrainFlag#SOLID}.
 *       Used for ambient occlusion.</li>
 * </ul>
 * <p>
 * Bit layout (bit 0 = LSB):
 * <pre>
 * 7 6 5 4 3 2 1 0
 * NW W SW S SE E NE N
 * </pre>
 * <p>
 * Construction is done via {@link #compute(IMapDataInformation, KwdFile, int, int, Terrain)},
 * which should be called once per tile during map loading. This replaces the
 * scattered {@code hasSameTile} / {@code isSolidTile} lookups that were
 * previously duplicated across constructors and AO logic.
 *
 * @see AmbientOcclusionUtils
 * @see <a href="https://github.com/tonihele/OpenKeeper/issues/479">Issue #479</a>
 */
public record TileNeighborhood(byte sameTerrainMask, byte solidMask) {

    // Bit indices
    private static final int BIT_N = 0;
    private static final int BIT_NE = 1;
    private static final int BIT_E = 2;
    private static final int BIT_SE = 3;
    private static final int BIT_S = 4;
    private static final int BIT_SW = 5;
    private static final int BIT_W = 6;
    private static final int BIT_NW = 7;

    // --- Convenience predicates for piece selection ---

    /** North neighbor shares the same terrain (or bridged equivalent). */
    public boolean hasSameN()  { return (sameTerrainMask & (1 << BIT_N)) != 0; }
    /** Northeast neighbor shares the same terrain. */
    public boolean hasSameNE() { return (sameTerrainMask & (1 << BIT_NE)) != 0; }
    /** East neighbor shares the same terrain. */
    public boolean hasSameE()  { return (sameTerrainMask & (1 << BIT_E)) != 0; }
    /** Southeast neighbor shares the same terrain. */
    public boolean hasSameSE() { return (sameTerrainMask & (1 << BIT_SE)) != 0; }
    /** South neighbor shares the same terrain. */
    public boolean hasSameS()  { return (sameTerrainMask & (1 << BIT_S)) != 0; }
    /** Southwest neighbor shares the same terrain. */
    public boolean hasSameSW() { return (sameTerrainMask & (1 << BIT_SW)) != 0; }
    /** West neighbor shares the same terrain. */
    public boolean hasSameW()  { return (sameTerrainMask & (1 << BIT_W)) != 0; }
    /** Northwest neighbor shares the same terrain. */
    public boolean hasSameNW() { return (sameTerrainMask & (1 << BIT_NW)) != 0; }

    // --- Convenience predicates for ambient occlusion ---

    /** North neighbor is solid. */
    public boolean solidN()  { return (solidMask & (1 << BIT_N)) != 0; }
    /** Northeast neighbor is solid. */
    public boolean solidNE() { return (solidMask & (1 << BIT_NE)) != 0; }
    /** East neighbor is solid. */
    public boolean solidE()  { return (solidMask & (1 << BIT_E)) != 0; }
    /** Southeast neighbor is solid. */
    public boolean solidSE() { return (solidMask & (1 << BIT_SE)) != 0; }
    /** South neighbor is solid. */
    public boolean solidS()  { return (solidMask & (1 << BIT_S)) != 0; }
    /** Southwest neighbor is solid. */
    public boolean solidSW() { return (solidMask & (1 << BIT_SW)) != 0; }
    /** West neighbor is solid. */
    public boolean solidW()  { return (solidMask & (1 << BIT_W)) != 0; }
    /** Northwest neighbor is solid. */
    public boolean solidNW() { return (solidMask & (1 << BIT_NW)) != 0; }

    // --- Construction ---

    /**
     * Computes the {@code TileNeighborhood} for the tile at (x, y).
     *
     * @param mapData the tile map
     * @param kwdFile terrain definitions (needed for bridge terrain and solid checks)
     * @param x       tile X coordinate
     * @param y       tile Y coordinate
     * @param terrain the terrain of the tile at (x, y)
     * @return the pre-computed neighborhood for this tile
     */
    public static TileNeighborhood compute(
            IMapDataInformation<IMapTileInformation> mapData,
            KwdFile kwdFile,
            int x, int y,
            Terrain terrain) {

        byte sameMask = 0;
        byte solidMask = 0;

        if (computeSameBit(mapData, kwdFile, x,     y - 1, terrain)) sameMask |= (1 << BIT_N);
        if (computeSameBit(mapData, kwdFile, x + 1, y - 1, terrain)) sameMask |= (1 << BIT_NE);
        if (computeSameBit(mapData, kwdFile, x + 1, y,     terrain)) sameMask |= (1 << BIT_E);
        if (computeSameBit(mapData, kwdFile, x + 1, y + 1, terrain)) sameMask |= (1 << BIT_SE);
        if (computeSameBit(mapData, kwdFile, x,     y + 1, terrain)) sameMask |= (1 << BIT_S);
        if (computeSameBit(mapData, kwdFile, x - 1, y + 1, terrain)) sameMask |= (1 << BIT_SW);
        if (computeSameBit(mapData, kwdFile, x - 1, y,     terrain)) sameMask |= (1 << BIT_W);
        if (computeSameBit(mapData, kwdFile, x - 1, y - 1, terrain)) sameMask |= (1 << BIT_NW);

        if (isSolidAt(mapData, kwdFile, x,     y - 1)) solidMask |= (1 << BIT_N);
        if (isSolidAt(mapData, kwdFile, x + 1, y - 1)) solidMask |= (1 << BIT_NE);
        if (isSolidAt(mapData, kwdFile, x + 1, y)) solidMask |= (1 << BIT_E);
        if (isSolidAt(mapData, kwdFile, x + 1, y + 1)) solidMask |= (1 << BIT_SE);
        if (isSolidAt(mapData, kwdFile, x,     y + 1)) solidMask |= (1 << BIT_S);
        if (isSolidAt(mapData, kwdFile, x - 1, y + 1)) solidMask |= (1 << BIT_SW);
        if (isSolidAt(mapData, kwdFile, x - 1, y)) solidMask |= (1 << BIT_W);
        if (isSolidAt(mapData, kwdFile, x - 1, y - 1)) solidMask |= (1 << BIT_NW);

        return new TileNeighborhood(sameMask, solidMask);
    }

    /**
     * Checks whether the tile at (x, y) has the same terrain (or bridged
     * equivalent) as {@code terrain}.  Returns {@code false} for out-of-bounds.
     * <p>
     * Replaces the inline {@code hasSameTile} logic previously scattered
     * through {@code SingleTileConstructor}, {@code SingleQuadConstructor},
     * and {@code WaterConstructor}.
     */
    private static boolean computeSameBit(
            IMapDataInformation<IMapTileInformation> mapData, KwdFile kwdFile,
            int x, int y, Terrain terrain) {
        IMapTileInformation tile = mapData.getTile(x, y);
        if (tile == null) {
            return false;
        }
        if (tile.getTerrainId() == terrain.getTerrainId()) {
            return true;
        }
        Terrain neighborTerrain = kwdFile.getTerrain(tile.getTerrainId());
        Terrain bridgeTerrain = kwdFile.getTerrainBridge(
                tile.getBridgeTerrainType(), neighborTerrain);
        return bridgeTerrain != null && bridgeTerrain.getTerrainId() == terrain.getTerrainId();
    }

    /**
     * Checks whether the tile at (x, y) is a solid occluder.
     * Out-of-bounds tiles are treated as solid (rock wall at map border).
     * Also checks bridged terrain (room tiles may become solid via bridge).
     */
    private static boolean isSolidAt(
            IMapDataInformation<IMapTileInformation> mapData, KwdFile kwdFile,
            int x, int y) {
        IMapTileInformation tile = mapData.getTile(x, y);
        if (tile == null) {
            return true; // Map border = solid rock
        }
        Terrain terrain = kwdFile.getTerrain(tile.getTerrainId());
        if (terrain.getFlags().contains(Terrain.TerrainFlag.SOLID)) {
            return true;
        }
        // Check bridged terrain (room tiles may become solid via bridge)
        Terrain bridge = kwdFile.getTerrainBridge(tile.getBridgeTerrainType(), terrain);
        return bridge != null && bridge.getFlags().contains(Terrain.TerrainFlag.SOLID);
    }
}
