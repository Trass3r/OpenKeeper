/*
 * Copyright (C) 2014-2026 OpenKeeper contributors
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package toniarts.openkeeper.desktop;

import com.jme3.system.AppSettings;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import javax.imageio.ImageIO;
import toniarts.openkeeper.Main;

/**
 * Desktop-only application icon loading, kept out of the shared game class.
 */
public final class DesktopApplicationIcons {

    private static final Logger logger = System.getLogger(DesktopApplicationIcons.class.getName());

    private DesktopApplicationIcons() {
    }

    public static void applyTo(AppSettings settings) {
        settings.setIcons(load());
    }

    public static BufferedImage[] load() {
        ImageIO.setUseCache(false);
        try {
            return new BufferedImage[]{
                read("/Icons/openkeeper256.png"),
                read("/Icons/openkeeper256.png"),
                read("/Icons/openkeeper128.png"),
                read("/Icons/openkeeper64.png"),
                read("/Icons/openkeeper48.png"),
                read("/Icons/openkeeper32.png"),
                read("/Icons/openkeeper24.png"),
                read("/Icons/openkeeper16.png")
            };
        } catch (IOException ex) {
            logger.log(Level.ERROR, "Failed to load the application icons!", ex);
            return null;
        }
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream input = Main.class.getResourceAsStream(path);
                BufferedInputStream buffered = new BufferedInputStream(input)) {
            return ImageIO.read(buffered);
        }
    }
}
