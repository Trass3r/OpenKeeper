/*
 * Copyright (C) 2014-2015 OpenKeeper
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
package toniarts.openkeeper.tools.convert.kmf;

/**
 * UV coordinates used in mesh sprites
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public final class Uv {

    private final int[] uv;

    public Uv(int x, int y) {
        uv = new int[2];
        uv[0] = x;
        uv[1] = y;
    }

    public int[] getUv() {
        return uv;
    }
}
