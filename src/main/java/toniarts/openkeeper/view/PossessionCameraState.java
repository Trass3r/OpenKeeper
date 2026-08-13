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
package toniarts.openkeeper.view;

import com.jme3.app.Application;
import com.jme3.app.state.AppStateManager;
import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.jme3.math.Quaternion;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import java.lang.System.Logger;
import toniarts.openkeeper.Main;
import toniarts.openkeeper.game.FunnyCameraContol;
import toniarts.openkeeper.game.data.Settings;
import toniarts.openkeeper.game.component.CreatureComponent;
import toniarts.openkeeper.game.component.Position;
import toniarts.openkeeper.game.state.AbstractPauseAwareState;
import toniarts.openkeeper.game.state.PlayerState;
import toniarts.openkeeper.tools.convert.map.Creature;

/**
 *
 * @author ArchDemon
 */
public final class PossessionCameraState extends AbstractPauseAwareState implements ActionListener, AnalogListener {

    private static final Logger logger = System.getLogger(PossessionCameraState.class.getName());
    
    private Main app;
    private InputManager inputManager;
    private AppStateManager stateManager;

    private EntityId target;
    private Creature creature;
    public Vector2f mousePosition = Vector2f.ZERO;

    private PossessionCamera camera;
    //private Integer specialKey = null;

    private static final String POSSESSION = "POSSESSION_";

    private static final String CAMERA_VIEW_LEFT = "CAMERA_VIEW_LEFT";
    private static final String CAMERA_VIEW_UP = "CAMERA_VIEW_UP";
    private static final String CAMERA_VIEW_RIGHT = "CAMERA_VIEW_RIGHT";
    private static final String CAMERA_VIEW_DOWN = "CAMERA_VIEW_DOWN";

    private static final String SPECIAL_KEY_CONTROL = "SPECIAL_KEY_CONTROL";
    private static final String SPECIAL_KEY_ALT = "SPECIAL_KEY_ALT";
    private static final String SPECIAL_KEY_SHIFT = "SPECIAL_KEY_SHIFT";

    private static final String[] mappings = new String[]{
        // view
        CAMERA_VIEW_LEFT,
        CAMERA_VIEW_UP,
        CAMERA_VIEW_RIGHT,
        CAMERA_VIEW_DOWN,
        // movement
        POSSESSION + Settings.Setting.CAMERA_UP.name(),
        POSSESSION + Settings.Setting.CAMERA_DOWN.name(),
        POSSESSION + Settings.Setting.CAMERA_LEFT.name(),
        POSSESSION + Settings.Setting.CAMERA_RIGHT.name(),
        Settings.Setting.POSSESSED_RUN.name(),
        Settings.Setting.POSSESSED_CREEP.name(),
        // attack
        Settings.Setting.POSSESSED_SELECT_MELEE.name(),
        Settings.Setting.POSSESSED_SELECT_SPELL_1.name(),
        Settings.Setting.POSSESSED_SELECT_SPELL_2.name(),
        Settings.Setting.POSSESSED_SELECT_SPELL_3.name(),
        Settings.Setting.POSSESSED_SELECT_ABILITY_1.name(),
        Settings.Setting.POSSESSED_SELECT_ABILITY_2.name(),
        // group
        Settings.Setting.POSSESSED_SELECT_GROUP.name(),
        Settings.Setting.POSSESSED_REMOVE_FROM_GROUP.name(),
        Settings.Setting.POSSESSED_PICK_LOCK_OR_DISARM.name(), // special
    //SPECIAL_KEY_CONTROL,
    //SPECIAL_KEY_ALT,
    //SPECIAL_KEY_SHIFT,
    };

    public PossessionCameraState(boolean enabled) {
        super.setEnabled(enabled);
    }

    @Override
    public void initialize(final AppStateManager stateManager, final Application app) {
        super.initialize(stateManager, app);

        this.app = (Main) app;
        this.stateManager = stateManager;
        inputManager = this.app.getInputManager();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        if (enabled) {
            // The camera
            camera = new PossessionCamera(app.getCamera(), creature.getAttributes().getSpeed(), creature.getFirstPersonOscillateScale());
            loadCameraStartLocation();

            Spatial targetSpatial = app.getStateManager().getState(PlayerEntityViewState.class).getEntitySpatial(target);
            FunnyCameraContol fcc;
            if (targetSpatial != null) {
                fcc = new FunnyCameraContol(app.getCamera(), targetSpatial);
            } else {
                fcc = new FunnyCameraContol(app.getCamera());
            }
            fcc.setLookAtOffset(new Vector3f(0, creature.getAttributes().getEyeHeight(), 0));
            fcc.setHeight(creature.getAttributes().getHeight());
            fcc.setDistance(1.5f);

            // The controls
            registerInput();
        } else {
            unregisterInput();
            Spatial targetSpatial = app.getStateManager().getState(PlayerEntityViewState.class).getEntitySpatial(target);
            if (targetSpatial != null) {
                targetSpatial.removeControl(FunnyCameraContol.class);
            }
            target = null;
        }
    }

    /**
     * Load the initial camera position
     */
    private void loadCameraStartLocation() {
        Camera cam = app.getCamera();
        if (target != null && creature != null) {
            PlayerState ps = app.getStateManager().getState(PlayerState.class);
            if (ps != null) {
                EntityData ed = ps.getEntityData();
                Position pos = ed.getComponent(target, Position.class);
                if (pos != null) {
                    cam.setLocation(pos.position.add(0, creature.getAttributes().getEyeHeight(), 0));
                    cam.setRotation(new Quaternion().fromAngles(0, pos.rotation, 0));
                    cam.setFrustumPerspective(45, cam.getWidth() / cam.getHeight(), 0.1f, creature.getAttributes().getDistanceCanSee() * 10);
                    return;
                }
            }
        }
        cam.setAxes(Vector3f.UNIT_X, Vector3f.UNIT_Y, Vector3f.UNIT_Z);
    }

