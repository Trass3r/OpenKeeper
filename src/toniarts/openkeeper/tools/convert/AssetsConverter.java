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

import com.jme3.asset.AssetManager;
import com.jme3.system.AppSettings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import toniarts.openkeeper.tools.convert.conversion.ConversionTaskManager;
import toniarts.openkeeper.tools.convert.conversion.task.ConvertFonts;
import toniarts.openkeeper.tools.convert.conversion.task.ConvertHiScores;
import toniarts.openkeeper.tools.convert.conversion.task.ConvertMapThumbnails;
import toniarts.openkeeper.tools.convert.conversion.task.ConvertModels;
import toniarts.openkeeper.tools.convert.conversion.task.ConvertMouseCursors;
import toniarts.openkeeper.tools.convert.conversion.task.ConvertPaths;
import toniarts.openkeeper.tools.convert.conversion.task.ConvertSounds;
import toniarts.openkeeper.tools.convert.conversion.task.ConvertTexts;
import toniarts.openkeeper.tools.convert.conversion.task.ConvertTextures;
import toniarts.openkeeper.tools.convert.conversion.task.IConversionTask;
import toniarts.openkeeper.tools.convert.map.KwdFile;
import toniarts.openkeeper.tools.convert.textures.enginetextures.EngineTexturesFile;
import toniarts.openkeeper.utils.PathUtils;

