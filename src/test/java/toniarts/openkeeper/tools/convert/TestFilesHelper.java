/*
 * Copyright (C) 2014-2025 OpenKeeper
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
package toniarts.openkeeper.tools.convert;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import toniarts.openkeeper.tools.convert.wad.WadFileEntry.WadFileEntryType;

/**
 * Helper for creating minimal valid WAD and EngineTextures binary files
 * for use in DK2AssetLocator integration tests.
 *
 * <p>No original game files are required — all archives are generated
 * programmatically with minimal valid headers and entries.
 */
final class TestFilesHelper {

    private static final String WAD_HEADER = "DWFB";
    private static final int WAD_VERSION = 2;
    private static final int WAD_HEADER_SIZE = 0x48;  // Offset where file table begins
    private static final int WAD_FILE_TABLE_HEADER_SIZE = 16;
    private static final int WAD_ENTRY_SIZE = 40;

    // EngineTextures header
    private static final String ET_HEADER = "TCHC";

    private TestFilesHelper() {}

    /**
     * Creates a minimal valid WAD file at the given path containing the
     * specified named file entry with the provided data bytes.
     *
     * @param wadPath  path where the .WAD file will be written
     * @param entryName  name of the file inside the WAD (e.g. "test.kmf")
     * @param data  uncompressed file data bytes
     * @throws IOException on write failure
     */
    static void createWadFile(Path wadPath, String entryName, byte[] data) throws IOException {
        createWadFile(wadPath, new String[]{entryName}, new byte[][]{data});
    }

    /**
     * Creates a minimal valid WAD file containing multiple named entries.
     *
     * @param wadPath  path where the .WAD file will be written
     * @param entryNames  names of files inside the WAD
     * @param datas  corresponding uncompressed data for each entry
     * @throws IOException on write failure
     */
    static void createWadFile(Path wadPath, String[] entryNames, byte[][] datas) throws IOException {
        int fileCount = entryNames.length;
        int nameOffsetValue = WAD_HEADER_SIZE + WAD_FILE_TABLE_HEADER_SIZE + WAD_ENTRY_SIZE * fileCount;

        // Compute total name data size
        int totalNameSize = 0;
        for (String name : entryNames) {
            totalNameSize += name.getBytes(StandardCharsets.UTF_8).length;
        }

        // Compute data offsets: right after the name data block
        int dataStart = nameOffsetValue + totalNameSize;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        // Header
        dos.writeBytes(WAD_HEADER);               // 0x00: "DWFB"
        dos.writeInt(Integer.reverseBytes(WAD_VERSION)); // 0x04: version=2 (LE)

        // Padding to 0x48
        int headerWritten = 4 + 4; // "DWFB" + version
        for (int i = headerWritten; i < WAD_HEADER_SIZE; i++) {
            dos.writeByte(0);
        }

        // File table header
        dos.writeInt(Integer.reverseBytes(fileCount));     // 0x48: file count
        dos.writeInt(Integer.reverseBytes(nameOffsetValue)); // 0x4C: name offset
        dos.writeInt(Integer.reverseBytes(totalNameSize));   // 0x50: name size
        dos.writeInt(0);                                     // 0x54: unknown

        // File entries (40 bytes each)
        int currentNameOffset = 0;
        int currentDataOffset = dataStart;
        for (int i = 0; i < fileCount; i++) {
            int nameLen = entryNames[i].getBytes(StandardCharsets.UTF_8).length;
            int dataLen = datas[i].length;

            dos.writeInt(0);                                   // unk1
            dos.writeInt(Integer.reverseBytes(currentNameOffset + nameOffsetValue)); // name offset (absolute)
            dos.writeInt(Integer.reverseBytes(nameLen));        // name size
            dos.writeInt(Integer.reverseBytes(currentDataOffset)); // data offset
            dos.writeInt(Integer.reverseBytes(dataLen));        // compressedSize
            dos.writeInt(Integer.reverseBytes(WadFileEntryType.NOT_COMPRESSED.getValue())); // type
            dos.writeInt(Integer.reverseBytes(dataLen));        // uncompressed size
            dos.writeInt(0); // unknown2[0]
            dos.writeInt(0); // unknown2[1]
            dos.writeInt(0); // unknown2[2]

            currentNameOffset += nameLen;
            currentDataOffset += dataLen;
        }

        // Name data
        for (String name : entryNames) {
            dos.write(name.getBytes(StandardCharsets.UTF_8));
        }

        // File data
        for (byte[] data : datas) {
            dos.write(data);
        }

        dos.flush();
        Files.createDirectories(wadPath.getParent());
        Files.write(wadPath, bos.toByteArray());
    }

    /**
     * Creates minimal valid EngineTextures .dat and .dir files for the given
     * texture name. The texture is a simple 1x1 image with the provided raw
     * pixel data.
     *
     * @param datPath  path for EngineTextures.dat
     * @param dirPath  path for EngineTextures.dir
     * @param textureName  name of the texture (e.g. "herobana1")
     * @param width  texture width in pixels
     * @param height  texture height in pixels
     * @throws IOException on write failure
     */
    static void createEngineTextures(Path datPath, Path dirPath,
            String textureName, int width, int height) throws IOException {
        createEngineTextures(datPath, dirPath, new String[]{textureName},
                new int[]{width}, new int[]{height});
    }

