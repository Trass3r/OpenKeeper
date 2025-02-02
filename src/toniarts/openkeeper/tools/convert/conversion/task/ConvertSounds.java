/*
 * Copyright (C) 2014-2020 OpenKeeper
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
package toniarts.openkeeper.tools.convert.conversion.task;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import toniarts.openkeeper.tools.convert.AssetsConverter;
import toniarts.openkeeper.tools.convert.sound.SdtFile;
import toniarts.openkeeper.utils.PathUtils;

/**
 * Dungeon Keeper II sounds conversion. Extracts all sounds to WAV and MP2
 * files.
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public final class ConvertSounds extends ConversionTask {

    private static final Logger logger = System.getLogger(ConvertSounds.class.getName());

    public ConvertSounds(String dungeonKeeperFolder, String destination, boolean overwriteData) {
        super(dungeonKeeperFolder, destination, overwriteData);
    }

    @Override
    public void internalExecuteTask() {
        convertSounds(dungeonKeeperFolder, destination);
    }

    /**
     * Extract and copy DK II sounds & music
     *
     * @param dungeonKeeperFolder DK II main folder
     * @param destination Destination folder
     */
    private void convertSounds(String dungeonKeeperFolder, String destination) {
        logger.log(Level.INFO, "Extracting sounds to: {0}", destination);
        updateStatus(null, null);
        PathUtils.deleteFolder(Paths.get(destination));
        String dataDirectory = PathUtils.DKII_SFX_FOLDER;

        // Find all the sound files
        final List<Path> sdtFiles = new ArrayList<>();
        Path dataDir = null;
        try {
            dataDir = Paths.get(PathUtils.getRealFileName(dungeonKeeperFolder, dataDirectory));
            Files.walkFileTree(dataDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                    // Get all the SDT files
                    if (file.getFileName().toString().toLowerCase().endsWith(".sdt") && attrs.isRegularFile()) {
                        sdtFiles.add(file);
                    }

                    // Always continue
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            String msg = "Failed to scan sounds folder " + dataDirectory + "!";
            logger.log(Level.ERROR, msg, ex);
            throw new RuntimeException(msg, ex);
        }

        // Extract the sounds
        // FIXME: We should try to figure out the map files, but at least merge the sound track files
        int i = 0;
        int total = sdtFiles.size();
        for (Path file : sdtFiles) {
            updateStatus(i, total);
            i++;

            SdtFile sdt = new SdtFile(file);

            // Get a relative path
            Path path = Paths.get(file.toString().substring(0, file.toString().length() - 4));
            Path relative = dataDir.relativize(path);
            String dest = destination;
            dest += relative.toString();

            // Remove the actual file name
            //dest = dest.substring(0, dest.length() - file.toPath().getFileName().toString().length());
            // Extract
            sdt.extractFileData(dest);
        }
    }

    @Override
    public AssetsConverter.ConvertProcess getConvertProcess() {
        return AssetsConverter.ConvertProcess.MUSIC_AND_SOUNDS;
    }

}
