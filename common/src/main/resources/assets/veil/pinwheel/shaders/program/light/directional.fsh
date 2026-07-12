#define VEIL_DEFERRED_NO_VOXEL_SHADOWS
#include veil:deferred_light

in vec2 texCoord;

uniform vec3 LightColor;
uniform vec3 LightDirection;
uniform float SpecularStrength;

out vec4 fragColor;

void main() {
    DeferredSurface surface;
    if (!deferredLoadSurface(texCoord, surface)) {
        discard;
    }

    vec3 light = deferredEvaluateDirectionalLight(surface, normalize(LightDirection), LightColor, max(SpecularStrength, 0.08));
    if (max(max(light.r, light.g), light.b) <= 0.00001) {
        discard;
    }
    fragColor = vec4(light, 1.0);
}
