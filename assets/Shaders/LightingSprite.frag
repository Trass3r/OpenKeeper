#ifdef GL_ES
precision highp float;
precision highp int;
#endif

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D m_DiffuseMap;
uniform float m_AlphaDiscardThreshold;

void main()
{
  vec2 newTexCoord;
  newTexCoord = texCoord;
  vec4 diffuseColor = texture(m_DiffuseMap, newTexCoord);
  #ifdef DISCARD_ALPHA
      if (all(lessThan(diffuseColor.rgb, vec3(m_AlphaDiscardThreshold))))
          discard;
  #endif

  fragColor = vec4(diffuseColor.rgb, 1.0);
}
