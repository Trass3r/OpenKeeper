package toniarts.openkeeper.tools.convert;

import com.jme3.anim.MorphTrack;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.mesh.MorphTarget;
import com.jme3.shader.VarType;
import com.jme3.util.clone.Cloner;
import java.io.IOException;

/**
 * An AnimTrack that just binds the right MorphTarget for the current frame.
 * It inherits from MorphTrack cause ClipAction is hardcoded.
 */
final class PoseTrack extends MorphTrack {
    private int frameCount;
    private float frameDuration;
    private int bytesPerFrame;

    /**
     * Serialization-only. Do not use.
     */
    protected PoseTrack() {}

    public PoseTrack(Geometry target, int frameCount, float frameDuration, int bytesPerFrame) {
        super(target, null, null, 0);
        this.frameCount = frameCount;
        this.frameDuration = frameDuration;
        this.bytesPerFrame = bytesPerFrame;
    }

    @Override
    public double getLength() {
        return (frameCount-1) * frameDuration;
    }

    @Override
    public void getDataAtTime(double t, float[] store) {
        // compute frame index from time
        int idx = 0;
        if (frameDuration > 0f) {
            idx = (int) Math.floor(t / frameDuration + 0.5);
        }
        if (idx < 0)
            idx = 0;
        if (idx >= frameCount)
            idx = frameCount - 1;

        if (idx == store[0])
            return;
        store[0] = idx;

        // update the geometry's combined position VBO offset
        Geometry geom = getTarget();
        Material mat = geom.getMaterial();
        mat.setParam("MorphWeights", VarType.FloatArray, new float[] {idx <= 0 ? 0f : 1f});

        if (idx <= 0) {
            return;
        }

        Mesh mesh = geom.getMesh();
        MorphTarget mt = mesh.getMorphTarget(idx-1);
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
        }
    }

    @Override
    public void write(JmeExporter ex) throws IOException {
        OutputCapsule oc = ex.getCapsule(this);
        oc.write(getTarget(), "target", null);
        oc.write(frameCount, "frameCount", 0);
        oc.write(frameDuration, "frameDuration", 0f);
        oc.write(bytesPerFrame, "bytesPerFrame", 0);
    }

    @Override
    public void read(JmeImporter im) throws IOException {
        InputCapsule ic = im.getCapsule(this);
        setTarget((Geometry)ic.readSavable("target", null));
        frameCount = ic.readInt("frameCount", frameCount);
        frameDuration = ic.readFloat("frameDuration", frameDuration);
        bytesPerFrame = ic.readInt("bytesPerFrame", bytesPerFrame);
    }

    @Override
    public Object jmeClone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void cloneFields(Cloner cloner, Object original) {
        super.cloneFields(cloner, original);
        // primitives copied by default clone; no extra action required
    }
}