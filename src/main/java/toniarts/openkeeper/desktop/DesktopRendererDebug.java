/*
 * Copyright (C) 2014-2026 OpenKeeper contributors
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package toniarts.openkeeper.desktop;

import com.jme3.renderer.Renderer;
import com.jme3.renderer.opengl.GLRenderer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GLUtil;

/**
 * LWJGL debug setup used only by the desktop launcher.
 */
public final class DesktopRendererDebug {

    private DesktopRendererDebug() {
    }

    public static void enable(Renderer renderer) {
        ((GLRenderer) renderer).setDebugEnabled(true);
        if (GL.getCapabilities().OpenGL43) {
            GLUtil.setupDebugMessageCallback();
            GL43C.glDebugMessageControl(
                    GL43.GL_DEBUG_SOURCE_APPLICATION,
                    GL43.GL_DONT_CARE,
                    GL43.GL_DONT_CARE,
                    (int[]) null,
                    false);
        }
    }
}
