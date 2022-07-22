#import "Common/ShaderLib/Instancing.glsllib"

uniform vec4 m_Ambient;
uniform vec4 m_Diffuse;
uniform vec4 m_Specular;
uniform float m_Shininess;

uniform vec4 g_AmbientLightColor;
out vec2 texCoord;

out vec3 AmbientSum;
out vec4 DiffuseSum;
out vec3 SpecularSum;

in vec3 inPosition;
in vec2 inTexCoord;
in vec3 inNormal;

out vec3 vNormal;
out vec3 vPos;
    #ifdef NORMALMAP
in vec4 inTangent;
out vec4 vTangent;
    #endif

vec4 hash44(vec4 p4) {
        p4 = fract(p4 * vec4(.1031, .1030, .0973, .1099));
        p4 += dot(p4, p4.wzxy + 33.33);
        return fract((p4.xxyz + p4.yzzw) * p4.zywx);
}

void main() {
        vec4 modelSpacePos = vec4(inPosition, 1.0);
        vec3 modelSpaceNorm = inNormal;

   #if  defined(NORMALMAP)
        vec3 modelSpaceTan = inTangent.xyz;
   #endif

        vec3 worldPos = TransformWorld(modelSpacePos).xyz;
        worldPos += 0.1f * (hash44(vec4(worldPos, 0)).xyz - 0.5f);
	    gl_Position = vec4(worldPos, 1.0f);
        gl_Position = g_ViewProjectionMatrix * gl_Position;
        texCoord = inTexCoord;

        vec3 wvPosition = TransformWorldView(modelSpacePos).xyz;
        vec3 wvNormal = normalize(TransformNormal(modelSpaceNorm));
        vec3 viewDir = normalize(-wvPosition);

    #if defined(NORMALMAP)
        vTangent = vec4(TransformNormal(modelSpaceTan).xyz, inTangent.w);
        vNormal = wvNormal;
        vPos = wvPosition;
    #else
        vNormal = wvNormal;
        vPos = wvPosition;
    #endif

    #ifdef MATERIAL_COLORS
        AmbientSum = m_Ambient.rgb * g_AmbientLightColor.rgb;
        SpecularSum = m_Specular.rgb;
        DiffuseSum = m_Diffuse;
    #else
        // Defaults: Ambient and diffuse are white, specular is black.
        AmbientSum = g_AmbientLightColor.rgb;
        SpecularSum = vec3(0.0);
        DiffuseSum = vec4(1.0);
    #endif
}