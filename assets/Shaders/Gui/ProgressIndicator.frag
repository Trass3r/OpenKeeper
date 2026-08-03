#ifdef GL_ES
precision highp float;
precision highp int;
#endif

#ifdef TEXTURE
    uniform sampler2D m_Texture;
#endif

in vec4 color;
in vec2 texCoord;
out vec4 fragColor;

uniform float m_Progress;

float PI = 3.14159265358979323846264;

void main() {
    vec2 uv = texCoord.xy;
    uv -= vec2(0.5, 0.5);
    float sweep = (-atan(uv.x,-uv.y) + PI) / (2.*PI);

    #ifdef TEXTURE
      if (sweep < m_Progress) {
        vec4 texVal = texture(m_Texture, texCoord);
        fragColor = texVal * color;
      } else
        discard;

    #else
      if (sweep < m_Progress)
        fragColor = color;
      else
        discard;

    #endif
}
