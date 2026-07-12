#include veil:common
#include veil:space_helper
#include veil:light
#ifndef VEIL_DEFERRED_NO_VOXEL_SHADOWS
#include veil:voxel_shadow
#endif

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;
uniform sampler2D AlbedoSampler;
uniform sampler2D NormalSampler;
uniform int HasAlbedoSampler;
uniform int HasNormalSampler;

const float DEFERRED_SKY_DEPTH = 0.99999;
const float DEFERRED_MIN_RANGE = 0.001;
const float DEFERRED_LIGHT_EPSILON = 0.0001;
// Sixteen low-cost samples with a stable interleaved offset look substantially
// smoother than a visibly banded fixed ten-sample march. The offset is screen
// stable, so it does not shimmer while the camera is still.
const int DEFERRED_GODRAY_STEPS = 16;

float deferredInterleavedNoise(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

struct DeferredSurface {
    vec3 position;
    vec3 normal;
    vec3 albedo;
    vec3 viewDirection;
    float roughness;
};

struct DeferredPointLight {
    vec4 positionRange;
    vec4 colorFalloff;
    vec4 params;
};

struct DeferredSpotLight {
    vec4 positionRange;
    vec4 colorFalloff;
    vec4 directionInnerCone;
    vec4 outerConeSpecularOcclusion;
    vec4 params;
};

vec3 deferredSceneAlbedo(vec3 color) {
    color = max(color, vec3(0.0));
    float detail = max(max(color.r, color.g), color.b);
    vec3 lifted = pow(color, vec3(0.82)) * 1.18;
    return clamp(mix(color, lifted, smoothstep(0.015, 0.32, detail)), vec3(0.015), vec3(1.0));
}

vec3 deferredNormalFromDepth(vec3 position) {
    vec3 dx = dFdx(position);
    vec3 dy = dFdy(position);
    vec3 normal = cross(dx, dy);
    if (dot(normal, normal) < 0.00000001) {
        return normalize(VeilCamera.CameraPosition - position);
    }

    normal = normalize(normal);
    vec3 viewDirection = normalize(position - VeilCamera.CameraPosition);
    return dot(normal, viewDirection) > 0.0 ? -normal : normal;
}

vec3 deferredReadAlbedo(vec2 uv) {
    if (HasAlbedoSampler != 0) {
        vec4 albedo = texture(AlbedoSampler, uv);
        if (albedo.a > 0.001 && max(max(albedo.r, albedo.g), albedo.b) > 0.0001) {
            return max(albedo.rgb, vec3(0.0));
        }
    }
    return deferredSceneAlbedo(texture(SceneSampler, uv).rgb);
}

vec3 deferredReadNormal(vec2 uv, vec3 position) {
    if (HasNormalSampler != 0) {
        vec3 encodedNormal = texture(NormalSampler, uv).xyz;
        if (dot(encodedNormal, encodedNormal) > 0.0001) {
            vec3 normalWS = (VeilCamera.IViewMat * vec4(normalize(encodedNormal), 0.0)).xyz;
            return normalize(normalWS);
        }
    }
    return deferredNormalFromDepth(position);
}

float deferredRoughnessFromAlbedo(vec3 albedo) {
    float luminance = dot(albedo, vec3(0.2126, 0.7152, 0.0722));
    return clamp(0.68 - luminance * 0.28, 0.32, 0.82);
}

bool deferredLoadSurface(vec2 uv, out DeferredSurface surface) {
    float depth = texture(DepthSampler, uv).r;
    if (depth >= DEFERRED_SKY_DEPTH) {
        return false;
    }

    surface.position = screenToWorldSpace(uv, depth).xyz;
    surface.albedo = deferredReadAlbedo(uv);
    surface.normal = deferredReadNormal(uv, surface.position);
    surface.viewDirection = normalize(VeilCamera.CameraPosition - surface.position);
    surface.roughness = deferredRoughnessFromAlbedo(surface.albedo);
    return true;
}

float deferredAttenuation(float distanceToLight, float range, float falloff, float falloffType) {
    float normalizedDistance = clamp(distanceToLight / max(range, DEFERRED_MIN_RANGE), 0.0, 1.0);
    float base = max(1.0 - normalizedDistance, 0.0);
    float curve = max(falloff, 0.001);

    if (falloffType > 2.5) {
        float inverse = 1.0 / (1.0 + pow(distanceToLight / max(range * 0.28, DEFERRED_MIN_RANGE), 2.0 * curve));
        return inverse * smoothstep(1.0, 0.0, normalizedDistance);
    }
    if (falloffType > 1.5) {
        return pow(base * base, curve);
    }
    if (falloffType > 0.5) {
        return pow(base, curve);
    }

    float shaped = pow(base, curve);
    return shaped * shaped * (3.0 - 2.0 * shaped);
}

vec3 deferredEvaluateLight(DeferredSurface surface, vec3 lightPosition, vec3 lightColor, float range, float falloff, float specularStrength, float occlusion, float godRayStrength, float falloffType) {
    if (range <= DEFERRED_MIN_RANGE || max(max(lightColor.r, lightColor.g), lightColor.b) <= DEFERRED_LIGHT_EPSILON) {
        return vec3(0.0);
    }

    vec3 offset = lightPosition - surface.position;
    float distanceToLight = length(offset);
    if (distanceToLight >= range || distanceToLight <= 0.0001) {
        return vec3(0.0);
    }

    vec3 lightDirection = offset / distanceToLight;
    float diffuse = max(dot(surface.normal, lightDirection), 0.0);
    float attenuation = deferredAttenuation(distanceToLight, range, falloff, falloffType);
#ifndef VEIL_DEFERRED_NO_VOXEL_SHADOWS
    if (occlusion > 0.0001) {
        float visibility = voxelshadowVisibility(surface.position + surface.normal * 0.025, lightPosition);
        attenuation *= mix(1.0, visibility, clamp(occlusion, 0.0, 1.0));
    }
#endif

    float specular = 0.0;
    if (specularStrength > DEFERRED_LIGHT_EPSILON && diffuse > DEFERRED_LIGHT_EPSILON) {
        vec3 halfVector = normalize(lightDirection + surface.viewDirection);
        float shininess = mix(72.0, 18.0, surface.roughness);
        specular = pow(max(dot(surface.normal, halfVector), 0.0), shininess) * specularStrength;
        specular *= smoothstep(0.04, 0.35, diffuse);
    }

    float scatter = godRayStrength > DEFERRED_LIGHT_EPSILON ? godRayStrength * attenuation * 0.05 : 0.0;
    if (diffuse <= DEFERRED_LIGHT_EPSILON && specular <= DEFERRED_LIGHT_EPSILON && scatter <= DEFERRED_LIGHT_EPSILON) {
        return vec3(0.0);
    }

    return (surface.albedo * diffuse + vec3(specular + scatter)) * lightColor * attenuation + lightColor * scatter;
}

vec3 deferredEvaluatePointLight(DeferredSurface surface, DeferredPointLight light) {
    return deferredEvaluateLight(
            surface,
            light.positionRange.xyz,
            light.colorFalloff.rgb,
            light.positionRange.w,
            light.colorFalloff.w,
            light.params.x,
            light.params.y,
            light.params.z,
            light.params.w);
}

float deferredSpotConeFalloff(vec3 worldPosition, DeferredSpotLight light) {
    vec3 lightToPosition = worldPosition - light.positionRange.xyz;
    float distanceToPosition = length(lightToPosition);
    if (distanceToPosition <= 0.0001 || distanceToPosition >= light.positionRange.w) {
        return 0.0;
    }

    vec3 spotDirection = normalize(light.directionInnerCone.xyz);
    float coneCos = dot(lightToPosition / distanceToPosition, spotDirection);
    return smoothstep(light.outerConeSpecularOcclusion.x, light.directionInnerCone.w, coneCos);
}

vec3 deferredEvaluateSpotLight(DeferredSurface surface, DeferredSpotLight light) {
    if (light.positionRange.w <= DEFERRED_MIN_RANGE || max(max(light.colorFalloff.r, light.colorFalloff.g), light.colorFalloff.b) <= DEFERRED_LIGHT_EPSILON) {
        return vec3(0.0);
    }

    float coneFalloff = deferredSpotConeFalloff(surface.position, light);
    if (coneFalloff <= 0.0) {
        return vec3(0.0);
    }

    vec3 lightPosition = light.positionRange.xyz;
    vec3 offset = lightPosition - surface.position;
    float distanceToLight = length(offset);
    if (distanceToLight >= light.positionRange.w || distanceToLight <= 0.0001) {
        return vec3(0.0);
    }

    vec3 lightDirection = offset / distanceToLight;
    float diffuse = max(dot(surface.normal, lightDirection), 0.0);
    float attenuation = deferredAttenuation(distanceToLight, light.positionRange.w, light.colorFalloff.w, light.params.x);
#ifndef VEIL_DEFERRED_NO_VOXEL_SHADOWS
    if (light.outerConeSpecularOcclusion.z > 0.0001) {
        float visibility = voxelshadowSpotVisibility(surface.position + surface.normal * 0.04, lightPosition);
        attenuation *= mix(1.0, visibility, clamp(light.outerConeSpecularOcclusion.z, 0.0, 1.0));
    }
#endif

    float specular = 0.0;
    if (light.outerConeSpecularOcclusion.y > DEFERRED_LIGHT_EPSILON && diffuse > DEFERRED_LIGHT_EPSILON) {
        vec3 halfVector = normalize(lightDirection + surface.viewDirection);
        float shininess = mix(72.0, 18.0, surface.roughness);
        specular = pow(max(dot(surface.normal, halfVector), 0.0), shininess) * light.outerConeSpecularOcclusion.y;
        specular *= smoothstep(0.04, 0.35, diffuse);
    }

    float scatter = light.outerConeSpecularOcclusion.w > DEFERRED_LIGHT_EPSILON ? light.outerConeSpecularOcclusion.w * attenuation * 0.05 : 0.0;
    if (diffuse <= DEFERRED_LIGHT_EPSILON && specular <= DEFERRED_LIGHT_EPSILON && scatter <= DEFERRED_LIGHT_EPSILON) {
        return vec3(0.0);
    }

    return ((surface.albedo * diffuse + vec3(specular + scatter)) * light.colorFalloff.rgb * attenuation + light.colorFalloff.rgb * scatter) * coneFalloff;
}

bool deferredRaySphere(vec3 rayOrigin, vec3 rayDirection, vec3 sphereCenter, float sphereRadius, out float rayStart, out float rayEnd) {
    vec3 offset = rayOrigin - sphereCenter;
    float b = dot(offset, rayDirection);
    float c = dot(offset, offset) - sphereRadius * sphereRadius;
    float h = b * b - c;
    if (h < 0.0) {
        return false;
    }

    h = sqrt(h);
    rayStart = max(-b - h, 0.0);
    rayEnd = -b + h;
    return rayEnd > rayStart;
}

vec3 deferredEvaluateSpotGodRays(vec2 uv, float sceneDepth, DeferredSpotLight light) {
    float godRayStrength = light.outerConeSpecularOcclusion.w;
    if (godRayStrength <= 0.0001) {
        return vec3(0.0);
    }

    vec3 rayOrigin = VeilCamera.CameraPosition;
    vec3 rayDirection = normalize(viewDirFromUv(uv));
    vec3 lightPosition = light.positionRange.xyz;
    float range = max(light.positionRange.w, DEFERRED_MIN_RANGE);

    float rayStart;
    float rayEnd;
    if (!deferredRaySphere(rayOrigin, rayDirection, lightPosition, range, rayStart, rayEnd)) {
        return vec3(0.0);
    }

    if (sceneDepth < DEFERRED_SKY_DEPTH) {
        float sceneDistance = length(screenToWorldSpace(uv, sceneDepth).xyz - rayOrigin);
        rayEnd = min(rayEnd, sceneDistance);
    }
    if (rayEnd <= rayStart) {
        return vec3(0.0);
    }

    float rayLength = rayEnd - rayStart;
    float stepLength = rayLength / float(DEFERRED_GODRAY_STEPS);
    float beam = 0.0;
    float jitter = deferredInterleavedNoise(gl_FragCoord.xy);
    for (int i = 0; i < DEFERRED_GODRAY_STEPS; i++) {
        float rayT = rayStart + (float(i) + jitter) * stepLength;
        vec3 samplePosition = rayOrigin + rayDirection * rayT;
        float cone = deferredSpotConeFalloff(samplePosition, light);
        if (cone <= 0.0) {
            continue;
        }

        float distanceToLight = length(samplePosition - lightPosition);
        float attenuation = deferredAttenuation(distanceToLight, range, light.colorFalloff.w, light.params.x);
#ifndef VEIL_DEFERRED_NO_VOXEL_SHADOWS
        if (light.outerConeSpecularOcclusion.z > 0.0001) {
            float visibility = voxelshadowSpotBeamVisibility(samplePosition, lightPosition);
            attenuation *= mix(1.0, visibility, clamp(light.outerConeSpecularOcclusion.z, 0.0, 1.0));
        }
#endif
        // A gentle distance weight prevents a harsh edge where the march hits
        // its range limit and gives the beam a more natural volumetric falloff.
        float distanceFade = smoothstep(1.0, 0.12, distanceToLight / range);
        beam += cone * attenuation * distanceFade;
    }

    beam *= stepLength / range;

    // Compress highlights so overlapping rays bloom smoothly instead of
    // producing hard additive stripes.
    beam = 1.0 - exp(-beam * 1.35);
    return light.colorFalloff.rgb * beam * godRayStrength * 0.52;
}

vec3 deferredEvaluateDirectionalLight(DeferredSurface surface, vec3 lightDirection, vec3 lightColor, float specularStrength, float shadowIntensity) {
    vec3 toLight = normalize(-lightDirection);
    float diffuse = max(dot(surface.normal, toLight), 0.0);
    diffuse = smoothstep(0.0, 0.55, diffuse);
#ifndef VEIL_DEFERRED_NO_VOXEL_SHADOWS
    if (shadowIntensity > DEFERRED_LIGHT_EPSILON) {
        float visibility = voxelshadowDirectionalVisibility(surface.position + surface.normal * 0.035, toLight);
        diffuse *= mix(1.0, visibility, clamp(shadowIntensity, 0.0, 1.0));
    }
#endif

    vec3 halfVector = normalize(toLight + surface.viewDirection);
    float specular = pow(max(dot(surface.normal, halfVector), 0.0), mix(72.0, 18.0, surface.roughness)) * specularStrength;
    specular *= smoothstep(0.08, 0.42, diffuse);

    return (surface.albedo * diffuse + vec3(specular)) * lightColor;
}
