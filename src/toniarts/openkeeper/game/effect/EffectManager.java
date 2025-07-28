/*
 * Copyright (C) 2014-2015 OpenKeeper
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
package toniarts.openkeeper.game.effect;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.lang.System.Logger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import toniarts.openkeeper.tools.convert.map.KwdFile;

/**
 * Modern effect manager that manages the lifecycle of visual effects.
 * This replaces the deprecated EffectManagerState and removes dependencies
 * on WorldState and other deprecated world types.
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public class EffectManager {

    private static final Logger logger = System.getLogger(EffectManager.class.getName());
    
    public static final int ROOM_CLAIM_ID = 2;

    private final KwdFile kwdFile;
    private final AssetManager assetManager;
    private final List<GameVisualEffect> activeEffects = new ArrayList<>();
    private final IEffectContextProvider contextProvider;

    public EffectManager(KwdFile kwdFile, AssetManager assetManager, IEffectContextProvider contextProvider) {
        this.kwdFile = kwdFile;
        this.assetManager = assetManager;
        this.contextProvider = contextProvider;
    }

    /**
     * Updates all active effects. Should be called every frame.
     *
     * @param tpf time per frame
     */
    public void update(float tpf) {
        Iterator<GameVisualEffect> iterator = activeEffects.iterator();
        // Maintain the effects (on every frame?)
        while (iterator.hasNext()) {
            GameVisualEffect visualEffect = iterator.next();
            if (!visualEffect.update(tpf)) {
                iterator.remove();
            }
        }
    }

    /**
     * Loads up a single particle effect, clearing any previous effects
     *
     * @param node the node to attach the effect to
     * @param location particle effect node location, maybe {@code null}
     * @param effectId the effect ID to load
     * @param infinite the effect should restart always, infinite effect (room
     * effects...?)
     */
    public void loadSingleEffect(Node node, Vector3f location, int effectId, boolean infinite) {
        clearActiveEffects();
        // Load the effect
        load(node, location, effectId, infinite);
    }

    /**
     * Clears all active effects
     */
    public void clearActiveEffects() {
        Iterator<GameVisualEffect> iterator = activeEffects.iterator();
        while (iterator.hasNext()) {
            GameVisualEffect visualEffect = iterator.next();
            visualEffect.removeEffect();
            visualEffect.update(-1);
        }
        activeEffects.clear();
    }

    /**
     * Loads up a particle effect
     *
     * @param node the node to attach the effect to
     * @param location particle effect node location, maybe {@code null}
     * @param effectId the effect ID to load
     * @param infinite the effect should restart always, infinite effect (room
     * effects...?)
     */
    public void load(Node node, Vector3f location, int effectId, boolean infinite) {
        // Load the effect
        if (effectId == 0) {
            return;
        }
        GameVisualEffect visualEffect = new GameVisualEffect(this, node, location, kwdFile.getEffect(effectId), infinite);
        activeEffects.add(visualEffect);
    }

    public AssetManager getAssetManager() {
        return assetManager;
    }

    public KwdFile getKwdFile() {
        return kwdFile;
    }

    public IEffectContextProvider getContextProvider() {
        return contextProvider;
    }
}