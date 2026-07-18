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

import static org.junit.jupiter.api.Assertions.*;

import com.jme3.asset.*;
import com.jme3.audio.AudioKey;
import com.jme3.system.JmeSystem;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import toniarts.openkeeper.tools.convert.textures.enginetextures.EngineTexturesFile;
import toniarts.openkeeper.tools.convert.wad.WadFile;

/**
 * Integration tests for {@link DK2AssetLocator} covering:
 * <ul>
 *   <li>Model lookup from Meshes.WAD (with .j3o→.kmf conversion)</li>
 *   <li>Texture lookup from EngineTextures</li>
 *   <li>Sprite lookup from Sprite.WAD</li>
 *   <li>Paths lookup from Paths.WAD</li>
 *   <li>Frontend lookup from Frontend.WAD</li>
 *   <li>Missing asset returns null</li>
 *   <li>Case-insensitive WAD lookup</li>
 *   <li>Cross-format keys (model .j3o→.kmf, texture .png→.dkt)</li>
 * </ul>
 *
 * <p>No original game files are required — minimal valid WAD and
 * EngineTextures archives are generated programmatically via
 * {@link TestFilesHelper}.
 */
class DK2AssetLocatorTest {

    @TempDir
    Path tempDir;

    private DK2AssetLocator locator;
    private AssetManager assetManager;

    @BeforeEach
    void setUp() {
        locator = new DK2AssetLocator();
        locator.setRootPath(tempDir.toString());
        assetManager = JmeSystem.newAssetManager(JmeSystem.getPlatformAssetConfigURL());
    }

