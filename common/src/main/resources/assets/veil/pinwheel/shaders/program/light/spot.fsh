#veil:buffer veil:camera VeilCamera

#include veil:common
#include veil:space_helper
#include veil:color_utilities
#include veil:light
#include veil:light_surface
#include veil:voxel_shadow

in vec3 lightPos;
in vec3 lightDirection;
in vec3 lightColor;
in float lightRange;
in float innerAngle;
in float outerAngle;
in float occluded;

uniform sampler2D MainSampler;
uniform sampler2D AlbedoSampler;
uniform sampler2D NormalSampler;
uniform sampler2D DepthSampler;

uniform vec2 ScreenSize;

out vec4 fragColor;

void main() {
    vec2 screenUv = gl_FragCoord.xy / ScreenSize;

    float depth = texture(DepthSampler, screenUv).r;
    if (depth <= 0.000001 || depth >= 0.999995) {
        discard;
    }

    vec3 pos = screenToWorldSpace(screenUv, depth).xyz;
    vec3 lightToPoint = pos - lightPos;
    float distanceToLight = length(lightToPoint);
    if (distanceToLight <= 0.0001 || distanceToLight > lightRange) {
        discard;
    }

    float clampedOuter = clamp(outerAngle, 0.002, 1.55334);
    float clampedInner = clamp(innerAngle, 0.001, clampedOuter - 0.001);
    float spotAmount = dot(normalize(lightToPoint), normalize(lightDirection));
    float spotFalloff = smoothstep(cos(clampedOuter), cos(clampedInner), spotAmount);
    if (spotFalloff <= 0.001) {
        discard;
    }

    vec4 mainColor = texture(MainSampler, screenUv);
    vec4 albedoColor = texture(AlbedoSampler, screenUv);
    vec3 normalSample = texture(NormalSampler, screenUv).xyz;
    vec3 normalVS = veil_light_normal_vs(pos, albedoColor, normalSample);
    vec3 pointToLightVS = normalize((VeilCamera.ViewMat * vec4(-lightToPoint, 0.0)).xyz);

    float diffuse = clamp(dot(normalVS, pointToLightVS), 0.0, 1.0);
    diffuse = (diffuse + MINECRAFT_AMBIENT_LIGHT) / (1.0 + MINECRAFT_AMBIENT_LIGHT);
    diffuse *= attenuate_no_cusp(distanceToLight, lightRange) * spotFalloff;

    if (occluded > 0.5) {
        diffuse *= voxelshadowVisibility(pos, lightPos);
    }

    float reflectivity = 0.05;
    vec3 diffuseColor = diffuse * lightColor;
    fragColor = vec4(veil_light_base_color(mainColor, albedoColor) * diffuseColor * (1.0 - reflectivity) + diffuseColor * reflectivity, 1.0);
}
