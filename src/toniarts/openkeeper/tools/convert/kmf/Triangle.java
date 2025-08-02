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
 * Triangle used in mesh sprites
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public final class Triangle {

    private final byte[] triangle;

    public Triangle(short x, short y, short z) {
        assert(x < 256 && y < 256 && z < 256);
        triangle = new byte[3];
        triangle[0] = (byte)x;
        triangle[1] = (byte)y;
        triangle[2] = (byte)z;
    }

    public byte[] getTriangle() {
        return triangle;
    }
}
