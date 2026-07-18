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
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.asset.TextureKey;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.plugins.AWTLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test verifying that the {@link OpenKeeperAssetManager} with
 * {@link FlexibleFileLocator} can load textures through the full pipeline:
 * locator → asset manager → texture loader.
 *
 * <p>Tests cover three scenarios:
 * <ol>
 *   <li><b>Override asset present</b> — a valid PNG texture exists at the
 *       requested path and is loaded successfully.</li>
 *   <li><b>Override asset NOT present</b> — no file exists, and the asset
 *       manager throws {@link AssetNotFoundException}.</li>
 *   <li><b>Override asset with different extension</b> — an override file
 *       exists with a different extension (e.g. {@code .dds} instead of
 *       {@code .png}), and the {@link FlexibleFileLocator} finds it.</li>
 * </ol>
 *
 * <p>No original game files are required — test fixtures are created
 * programmatically in temporary directories.
 */
class AssetLoadingIntegrationTest {

    @TempDir
    Path tempDir;

    private OpenKeeperAssetManager assetManager;

    @BeforeEach
    void setUp() {
        // Create an OpenKeeperAssetManager and register the loaders we need.
        // We register AWTLoader for standard image formats and a custom
        // DummyDdsLoader for .dds files so we can test the cross-format
        // dispatch path without needing valid DDS fixture files.
        assetManager = new OpenKeeperAssetManager();
        assetManager.registerLoader(AWTLoader.class, "png", "jpg", "bmp", "gif", "tga");
        assetManager.registerLoader(DummyDdsLoader.class, "dds");

        // Register locators pointing at our temp directory
        assetManager.registerLocator(tempDir.toString(), FileLocator.class);
        assetManager.registerLocator(tempDir.toString(), FlexibleFileLocator.class);
    }

    @AfterEach
    void tearDown() {
        // Prevent LWJGL cleanup errors in headless environments
    }

    // ── Override asset present ─────────────────────────────────────────────

    @Test
    void loadTexture_validPng_succeeds() throws IOException {
        Path texturesDir = tempDir.resolve("Textures");
        Files.createDirectories(texturesDir);
        createValidPng(texturesDir.resolve("test.png"));

        Texture tex = assetManager.loadTexture("Textures/test.png");

        assertNotNull(tex, "Loaded texture should not be null");
        assertEquals("Textures/test.png", tex.getKey().getName());
    }

    @Test
    void loadTexture_validPng_viaTextureKey_succeeds() throws IOException {
        Path texturesDir = tempDir.resolve("Textures");
        Files.createDirectories(texturesDir);
        createValidPng(texturesDir.resolve("test.png"));

        TextureKey key = new TextureKey("Textures/test.png", false);
        Texture tex = assetManager.loadTexture(key);

        assertNotNull(tex, "Loaded texture should not be null");
        assertEquals("Textures/test.png", tex.getKey().getName());
    }

    // ── Override asset NOT present ─────────────────────────────────────────

    @Test
    void loadTexture_missingTexture_throwsAssetNotFoundException() {
        AssetNotFoundException ex = assertThrows(
                AssetNotFoundException.class,
                () -> assetManager.loadTexture("Textures/missing.png"),
                "Should throw AssetNotFoundException when texture does not exist");

        assertTrue(ex.getMessage().contains("Textures/missing.png"),
                "Exception message should mention the missing asset path");
    }

    @Test
    void loadTexture_missingTextureViaTextureKey_throwsAssetNotFoundException() {
        TextureKey key = new TextureKey("Textures/missing.png", false);

        AssetNotFoundException ex = assertThrows(
                AssetNotFoundException.class,
                () -> assetManager.loadTexture(key),
                "Should throw AssetNotFoundException when texture does not exist");

        assertTrue(ex.getMessage().contains("Textures/missing.png"),
                "Exception message should mention the missing asset path");
    }

    @Test
    void locateAsset_missingTexture_returnsNull() {
        TextureKey key = new TextureKey("Textures/missing.png", false);
        AssetInfo info = assetManager.locateAsset(key);

        assertNull(info, "locateAsset should return null when no locator "
                + "can find the file");
    }

    // ── Override asset with different extension ────────────────────────────

    @Test
    void locateAsset_ddsOverride_foundByFlexibleLocator() throws IOException {
        // Create a .dds file — the DummyDdsLoader will handle it
        Path texturesDir = tempDir.resolve("Textures");
        Files.createDirectories(texturesDir);
        Files.writeString(texturesDir.resolve("test.dds"), "dummy-dds-content");

        TextureKey key = new TextureKey("Textures/test.png", false);
        AssetInfo info = assetManager.locateAsset(key);

        assertNotNull(info, "FlexibleFileLocator should find the .dds override");
        assertEquals("Textures/test.dds", info.getKey().getName(),
                "AssetInfo key should reflect the actual file format found");
    }

    @Test
    void loadTexture_ddsOverride_loadsSuccessfully() throws IOException {
        // Create a .dds file — the DummyDdsLoader will handle it
        Path texturesDir = tempDir.resolve("Textures");
        Files.createDirectories(texturesDir);
        Files.writeString(texturesDir.resolve("test.dds"), "dummy-dds-content");

        TextureKey key = new TextureKey("Textures/test.png", false);
        Texture tex = assetManager.loadTexture(key);

        assertNotNull(tex, "Should load the DDS override successfully");
        assertEquals("Textures/test.dds", tex.getKey().getName(),
                "Texture key should reflect the actual format loaded");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Creates a minimal 1×1 valid PNG file at the given path.
     */
    static void createValidPng(Path path) throws IOException {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFFFF0000); // single red pixel
        Files.createDirectories(path.getParent());
        ImageIO.write(img, "PNG", path.toFile());
    }

    /**
     * A minimal {@link AssetLoader} for {@code .dds} files that returns a
     * dummy {@link Image} without parsing actual DDS data. This allows
     * testing the full locator → manager → loader pipeline without needing
     * valid DDS fixture files.
     *
     * <p>Returns {@link Image} (not {@link com.jme3.texture.Texture2D})
     * because JME's {@code TextureProcessor} casts loaded textures to
     * {@code Image} during post-processing.
     */
    public static final class DummyDdsLoader implements AssetLoader {

        @Override
        public Object load(AssetInfo assetInfo) throws IOException {
            // Just consume the stream so we know it was opened
            try (InputStream is = assetInfo.openStream()) {
                is.readAllBytes();
            }
            return new Image();
        }
    }
}
