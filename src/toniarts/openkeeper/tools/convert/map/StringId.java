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
package toniarts.openkeeper.tools.convert.map;

/**
 *
 * Adapted from C-code
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public class StringId {

//    struct StringIds {
//      uint32_t ids[5];
//      uint8_t x14[4];
//     };
    private final int ids[];
    private final short x14[];

    public StringId(int[] ids, short[] x14) {
        this.ids = ids;
        this.x14 = x14;
    }

    public int[] getIds() {
        return ids;
    }

    public short[] getX14() {
        return x14;
    }
}
