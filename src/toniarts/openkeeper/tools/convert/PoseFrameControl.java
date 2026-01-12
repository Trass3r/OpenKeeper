package toniarts.openkeeper.tools.convert;

import com.jme3.anim.MorphControl;
import com.jme3.material.Material;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.mesh.MorphTarget;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.shader.VarType;
import com.jme3.util.clone.Cloner;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A simplified MorphControl for Pose frames where each frame is represented by
 * a single full MorphTarget (relative to base pose). This control binds the
 * appropriate morph target buffers directly to the mesh instead of doing the
 * generic GPU/CPU morph merging logic from the stock MorphControl.
 */
public final class PoseFrameControl extends AbstractControl {

    private List<Geometry> targets = new ArrayList<>();
    private int currentFrame = 0;

    public PoseFrameControl() {
        super();
    }

    @Override
    public void setSpatial(Spatial spatial) {
        super.setSpatial(spatial);
        targets.clear();
        if (spatial == null) {
            return;
        }
        gatherGeometries(spatial);
    }

    private void gatherGeometries(Spatial s) {
        if (s instanceof Geometry) {
            targets.add((Geometry) s);
            return;
        }
        if (s instanceof Node) {
            for (Spatial child : ((Node) s).getChildren()) {
                gatherGeometries(child);
            }
        }
    }

    @Override
    protected void controlUpdate(float tpf) {
    }

    @Override
    protected void controlRender(RenderManager rm, ViewPort vp) {
        // Simplified: for each geometry bind the morph target buffers for the
        // requested frame (or clear them for frame 0). We always use a single
        // morph target (MorphTarget0..2 sequence for pos/norm/tan).
        for (Geometry geom : targets) {
            if (geom == null) continue;
            Mesh mesh = geom.getMesh();
            if (mesh == null) continue;

            if (!geom.isDirtyMorph())
                continue;

            MorphTarget[] morphTargets = mesh.getMorphTargets();
            if (morphTargets == null || morphTargets.length == 0)
                continue;

            Material mat = geom.getMaterial();
            mat.setParam("MorphWeights", VarType.FloatArray, new float[] {currentFrame <= 0 ? 0f : 1f});

            float weights[] = geom.getMorphState();
            currentFrame = (int)weights[0];

            if (currentFrame <= 0) {
                continue;
            }

            int idx = currentFrame - 1;
            if (idx >= morphTargets.length)
                idx = morphTargets.length - 1;
            MorphTarget mt = morphTargets[idx];

            // try to use MorphTargetFix vertex buffers when available
            if (mt instanceof MorphTargetFix mtf) {
                VertexBuffer posVB  = mtf.getVertexBuffer(Type.MorphTarget0);
                VertexBuffer normVB = mtf.getVertexBuffer(Type.MorphTarget1);
                VertexBuffer tanVB  = mtf.getVertexBuffer(Type.MorphTarget2);

                if (posVB != null) {
                    mesh.clearBuffer(VertexBuffer.Type.MorphTarget0);
                    mesh.setBuffer(posVB);
                }
                if (normVB != null) {
                    mesh.clearBuffer(VertexBuffer.Type.MorphTarget1);
                    mesh.setBuffer(normVB);
                }
                if (tanVB != null) {
                    mesh.clearBuffer(VertexBuffer.Type.MorphTarget2);
                    mesh.setBuffer(tanVB);
                }
            } else {
                // fallback to FloatBuffer-backed MorphTarget
                mesh.clearBuffer(VertexBuffer.Type.MorphTarget0);
                mesh.clearBuffer(VertexBuffer.Type.MorphTarget1);
                mesh.clearBuffer(VertexBuffer.Type.MorphTarget2);
                FloatBuffer pos = mt.getBuffer(Type.Position);
                FloatBuffer norm = mt.getBuffer(Type.Normal);
                FloatBuffer tan = mt.getBuffer(Type.Tangent);
                if (pos != null)  mesh.setBuffer(VertexBuffer.Type.MorphTarget0, 3, pos);
                if (norm != null) mesh.setBuffer(VertexBuffer.Type.MorphTarget1, 3, norm);
                if (tan != null)  mesh.setBuffer(VertexBuffer.Type.MorphTarget2, 3, tan);
            }
        }
    }

    @Override
    public void write(JmeExporter ex) throws IOException {
        super.write(ex);
        OutputCapsule oc = ex.getCapsule(this);
        oc.write(currentFrame, "currentFrame", 0);
        oc.writeSavableArrayList(new ArrayList(targets), "targets", null);
    }

    @Override
    public void read(JmeImporter im) throws IOException {
        super.read(im);
        InputCapsule ic = im.getCapsule(this);
        currentFrame = ic.readInt("currentFrame", 0);
        targets.addAll(ic.readSavableArrayList("targets", null));
    }

    @Override
    public PoseFrameControl jmeClone() {
        return (PoseFrameControl) super.jmeClone();
    }

    @Override
    public void cloneFields(Cloner cloner, Object original) {
        super.cloneFields(cloner, original);
        PoseFrameControl o = (PoseFrameControl) original;
        this.currentFrame = o.currentFrame;
        targets = cloner.clone(targets);
    }
}
