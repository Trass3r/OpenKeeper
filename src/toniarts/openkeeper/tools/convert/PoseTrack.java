package toniarts.openkeeper.tools.convert;

import com.jme3.anim.MorphTrack;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.clone.Cloner;
import java.io.IOException;

/**
 * A Track that expects the VBOs to contain data for all frames
 * and just switches the offsets to select the active pose frame.
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
        if (idx < 0) idx = 0;
        if (idx >= frameCount) idx = frameCount - 1;

        // update the geometry's combined position VBO offset
        Geometry geom = getTarget();
        Mesh mesh = geom.getMesh();
        VertexBuffer vb = mesh.getBuffer(VertexBuffer.Type.Position);
        if (vb != null)
            vb.setOffset(bytesPerFrame * idx);

        // Keep returning a sensible weights array to remain compatible
        //if (store != null && store.length > 0) {
        //    Arrays.fill(store, 0f);
        //    if (idx < store.length) store[idx] = 1f;
        //}
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