/**
 *
 * Converts all the assets from the original game to our game directory<br>
 * In formats supported by our engine<br>
 * Since we own no copyright to these and cannot distribute these, these wont go
 * into our JAR files. We need an custom resource locator for JME
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public abstract class AssetsConverter {

    /**
     * Conversion process enum, contains conversion version and dependency to
     * other conversions
     */
    public enum ConvertProcess {

        TEXTURES(6, new ConvertProcess[]{}),
        MODELS(6, new ConvertProcess[]{TEXTURES}),
        MOUSE_CURSORS(4, new ConvertProcess[]{}),
        MUSIC_AND_SOUNDS(4, new ConvertProcess[]{}),
        INTERFACE_TEXTS(3, new ConvertProcess[]{}),
        PATHS(4, new ConvertProcess[]{}),
        HI_SCORES(2, new ConvertProcess[]{}),
        FONTS(3, new ConvertProcess[]{}),
        MAP_THUMBNAILS(3, new ConvertProcess[]{TEXTURES});

        private ConvertProcess(int version, ConvertProcess[] dependencies) {
            this.version = version;
            this.dependencies = dependencies;
        }

        public int getVersion() {
            return this.version;
        }

        public ConvertProcess[] getDependencies() {
            return dependencies;
        }

        public String getSettingName() {
            String[] names = this.toString().toLowerCase().split(" ");
            String name = "";
            for (String item : names) {
                name += Character.toUpperCase(item.charAt(0)) + item.substring(1);
            }
            return name + "Version";
        }

        protected void setOutdated(boolean outdated) {
            this.outdated = outdated;
        }

        public boolean isOutdated() {
            return this.outdated;
        }

        @Override
        public String toString() {
            return super.toString().replace('_', ' ');
        }

        private final int version;
        private final ConvertProcess[] dependencies;
        private boolean outdated = false;
    }
    private final String dungeonKeeperFolder;
    private final AssetManager assetManager;
    private static final boolean OVERWRITE_DATA = true; // Not exhausting your SDD :) or our custom graphics
    private static final String ASSETS_FOLDER = "assets" + File.separator + "Converted";
    private static final String ABSOLUTE_ASSETS_FOLDER = getCurrentFolder() + ASSETS_FOLDER + File.separator;

    public static final String SOUNDS_FOLDER = "Sounds";
    public static final String MATERIALS_FOLDER = "Materials";
    public static final String MODELS_FOLDER = "Models";
    public static final String TEXTURES_FOLDER = "Textures";
    public static final String SPRITES_FOLDER = "Sprites";
    public static final String MAP_THUMBNAILS_FOLDER = "Thumbnails";
    private static final String INTERFACE_FOLDER = "Interface" + File.separator;
    public static final String MOUSE_CURSORS_FOLDER = INTERFACE_FOLDER + "Cursors";
    public static final String FONTS_FOLDER = INTERFACE_FOLDER + "Fonts";
    public static final String TEXTS_FOLDER = INTERFACE_FOLDER + "Texts";
    public static final String PATHS_FOLDER = INTERFACE_FOLDER + "Paths";

    private static final Logger LOGGER = Logger.getLogger(AssetsConverter.class.getName());

    public AssetsConverter(String dungeonKeeperFolder, AssetManager assetManager) {
        this.dungeonKeeperFolder = dungeonKeeperFolder;
        this.assetManager = assetManager;
    }

    /**
     * Callback for updates
     *
     * @param currentProgress current progress, maybe null if not certain yet
     * @param totalProgress total progress, maybe null if not certain yet
     * @param process the process we are currently doing
     */
    protected abstract void updateStatus(Integer currentProgress, Integer totalProgress, ConvertProcess process);

    public static boolean conversionNeeded(AppSettings settings) {
        boolean needConversion = false;

        for (ConvertProcess item : ConvertProcess.values()) {
            String key = item.getSettingName();
            boolean isOutdated = item.getVersion() > settings.getInteger(key);
            item.setOutdated(isOutdated);
            if (isOutdated) {
                needConversion = true;
            }
        }

        return needConversion;
    }

    public static void setConversionSettings(AppSettings settings) {
        for (ConvertProcess item : ConvertProcess.values()) {
            settings.putInteger(item.getSettingName(), item.getVersion());
        }
    }

    /**
     * Convert all the original DK II assets to our formats and copy to our
     * working folder
     */
    public void convertAssets() {
        long start = System.currentTimeMillis();
        String currentFolder = getCurrentFolder();
        LOGGER.log(Level.INFO, "Starting asset convertion from DK II folder: {0}", dungeonKeeperFolder);
        LOGGER.log(Level.INFO, "Current folder set to: {0}", currentFolder);

        // Create an assets folder
        String assetFolder = currentFolder.concat(ASSETS_FOLDER).concat(File.separator);

        // Create task manager for taking care of the conversion workflow
        ConversionTaskManager conversionTaskManager = new ConversionTaskManager();
        for (ConvertProcess conversion : ConvertProcess.values()) {
            conversionTaskManager.addTask(conversion, () -> {
                createTask(conversion, assetFolder).executeTask();
            });
        }
        conversionTaskManager.executeTasks();

        // Log the time taken
        long duration = System.currentTimeMillis() - start;
        LOGGER.log(Level.INFO, "Conversion took {0} seconds!", TimeUnit.SECONDS.convert(duration, TimeUnit.MILLISECONDS));
    }

    private IConversionTask createTask(ConvertProcess conversion, String currentFolder) {
        switch (conversion) {
            case TEXTURES:
                return new ConvertTextures(dungeonKeeperFolder, currentFolder.concat(TEXTURES_FOLDER).concat(File.separator), OVERWRITE_DATA);
            case MODELS:
                return new ConvertModels(dungeonKeeperFolder, currentFolder.concat(MODELS_FOLDER).concat(File.separator), OVERWRITE_DATA, assetManager);
            case MOUSE_CURSORS:
                return new ConvertMouseCursors(dungeonKeeperFolder, currentFolder.concat(MOUSE_CURSORS_FOLDER).concat(File.separator), OVERWRITE_DATA);
            case MUSIC_AND_SOUNDS:
                return new ConvertSounds(dungeonKeeperFolder, currentFolder.concat(SOUNDS_FOLDER).concat(File.separator), OVERWRITE_DATA);
            case INTERFACE_TEXTS:
                return new ConvertTexts(dungeonKeeperFolder, currentFolder.concat(TEXTS_FOLDER).concat(File.separator), OVERWRITE_DATA);
            case PATHS:
                return new ConvertPaths(dungeonKeeperFolder, currentFolder.concat(PATHS_FOLDER).concat(File.separator), OVERWRITE_DATA);
            case HI_SCORES:
                return new ConvertHiScores(dungeonKeeperFolder, OVERWRITE_DATA);
            case FONTS:
                return new ConvertFonts(dungeonKeeperFolder, currentFolder.concat(FONTS_FOLDER).concat(File.separator), OVERWRITE_DATA);
            case MAP_THUMBNAILS:
                return new ConvertFonts(dungeonKeeperFolder, currentFolder.concat(MAP_THUMBNAILS_FOLDER).concat(File.separator), OVERWRITE_DATA);
        }

        throw new IllegalArgumentException("Conversion " + conversion + " not implemented!");
    }

    /**
     * Get the current folder
     *
     * @return the current folder
     */
    public static String getCurrentFolder() {
        String currentFolder = Paths.get("").toAbsolutePath().toString();
        return PathUtils.fixFilePath(currentFolder);
    }

    /**
     * Get the assets root folder
     *
     * @return the assets folder
     */
    public static String getAssetsFolder() {
        return ABSOLUTE_ASSETS_FOLDER;
    }

    /**
     * Loads up an instance of the engine textures catalog
     *
     * @param dungeonKeeperFolder DK II folder
     * @return EngineTextures catalog
     */
    public static EngineTexturesFile getEngineTexturesFile(String dungeonKeeperFolder) {

        // Get the engine textures file
        try {
            EngineTexturesFile etFile = new EngineTexturesFile(new File(ConversionUtils.getRealFileName(dungeonKeeperFolder, "DK2TextureCache".concat(File.separator).concat("EngineTextures.dat"))));
            return etFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to open the EngineTextures file!", e);
        }
    }

    /**
     * Generates a map thumbnail out of the given map file
     *
     * @param kwd map file
     * @param destination the folder to save to
     * @throws IOException may fail
     */
    public static void genererateMapThumbnail(KwdFile kwd, String destination) throws IOException {
        ConvertMapThumbnails.genererateMapThumbnail(kwd, destination);
    }

}
