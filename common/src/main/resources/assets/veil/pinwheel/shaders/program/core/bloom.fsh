#include veil:color_utilities
#define SKY_DEPTH 0.99999

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D BloomSampler;
uniform sampler2D BlurFinal;

in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = texture(DiffuseSampler0, texCoord);

    float sceneDepth = texture(DiffuseDepthSampler, texCoord).r;
    if (sceneDepth >= SKY_DEPTH) {
        fragColor.a = 1.0;
        return;
    }

    vec4 bloomBase = texture(BloomSampler, texCoord);
    vec2 depthTexel = 1.0 / vec2(textureSize(DiffuseDepthSampler, 0));
    float depthMin = sceneDepth;
    float depthMax = sceneDepth;
    float depthLeft = texture(DiffuseDepthSampler, texCoord - vec2(depthTexel.x, 0.0)).r;
    float depthRight = texture(DiffuseDepthSampler, texCoord + vec2(depthTexel.x, 0.0)).r;
    float depthUp = texture(DiffuseDepthSampler, texCoord - vec2(0.0, depthTexel.y)).r;
    float depthDown = texture(DiffuseDepthSampler, texCoord + vec2(0.0, depthTexel.y)).r;
    depthMin = min(depthMin, min(min(depthLeft, depthRight), min(depthUp, depthDown)));
    depthMax = max(depthMax, max(max(depthLeft, depthRight), max(depthUp, depthDown)));

    float edgeFade = 1.0 - smoothstep(0.0015, 0.02, depthMax - depthMin) * 0.45;
    float sourceMask = smoothstep(0.35, 0.95, bloomBase.a);
    vec3 bloomBlur = texture(BlurFinal, texCoord).rgb * edgeFade * mix(1.1, 1.45, sourceMask);
    vec3 bloomSpark = bloomBase.rgb * sourceMask * 0.18;

    fragColor.rgb += acesToneMapping(bloomBlur + bloomSpark);
    fragColor.a = 1.0;
}
