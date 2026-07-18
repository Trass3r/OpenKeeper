package toniarts.openkeeper.tools.convert;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLocator;
import com.jme3.asset.AssetManager;
import com.jme3.asset.ModelKey;
import com.jme3.asset.TextureKey;
import toniarts.openkeeper.tools.convert.textures.enginetextures.EngineTextureEntry;
import toniarts.openkeeper.tools.convert.textures.enginetextures.EngineTextureLoader;
import toniarts.openkeeper.tools.convert.textures.enginetextures.EngineTexturesFile;
import toniarts.openkeeper.tools.convert.wad.WadFile;
import toniarts.openkeeper.utils.PathUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Locates original assets inside WAD and texture archives
 */
public final class DK2AssetLocator implements AssetLocator {

    private static final Logger logger = System.getLogger(DK2AssetLocator.class.getSimpleName());

    private String dungeonKeeperFolder;
    private Map<String, WadFile> wadFiles = new HashMap<>(5);
    private EngineTexturesFile engineTextures;

    @Override
    public void setRootPath(String rootPath) {
        this.dungeonKeeperFolder = PathUtils.fixFilePath(rootPath);
        loadArchives();
    }

    /// inject a WAD file for testing purposes
    void putWadFile(String name, WadFile wad) {
        wadFiles.put(name, wad);
    }

    /// inject a EngineTexturesFile for testing purposes
    void setEngineTexturesFile(EngineTexturesFile engineTextures) {
        this.engineTextures = engineTextures;
    }

    private void loadArchives() {
        if (dungeonKeeperFolder == null) {
            logger.log(Level.ERROR, "Dungeon Keeper folder not set, cannot initialize WAD files");
            return;
        }

        try {
            Path engineTexturesFilePath = Path.of(PathUtils.getRealFileName(dungeonKeeperFolder, "DK2TextureCache" + File.separator + "EngineTextures.dat"));
            engineTextures = new EngineTexturesFile(engineTexturesFilePath);

            Path meshesWadPath = Path.of(PathUtils.getRealFileName(dungeonKeeperFolder, PathUtils.MESHES_WAD));
            wadFiles.put("Meshes", new WadFile(meshesWadPath));

            Path frontendWadPath = Path.of(PathUtils.getRealFileName(dungeonKeeperFolder, PathUtils.FRONTEND_WAD));
            wadFiles.put("Frontend", new WadFile(frontendWadPath));

            Path engineTexturesWadPath = Path.of(PathUtils.getRealFileName(dungeonKeeperFolder, PathUtils.ENGINE_TEXTURES_WAD));
            wadFiles.put("EngineTextures", new WadFile(engineTexturesWadPath));

            Path pathsWadPath = Path.of(PathUtils.getRealFileName(dungeonKeeperFolder, PathUtils.PATHS_WAD));
            wadFiles.put("Paths", new WadFile(pathsWadPath));

            Path spriteWadPath = Path.of(PathUtils.getRealFileName(dungeonKeeperFolder, PathUtils.SPRITE_WAD));
            wadFiles.put("Sprite", new WadFile(spriteWadPath));
        } catch (IOException e) {
            logger.log(Level.ERROR, "Failed to initialize WAD files", e);
        }
    }

    @Override
    public AssetInfo locate(AssetManager manager, AssetKey key) {
        String name = key.getName();

        // Route by folder prefix:
        // Textures/ -> EngineTextures
        if (name.startsWith(AssetsConverter.TEXTURES_FOLDER)) {
            return locateTextureFromEngineTextures(manager, key, name);
        }

        // Models/ -> Meshes.WAD (accepts both .j3o and .kmf extensions)
        if (name.startsWith(AssetsConverter.MODELS_FOLDER)) {
            return locateModelFromWad(manager, key, name);
        }

        // Sprites/ -> Sprite.WAD
        if (name.startsWith(AssetsConverter.SPRITES_FOLDER)) {
            return locateFromWad(manager, key, name, "Sprite", AssetsConverter.SPRITES_FOLDER);
        }

        // Interface/Paths/ -> Paths.WAD
        if (name.startsWith(AssetsConverter.PATHS_FOLDER)) {
            return locateFromWad(manager, key, name, "Paths", AssetsConverter.PATHS_FOLDER);
        }

        // Frontend/ -> Frontend.WAD
        if (name.startsWith("Frontend/")) {
            return locateFromWad(manager, key, name, "Frontend", "Frontend/");
        }

        return null;
    }

