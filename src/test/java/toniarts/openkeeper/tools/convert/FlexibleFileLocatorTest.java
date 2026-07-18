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
import com.jme3.asset.AssetManager;
import com.jme3.asset.ModelKey;
import com.jme3.asset.TextureKey;
import com.jme3.system.JmeSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FlexibleFileLocator} covering:
 * <ul>
 *   <li>Exact file match (file exists with requested extension)</li>
 *   <li>Alternative extension match (e.g. DDS override for a PNG request)</li>
 *   <li>No file found (requested extension nor any alternative exists)</li>
 *   <li>Model extension probing (.kmf → .gltf/.glb/.j3o)</li>
 *   <li>Extension priority order</li>
 * </ul>
 */
class FlexibleFileLocatorTest {

    @TempDir
    Path tempDir;

    private FlexibleFileLocator locator;
    private AssetManager assetManager;

    @BeforeEach
    void setUp() {
        locator = new FlexibleFileLocator();
        locator.setRootPath(tempDir.toString());

        // Create a minimal AssetManager for AssetInfo construction.
        // This does not initialize any OpenGL context — it just reads the
        // Desktop.cfg configuration from the jme3-desktop classpath.
        assetManager = JmeSystem.newAssetManager(
                Thread.currentThread().getContextClassLoader()
                        .getResource("com/jme3/asset/Desktop.cfg"));
    }

    // ── Exact match ────────────────────────────────────────────────────────

    @Test
    void locate_exactMatch_pngFound() throws IOException {
        Path texturesDir = tempDir.resolve("Textures");
        Files.createDirectories(texturesDir);
        Files.createFile(texturesDir.resolve("test.png"));

        TextureKey key = new TextureKey("Textures/test.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find exact PNG match");
        assertEquals("Textures/test.png", info.getKey().getName());
        assertInstanceOf(TextureKey.class, info.getKey());
    }

    @Test
    void locate_exactMatch_kmfFound() throws IOException {
        Path modelsDir = tempDir.resolve("Models");
        Files.createDirectories(modelsDir);
        Files.createFile(modelsDir.resolve("test.kmf"));

        ModelKey key = new ModelKey("Models/test.kmf");
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find exact KMF match");
        assertEquals("Models/test.kmf", info.getKey().getName());
    }

    // ── Alternative extension: textures ────────────────────────────────────

    @Test
    void locate_alternativeExtension_ddsFound() throws IOException {
        // Create a .dds instead of .png
        Path texturesDir = tempDir.resolve("Textures");
        Files.createDirectories(texturesDir);
        Files.createFile(texturesDir.resolve("test.dds"));

        TextureKey key = new TextureKey("Textures/test.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find DDS as alternative to PNG");
        assertEquals("Textures/test.dds", info.getKey().getName());
        assertInstanceOf(TextureKey.class, info.getKey(),
                "Key should still be a TextureKey for alternative texture ext");
    }

    @Test
    void locate_alternativeExtension_bmpFound() throws IOException {
        Path texturesDir = tempDir.resolve("Textures");
        Files.createDirectories(texturesDir);
        Files.createFile(texturesDir.resolve("test.bmp"));

        TextureKey key = new TextureKey("Textures/test.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find BMP as alternative to PNG");
        assertEquals("Textures/test.bmp", info.getKey().getName());
    }

    @Test
    void locate_alternativeExtension_jpgFound() throws IOException {
        Path texturesDir = tempDir.resolve("Textures");
        Files.createDirectories(texturesDir);
        Files.createFile(texturesDir.resolve("test.jpg"));

        TextureKey key = new TextureKey("Textures/test.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find JPG as alternative to PNG");
        assertEquals("Textures/test.jpg", info.getKey().getName());
    }

    @Test
    void locate_alternativeExtension_tgaFound() throws IOException {
        Path texturesDir = tempDir.resolve("Textures");
        Files.createDirectories(texturesDir);
        Files.createFile(texturesDir.resolve("test.tga"));

        TextureKey key = new TextureKey("Textures/test.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find TGA as alternative to PNG");
        assertEquals("Textures/test.tga", info.getKey().getName());
    }

    // ── Alternative extension: models ──────────────────────────────────────

