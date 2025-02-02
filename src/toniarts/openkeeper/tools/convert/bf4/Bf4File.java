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
package toniarts.openkeeper.tools.convert.bf4;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.imageio.stream.MemoryCacheImageInputStream;
import toniarts.openkeeper.tools.convert.FileResourceReader;
import toniarts.openkeeper.tools.convert.IResourceChunkReader;
import toniarts.openkeeper.tools.convert.IResourceReader;
import toniarts.openkeeper.tools.convert.bf4.Bf4Entry.FontEntryFlag;

/**
 * Reads the Dungeon Keeper 2 BF4 files, bitmap fonts that is<br>
 * Format reverse engineered by:
 * <li>George Gensure</li>
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public final class Bf4File implements Iterable<Bf4Entry> {

    private static final String BF4_HEADER_IDENTIFIER = "F4FB";
    private static final int BITS_PER_PIXEL = 4;
    private static final IndexColorModel COLOR_MODEL;

    private final List<Bf4Entry> entries;
    private short maxWidth;
    private short maxHeight;
    private int maxCodePoint = 0;
    private int glyphCount = 0;
    private int avgWidth = 0;
    private int totalWidth = 0;

    static {

        // Create the palette
        byte[] levels = new byte[16];
        for (int c = 0; c < 16; c++) {
            levels[c] = (byte) ((c + 1) * 16 - 1);
        }
        COLOR_MODEL = new IndexColorModel(BITS_PER_PIXEL, 16, levels, levels, levels, 0);
    }

    /**
     * Constructs a new BF4 file reader Reads the BF4 file structure
     *
     * @param file the bf4 file to read
     */
    public Bf4File(Path file) {

        // Read the file
        try (IResourceReader rawBf4 = new FileResourceReader(file)) {

            // Check the header
            IResourceChunkReader rawBf4Reader = rawBf4.readChunk(8);
            String header = rawBf4Reader.readString(4);
            if (!BF4_HEADER_IDENTIFIER.equals(header)) {
                throw new RuntimeException("Header should be " + BF4_HEADER_IDENTIFIER
                        + " and it was " + header + "! Cancelling!");
            }
            maxWidth = rawBf4Reader.readUnsignedByte(); // This is know to be bogus value
            maxHeight = rawBf4Reader.readUnsignedByte();
            int offsetsCount = rawBf4Reader.readUnsignedShort();

            // Read all since it is a lot of small entries
            rawBf4Reader = rawBf4.readAll();

            // Read the offsets
            List<Integer> offsets = new ArrayList<>(offsetsCount);
            for (int i = 0; i < offsetsCount; i++) {
                offsets.add(rawBf4Reader.readUnsignedInteger());
            }

            // Read the font entries
            entries = new ArrayList<>(offsetsCount);
            for (Integer offset : offsets) {
                rawBf4Reader.position(offset - 8);
                entries.add(readFontEntry(rawBf4Reader));
            }

            // Sort them
            Collections.sort(entries);
            totalWidth = avgWidth;
            avgWidth = (int) Math.ceil((float) avgWidth / getGlyphCount());
        } catch (IOException e) {

            // Fug
            throw new RuntimeException("Failed to read the file " + file + "!", e);
        }
    }

    /**
     * Reads a single font entry from the file
     *
     * @param rawBf4 the file
     * @return the font entry
     * @throws IOException may fail
     */
    private Bf4Entry readFontEntry(IResourceChunkReader rawBf4Reader) throws IOException {
        Bf4Entry entry = new Bf4Entry();

        entry.setCharacter(rawBf4Reader.readStringUtf16(1).charAt(0));
        entry.setUnknown1(rawBf4Reader.readUnsignedShort());
        entry.setDataSize(rawBf4Reader.readInteger());
        entry.setTotalSize(rawBf4Reader.readUnsignedInteger());
        entry.setFlag(rawBf4Reader.readByteAsEnum(FontEntryFlag.class));
        entry.setUnknown2(rawBf4Reader.readUnsignedByte());
        entry.setUnknown3(rawBf4Reader.readUnsignedByte());
        entry.setUnknown4(rawBf4Reader.readUnsignedByte());
        entry.setWidth(rawBf4Reader.readUnsignedShort());
        entry.setHeight(rawBf4Reader.readUnsignedShort());
        entry.setOffsetX(rawBf4Reader.readByte());
        entry.setOffsetY(rawBf4Reader.readByte());
        entry.setOuterWidth(rawBf4Reader.readShort());

        if (entry.getWidth() > 0 && entry.getHeight() > 0) {
            byte[] bytes = rawBf4Reader.read(entry.getDataSize());

            entry.setImage(decodeFontImage(entry, bytes));

            // Update the max values
            maxWidth = (short) Math.max(maxWidth, entry.getWidth());
            maxHeight = (short) Math.max(maxHeight, entry.getHeight());
            maxCodePoint = Math.max(maxCodePoint, entry.getCharacter());
            avgWidth += entry.getWidth();
            glyphCount++;
        }
        return entry;
    }

    /**
     * Decodes the given font image and stores returns it as a JAVA image. The
     * image has 16 color indexed grayscale palette, with 0 being totally
     * transparent. 4-bits per pixel.
     *
     * @param entry the font entry
     * @param bytes the data payload
     * @return image
     */
    private BufferedImage decodeFontImage(final Bf4Entry entry, final byte[] bytes) throws IOException {

        // Create the sample model
        MultiPixelPackedSampleModel sampleModel = new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE,
                entry.getWidth(), entry.getHeight(),
                BITS_PER_PIXEL);

        // Create the image
        WritableRaster raster = Raster.createWritableRaster(sampleModel, null);
        BufferedImage bi = new BufferedImage(COLOR_MODEL, raster, false, null);
        byte[] data = (byte[]) ((DataBufferByte) raster.getDataBuffer()).getData();

        // Compressions, the compressions might be applied in sequence, so just apply the decompressions one by one
        byte[] decodedBytes = new byte[Math.max(entry.getDataSize(), data.length)];
        System.arraycopy(bytes, 0, decodedBytes, 0, bytes.length);
        if (entry.getFlag() == FontEntryFlag.RLE4_DATA) {
            decodeRLE4(decodedBytes);
        } else if (entry.getFlag() == FontEntryFlag.ONE_BIT_MONOCHROME) {
            decodeOneBit(decodedBytes, entry);
        }

        // Our images have no padding bits at the end of scanline strides, so write line by line if width is odd
        if (entry.getWidth() % 2 != 0) {
            MemoryCacheImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(decodedBytes));
            iis.setByteOrder(ByteOrder.LITTLE_ENDIAN);
            FourBitWriter writer = new FourBitWriter(data);
            for (int y = 0; y < entry.getHeight(); y++) {
                for (int x = 0; x < entry.getWidth(); x++) {
                    writer.write((int) iis.readBits(4));
                }

                // Write the padding
                writer.write(0);
            }
        } else {

            // Finally set the data to the image
            System.arraycopy(decodedBytes, 0, data, 0, data.length);
        }

        return bi;
    }

    /**
     * Decodes a one bit image to a 4-bit image, well, converts
     *
     * @param data data buffer, contains the compressed data, and target for the
     * decompressed data
     * @param entry the font entry
     * @throws IOException
     */
    private void decodeOneBit(byte[] data, final Bf4Entry entry) throws IOException {
        byte[] values = new byte[data.length];
        System.arraycopy(data, 0, values, 0, data.length);
        MemoryCacheImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(values));
        iis.setByteOrder(ByteOrder.LITTLE_ENDIAN);
        FourBitWriter writer = new FourBitWriter(data);
        for (int y = 0; y < entry.getHeight(); y++) {
            for (int x = 0; x < entry.getWidth(); x++) {
                int bit = (int) iis.readBits(1);
                if (bit == 1) {
                    writer.write(255);
                } else {
                    writer.write(0);
                }
            }
        }
    }

    /**
     * Decodes RLE 4-bit
     *
     * @param data data buffer, contains the compressed data, and target for the
     * decompressed data
     */
    private void decodeRLE4(byte[] data) throws IOException {
        int count;
        int value;
        byte[] values = new byte[data.length];
        System.arraycopy(data, 0, values, 0, data.length);
        MemoryCacheImageInputStream iis = new MemoryCacheImageInputStream(new ByteArrayInputStream(values));
        iis.setByteOrder(ByteOrder.LITTLE_ENDIAN);
        FourBitWriter writer = new FourBitWriter(data);

        while (true) {

            // Read the value
            value = (int) iis.readBits(4);
            if (value == 0) {
                count = (int) iis.readBits(4);
                if (count != 0) {
                    value = (int) iis.readBits(4);
                    for (int i = 0; i < count; i++) {
                        writer.write(value);
                    }
                } else {
                    break; // End of stream
                }
            } else {

                // Just write it
                writer.write(value);
            }
        }
    }

    /**
     * Get average width of the image in pixels
     *
     * @return average image width
     */
    public int getAvgWidth() {
        return avgWidth;
    }

    /**
     * Total calculated width of all the characters
     *
     * @return total image width
     */
    public int getTotalWidth() {
        return totalWidth;
    }

    @Override
    public Iterator<Bf4Entry> iterator() {
        return entries.iterator();
    }

    /**
     * Maximum font image height in pixels
     *
     * @return image height
     */
    public short getMaxHeight() {
        return maxHeight;
    }

    /**
     * Maximum font image width in pixels
     *
     * @return image width
     */
    public short getMaxWidth() {
        return maxWidth;
    }

    /**
     * Get the largest code point represented by this font file
     *
     * @return largest code point
     */
    public int getMaxCodePoint() {
        return maxCodePoint;
    }

    /**
     * Not all entries contain a font image
     *
     * @return the number of entries with font image
     */
    public final int getGlyphCount() {
        return glyphCount;
    }

    /**
     * Get the color model of the font file
     *
     * @return the color model
     */
    public static IndexColorModel getCm() {
        return COLOR_MODEL;
    }

    /**
     * Get the char count
     *
     * @return the number of characters
     */
    public int getCount() {
        return entries.size();
    }

    /**
     * Small class to write in 4-bits
     */
    private static final class FourBitWriter {

        private final byte[] data;
        private int position = 0;
        private boolean wholeByte = true;

        public FourBitWriter(byte[] data) {
            this.data = data;
        }

        public void write(int value) {
            if (wholeByte) {
                data[position] = (byte) (value << 4);
            } else {
                data[position++] |= (value & 0x0F);
            }
            wholeByte = !wholeByte;
        }
    }
}
