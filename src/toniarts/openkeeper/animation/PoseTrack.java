/*
 * Copyright (c) 2009-2012 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package toniarts.openkeeper.animation;

import com.jme3.anim.MorphTrack;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.export.Savable;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.util.BufferUtils;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A single track of pose animation associated with a certain mesh.
 */
public final class PoseTrack extends MorphTrack {

    private PoseTrack.PoseFrame[] frames;

    public static final class PoseFrame implements Savable, Cloneable {

        Pose[] poses;
        float[] weights;

        public PoseFrame(Pose[] poses, float[] weights) {
            this.poses = poses;
            this.weights = weights;
        }

        /**
         * Serialization-only. Do not use.
         */
        public PoseFrame() {
        }

        /**
         * This method creates a clone of the current object.
         *
         * @return a clone of the current object
         */
        @Override
        public PoseTrack.PoseFrame clone() throws CloneNotSupportedException {
            try {
                PoseTrack.PoseFrame result = (PoseTrack.PoseFrame) super.clone();
                result.weights = this.weights.clone();
                if (this.poses != null) {
                    result.poses = new Pose[this.poses.length];
                    for (int i = 0; i < this.poses.length; ++i) {
                        result.poses[i] = this.poses[i].clone();
                    }
                }
                return result;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }

        @Override
        public void write(JmeExporter e) throws IOException {
            OutputCapsule out = e.getCapsule(this);
            out.write(poses, "poses", null);
            out.write(weights, "weights", null);
        }

        @Override
        public void read(JmeImporter i) throws IOException {
            InputCapsule in = i.getCapsule(this);
            weights = in.readFloatArray("weights", null);

            Savable[] readSavableArray = in.readSavableArray("poses", null);
            if (readSavableArray != null) {
                poses = new Pose[readSavableArray.length];
                System.arraycopy(readSavableArray, 0, poses, 0, readSavableArray.length);
            }
        }
    }

    public PoseTrack(Geometry target, float[] times, PoseTrack.PoseFrame[] frames)
    {
        super(target, times, new float[times.length], 1);
        this.frames = frames;
    }

    /**
     * Serialization-only. Do not use.
     */
    public PoseTrack() {
    }
private final Map<Pose, ColorRGBA> poseColors = new HashMap<>();
private float nextHue = 0;

private ColorRGBA getPoseColor(Pose pose) {
    return poseColors.computeIfAbsent(pose, p -> {
        ColorRGBA color = hsvToRgb(nextHue, 1f, 1f);
        nextHue = (nextHue + 0.618034f) % 1f; // Golden ratio for good distribution
        return color;
    });
}

    private void applyFrame(Mesh target, int frameIndex) {
        PoseFrame frame = frames[frameIndex];
        VertexBuffer pb = target.getBuffer(Type.Position);
/*
        // Add color buffer if it doesn't exist
        VertexBuffer cb = target.getBuffer(Type.Color);
        if (cb == null) {
            cb = new VertexBuffer(Type.Color);
            cb.setNormalized(true);
            target.setBuffer(cb);
        }

        // Create or get color data buffer
        FloatBuffer colorData;
        if (cb.getData() == null) {
            colorData = BufferUtils.createFloatBuffer(pb.getNumElements() * 4); // 4 components (RGBA)
            cb.setupData(Usage.Stream, 4, Format.Float, colorData);
        } else {
            colorData = (FloatBuffer) cb.getData();
            colorData.rewind();
        }

        // Reset colors to default
        while (colorData.remaining() > 0) {
            colorData.put(1f).put(1f).put(1f).put(1f); // White
        }
        colorData.rewind();
*/
        for (int i = 0; i < frame.poses.length / 2; i++) {

            Pose startPose = frame.poses[i * 2];
            Pose endPose   = frame.poses[i * 2 + 1];

            // Use consistent color based on start pose
            //ColorRGBA poseColor = getPoseColor(startPose);
            //applyPoseColors(startPose, endPose, frame.weights[i], colorData, poseColor);

            // Apply regular pose transformation
            applyPose(startPose, endPose, frame.weights[i], (FloatBuffer) pb.getData());
        }

        // force to re-upload data to gpu
        pb.updateData(pb.getData());
        //cb.updateData(colorData);
    }

