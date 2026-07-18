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

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLocator;
import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.asset.ModelKey;
import com.jme3.asset.plugins.FileLocator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An {@link AssetLocator} that probes for multiple file extensions on disk.
 * <p>
 * When an asset is requested with one extension (e.g., {@code .png}),
 * this locator also checks for alternative extensions (e.g., {@code .dds},
 * {@code .bmp}) on disk. If an alternative is found, it returns the
 * asset with the alternative extension's key, allowing
 * {@link OpenKeeperAssetManager} to dispatch the correct loader.
 * <p>
 * This enables modders to provide replacement assets in different formats
 * without changing the codebase. For example, a high-res DDS texture can
 * replace a PNG, and a glTF model can replace a KMF model.
 */
public final class FlexibleFileLocator implements AssetLocator {

    private String rootPath;

    /**
     * Extension probe order for textures. Earlier entries take priority.
     * The requested extension is always tried first.
     */
    private static final String[] TEXTURE_EXTENSIONS = {".dds", ".bmp", ".jpg", ".tga"};

    /**
     * Extension probe order for models. Earlier entries take priority.
     */
    private static final String[] MODEL_EXTENSIONS = {".gltf", ".glb", ".j3o"};

    /**
     * Map of extension → list of alternative extensions to probe.
     */
    private static final Map<String, String[]> ALTERNATIVE_EXTENSIONS = new LinkedHashMap<>();

    static {
        // When .png is requested, also try texture formats
        ALTERNATIVE_EXTENSIONS.put("png", TEXTURE_EXTENSIONS);
        // When .kmf is requested, also try model formats
        ALTERNATIVE_EXTENSIONS.put("kmf", MODEL_EXTENSIONS);
        // When .j3o is requested, also try model formats (but keep .j3o first via FileLocator)
        ALTERNATIVE_EXTENSIONS.put("j3o", new String[]{".gltf", ".glb"});
    }

    @Override
    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    @Override
    public AssetInfo locate(AssetManager manager, AssetKey key) {
        String name = key.getName();
        String ext = key.getExtension();

        // Try the exact path first (standard behavior)
        Path exactFile = Paths.get(rootPath, name);
        if (Files.isRegularFile(exactFile)) {
            return createAssetInfo(manager, key, exactFile.toFile());
        }

        // Probe for alternative extensions
        String[] alternatives = ALTERNATIVE_EXTENSIONS.get(ext);
        if (alternatives != null) {
            // Strip the existing extension and try alternatives
            String baseName = name.substring(0, name.lastIndexOf('.'));
            for (String altExt : alternatives) {
                String altName = baseName + altExt;
                Path altFile = Paths.get(rootPath, altName);
                if (Files.isRegularFile(altFile)) {
                    // Return with the alternative extension's key so
                    // OpenKeeperAssetManager dispatches the right loader
                    AssetKey altKey = createAlternateKey(key, baseName, altExt);
                    return createAssetInfo(manager, altKey, altFile.toFile());
                }
            }
        }

        return null;
    }

    /**
     * Creates an appropriate AssetKey subtype for the alternative format.
     */
    private static AssetKey createAlternateKey(AssetKey original, String baseName, String altExt) {
        String altName = baseName + altExt;
        if (original instanceof TextureKey) {
            return new TextureKey(altName, ((TextureKey) original).isFlipY());
        } else if (original instanceof ModelKey) {
            return new ModelKey(altName);
        }
        return new AssetKey(altName);
    }

    /**
     * Creates a simple AssetInfo wrapping a disk file.
     */
    private static AssetInfo createAssetInfo(AssetManager manager, AssetKey key, File file) {
        return new AssetInfo(manager, key) {
            @Override
            public java.io.InputStream openStream() {
                try {
                    return Files.newInputStream(file.toPath());
                } catch (IOException e) {
                    throw new RuntimeException("Failed to open: " + file, e);
                }
            }
        };
    }
}
