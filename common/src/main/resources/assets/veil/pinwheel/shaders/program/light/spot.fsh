#include veil:deferred_light

in vec2 texCoord;

uniform vec4 LightPositionRange;
uniform vec4 LightColorFalloff;
uniform vec4 LightDirectionInnerCone;
uniform vec4 LightOuterConeSpecularOcclusion;
uniform vec4 LightParams;

out vec4 fragColor;

void main() {
    DeferredSpotLight light = DeferredSpotLight(
            LightPositionRange,
            LightColorFalloff,
            LightDirectionInnerCone,
            LightOuterConeSpecularOcclusion,
            LightParams);

    float depth = texture(DepthSampler, texCoord).r;
    vec3 lightAccum = deferredEvaluateSpotGodRays(texCoord, depth, light);

    DeferredSurface surface;
    if (deferredLoadSurface(texCoord, surface)) {
        lightAccum += deferredEvaluateSpotLight(surface, light);
    }

    if (max(max(lightAccum.r, lightAccum.g), lightAccum.b) <= 0.00001) {
        discard;
    }
    fragColor = vec4(lightAccum, 1.0);
}