    // ── Model lookup (Meshes.WAD) ──────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "Models/test.j3o, Models/test.kmf",
        "Models/test.kmf, Models/test.kmf",
        "Models/Test.kmf, Models/Test.kmf"
    })
    void locate_modelFromWad(String modelPath, String expectedName) throws IOException {
        writeMeshesWad("test.kmf", "dummy-kmf-data");

        var key = new ModelKey(modelPath);
        var info = locator.locate(assetManager, key);
        assertNotNull(info);

        assertEquals("kmf", info.getKey().getExtension(), "for correct asset loader dispatch");
        assertEquals(expectedName, info.getKey().getName());
        assertEquals("dummy-kmf-data", new String(info.openStream().readAllBytes(), StandardCharsets.ISO_8859_1));
    }

    @Test
    void locate_modelFromWad_missingModel_returnsNull() throws IOException {
        writeMeshesWad("existing.kmf", "dummy-kmf-data");

        var key = new ModelKey("Models/missing.j3o");
        AssetInfo info = locator.locate(assetManager, key);

        assertNull(info, "Should return null for missing model");
    }

    @Test
    void locate_modelFromWad_readableStream() throws IOException {
        String content = "hello-model-data";
        writeMeshesWad("test.kmf", content);

        var key = new ModelKey("Models/test.j3o");
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info);
        byte[] readBack = info.openStream().readAllBytes();
        assertEquals(content, new String(readBack, StandardCharsets.ISO_8859_1));
    }
 
    // ── Texture lookup (EngineTextures) ────────────────────────────────────

    @Test
    void locate_textureFromEngineTextures_pngRequest() throws IOException {
        writeEngineTextures("herobana1", 8, 8);

        var key = new TextureKey("Textures/herobana1.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find texture from EngineTextures");
        assertTrue(info.getKey().getName().endsWith(".png"));
    }

    @Test
    void locate_textureFromEngineTextures_missingTexture_returnsNull() throws IOException {
        writeEngineTextures("existing", 8, 8);

        var key = new TextureKey("Textures/missing.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNull(info, "Should return null for missing texture");
    }

    @Test
    void locate_textureFromEngineTextures_readableStream() throws IOException {
        writeEngineTextures("herobana1", 8, 8);

        var key = new TextureKey("Textures/herobana1.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info);
        byte[] data = info.openStream().readAllBytes();
        assertTrue(data.length > 0, "Should have non-empty raw texture data");
    }

    @Test
    void locate_loadingScreenFromFrontEndWad_pngRequest() throws IOException {
        byte[] loadingScreenData = TestFilesHelper.createLoadingScreenTexture(8, 8, false);
        writeWad("Frontend", "LoadingScreen8x8.444", loadingScreenData);

        var key = new TextureKey("Textures/LoadingScreen8x8.png");
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find loading screen from FrontEnd.WAD");
        assertEquals("444", info.getKey().getExtension());
        assertEquals("Textures/LoadingScreen8x8.444", info.getKey().getName());
        assertArrayEquals(loadingScreenData, info.openStream().readAllBytes());
    }

    // ── Sprite lookup (Sprite.WAD) ─────────────────────────────────────────

    @Test
    void locate_spriteFromWad() throws IOException {
        writeWad("Sprite", "mysprite.png", "sprite-png-data");

        var key = new TextureKey("Sprites/mysprite.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info, "Should find sprite from Sprite.WAD");
        assertEquals("Sprites/mysprite.png", info.getKey().getName());
    }

    @Test
    void locate_spriteFromWad_missingSprite_returnsNull() throws IOException {
        writeWad("Sprite", "existing.png", "data");

        var key = new TextureKey("Sprites/missing.png", false);
        AssetInfo info = locator.locate(assetManager, key);

        assertNull(info, "Should return null for missing sprite");
    }

    // ── Paths lookup (Paths.WAD) ───────────────────────────────────────────

    @Test
    void locate_pathFromWad() throws IOException {
        writeWad("Paths", "TestPath.dat", "path-data");

        AssetInfo info = locator.locate(assetManager, new AssetKey<>("Interface/Paths/TestPath.dat"));

        assertNotNull(info, "Should find path from Paths.WAD");
    }

    @Test
    void locate_pathFromWad_csdExtension() throws IOException {
        // Paths are converted to .csd, look up by .csd key
        writeWad("Paths", "TestPath.csd", "path-data");

        AssetInfo info = locator.locate(assetManager, new AssetKey<>("Interface/Paths/TestPath.csd"));

        assertNotNull(info, "Should find .csd path from Paths.WAD");
        assertEquals("Interface/Paths/TestPath.csd", info.getKey().getName());
    }

    @Test
    void locate_pathFromWad_subPath_returnsNull() throws IOException {
        writeWad("Paths", "TestPath.dat", "path-data");

        AssetInfo info = locator.locate(assetManager, new AssetKey<>("Interface/Paths/missing.dat"));

        assertNull(info, "Should return null for missing path");
    }

    // ── Frontend lookup (Frontend.WAD) ─────────────────────────────────────

    @Test
    void locate_frontendFromWad() throws IOException {
        writeWad("Frontend", "menu.png", "menu-data");

        AssetInfo info = locator.locate(assetManager, new TextureKey("Textures/menu.png"));
        assertNotNull(info, "Should find frontend asset from Frontend.WAD");
    }

    @Test
    void locate_frontendFromWad_missing_returnsNull() throws IOException {
        writeWad("Frontend", "menu.png", "menu-data");

        AssetInfo info = locator.locate(assetManager, new TextureKey("Frontend/missing.png"));

        assertNull(info, "Should return null for missing frontend asset");
    }

    // ── Unknown prefix ─────────────────────────────────────────────────────

    @Test
    void locate_unknownPrefix_returnsNull() {
        // No WAD files loaded for unknown path
        AssetInfo info = locator.locate(assetManager, new AssetKey<>("Unknown/thing.dat"));

        assertNull(info, "Should return null for unknown path prefix");
    }

    @Test
    void locate_soundsPrefix_returnsNull() throws Exception {
        File file = tempDir.resolve("Data/Sound/Sfx/test.mp2").toFile();
        file.getParentFile().mkdirs();
        file.createNewFile();

        var info = locator.locate(assetManager, new AudioKey("Sounds/Global/test.mp2"));
        assertNull(info, "DK2AssetLocator should find sounds"); // TODO:
    }

    @Test
    @Disabled
    void locate_fontsPrefix_returnsNull() {
        // DK2AssetLocator does not route Interface/Fonts/ — fonts come from
        // converted assets on disk via FlexibleFileLocator/FileLocator
        AssetInfo info = locator.locate(assetManager, new AssetKey<>("Interface/Fonts/test.fnt"));
        assertNull(info, "DK2AssetLocator should not route Interface/Fonts/");
    }

    // ── Multiple entries in WAD ────────────────────────────────────────────

    @Test
    void locate_multipleEntriesInWad() throws IOException {
        String[] names = {"model_a.kmf", "model_b.kmf", "model_c.kmf"};
        byte[][] datas = {"data-a".getBytes(), "data-b".getBytes(), "data-c".getBytes()};
        Path wadPath = tempDir.resolve("Meshes.WAD");
        TestFilesHelper.createWadFile(wadPath, names, datas);
        var wad = new WadFile(wadPath);
        locator.putWadFile("Meshes", wad);

        // Should find the second entry
        var key = new ModelKey("Models/model_b.j3o");
        AssetInfo info = locator.locate(assetManager, key);

        assertNotNull(info);
        assertEquals("data-b", new String(info.openStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void writeMeshesWad(String entryName, String content) throws IOException {
        Path wadPath = tempDir.resolve("Meshes.WAD");
        TestFilesHelper.createWadFile(wadPath, entryName, content.getBytes(StandardCharsets.UTF_8));
        locator.putWadFile("Meshes", new WadFile(wadPath));
    }

    private void writeWad(String wadName, String entryName, String content) throws IOException {
        writeWad(wadName, entryName, content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeWad(String wadName, String entryName, byte[] data) throws IOException {
        Path wadPath = tempDir.resolve(wadName + ".WAD");
        TestFilesHelper.createWadFile(wadPath, entryName, data);
        locator.putWadFile(wadName, new WadFile(wadPath));
    }

    private void writeEngineTextures(String textureName, int width, int height) throws IOException {
        Path datPath = tempDir.resolve("DK2TextureCache/EngineTextures.dat");
        Path dirPath = tempDir.resolve("DK2TextureCache/EngineTextures.dir");
        TestFilesHelper.createEngineTextures(datPath, dirPath, textureName, width, height);
        locator.setEngineTexturesFile(new EngineTexturesFile(datPath));
    }
}
