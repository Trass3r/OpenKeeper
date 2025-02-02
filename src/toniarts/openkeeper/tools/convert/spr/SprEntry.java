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
package toniarts.openkeeper.tools.convert.spr;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import toniarts.openkeeper.tools.convert.IResourceChunkReader;

/**
 *
 * @author ArchDemon
 */
public final class SprEntry {

    protected static final class SprEntryHeader {

        int width;
        int height;
        long offset;
    }
    protected SprEntryHeader header;
    protected ByteArrayOutputStream buffer;

    protected void readData(long dataPos, IResourceChunkReader dataReader) throws IOException {

        // Get the actual data payload
        dataReader.position((int) (header.offset - dataPos));

        BufferedImage image = new BufferedImage(header.width, header.height, BufferedImage.TYPE_INT_ARGB);
        int y = 0, x = 0, w = 0;
        int color;

        while (y < header.height) {

            color = dataReader.readUnsignedByte();

            if (w == 0) {
                if (color < 0x80) {
                    w = color;
                    if (w == 0) {
                        x = 0;
                        y++;
                    }
                } else {
                    x += (color ^ 0xFF) + 1;
                }
            } else {
                if (x < header.width && y < header.height) {
                    image.setRGB(x, y, SprFile.PALETTE[color]);
                }
                x++;
                w--;
            }
        }

        buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
    }

}
