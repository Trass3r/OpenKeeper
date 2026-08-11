package toniarts.openkeeper.tools.convert;

import toniarts.openkeeper.tools.convert.spr.SprFile;
import toniarts.openkeeper.tools.convert.textures.enginetextures.EngineTextureLoader;
import toniarts.openkeeper.tools.convert.textures.enginetextures.EngineTexturesFile;
import toniarts.openkeeper.tools.convert.wad.WadFile;
import toniarts.openkeeper.utils.PathUtils;
import com.jme3.asset.*;
import com.jme3.audio.AudioKey;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.annotation.Nullable;

/**
 * Locates original assets inside WAD and texture archives
 */
@SuppressWarnings({"rawtypes"})
public final class DK2AssetLocator implements AssetLocator {

    private static final Logger logger = System.getLogger(DK2AssetLocator.class.getSimpleName());

    private static final String[] SOUND_EXTENSIONS = {".map", ".sdt", ".SF2", ".sdT", ".MAP"};

    private String dungeonKeeperFolder;
    private Map<String, WadFile> wadFiles = new HashMap<>(5); // TODO
    private EngineTexturesFile engineTextures;

    // cache the reflection fields
    private static final Field assetKeyNameField;
    private static final Field assetKeyExtensionField;
    static {
        try {
            assetKeyNameField = AssetKey.class.getDeclaredField("name");
            assetKeyExtensionField = AssetKey.class.getDeclaredField("extension");
            assetKeyNameField.setAccessible(true);
            assetKeyExtensionField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

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

    private static String getFileName(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 && idx + 1 < path.length() ? path.substring(idx + 1) : path;
    }
    private static String getBaseName(String path) {
        int idx = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return (dot > 0) ? path.substring(idx + 1, dot) : path;
    }

    @Override
    public AssetInfo locate(AssetManager manager, AssetKey key) {
        final String path = key.getName(); // .replace("Thumbnails/MapColours.png", "GUI/Map/MapColours.png");

        final String filename = getFileName(path);
        final String basename = getBaseName(path);

        // Textures -> EngineTextures.dat or FrontEnd.WAD (loading screens)
        if (/*key instanceof TextureKey ||*/ path.startsWith(AssetsConverter.TEXTURES_FOLDER)) { // TODO: sprites also use TextureKey
            if (path.contains("LoadingScreen")) {
                var info = locateFromWad(manager, key, "Frontend", AssetsConverter.TEXTURES_FOLDER, "444");
                if (info != null)
                    return info;
            }
            var info = locateTextureFromEngineTextures(manager, key, path);
            if (info != null)
                return info;
            // if (path.contains("GUI"))
            // doesnt work out
            // there are only a few in there and they seem to be weirdly darker
            //info = locateFromWad(manager, key, path, "EngineTextures", AssetsConverter.TEXTURES_FOLDER);
            //if (info != null)
            //    return info;
            // TODO: Frontend contains many *.png, Thumbnails/*.bmp, LoadingScreen*.444, HiScores.dat, Titlescreen*.png, Titlescreen-Japanese.png, Titlescreen-Japanese.444,
            // TitleScreen/icon-Mature.gif
            return locateFromWad(manager, key, "Frontend", AssetsConverter.TEXTURES_FOLDER, null);
        }

        if (key instanceof ModelKey || path.startsWith(AssetsConverter.MODELS_FOLDER))
            return locateFromWad(manager, key, "Meshes", AssetsConverter.MODELS_FOLDER, "kmf");

        if (key instanceof MaterialKey || path.startsWith(AssetsConverter.MATERIALS_FOLDER))
            return null;

        // Sprites/cursors .png -> Sprite.WAD
        if (/*key instanceof TextureKey ||*/ path.startsWith(AssetsConverter.SPRITES_FOLDER) || path.startsWith(AssetsConverter.MOUSE_CURSORS_FOLDER))
            return locateSpriteFromWad(manager, key, basename);
            //return locateFromWad(manager, key, name, "Sprite", AssetsConverter.SPRITES_FOLDER);

        // Interface/Paths/ -> Paths.WAD
        // Converted are .csd files
        if (path.startsWith(AssetsConverter.PATHS_FOLDER))
            return locateFromWad(manager, key, "Paths", AssetsConverter.PATHS_FOLDER, null);

        // Sounds: look in DKII sound folders for matching originals
        if (key instanceof AudioKey || path.startsWith(AssetsConverter.SOUNDS_FOLDER))
            return locateSound(manager, key, basename);

        // .png files -> DKII map thumbnails
        if (/*key instanceof TextureKey ||*/ path.startsWith(AssetsConverter.THUMBNAILS_FOLDER))
            return readFileSystemAsset(manager, key, PathUtils.DKII_MAPS_FOLDER + "Thumbnails/" + basename + ".bmp", "bmp");

        // .properties files -> .str
        if (path.startsWith(AssetsConverter.TEXTS_FOLDER))
            return readFileSystemAsset(manager, key, PathUtils.DKII_TEXT_DEFAULT_FOLDER + basename + ".str", "str");

        // .fnt + .png files -> .bf4
        if (path.startsWith(AssetsConverter.FONTS_FOLDER))
            return readFileSystemAsset(manager, key, PathUtils.DKII_TEXT_DEFAULT_FOLDER + basename + ".bf4", "bf4");

        if (true)
        return null; // not found
        // Fallback: try generic WAD search for file name match across known WADs
        for (var wadFile : wadFiles.values()) {
            for (String wadEntry : wadFile.getWadFileEntries()) {
                if (wadEntry.toLowerCase(Locale.ROOT).endsWith(filename.toLowerCase(Locale.ROOT))) {
                    try {
                        byte[] data = wadFile.getFileData(wadEntry);
                        // Use found original extension for loader: guess extension from wadEntry
                        String foundExt = ""; // TODO: interesting idea, but at least Frontend has Titlescreen.psd + Titlescreen.png
                        int idx = wadEntry.lastIndexOf('.');
                        if (idx >= 0) foundExt = wadEntry.substring(idx + 1);
                        changeAssetKeyExtension(key, foundExt);
                        return assetInfo(manager, key, data);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to load fallback asset from WAD: " + path, e);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Generic WAD file lookup. Strips the folder prefix, looks up the entry by name (case-insensitive) in the specified WAD, and returns raw bytes.
     * The original key is preserved unless {@code extensionOverride} is set, which is needed so {@link DesktopAssetManager} dispatches to the correct loader.
     */
    private @Nullable AssetInfo locateFromWad(AssetManager manager, AssetKey key, String wadName, String folderPrefix, @Nullable String orgExt) {
        WadFile wad = wadFiles.get(wadName);

        try {
            String entryName = key.getName().substring(folderPrefix.length()); // TODO: support without prefix?
            if (orgExt != null && !entryName.endsWith(orgExt))
                entryName = changeExtension(entryName, orgExt);

            if (wad.contains(entryName)) {
                if (orgExt != null)
                   changeAssetKeyExtension(key, orgExt);
                return assetInfo(manager, key, wad.getFileData(entryName));
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load from " + wadName + " WAD: " + key.getName(), e);
        }
        return null;
    }

    private @Nullable AssetInfo locateTextureFromEngineTextures(AssetManager manager, AssetKey key, String name) {
        try {
            // Textures/filename.ext -> filenameMM0
            String textureName = name.substring(AssetsConverter.TEXTURES_FOLDER.length());
            textureName = textureName.substring(0, textureName.lastIndexOf('.')) + "MM0";

            if (engineTextures.getEntry(textureName) != null) {
                byte[] textureData = engineTextures.getTextureAsPng(textureName);
                if (textureData != null)
                    return assetInfo(manager, key, textureData);
            } else if (false) { // TODO: enable this
                // Get raw compressed texture data (serialized metadata + compressed longs)
                byte[] rawData = engineTextures.getRawTexture(textureName);
                if (rawData != null) {
                    // Use a .dkt key so OpenKeeperAssetManager dispatches to EngineTextureLoader
                    var dktKey = new TextureKey(AssetsConverter.TEXTURES_FOLDER + textureName + '.' + EngineTextureLoader.FILE_EXTENSION);
                    changeAssetKeyExtension(key, EngineTextureLoader.FILE_EXTENSION);
                    return assetInfo(manager, key, rawData);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load texture from EngineTextures: " + name, e);
        }
        return null;
    }

    /**
     * Locate sprite/image from Sprite.WAD. .spr entries are decoded to PNG in memory; direct image entries are returned as-is.
     */
    private @Nullable AssetInfo locateSpriteFromWad(AssetManager manager, AssetKey key, String resourceName) {

        if (false) {
        // direct .png lookup
        var info = locateFromWad(manager, key, "Sprite", AssetsConverter.SPRITES_FOLDER, null);
        if (info != null)
            return info;

        info = locateFromWad(manager, key, "Sprite", AssetsConverter.SPRITES_FOLDER, "spr");
        if (info == null)
            return null;
        try {
            byte[] pngData = new SprFile(info.openStream().readAllBytes()).getFrameAsPng(0);
        } catch (IOException e) { }
        }

        WadFile spriteWad = wadFiles.get("Sprite");
        try {
            String lowerResource = resourceName.toLowerCase(Locale.ROOT);
            for (String entryName : spriteWad.getWadFileEntries()) {
                String lowerEntry = entryName.toLowerCase(Locale.ROOT);

                // Sprites are stored as .spr archives; decode the first frame to PNG
                if (lowerEntry.endsWith(lowerResource + ".spr")) {
                    byte[] pngData = new SprFile(spriteWad.getFileData(entryName)).getFrameAsPng(0); // FIXME: always 0? no
                    changeAssetKeyExtension(key, "png");
                    return assetInfo(manager, key, pngData);
                }

                // direct image entries
                if (lowerEntry.endsWith(lowerResource) || lowerEntry.endsWith(lowerResource + ".png")) {
                    changeAssetKeyExtension(key, extensionOf(entryName));
                    return assetInfo(manager, key, spriteWad.getFileData(entryName));
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load sprite from Sprite.WAD: " + resourceName, e);
        }
        return null;
    }

    private @Nullable AssetInfo locateSound(AssetManager manager, AssetKey key, String resourceName) {
        // FIXME: resource name is e.g. "FE3D CLICK ON T"

        // "Sounds\Global\Track_1_1HD\1pt1-001.mp2"
        for (String cand : SOUND_EXTENSIONS) {
            try {
                String real = PathUtils.getRealFileName(dungeonKeeperFolder, PathUtils.DKII_SFX_FOLDER + resourceName + cand);
                byte[] data = Files.readAllBytes(Path.of(real));
                changeAssetKeyExtension(key, cand.substring(1));
                return assetInfo(manager, key, data);
            } catch (IOException ex) {
                // ignore and try next
            }
        }
        return null;
    }

    private @Nullable AssetInfo readFileSystemAsset(AssetManager manager, AssetKey key, String relativePath, String newExt) {
        try {
            String real = PathUtils.getRealFileName(dungeonKeeperFolder, relativePath);
            byte[] data = Files.readAllBytes(Path.of(real));
            changeAssetKeyExtension(key, newExt);
            return assetInfo(manager, key, data);
        } catch (IOException ex) {
            return null;
        }
    }

    private static AssetInfo assetInfo(AssetManager manager, AssetKey key, byte[] data) {
        return new WadAssetInfo(manager, key, new ByteArrayInputStream(data));
    }

    private static String extensionOf(String path) {
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1) : "";
    }

    private static String changeExtension(String filename, String newExt) {
        assert newExt.charAt(0) != '.';
        int idx = filename.lastIndexOf('.');
        if (idx >= 0)
            return filename.substring(0, idx + 1) + newExt;
        throw new IllegalArgumentException("Path has no extension: " + filename);
    }

    private static void changeAssetKeyExtension(AssetKey key, String ext) {
        assert ext.charAt(0) != '.';

        String name = changeExtension(key.getName(), ext);

        try {
            assetKeyNameField.setAccessible(true);
            assetKeyNameField.set(key, name);

            assetKeyExtensionField.setAccessible(true);
            assetKeyExtensionField.set(key, ext);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to set asset key extension to " + ext, e);
        }
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