package toniarts.openkeeper.tools.convert;

import com.jme3.scene.control.AbstractControl;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

public final class LodControl extends AbstractControl {

	private static final Logger logger = System.getLogger(LodControl.class.getSimpleName());

	@Override
	protected void controlUpdate(float tpf) {}

	@Override
	protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {
		// choose level just based on camera distance
		var cam = vp.getCamera();
		var dist = cam.getLocation().distanceSquared(spatial.getWorldTranslation());
		int lod = Math.min(8-1, (int) (dist / 10));
		logger.log(Level.INFO, "LOD distance {0} level: {1}", dist, lod);
		spatial.setLodLevel(lod);
	}
}