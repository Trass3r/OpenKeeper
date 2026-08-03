/*
 * Copyright (C) 2014-2026 OpenKeeper contributors
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package toniarts.openkeeper.tools.convert;

import com.jme3.asset.AssetManager;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.system.JmeSystem;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import toniarts.openkeeper.utils.PathUtils;
import toniarts.openkeeper.utils.SettingUtils;

/**
 * Headless desktop-side asset preparation for platforms where the original
 * Dungeon Keeper II installation cannot be selected directly.
 */
public final class AssetConverterCli {

    private static final Logger logger = System.getLogger(AssetConverterCli.class.getName());

    private AssetConverterCli() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected the Dungeon Keeper II installation folder.");
        }

        String dungeonKeeperFolder = PathUtils.fixFilePath(args[0]);
        PathUtils.setDKIIFolder(dungeonKeeperFolder);
        if (!PathUtils.checkDkFolder(dungeonKeeperFolder)) {
            throw new IllegalArgumentException(
                    "Not a valid Dungeon Keeper II installation: " + dungeonKeeperFolder);
        }

        URL desktopConfig = Thread.currentThread().getContextClassLoader()
                .getResource("com/jme3/asset/Desktop.cfg");
        AssetManager assetManager = JmeSystem.newAssetManager(desktopConfig);
        assetManager.registerLocator(AssetsConverter.getAssetsFolder(), FileLocator.class);

        AssetsConverter converter = new AssetsConverter(dungeonKeeperFolder, assetManager) {
            @Override
            public void onUpdateStatus(
                    Integer currentProgress,
                    Integer totalProgress,
                    ConvertProcess process) {
                if (currentProgress == null || totalProgress == null || totalProgress == 0) {
                    logger.log(Level.INFO, "Starting {0}", process);
                    return;
                }
                if (currentProgress == 0
                        || currentProgress.equals(totalProgress)
                        || currentProgress % Math.max(1, totalProgress / 10) == 0) {
                    logger.log(Level.INFO, "{0}: {1}/{2}",
                            process, currentProgress, totalProgress);
                }
            }

            @Override
            public void onComplete(ConvertProcess process) {
                logger.log(Level.INFO, "Completed {0}", process);
            }

            @Override
            public void onError(Exception exception, ConvertProcess process) {
                logger.log(Level.ERROR, "Failed " + process, exception);
            }
        };

        if (!converter.convertAssets()) {
            throw new IllegalStateException("One or more asset conversion tasks failed.");
        }

        SettingUtils.getInstance().saveSettings();
        logger.log(Level.INFO, "Converted assets are ready at {0}",
                AssetsConverter.getAssetsFolder());
    }
}
