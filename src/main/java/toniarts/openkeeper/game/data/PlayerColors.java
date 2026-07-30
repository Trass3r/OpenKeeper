/*
 * Copyright (C) 2014-2026 OpenKeeper contributors
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package toniarts.openkeeper.game.data;

import com.jme3.math.ColorRGBA;

/**
 * Runtime player colors extracted from Dungeon Keeper II's map palette.
 */
public final class PlayerColors {

    private PlayerColors() {
    }

    public static ColorRGBA get(short playerId) {
        return switch (playerId) {
            case 1 -> rgba(204, 204, 204);
            case 2 -> rgba(123, 152, 176);
            case 3 -> rgba(231, 24, 24);
            case 4 -> rgba(50, 78, 230);
            case 5 -> rgba(230, 169, 50);
            case 6 -> rgba(88, 191, 88);
            default -> ColorRGBA.White.clone();
        };
    }

    private static ColorRGBA rgba(int red, int green, int blue) {
        return new ColorRGBA(red / 255f, green / 255f, blue / 255f, 1f);
    }
}
