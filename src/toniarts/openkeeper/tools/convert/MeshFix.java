package toniarts.openkeeper.tools.convert;

import com.jme3.scene.Mesh;
import com.jme3.util.clone.Cloner;

public final class MeshFix extends Mesh {
	// for serialization
	MeshFix() {}

	@Override
	public int getTriangleCount(int lod) {
		if (getNumLodLevels() == 0) {
			if (lod != 0)
				throw new IllegalArgumentException("There are no LOD levels on the mesh!");
			return getTriangleCount();
		}
		if (getLodLevel(lod) == null) // fix JME bug
			return 0;
		return super.getTriangleCount(lod);
	}
	
	@Override
	public void cloneFields(Cloner cloner, Object original) {
		super.cloneFields(cloner, original);
		//this.setname = cloner.clone(meshBound);
	}
}