    private void applyPoseColors(Pose startPose, Pose endPose, float weight,
            FloatBuffer colorBuffer, ColorRGBA poseColor) {
                    if (startPose == null) {
                        return;
                    }
            
                    int[] indices = startPose.getIndices();
                    for (int vertIndex : indices) {
                        // Calculate buffer position for this vertex
                        int colorIndex = vertIndex * 4;
            
                        // Blend with existing color
                        colorBuffer.position(colorIndex);
                        float r = colorBuffer.get();
                        float g = colorBuffer.get();
                        float b = colorBuffer.get();
                        float a = colorBuffer.get();
            
                        // Apply new color with weight
                        colorBuffer.position(colorIndex);
                        colorBuffer.put(FastMath.interpolateLinear(weight, r, poseColor.r));
                        colorBuffer.put(FastMath.interpolateLinear(weight, g, poseColor.g));
                        colorBuffer.put(FastMath.interpolateLinear(weight, b, poseColor.b));
                        colorBuffer.put(FastMath.interpolateLinear(weight, a, poseColor.a));
        }
    }

    /**
     * Applies pose for this frame
     *
     * @param startPose starting pose for the vertices
     * @param endPose ending pose for the vertices
     * @param weight weight on which to apply the interpolation
     * @param vertexBuffer the vertex buffer
     */
    private void applyPose(@Nullable Pose startPose, Pose endPose, float weight, FloatBuffer vertexBuffer) {
        if (startPose == null) {
            // FIXME should we skip if null?
            return;
        }
        int[] startingIndices = startPose.getIndices();
        Vector3f interpOffset = new Vector3f();
        for (int i = 0; i < startingIndices.length; i++) {
            int vertIndex = startingIndices[i];
            Vector3f startOffset = startPose.getOffsets()[i];
            Vector3f endOffset = endPose.getOffsets()[i];

            // Interpolate
            FastMath.interpolateLinear(weight, startOffset, endOffset, interpOffset);

            // Write modified vertex
            BufferUtils.setInBuffer(interpOffset, vertexBuffer, vertIndex);
        }
    }

    @Override
    public void getDataAtTime(double time, float[] store) {
        Mesh mesh = getTarget().getMesh();
        VertexBuffer bindPos = mesh.getBuffer(Type.BindPosePosition);
        VertexBuffer pos = mesh.getBuffer(Type.Position);
        FloatBuffer pb = (FloatBuffer) pos.getData();
        FloatBuffer bpb = (FloatBuffer) bindPos.getData();
        pb.clear();
        bpb.clear();
        pb.put(bpb).clear();

        if (time < getTimes()[0]) {
            applyFrame(mesh, 0);
        } else if (time > getTimes()[getTimes().length - 1]) {
            applyFrame(mesh, getTimes().length - 1);
        } else {
            int startFrame = 0;
            for (int i = 0; i < getTimes().length; i++) {
                if (getTimes()[i] < time) {
                    startFrame = i;
                }
            }

            int endFrame = startFrame + 1;
            applyFrame(mesh, endFrame);
        }
    }

    /**
     * This method creates a clone of the current object.
     *
     * @return a clone of the current object
     */
    @Override
    public PoseTrack clone() {
        try {
            PoseTrack result = (PoseTrack) super.clone();
            //result.getTimes() = this.getTimes().clone();
            if (this.frames != null) {
                result.frames = new PoseTrack.PoseFrame[this.frames.length];
                for (int i = 0; i < this.frames.length; ++i) {
                    result.frames[i] = this.frames[i].clone();
                }
            }
            return result;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    @Override
    public Object jmeClone() {
        try {
            PoseTrack clone = (PoseTrack) super.clone();
            return clone;
        } catch (CloneNotSupportedException ex) {
            throw new AssertionError();
        }
    }

    @Override
    public void write(JmeExporter e) throws IOException {
        super.write(e);

        OutputCapsule out = e.getCapsule(this);
        out.write(frames, "frames", null);
    }

    @Override
    public void read(JmeImporter i) throws IOException {
        super.read(i);

        InputCapsule in = i.getCapsule(this);
        Savable[] readSavableArray = in.readSavableArray("frames", null);
        if (readSavableArray != null) {
            frames = new PoseTrack.PoseFrame[readSavableArray.length];
            System.arraycopy(readSavableArray, 0, frames, 0, readSavableArray.length);
        }
    }

    private ColorRGBA hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        float m = v - c;

        float r = 0, g = 0, b = 0;
        if (h < 1.0f/6.0f) {
            r = c; g = x; b = 0;
        } else if (h < 2.0f/6.0f) {
            r = x; g = c; b = 0;
        } else if (h < 3.0f/6.0f) {
            r = 0; g = c; b = x;
        } else if (h < 4.0f/6.0f) {
            r = 0; g = x; b = c;
        } else if (h < 5.0f/6.0f) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }

        return new ColorRGBA(r + m, g + m, b + m, 1f);
    }
}
