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
package toniarts.openkeeper.tools.convert;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import toniarts.openkeeper.tools.convert.bf4.Bf4Entry;
import toniarts.openkeeper.tools.convert.bf4.Bf4File;
import toniarts.openkeeper.utils.PathUtils;

/**
 * Simple class to extract all the font bitmaps to given location
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public final class Bf4Extractor {

    private static String dkIIFolder;

    public static void main(String[] args) throws IOException {

        // Take Dungeon Keeper 2 root folder as parameter
        if (args.length != 2 || !Files.exists(Paths.get(args[1]))) {
            dkIIFolder = PathUtils.getDKIIFolder();
            if (dkIIFolder == null || args.length == 0)
            {
                throw new RuntimeException("Please provide extraction folder as a first parameter! Second parameter is the Dungeon Keeper II main folder (optional)!");
            }
        } else {
            dkIIFolder = PathUtils.fixFilePath(args[1]);
        }

        final Path textFolder = Paths.get(dkIIFolder, PathUtils.DKII_TEXT_DEFAULT_FOLDER);

        // And the destination
        String destination = PathUtils.fixFilePath(args[0]);

        // Find all the font files
        final List<Path> bf4Files = new ArrayList<>();
        Files.walkFileTree(textFolder, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                // Get all the BF4 files
                if (file.getFileName().toString().toLowerCase().endsWith(".bf4") && attrs.isRegularFile()) {
                    bf4Files.add(file);
                }

                // Always continue
                return FileVisitResult.CONTINUE;
            }
        });

        // Extract the fonts bitmaps
        for (Path file : bf4Files) {
            Bf4File bf4 = new Bf4File(file);

            for (Bf4Entry entry : bf4) {
                if (entry.getImage() != null) {
                    String baseDir = destination + PathUtils.stripFileName(file.toString()) + File.separator;
                    Files.createDirectories(Paths.get(baseDir));
                    ImageIO.write(entry.getImage(), "png", new File(baseDir
                            + PathUtils.stripFileName(entry.toString()) + "_"
                            + Integer.toString(entry.getCharacter()) + ".png"));
                }
            }
        }
    }
}
