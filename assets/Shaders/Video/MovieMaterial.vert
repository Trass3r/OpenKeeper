#ifdef GL_ES
precision highp float;
precision highp int;
#endif

uniform mat4 g_WorldViewProjectionMatrix;

in vec3 inPosition;
in vec2 inTexCoord;

out vec2 texCoord;

void main(){

    texCoord = inTexCoord;
    gl_Position = g_WorldViewProjectionMatrix*vec4(inPosition,1.0);
}
