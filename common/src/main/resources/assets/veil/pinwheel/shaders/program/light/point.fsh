#include veil:deferred_light

layout(std430) readonly buffer VeilPointLights {
    DeferredPointLight PointLights[];
};

in vec2 texCoord;

uniform int LightCount;

out vec4 fragColor;

void main() {
    DeferredSurface surface;
    if (!deferredLoadSurface(texCoord, surface)) {
        discard;
    }

    vec3 lightAccum = vec3(0.0);
    for (int i = 0; i < LightCount; i++) {
        lightAccum += deferredEvaluatePointLight(surface, PointLights[i]);
    }

    if (max(max(lightAccum.r, lightAccum.g), lightAccum.b) <= 0.00001) {
        discard;
    }
    fragColor = vec4(lightAccum, 1.0);
}
