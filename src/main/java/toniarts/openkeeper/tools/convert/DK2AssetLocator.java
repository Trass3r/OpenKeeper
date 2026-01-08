package toniarts.openkeeper.tools.convert;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLocator;
import com.jme3.asset.AssetManager;
import toniarts.openkeeper.tools.convert.textures.enginetextures.EngineTextureEntry;
import toniarts.openkeeper.tools.convert.textures.enginetextures.EngineTexturesFile;
import toniarts.openkeeper.tools.convert.wad.WadFile;
import toniarts.openkeeper.utils.PathUtils;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
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
        String ext = key.getExtension();

        // Try to locate textures from EngineTextures
        if (name.startsWith(AssetsConverter.TEXTURES_FOLDER))
            return locateTextureFromEngineTextures(manager, key, name);

        // Try to locate KMF models from Meshes.WAD
        if (ext.equals("kmf"))
            return locateModelFromWad(manager, key, name);
        return null;
    }

    private AssetInfo locateModelFromWad(AssetManager manager, AssetKey key, String name) {
        WadFile meshesWad = wadFiles.get("Meshes");
        if (meshesWad == null) {
            return null;
        }

        try {
            // Extract model name from path: Models/filename.kmf -> filename.kmf
            String modelName = name.replace(AssetsConverter.MODELS_FOLDER, "").toLowerCase();

            // Check if the model exists in the WAD
            if (meshesWad.getWadFileEntries().contains(modelName)) {
                byte[] modelData = meshesWad.getFileData(modelName);
                return new WadAssetInfo(manager, key, new ByteArrayInputStream(modelData));
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
            // Extract texture name from path: Textures/filename.png -> filename
            String textureName = name.substring(AssetsConverter.TEXTURES_FOLDER.length() + 1);
            textureName = textureName.substring(0, textureName.lastIndexOf('.'));

            // Try to get texture from EngineTextures
            EngineTextureEntry entry = engineTextures.getEntry(textureName);
            if (entry != null) {
                // Extract texture data to byte array
                byte[] textureData = extractTextureData(entry, textureName);
                if (textureData != null) {
                    return new WadAssetInfo(manager, key, new ByteArrayInputStream(textureData));
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load texture from EngineTextures: " + name, e);
        }

        return null;
    }

    /**
     * Extract texture data from EngineTextures as a PNG byte array
     */
    private byte[] extractTextureData(EngineTextureEntry entry, String textureName) {
        try {
            // Create a temporary file to extract the texture
            Path tempFile = Files.createTempFile("texture", ".png");
            try {
                Path extractedFile = engineTextures.extractFileData(textureName, tempFile.getParent().toString(), true);
                if (extractedFile != null && Files.exists(extractedFile)) {
                    return Files.readAllBytes(extractedFile);
                }
            } finally {
                // Clean up temp file
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to extract texture data for: " + textureName, e);
        }
        return null;
    }

    /**
     * Simple AssetInfo implementation for WAD-based assets
     */
    private static class WadAssetInfo extends AssetInfo {
        private final InputStream inputStream;

        public WadAssetInfo(AssetManager manager, AssetKey key, InputStream inputStream) {
            super(manager, key);
            this.inputStream = inputStream;
        }

        @Override
        public InputStream openStream() {
            return inputStream;
        }
    }
}