/*
 * Copyright (C) 2014-2017 OpenKeeper
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
package toniarts.openkeeper.game.state.loading;

import com.jme3.app.Application;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.TextureKey;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import toniarts.openkeeper.Main;
import toniarts.openkeeper.tools.convert.map.Player;
import toniarts.openkeeper.utils.MapThumbnailGenerator;

/**
 * Loading state with a multiple (4) loading bars
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public abstract class MultiplayerLoadingState extends LoadingState implements IPlayerLoadingProgress {

    private static final String[] availableScreens = {"M-LoadingScreen1024x768.png",            "M-LoadingScreen1280x1024.png", "M-LoadingScreen1600x1200.png", "M-LoadingScreen400x300.png",
        "M-LoadingScreen512x384.png", "M-LoadingScreen640x480.png", "M-LoadingScreen800x600.png"};
    private static final List<Integer> availableWidths = new ArrayList<>(availableScreens.length);
    private static final Map<Integer, String> screens = HashMap.newHashMap(availableScreens.length);
    private static final float BAR_OFFSET = 17.580f / 100;
    private static final float BAR_MARGIN = 5.825f / 100;
    private final List<Geometry> progressBars = new ArrayList<>(4);

    static {

        // Select by width
        Pattern p = Pattern.compile("M-LoadingScreen(?<width>\\d+)x(?<height>\\d+)\\.png");
        for (String screen : availableScreens) {
            Matcher m = p.matcher(screen);
            m.matches();
            int width = Integer.parseInt(m.group("width"));
            availableWidths.add(width);
            screens.put(width, screen);
        }
        Collections.sort(availableWidths);
    }

    public MultiplayerLoadingState(final Main app, String name) {
        super(app, name);
    }

    @Override
    public void initialize(AppStateManager stateManager, Application app) {
        super.initialize(stateManager, app);

        for (short i = Player.KEEPER1_ID; i <= Player.KEEPER4_ID; i++) {
            Geometry progressBar = new Geometry("ProgressBar", new Quad(0, imageHeight * BAR_HEIGHT));
            float margin = (i - Player.KEEPER1_ID) * BAR_MARGIN;
            progressBar.setLocalTranslation((Main.getUserSettings().getAppSettings().getWidth() - imageWidth) / 2 + imageWidth * BAR_X,
                    imageHeight - ((Main.getUserSettings().getAppSettings().getHeight() - imageHeight) / 2 + imageHeight * (BAR_Y - BAR_OFFSET + margin)) - imageHeight * BAR_HEIGHT, 0);
            Material mat = new Material(assetManager,
                    "Common/MatDefs/Misc/Unshaded.j3md");
            Color c = MapThumbnailGenerator.getPlayerColor(i);
            mat.setColor("Color", new ColorRGBA(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f));
            progressBar.setMaterial(mat);

            progressBars.add(progressBar);
        }

        // Attach the loading bars
        for (Geometry progressBar : progressBars) {
            this.app.getGuiNode().attachChild(progressBar);
        }
    }

    @Override
    protected Texture getLoadingScreenTexture() {

        // Use binary search to get the nearest resolution index
        int index = Collections.binarySearch(availableWidths, Main.getUserSettings().getAppSettings().getWidth());
        if (index < 0) {
            index = Math.min(availableWidths.size() - 1, ~index + 1);
        }

        // Load up the texture, there are few localized ones available
        String screen = screens.get(availableWidths.get(index));
        TextureKey texKey = new TextureKey(getLocalizedLoadingScreenTextureFolder() + screen);
        return assetManager.loadTexture(texKey);
    }

    /**
     * Set the loading progress
     *
     * @param progress 0.0 to 1.0, 1 being complete
     * @param playerId the player ID whose progress is updated
     */
    @Override
    public void setProgress(final float progress, final short playerId) {
        // Since this method is called from another thread,
        // we enqueue the changes to the progressbar to the update loop thread
        if (initialized && this.progress != Math.round(progress * 100)) {
            this.progress = Math.round(progress * 100);
            app.enqueue(() -> {
                // Adjust the progress bar
                Quad q = (Quad) progressBars.get(playerId - Player.KEEPER1_ID).getMesh();
                q.updateGeometry(imageWidth * BAR_WIDTH * progress, q.getHeight());
            });
        }
    }

    @Override
    public void cleanup() {

        // Remove the title screen
        for (Geometry progressBar : progressBars) {
            this.app.getGuiNode().detachChild(progressBar);
        }

        super.cleanup();
    }
}