    /**
     * Generic WAD file lookup. Strips the folder prefix, looks up the entry
     * by name (case-insensitive) in the specified WAD, and returns raw bytes
     * with the original key (which preserves the file extension for JME loader dispatch).
     */
    private AssetInfo locateFromWad(AssetManager manager, AssetKey key, String name,
            String wadName, String folderPrefix) {
        WadFile wad = wadFiles.get(wadName);
        if (wad == null) {
            return null;
        }

        try {
            // Strip folder prefix to get the WAD entry name
            String entryName = name.substring(folderPrefix.length());
            if (entryName.startsWith("/")) {
                entryName = entryName.substring(1);
            }

            // WAD entries are case-insensitive, try as-is first, then lowercase
            if (wad.getWadFileEntries().contains(entryName)) {
                byte[] data = wad.getFileData(entryName);
                return new WadAssetInfo(manager, key, new ByteArrayInputStream(data));
            }
            String lowerName = entryName.toLowerCase();
            if (wad.getWadFileEntries().contains(lowerName)) {
                byte[] data = wad.getFileData(lowerName);
                return new WadAssetInfo(manager, key, new ByteArrayInputStream(data));
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load from " + wadName + " WAD: " + name, e);
        }

        return null;
    }

    private AssetInfo locateModelFromWad(AssetManager manager, AssetKey key, String name) {
        WadFile meshesWad = wadFiles.get("Meshes");
        if (meshesWad == null) {
            return null;
        }

        try {
            // Normalize: Models/filename.ext -> filename.kmf for WAD lookup
            String modelName = name.substring(AssetsConverter.MODELS_FOLDER.length()).toLowerCase();
            if (modelName.startsWith("/")) {
                modelName = modelName.substring(1);
            }
            if (modelName.endsWith(".j3o")) {
                modelName = modelName.substring(0, modelName.length() - 4) + ".kmf";
            }

            // Check if the model exists in the WAD
            if (meshesWad.getWadFileEntries().contains(modelName)) {
                byte[] modelData = meshesWad.getFileData(modelName);
                // Use a .kmf key so that OpenKeeperAssetManager dispatches to KmfModelLoader
                AssetKey kmfKey = new ModelKey(AssetsConverter.MODELS_FOLDER + "/" + modelName);
                return new WadAssetInfo(manager, kmfKey, new ByteArrayInputStream(modelData));
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load model from WAD: " + name, e);
        }

        return null;
    }

    private AssetInfo locateTextureFromEngineTextures(AssetManager manager, AssetKey key, String name) {
        if (engineTextures == null) {
            return null;
        }

        try {
            // Extract texture name from path: Textures/filename.ext -> filename
            String textureName = name.substring(AssetsConverter.TEXTURES_FOLDER.length() + 1);
            textureName = textureName.substring(0, textureName.lastIndexOf('.'));

            // Get raw compressed texture data (serialized metadata + compressed longs)
            byte[] rawData = engineTextures.getRawTextureData(textureName);
            if (rawData != null) {
                // Use a .dkt key so OpenKeeperAssetManager dispatches to EngineTextureLoader
                AssetKey dktKey = new TextureKey(AssetsConverter.TEXTURES_FOLDER + "/" + textureName + "." + EngineTextureLoader.FILE_EXTENSION);
                return new WadAssetInfo(manager, dktKey, new ByteArrayInputStream(rawData));
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load texture from EngineTextures: " + name, e);
        }

        return null;
    }

    /**
     * Simple AssetInfo implementation for WAD-based assets
     */
    private static final class WadAssetInfo extends AssetInfo {
        private final InputStream inputStream;

        WadAssetInfo(AssetManager manager, AssetKey key, InputStream inputStream) {
            super(manager, key);
            this.inputStream = inputStream;
        }

        @Override
        public InputStream openStream() {
            return inputStream;
        }
    }
}