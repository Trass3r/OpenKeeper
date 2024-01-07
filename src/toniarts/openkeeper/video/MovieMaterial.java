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
package toniarts.openkeeper.video;

import com.jme3.app.Application;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.texture.Image;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import toniarts.openkeeper.video.tgq.TgqFrame;

/**
 * This class has been modified for our usage, for watching TGQ movies.<br>
 * Original authors: Empire_Phoenix and abies (JME forums)
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public class MovieMaterial {
    
    private static final Logger logger = System.getLogger(MovieMaterial.class.getName());

    private static final Image emptyImage = new Image(Format.ABGR8, 1, 1, BufferUtils.createByteBuffer(new byte[]{0, 0, 0, 0}), ColorSpace.Linear);
    private boolean noFrame = true;
    private final boolean letterbox;
    private final ColorRGBA letterboxColor = ColorRGBA.Red.clone();
    private final Vector2f aspectValues = new Vector2f(1, 1);
    private final Vector2f validRange = new Vector2f(1, 1);
    private float aspectRatio = 1.0f;
    private boolean running = true;
    private Texture2D textureLuma;
    private Texture2D textureCr;
    private Texture2D textureCb;
    private final Application app;
    private TgqFrame latestFrame;
    private TgqFrame jmeFrame;
    private Material material;

    public MovieMaterial(final Application app, boolean letterbox) {
        this.app = app;
        this.letterbox = letterbox;
    }

    protected void videoFrameUpdated(TgqFrame frame) {
        try {
            float bufferWidth, bufferHeight;

            ByteBuffer mainBuffer = frame.getBufferForPlane(TgqFrame.YCBCR_PLANE_LUMA);
            bufferWidth = frame.getLineSize(TgqFrame.YCBCR_PLANE_LUMA);
            bufferHeight = mainBuffer.capacity() / bufferWidth;

            float validWidth = frame.getWidth() / bufferWidth;
            float validHeight = frame.getHeight() / bufferHeight;

            aspectRatio = frame.getWidth() / (float) frame.getHeight();

            synchronized (MovieMaterial.this) {
                if (!running) {
                    return;
                }
                if (letterbox) {
                    aspectValues.set(Math.max(1, 1 / aspectRatio), Math.max(1, aspectRatio));
                } else {
                    aspectValues.set(1, 1);
                }
                validRange.set(validWidth, validHeight);
                latestFrame = frame;
            }

        } catch (Exception ex) {
            logger.log(Level.ERROR, "Failed to update the frame!", ex);
        }
    }

    private void updateTexture(Texture2D tex, ByteBuffer buf, int stride) {
        if (buf == null) {
            tex.setImage(emptyImage);
            return;
        }
        if (tex.getImage().getData(0).capacity() != buf.capacity()) {
            Image img = new Image(Format.Luminance8, stride, buf.capacity() / stride, buf, ColorSpace.Linear);
            tex.setImage(img);
        } else {
            tex.getImage().setData(buf);
        }
    }

    /**
     * Sets the color which should be used for letterbox fill. It is annoying
     * red by default to help with debugging.
     *
     * @param letterboxColor
     */
    public final void setLetterboxColor(ColorRGBA letterboxColor) {
        this.letterboxColor.set(letterboxColor);
        if (letterbox && material != null) {
            material.setColor("LetterboxColor", letterboxColor);
        }
    }

    /**
     *
     * @return aspect ratio of played movie (width/height) - for widescreen
     * movies it will be in range of 1.8-2.9
     */
    public float getAspectRatio() {
        return aspectRatio;
    }

    public Material getMaterial() {
        if (material == null) {
            init();
        }
        return material;
    }

    private void init() {
        textureLuma = new Texture2D(emptyImage);
        textureCr = new Texture2D(emptyImage);
        textureCb = new Texture2D(emptyImage);

        material = new Material(app.getAssetManager(), "MatDefs/Video/MovieMaterial.j3md");

        material.setTexture("TexLuma", textureLuma);
        material.setTexture("TexCr", textureCr);
        material.setTexture("TexCb", textureCb);
        material.setVector2("AspectValues", aspectValues.clone());
        material.setVector2("ValidRange", validRange.clone());
        material.setBoolean("NoFrame", noFrame);

        if (letterbox) {
            material.setColor("LetterboxColor", letterboxColor);
        }
    }

    public void update(float tpf) {
        synchronized (MovieMaterial.this) {
            if (latestFrame != null && latestFrame != jmeFrame) {
                if (!aspectValues.equals(material.getParam("AspectValues").getValue())) {
                    material.setVector2("AspectValues", aspectValues.clone());
                }
                if (!validRange.equals(material.getParam("ValidRange").getValue())) {
                    material.setVector2("ValidRange", validRange.clone());
                }
                if (noFrame) {
                    noFrame = false;
                    material.setBoolean("NoFrame", noFrame);
                }

                updateTexture(textureLuma, latestFrame.getBufferForPlane(TgqFrame.YCBCR_PLANE_LUMA),
                        latestFrame.getLineSize(TgqFrame.YCBCR_PLANE_LUMA));
                updateTexture(textureCr, latestFrame.getBufferForPlane(TgqFrame.YCBCR_PLANE_CR),
                        latestFrame.getLineSize(TgqFrame.YCBCR_PLANE_CR));
                updateTexture(textureCb, latestFrame.getBufferForPlane(TgqFrame.YCBCR_PLANE_CB),
                        latestFrame.getLineSize(TgqFrame.YCBCR_PLANE_CB));

                jmeFrame = latestFrame;
            }

        }

    }

    public void dispose() {
        synchronized (MovieMaterial.this) {
            running = false;
            textureLuma.setImage(emptyImage);
            textureCr.setImage(emptyImage);
            textureCb.setImage(emptyImage);
            latestFrame = null;
            jmeFrame = null;
        }

    }
}
