package toniarts.openkeeper.tools.convert;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLocator;
import com.jme3.asset.AssetManager;
import toniarts.openkeeper.tools.convert.wad.WadFile;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Locates assets inside WAD archives
 */
public final class WadLocator implements AssetLocator {

    private Path wadPath;
    private WadFile wadFile;
    private final Map<String, WadFile> wadFiles = new HashMap<>();

    @Override
    public void setRootPath(String rootPath) {
        wadPath = Path.of(rootPath);
        try {
            wadFile = new WadFile(wadPath);
            // Cache it
            wadFiles.put(wadPath.toString(), wadFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to open WAD file: " + rootPath, e);
        }
    }

    @Override
    public AssetInfo locate(AssetManager manager, AssetKey key) {
        String name = key.getName();
        WadFile wad = wadFile;

        // See if this is a direct path to WAD
        if (name.toLowerCase().endsWith(".wad")) {
            wad = wadFiles.computeIfAbsent(name, k -> {
                try {
                    return new WadFile(Path.of(k));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to open WAD file: " + k, e);
                }
            });
            return null;
        }

        try {
            // Get the file from WAD
            byte[] data = wad.getFileData(name);
            if (data == null) {
                return null;
            }

            return new AssetInfo(manager, key) {
                @Override
                public InputStream openStream() {
                    return new ByteArrayInputStream(data);
                }
            };
        } catch (Exception e) {
            return null;
        }
    }
}