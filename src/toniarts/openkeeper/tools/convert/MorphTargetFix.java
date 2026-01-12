package toniarts.openkeeper.tools.convert;

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeImporter;
import com.jme3.export.JmeExporter;
import com.jme3.export.OutputCapsule;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.mesh.MorphTarget;
import java.io.IOException;
import java.util.*;

/**
 * MorphTarget variant that stores full VertexBuffer instances instead of
 * FloatBuffers. Provides serialization compatible with Savable VertexBuffer
 * objects so buffers can be written/read as part of the MorphTarget.
 */
final class MorphTargetFix extends MorphTarget {
    private final List<VertexBuffer> vbuffers = new ArrayList<>(3);

    public MorphTargetFix() {
        super();
        for (int i = 0; i < 3; ++i)
            vbuffers.add(null);
    }

    public MorphTargetFix(String name) {
        super(name);
        for (int i = 0; i < 3; ++i)
            vbuffers.add(null);
    }

    /**
     * Set a vertex buffer for the given type (Position/Normal/Tangent etc).
     */
    public void setVertexBuffer(VertexBuffer buffer) {
        vbuffers.set(buffer.getBufferType().ordinal() - Type.MorphTarget0.ordinal(), buffer);
    }

    /**
     * Get the vertex buffer stored for the given type, or null.
     */
    public VertexBuffer getVertexBuffer(Type type) {
        return vbuffers.get(type.ordinal() - Type.MorphTarget0.ordinal());
    }

    @Override
    public int getNumBuffers() {
        return vbuffers.size();
    }

    @Override
    public void write(JmeExporter ex) throws IOException {
        OutputCapsule oc = ex.getCapsule(this);
        for (var vb : vbuffers	) {
            if (vb != null) {
                oc.write(vb, vb.getBufferType().name(), null);
            }
        }
        // write name using parent behaviour
        oc.write(getName(), "morphName", null);
    }

    @Override
    public void read(JmeImporter im) throws IOException {
        InputCapsule ic = im.getCapsule(this);
        for (VertexBuffer.Type t : VertexBuffer.Type.values()) {
            VertexBuffer vb = (VertexBuffer) ic.readSavable(t.name(), null);
            if (vb != null) {
                vbuffers.set(t.ordinal() - Type.MorphTarget0.ordinal(), vb);
            }
        }
        // read name
        String name = ic.readString("morphName", null);
        if (name != null) {
            setName(name);
        }
    }
}