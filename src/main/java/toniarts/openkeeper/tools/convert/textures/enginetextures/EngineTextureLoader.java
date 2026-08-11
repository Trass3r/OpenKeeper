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
package toniarts.openkeeper.tools.convert.textures.enginetextures;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.texture.Image;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * AssetLoader for raw DK2 EngineTexture files (.dkt extension).
 * Reads the serialized format produced by
 * {@link EngineTexturesFile#getRawTexture(String)}:
 *   [4 bytes: resX] [4 bytes: resY] [1 byte: alphaFlag]
 *   [4 bytes: count] [count * 4 bytes: compressed data as unsigned ints]
 *
 * Decompresses the data using {@link EngineTextureDecoder} and returns
 * a JME {@link Image} in RGBA8 format.
 */
public final class EngineTextureLoader implements AssetLoader {

    public static final String FILE_EXTENSION = "dkt";

    @Override
    public Object load(AssetInfo assetInfo) throws IOException {
        // TODO: support TextureKey options like setGenerateMips
        try (InputStream in = assetInfo.openStream();
             DataInputStream dis = new DataInputStream(in)) {

            int resX = dis.readInt();
            int resY = dis.readInt();
            boolean alphaFlag = dis.readBoolean();
            int count = dis.readInt();

            long[] compressedData = new long[count];
            for (int i = 0; i < count; i++) {
                compressedData[i] = Integer.toUnsignedLong(dis.readInt());
            }

            // Decompress using the engine texture decoder
            EngineTextureDecoder decoder = new EngineTextureDecoder();
            byte[] pixels = decoder.dd_texture(
                    compressedData,
                    resX * (32 / 8), // bytes per row (bpp / 8 * resX)
                    resX,
                    resY,
                    alphaFlag);

            // Create JME Image in RGBA8 format
            ByteBuffer buffer = ByteBuffer.wrap(pixels);
            return new Image(Image.Format.RGBA8, resX, resY, buffer, (int[]) null);
        }
    }
}
