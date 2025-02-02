#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform float g_Time;

#ifdef OBJECTIVE_TEXTURE
    uniform sampler2D m_ObjectiveTexture;
#endif

    uniform sampler2D m_CenterTexture;
    uniform sampler2D m_HealthTexture;

varying vec4 color;
varying vec2 texCoord;

#ifdef EXPERIENCE
    uniform float m_Experience;
#endif

uniform bool m_FlashColors;
uniform float m_FlashInterval;
varying vec4 color1;
varying vec4 color2;
varying vec4 color3;
varying vec4 color4;
varying vec4 color5;
varying vec4 color6;
varying vec4 color7;

const float PI = 3.14159265358979323846264;
const int numOfColors = 7;

mediump vec2 fastUnitVector_rough(mediump float angle) {
    // Simplest possible approximation
    // Scale angle to smaller range first to avoid precision loss
    // range ~ [0,1]
    mediump float a = angle * 0.25; // 1/(8) instead of 1/(2π)
    // quadratic approximation
    // creates a parabola that goes from 0→0.25→0 as input goes from 0→0.5→1
    // This is stable because the quadratic function is smooth
    // The result s is always in [0, 0.25] range
    mediump float t = a - a * a;
    return vec2(1.0 - t, t * 4.0);
}

// For sectors greater than 180 degrees, use this version:
bool isPointInWideSektor(vec2 point, vec2 dirA, vec2 dirB) {
    //float lenSq = dot(point, point);
    //if (lenSq > 1.0) return false;

    float cross1 = dirA.x * point.y - dirA.y * point.x;
    float cross2 = point.x * dirB.y - point.y * dirB.x;

    // Check if the angle between vectors is > 180 degrees using cross product
    float sectorCross = dirA.x * dirB.y - dirA.y * dirB.x;

    return sectorCross >= 0.0 ?
           (cross1 >= 0.0 && cross2 >= 0.0) :
           (cross1 >= 0.0 || cross2 >= 0.0);
}

void main() {

    // Scale the center and health, they are half the size of the canvas
    vec2 scaleCenter = vec2(0.5);
    vec2 uv = texCoord - scaleCenter;
    vec2 uvScaled = uv * 2.0 + scaleCenter;
    float invY = 1.0 - texCoord.y;

    vec4 finalColor = vec4(0.0);

    // Draw the textures with Porter-Duff Source Over Destination rule
    if (all(greaterThanEqual(uvScaled, vec2(0.0))) && all(lessThanEqual(uvScaled, vec2(1.0)))) {
        vec4 centerTextureColor = texture(m_CenterTexture, vec2(uvScaled.x, 1.0 - uvScaled.y));
        vec4 healthTextureColor = texture(m_HealthTexture, vec2(uvScaled.x, 1.0 - uvScaled.y));

        // Blend textures
        finalColor = centerTextureColor + finalColor*(1.0-centerTextureColor.a);
        finalColor = healthTextureColor + finalColor*(1.0-healthTextureColor.a);
    }

#ifdef OBJECTIVE_TEXTURE
    vec4 objectiveTextureColor = texture(m_ObjectiveTexture, vec2(texCoord.x, invY));

    // Some "dirt" on the objective texture, or I just can't pick a correct composition mode
    if(objectiveTextureColor.a > 0.075) {
        finalColor = objectiveTextureColor + finalColor*(1.0-objectiveTextureColor.a);
    }
#endif

    // Simplified color flashing
    if(m_FlashColors) {
        float t = mod(g_Time * m_FlashInterval, 7.0);
        int i = int(t);

        // Simple step-based color selection
        vec4 currentColor =
            step(6.0, float(i)) * color7 +
            step(5.0, float(i)) * (1.0 - step(6.0, float(i))) * color6 +
            step(4.0, float(i)) * (1.0 - step(5.0, float(i))) * color5 +
            step(3.0, float(i)) * (1.0 - step(4.0, float(i))) * color4 +
            step(2.0, float(i)) * (1.0 - step(3.0, float(i))) * color3 +
            step(1.0, float(i)) * (1.0 - step(2.0, float(i))) * color2 +
            (1.0 - step(1.0, float(i))) * color1;

        finalColor *= currentColor;
    } else {
        finalColor *= color;
    }

#ifdef EXPERIENCE
    // Normalize coordinates to [-1,1]
    vec2 point = normalize(2.0 * vec2(texCoord.x, 1-texCoord.y) - 1);

    // Define sector by two normalized direction vectors
    float angle = -(m_Experience -0.25)*6.28; // 45 degrees in radians
    vec2 dirA = vec2(0.0, 1.0);  // Starting direction (positive X axis)
    vec2 dirB = vec2(cos(angle), sin(angle));  // Ending direction
	//vec2 dirB = fastUnitVector(angle);
    bool inside = isPointInWideSektor(point, dirA, dirB);

    finalColor.xyz -= vec3(float(inside) * 0.2);
    //finalColor.xy = vec2(texCoord.x, 1-texCoord.y);
    //finalColor.xy = dirB;
    //finalColor.w = 1;
#endif

    gl_FragColor = finalColor;
}
