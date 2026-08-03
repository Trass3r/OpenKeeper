#ifdef GL_ES
precision highp float;
precision highp int;
#endif

uniform mat4 g_WorldViewProjectionMatrix;
uniform vec4 m_Color;

in vec3 inPosition;

#ifdef VERTEX_COLOR
    in vec4 inColor;
#endif

in vec2 inTexCoord;
out vec2 texCoord;

out vec4 color;

void main() {
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
    texCoord = inTexCoord;
    #ifdef VERTEX_COLOR
        color = m_Color * inColor;
    #else
        color = m_Color;
    #endif
}