    private void registerInput() {

        // Add the keys
        Settings settings = Main.getUserSettings();
        inputManager.addMapping(POSSESSION + Settings.Setting.CAMERA_UP.name(), new KeyTrigger(settings.getInteger(Settings.Setting.CAMERA_UP)));
        inputManager.addMapping(POSSESSION + Settings.Setting.CAMERA_DOWN.name(), new KeyTrigger(settings.getInteger(Settings.Setting.CAMERA_DOWN)));
        inputManager.addMapping(POSSESSION + Settings.Setting.CAMERA_LEFT.name(), new KeyTrigger(settings.getInteger(Settings.Setting.CAMERA_LEFT)));
        inputManager.addMapping(POSSESSION + Settings.Setting.CAMERA_RIGHT.name(), new KeyTrigger(settings.getInteger(Settings.Setting.CAMERA_RIGHT)));

        inputManager.addMapping(Settings.Setting.POSSESSED_RUN.name(), new KeyTrigger(settings.getInteger(Settings.Setting.POSSESSED_RUN)));
        inputManager.addMapping(Settings.Setting.POSSESSED_CREEP.name(), new KeyTrigger(settings.getInteger(Settings.Setting.POSSESSED_CREEP)));

        inputManager.addMapping(CAMERA_VIEW_LEFT, new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping(CAMERA_VIEW_RIGHT, new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping(CAMERA_VIEW_UP, new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        inputManager.addMapping(CAMERA_VIEW_DOWN, new MouseAxisTrigger(MouseInput.AXIS_Y, false));

        //inputManager.addMapping(SPECIAL_KEY_ALT, new KeyTrigger(KeyInput.KEY_LMENU), new KeyTrigger(KeyInput.KEY_RMENU));
        //inputManager.addMapping(SPECIAL_KEY_CONTROL, new KeyTrigger(KeyInput.KEY_LCONTROL), new KeyTrigger(KeyInput.KEY_RCONTROL));
        //inputManager.addMapping(SPECIAL_KEY_SHIFT, new KeyTrigger(KeyInput.KEY_LSHIFT), new KeyTrigger(KeyInput.KEY_RSHIFT));
        inputManager.addListener(this, mappings);
    }

    @Override
    public boolean isPauseable() {
        return false;
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (!isEnabled()) {
            return;
        }

        if (name.equals(Settings.Setting.POSSESSED_RUN.name())) {
            if (isPressed) {
                camera.setSpeed(creature.getAttributes().getRunSpeed());
            } else {
                camera.setSpeed(creature.getAttributes().getSpeed());
            }
        } else if (name.equals(Settings.Setting.POSSESSED_CREEP.name())) {
            if (isPressed) {
                camera.setSpeed(creature.getAttributes().getShuffleSpeed());
            } else {
                camera.setSpeed(creature.getAttributes().getSpeed());
            }
        }
    }

    @Override
    public void onAnalog(String name, float value, float tpf) {
        if (!isEnabled()) {
            return;
        }

        switch (name) {
            case CAMERA_VIEW_LEFT -> camera.rotate(value, true);
            case CAMERA_VIEW_RIGHT -> camera.rotate(-value, true);
            case CAMERA_VIEW_UP -> camera.rotate(value, false);
            case CAMERA_VIEW_DOWN -> camera.rotate(-value, false);
        }

        if (name.equals(POSSESSION + Settings.Setting.CAMERA_UP.name())) {
            camera.move(value, false);
        } else if (name.equals(POSSESSION + Settings.Setting.CAMERA_DOWN.name())) {
            camera.move(-value, false);
        } else if (name.equals(POSSESSION + Settings.Setting.CAMERA_LEFT.name())) {
            camera.move(value, true);
        } else if (name.equals(POSSESSION + Settings.Setting.CAMERA_RIGHT.name())) {
            camera.move(-value, true);
        }
    }

    private void unregisterInput() {
        for (String s : mappings) {
            inputManager.deleteMapping(s);
        }
        inputManager.removeListener(this);
    }

    @Override
    public void cleanup() {

        // Unregister controls
        unregisterInput();

        super.cleanup();
    }

    public void setTarget(EntityId target) {
        this.target = target;
        this.creature = null;
        if (target == null) {
            return;
        }
        // Get entity data and creature definition
        PlayerState ps = app.getStateManager().getState(PlayerState.class);
        if (ps == null) {
            logger.log(System.Logger.Level.WARNING, "PlayerState not available when setting possession target");
            return;
        }
        EntityData ed = ps.getEntityData();
        CreatureComponent cc = ed.getComponent(target, CreatureComponent.class);
        if (cc == null) {
            logger.log(System.Logger.Level.WARNING, "CreatureComponent missing for entity " + target);
            return;
        }
        Creature c = ps.getKwdFile().getCreature(cc.creatureId);
        if (c == null) {
            logger.log(System.Logger.Level.WARNING, "Creature definition not found for id " + cc.creatureId);
            return;
        }
        this.creature = c;

        // Try to set initial camera location/rotation from the entity Position
        Position pos = ed.getComponent(target, Position.class);
        if (pos != null) {
            Camera cam = app.getCamera();
            cam.setLocation(pos.position.add(0, creature.getAttributes().getEyeHeight(), 0));
            cam.setRotation(new Quaternion().fromAngles(0, pos.rotation, 0));
        }
    }
}