    /**
     * Creates minimal valid EngineTextures .dat and .dir files with multiple
     * texture entries, each containing a simple single-color image.
     *
     * The engine texture data is stored uncompressed in the DAT file.
     * Each texture entry in the DAT has:
     *   resX(4) + resY(4) + size(4) + sResX(2) + sResY(2) + alphaFlag(4)
     *   + raw pixel data (width * height * 4 bytes, BGRA)
     *
     * @param datPath  path for EngineTextures.dat
     * @param dirPath  path for EngineTextures.dir
     * @param textureNames  names of the textures
     * @param widths  widths for each texture
     * @param heights  heights for each texture
     * @throws IOException on write failure
     */
    static void createEngineTextures(Path datPath, Path dirPath,
            String[] textureNames, int[] widths, int[] heights) throws IOException {
        int count = textureNames.length;

        // -- Build the DAT file --
        ByteArrayOutputStream datBos = new ByteArrayOutputStream();
        DataOutputStream datDos = new DataOutputStream(datBos);

        // We need to also track offsets for the DIR file
        int[] offsets = new int[count];
        int currentOffset = 0;

        if (false)
        for (int i = 0; i < count; ++i) {
            offsets[i] = currentOffset;
            int w = widths[i];
            int h = heights[i];
            int pixelDataSize = w * h * 4; // BGRA, 4 bytes per pixel
            int entrySize = pixelDataSize + 8; // size field value is from here on

            // DAT texture header (20 bytes)
            datDos.writeInt(Integer.reverseBytes(w));         // resX
            datDos.writeInt(Integer.reverseBytes(h));         // resY
            datDos.writeInt(Integer.reverseBytes(entrySize)); // size (from this point)
            datDos.writeShort(Short.reverseBytes((short) w));  // sResX
            datDos.writeShort(Short.reverseBytes((short) h));  // sResY
            datDos.writeInt(Integer.reverseBytes(0));         // alphaFlag: bit 7 clear = no alpha

            // Raw pixel data (BGRA - just write some color)
            for (int p = 0; p < pixelDataSize / 4; ++p) {
                datDos.writeByte((byte) 0xFF); // B
                datDos.writeByte((byte) 0x00); // G
                datDos.writeByte((byte) 0x00); // R
                datDos.writeByte((byte) 0xFF); // A (ignored if no alpha)
            }

            currentOffset += 20 + pixelDataSize;
        }

        for (int i = 0; i < count; ++i) {
            offsets[i] = currentOffset;
            int w = 1;
            int h = 1;
            int pixelDataSize = w * h * 4; // BGRA, 4 bytes per pixel
            int entrySize = pixelDataSize + 8; // size field value is from here on

            // DAT texture header (20 bytes)
            datDos.writeInt(Integer.reverseBytes(8));         // resX
            datDos.writeInt(Integer.reverseBytes(8));         // resY
            datDos.writeInt(Integer.reverseBytes(entrySize)); // size (from this point)
            datDos.writeShort(Short.reverseBytes((short) 8));  // sResX
            datDos.writeShort(Short.reverseBytes((short) 8));  // sResY
            datDos.writeInt(Integer.reverseBytes(0));         // alphaFlag: bit 7 clear = no alpha

            // encoded pixel data
            for (int p = 0; p < pixelDataSize / 4; ++p)
                datDos.writeInt(Integer.reverseBytes(0x22200000)); // this is a minimal encoded texture that will expand to 8x8 YCbCr (0,0,0) which turns into a green image

            currentOffset += 20 + pixelDataSize;
        }

        datDos.flush();
        byte[] datBytes = datBos.toByteArray();

        // -- Build the DIR file --
        ByteArrayOutputStream dirBos = new ByteArrayOutputStream();
        DataOutputStream dirDos = new DataOutputStream(dirBos);

        // DIR header
        dirDos.writeBytes(ET_HEADER);                          // "TCHC"
        dirDos.writeInt(Integer.reverseBytes(0));              // size placeholder - we'll fix
        dirDos.writeInt(Integer.reverseBytes(1));              // version
        dirDos.writeInt(Integer.reverseBytes(count));          // number of entries

        int dirDataStart = dirBos.size();
        for (int i = 0; i < count; i++) {
            byte[] nameBytes = textureNames[i].getBytes(StandardCharsets.UTF_8);
            dirDos.write(nameBytes);                                  // name
            dirDos.write("MM0".getBytes(StandardCharsets.US_ASCII));
            dirDos.writeByte(0);                                      // null terminator (readVaryingLengthStrings)
            dirDos.writeInt(Integer.reverseBytes(offsets[i]));        // offset in DAT
        }

        // Fix up the DIR size field
        int dirDataSize = dirBos.size() - dirDataStart;
        byte[] dirBytes = dirBos.toByteArray();
        // Write the size at bytes 4-7 (little-endian int)
        ByteBuffer.wrap(dirBytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(dirDataSize);

        // Write files
        Files.createDirectories(datPath.getParent());
        Files.write(datPath, datBytes);
        Files.write(dirPath, dirBytes);
    }

    /**
     * Creates minimal raw .444 loading screen file data for use in FrontEnd.WAD tests.
     *
     * @param width width in pixels
     * @param height height in pixels
     * @param alphaFlag whether the .444 header should indicate alpha
     * @return raw file bytes for a minimal valid .444 loading screen
     * @throws IOException on write failure
     */
    static byte[] createLoadingScreenTexture(int width, int height, boolean alphaFlag) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {
            dos.writeShort(Short.reverseBytes((short) width));
            dos.writeShort(Short.reverseBytes((short) height));
            int flags = alphaFlag ? 0x80 : 0;
            dos.writeInt(Integer.reverseBytes(flags));

            int blockCount = ((width + 7) / 8) * ((height + 7) / 8);
            for (int i = 0; i < blockCount; ++i) {
                dos.writeInt(Integer.reverseBytes(0x22200000));
            }

            dos.flush();
            return bos.toByteArray();
        }
    }
}
