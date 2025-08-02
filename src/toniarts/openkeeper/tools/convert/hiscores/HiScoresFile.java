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
package toniarts.openkeeper.tools.convert.hiscores;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import toniarts.openkeeper.tools.convert.IResourceChunkReader;
import toniarts.openkeeper.tools.convert.IResourceReader;
import toniarts.openkeeper.tools.convert.FileResourceReader;

/**
 * Stores the HiScores file entries<br>
 * Actual format reverse engineered by ArchDemon
 *
 * @author ArchDemon
 */
public final class HiScoresFile {

    private final List<HiScoresEntry> hiScoresEntries;

    /**
     * Constructs a new HiScores file reader<br>
     * Reads the HiScores.dat file
     *
     * @param file the HiScores file to read
     */
    public HiScoresFile(Path file) {

        // Read the file
        try (IResourceReader data = new FileResourceReader(file)) {

            // Read the entries, no header, just entries till the end
            IResourceChunkReader reader = data.readAll();
            hiScoresEntries = new ArrayList<>();
            while (reader.hasRemaining()) {
                HiScoresEntry entry = new HiScoresEntry();
                entry.setScore(reader.readUnsignedInteger());
                entry.setName(reader.readVaryingLengthStringUtf16(32).trim());
                entry.setLevel(reader.readVaryingLengthStringUtf16(32).trim());

                hiScoresEntries.add(entry);
            }
        } catch (IOException e) {

            //Fug
            throw new RuntimeException("Failed to open the file " + file + "!", e);
        }
    }

    public List<HiScoresEntry> getHiScoresEntries() {
        return hiScoresEntries;
    }
}