    @Test
    void locate_alternativeExtension_gltfFound() throws IOException {
        Path modelsDir = tempDir.resolve("Models");
        Files.createDirectories(modelsDir);
        Files.createFile(modelsDir.resolve("test.gltf"));

        ModelKey key = new ModelKey("Models/test.kmf");
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find GLTF as alternative to KMF");
        assertEquals("Models/test.gltf", info.getKey().getName());
        assertInstanceOf(ModelKey.class, info.getKey(),
                "Key should still be a ModelKey for alternative model ext");
    }

    @Test
    void locate_alternativeExtension_glbFound() throws IOException {
        Path modelsDir = tempDir.resolve("Models");
        Files.createDirectories(modelsDir);
        Files.createFile(modelsDir.resolve("test.glb"));

        ModelKey key = new ModelKey("Models/test.kmf");
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find GLB as alternative to KMF");
        assertEquals("Models/test.glb", info.getKey().getName());
    }

    @Test
    void locate_alternativeExtension_j3oFound() throws IOException {
        Path modelsDir = tempDir.resolve("Models");
        Files.createDirectories(modelsDir);
        Files.createFile(modelsDir.resolve("test.j3o"));

        ModelKey key = new ModelKey("Models/test.kmf");
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find J3O as alternative to KMF");
        assertEquals("Models/test.j3o", info.getKey().getName());
    }

    @Test
    void locate_j3oRequest_gltfOverrideFound() throws IOException {
        // When .j3o is requested, .gltf and .glb are probed as alternatives
        Path modelsDir = tempDir.resolve("Models");
        Files.createDirectories(modelsDir);
        Files.createFile(modelsDir.resolve("test.gltf"));

        ModelKey key = new ModelKey("Models/test.j3o");
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find GLTF as alternative to J3O");
        assertEquals("Models/test.gltf", info.getKey().getName());
    }

    // ── Priority: first match wins ─────────────────────────────────────────

    @Test
    void locate_alternativeExtensionPriority_firstMatchWins() throws IOException {
        // Create both .dds and .bmp — .dds should be returned first (.dds has
        // higher priority than .bmp in FlexibleFileLocator.TEXTURE_EXTENSIONS)
        Path texturesDir = tempDir.resolve("Textures");
        Files.createDirectories(texturesDir);
        Files.createFile(texturesDir.resolve("test.dds"));
        Files.createFile(texturesDir.resolve("test.bmp"));

        TextureKey key = new TextureKey("Textures/test.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find the highest-priority alternative");
        assertEquals("Textures/test.dds", info.getKey().getName(),
                "DDS has higher priority than BMP, so it should be returned first");
    }

