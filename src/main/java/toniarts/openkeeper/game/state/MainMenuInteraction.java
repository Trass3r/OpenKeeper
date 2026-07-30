
package toniarts.openkeeper.game.state;

import com.jme3.collision.CollisionResults;
import com.jme3.input.MouseInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import toniarts.openkeeper.view.map.construction.FrontEndLevelControl;

/**
 *
 * This is for the level pick up
 */
public final class MainMenuInteraction implements RawInputListener {
    private final MainMenuState mainMenuState;
    private FrontEndLevelControl currentControl;

    public MainMenuInteraction(MainMenuState mainMenuState) {
        this.mainMenuState = mainMenuState;
    }

    @Override
    public void beginInput() {
    }

    @Override
    public void endInput() {
    }

    @Override
    public void onJoyAxisEvent(JoyAxisEvent evt) {
    }

    @Override
    public void onJoyButtonEvent(JoyButtonEvent evt) {
    }

    @Override
    public void onMouseMotionEvent(MouseMotionEvent evt) {
        setCampaignMapActive(evt.getX(), evt.getY());
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent evt) {
        if (currentControl != null && evt.getButtonIndex() == MouseInput.BUTTON_LEFT) {
            evt.setConsumed();

            // Select level
            mainMenuState.selectCampaignLevel(currentControl);
        }
    }

    @Override
    public void onKeyEvent(KeyInputEvent evt) {
    }

    @Override
    public void onTouchEvent(TouchEvent evt) {
        switch (evt.getType()) {
            case HOVER_START:
            case HOVER_MOVE:
                // Stylus proximity should highlight a campaign marker without
                // activating it. Tip contact is already mapped to a normal
                // left mouse click by jMonkeyEngine.
                evt.setConsumed();
                setCampaignMapActive((int) evt.getX(), (int) evt.getY());
                break;
            case HOVER_END:
                evt.setConsumed();
                clearCampaignMapActive();
                break;
            case DOWN:
            case MOVE:
                // Touch input does not always emit a preceding mouse-motion
                // event. Establish the pointed marker first; the simulated
                // left mouse button event that follows performs activation.
                if (!evt.isScaleSpanInProgress()) {
                    setCampaignMapActive((int) evt.getX(), (int) evt.getY());
                }
                break;
            default:
                break;
        }
    }

    private void clearCampaignMapActive() {
        if (currentControl != null) {
            currentControl.setActive(false);
            currentControl = null;
        }
    }

    /**
         * Sets the map at certain point as active (i.e. selected), IF there is
         * one
         *
         * @param x x screen coordinate
         * @param y y screen coordinate
         */
        private void setCampaignMapActive(int x, int y) {

            // See if we hit a map
            CollisionResults results = new CollisionResults();

            // Convert screen click to 3D position
            Vector3f click3d = mainMenuState.app.getCamera().getWorldCoordinates(
                    new Vector2f(x, y), 0f);
            Vector3f dir = mainMenuState.app.getCamera().getWorldCoordinates(
                    new Vector2f(x, y), 1f).subtractLocal(click3d);

            // Ray requires a finite unit direction. Desktop JVMs commonly run
            // with assertions disabled, while Android catches the unnormalised
            // direction here and aborts the render thread.
            if (!Vector3f.isValidVector(dir) || dir.lengthSquared() == 0f) {
                return;
            }
            dir.normalizeLocal();

            // Aim the ray from the clicked spot forwards
            Ray ray = new Ray(click3d, dir);

            // Collect intersections between ray and all nodes in results list
            mainMenuState.menuNode.collideWith(ray, results);

            // See the results so we see what is going on
            for (int i = 0; i < results.size(); i++) {

                FrontEndLevelControl controller = results.getCollision(i).getGeometry().getParent().getParent().getControl(FrontEndLevelControl.class);
                if (controller != null) {

                    // Deactivate current controller
                    if (currentControl != null && !currentControl.equals(controller)) {
                        currentControl.setActive(false);
                    }

                    // Set and activate current controller
                    currentControl = controller;
                    currentControl.setActive(true);
                    return;
                }
            }

            // Deactivate current controller, nothing is selected
            if (currentControl != null) {
                currentControl.setActive(false);
                currentControl = null;
            }
        }
}