    @Test
    void locate_exactMatchTakesPriorityOverAlternative() throws IOException {
        // Both an exact .png and an alternative .dds exist — the exact match
        // should take priority
        Path texturesDir = tempDir.resolve("Textures");
        Files.createDirectories(texturesDir);
        Files.createFile(texturesDir.resolve("test.png"));
        Files.createFile(texturesDir.resolve("test.dds"));

        TextureKey key = new TextureKey("Textures/test.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info);
        assertEquals("Textures/test.png", info.getKey().getName(),
                "Exact match should take priority over alternative extensions");
    }

    // ── No file found ──────────────────────────────────────────────────────

    @Test
    void locate_noFileFound_returnsNull() {
        TextureKey key = new TextureKey("Textures/missing.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNull(info, "Should return null when neither the requested file "
                + "nor any alternative exists");
    }

    @Test
    void locate_noModelFileFound_returnsNull() {
        ModelKey key = new ModelKey("Models/missing.kmf");
        AssetInfo info = locator.locate(assetManager, key);

        assertNull(info, "Should return null when no model file found");
    }

    // ── Edge cases ─────────────────────────────────────────────────────────

    @Test
    void locate_nonexistentSubdirectory_returnsNull() {
        // File doesn't exist because the directory doesn't even exist
        TextureKey key = new TextureKey("NonexistentDir/test.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNull(info, "Should return null when the directory doesn't exist");
    }

    // ── Sounds (.mp2) ──────────────────────────────────────────────────────

    @Test
    void locate_sound_mp2_exactMatch() throws IOException {
        Path soundsDir = tempDir.resolve(AssetsConverter.SOUNDS_FOLDER);
        Files.createDirectories(soundsDir);
        Files.createFile(soundsDir.resolve("test.mp2"));

        AssetInfo info = locator.locate(assetManager,
                new com.jme3.asset.AssetKey("Sounds/test.mp2"));

        assertNotNull(info, "Should find exact .mp2 match");
        assertEquals("Sounds/test.mp2", info.getKey().getName());
    }

    @Test
    void locate_sound_mp2_missing_returnsNull() throws IOException {
        Path soundsDir = tempDir.resolve(AssetsConverter.SOUNDS_FOLDER);
        Files.createDirectories(soundsDir);
        Files.createFile(soundsDir.resolve("existing.mp2"));

        AssetInfo info = locator.locate(assetManager,
                new com.jme3.asset.AssetKey("Sounds/missing.mp2"));

        assertNull(info, "Should return null for missing .mp2");
    }

    // ── Paths (.csd) ───────────────────────────────────────────────────────

    @Test
    void locate_path_csd_exactMatch() throws IOException {
        Path pathsDir = tempDir.resolve(AssetsConverter.PATHS_FOLDER);
        Files.createDirectories(pathsDir);
        Files.createFile(pathsDir.resolve("TestPath.csd"));

        AssetInfo info = locator.locate(assetManager,
                new com.jme3.asset.AssetKey("Interface/Paths/TestPath.csd"));

        assertNotNull(info, "Should find exact .csd match");
        assertEquals("Interface/Paths/TestPath.csd", info.getKey().getName());
    }

    @Test
    void locate_path_csd_missing_returnsNull() throws IOException {
        Path pathsDir = tempDir.resolve(AssetsConverter.PATHS_FOLDER);
        Files.createDirectories(pathsDir);
        Files.createFile(pathsDir.resolve("existing.csd"));

        AssetInfo info = locator.locate(assetManager,
                new com.jme3.asset.AssetKey("Interface/Paths/missing.csd"));

        assertNull(info, "Should return null for missing .csd");
    }

    // ── Fonts (.fnt + .png) ────────────────────────────────────────────────

    @Test
    void locate_font_fnt_exactMatch() throws IOException {
        Path fontsDir = tempDir.resolve(AssetsConverter.FONTS_FOLDER);
        Files.createDirectories(fontsDir);
        Files.createFile(fontsDir.resolve("test.fnt"));

        AssetInfo info = locator.locate(assetManager,
                new com.jme3.asset.AssetKey("Interface/Fonts/test.fnt"));

        assertNotNull(info, "Should find exact .fnt match");
        assertEquals("Interface/Fonts/test.fnt", info.getKey().getName());
    }

    @Test
    void locate_font_fnt_missing_returnsNull() throws IOException {
        Path fontsDir = tempDir.resolve(AssetsConverter.FONTS_FOLDER);
        Files.createDirectories(fontsDir);
        Files.createFile(fontsDir.resolve("existing.fnt"));

        AssetInfo info = locator.locate(assetManager,
                new com.jme3.asset.AssetKey("Interface/Fonts/missing.fnt"));

        assertNull(info, "Should return null for missing .fnt");
    }

    @Test
    void locate_font_png_exactMatch() throws IOException {
        // Fonts can also reference .png texture atlases
        Path fontsDir = tempDir.resolve(AssetsConverter.FONTS_FOLDER);
        Files.createDirectories(fontsDir);
        Files.createFile(fontsDir.resolve("fonttex.png"));

        TextureKey key = new TextureKey("Interface/Fonts/fonttex.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find font PNG texture");
        assertEquals("Interface/Fonts/fonttex.png", info.getKey().getName());
        assertInstanceOf(TextureKey.class, info.getKey());
    }

    @Test
    void locate_font_png_missing_returnsNull() throws IOException {
        Path fontsDir = tempDir.resolve(AssetsConverter.FONTS_FOLDER);
        Files.createDirectories(fontsDir);
        Files.createFile(fontsDir.resolve("existing.png"));

        TextureKey key = new TextureKey("Interface/Fonts/missing.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNull(info, "Should return null for missing font PNG");
    }

    @Test
    void locate_font_png_alternativeDdsOverride() throws IOException {
        // When an override .dds exists for a font PNG, it should be found
        Path fontsDir = tempDir.resolve(AssetsConverter.FONTS_FOLDER);
        Files.createDirectories(fontsDir);
        Files.createFile(fontsDir.resolve("fonttex.dds"));

        TextureKey key = new TextureKey("Interface/Fonts/fonttex.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find DDS override for font PNG");
        assertEquals("Interface/Fonts/fonttex.dds", info.getKey().getName());
    }
}
