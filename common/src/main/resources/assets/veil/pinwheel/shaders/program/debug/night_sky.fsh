#version 150

#include veil:space_helper
#veil:buffer veil:camera VeilCamera

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;
uniform sampler2D MoonSampler;

uniform float Time;
uniform float NightStrength;
uniform float SkyStrength;
uniform float StarDensity;
uniform float ColorStrength;
uniform float MeteorStrength;

in vec2 texCoord;

out vec4 fragColor;

#define PI 3.14159265359
#define TAU 6.28318530718

float hash2(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash2(i);
    float b = hash2(i + vec2(1.0, 0.0));
    float c = hash2(i + vec2(0.0, 1.0));
    float d = hash2(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

vec2 domeCoord(vec3 rd) {
    float denom = max(rd.y + 1.24, 0.24);
    return rd.xz / denom;
}

vec3 starTint(vec2 cell) {
    float h = hash2(cell * 0.41 + 19.7);
    vec3 cool = vec3(0.68, 0.80, 1.00);
    vec3 warm = vec3(1.00, 0.94, 0.78);
    vec3 rose = vec3(1.00, 0.68, 0.82);
    vec3 tint = mix(cool, warm, smoothstep(0.15, 0.72, h));
    return mix(tint, rose, smoothstep(0.82, 1.0, h) * 0.35);
}

float starLayer(vec2 dome, float scale, float threshold, float size, float phase, out vec2 cell) {
    vec2 p = dome * scale + vec2(phase, -phase * 0.37);
    cell = floor(p);
    vec2 local = abs(fract(p) - 0.5);
    float seed = hash2(cell);
    float star = step(threshold, seed);
    float box = max(local.x, local.y);
    float shape = 1.0 - smoothstep(size, size + 0.018, box);
    float twinkle = 0.84 + 0.16 * sin(Time * 0.65 + seed * TAU);
    return star * shape * twinkle;
}

vec3 skyGradient(vec3 rd, vec2 dome) {
    float up = smoothstep(-0.04, 0.82, rd.y);
    vec3 horizon = vec3(0.030, 0.050, 0.110);
    vec3 mid = vec3(0.032, 0.072, 0.165);
    vec3 zenith = vec3(0.006, 0.010, 0.035);
    vec3 sky = mix(horizon, mid, up);
    sky = mix(sky, zenith, smoothstep(0.45, 1.0, rd.y) * 0.72);

    float nebulaA = valueNoise(dome * 2.1 + vec2(Time * 0.006, -Time * 0.002));
    float nebulaB = valueNoise(dome * 3.7 + vec2(-6.0, 3.0));
    sky += vec3(0.085, 0.035, 0.185) * smoothstep(0.42, 0.88, nebulaA) * smoothstep(0.0, 0.72, rd.y) * 0.14;
    sky += vec3(0.020, 0.110, 0.145) * smoothstep(0.56, 0.93, nebulaB) * smoothstep(0.10, 0.88, rd.y) * 0.09;
    return sky;
}

float moonMask(vec3 rd, vec3 moonDir, out vec2 moonUv) {
    vec3 upHint = abs(moonDir.y) > 0.92 ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);
    vec3 right = normalize(cross(upHint, moonDir));
    vec3 up = normalize(cross(moonDir, right));
    vec2 local = vec2(dot(rd, right), dot(rd, up)) / 0.18;
    moonUv = local * 0.5 + 0.5;
    moonUv.y = 1.0 - moonUv.y;
    return 1.0 - smoothstep(0.96, 1.04, length(local));
}

vec3 meteor(vec2 dome, float seed) {
    float cycle = fract(Time * 0.018 + seed);
    float active = smoothstep(0.02, 0.08, cycle) * (1.0 - smoothstep(0.28, 0.42, cycle));
    vec2 dir = normalize(vec2(-0.78, -0.46));
    vec2 head = vec2(0.88 + seed * 0.35, 0.68 + hash2(vec2(seed, 3.0)) * 0.25) + dir * cycle * 2.1;
    float along = dot(dome - head, -dir);
    float fromLine = distance(dome, head - dir * along);
    float trail = (1.0 - smoothstep(0.0, 0.010, fromLine)) * smoothstep(0.0, 0.06, along) * (1.0 - smoothstep(0.12, 0.42, along));
    float headGlow = 1.0 - smoothstep(0.0, 0.018, distance(dome, head));
    return (vec3(0.72, 0.86, 1.0) * trail + vec3(1.0, 0.96, 0.84) * headGlow * 1.8) * active * MeteorStrength;
}

bool looksLikeSky(vec3 color) {
    float maxChannel = max(max(color.r, color.g), color.b);
    float minChannel = min(min(color.r, color.g), color.b);
    float saturation = maxChannel - minChannel;
    bool missingTexture = color.r > 0.82 && color.g < 0.22 && color.b > 0.82;
    bool invalidBlank = maxChannel < 0.0001 || (maxChannel > 0.98 && saturation < 0.015);
    if (missingTexture || invalidBlank) {
        return false;
    }
    bool blueSky = color.b > color.r * 1.08 && color.g > color.r * 0.82;
    bool paleSky = maxChannel > 0.72 && saturation < 0.22 && color.b >= color.r * 0.92;
    bool nightSky = maxChannel < 0.28 && color.b >= color.r && color.g >= color.r * 0.55;
    return blueSky || paleSky || nightSky;
}

void main() {
    vec4 base = texture(DiffuseSampler, texCoord);
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    vec3 worldFar = screenToWorldSpace(texCoord, 1.0).xyz;
    vec3 rd = normalize(worldFar - VeilCamera.CameraPosition);

    bool clearDepth = depth >= 0.999995;
    if (!clearDepth || rd.y < -0.02 || !looksLikeSky(base.rgb)) {
        fragColor = base;
        return;
    }

    float night = clamp(NightStrength, 0.0, 1.0);
    float skyStrength = clamp(SkyStrength * night, 0.0, 1.0);
    if (skyStrength <= 0.001) {
        fragColor = base;
        return;
    }

    vec2 dome = domeCoord(rd);
    float horizonFade = smoothstep(-0.05, 0.24, rd.y);

    vec2 farCell;
    vec2 nearCell;
    float farStars = starLayer(dome, 118.0, StarDensity, 0.038, 13.0, farCell);
    float nearStars = starLayer(dome, 184.0, min(0.993, StarDensity + 0.012), 0.032, -29.0, nearCell);
    vec3 stars = starTint(farCell) * farStars * 2.15 + starTint(nearCell + 41.0) * nearStars * 2.55;
    stars *= horizonFade * night;

    vec3 moonDir = normalize(vec3(-0.38, 0.55, 0.74));
    vec2 moonUv;
    float moon = moonMask(rd, moonDir, moonUv);
    vec2 atlasUv = moonUv / vec2(4.0, 2.0);
    vec4 moonTex = texture(MoonSampler, clamp(atlasUv, vec2(0.0), vec2(0.25, 0.5)));
    float moonHalo = pow(max(dot(rd, moonDir), 0.0), 26.0) * 0.24;
    vec3 moonColor = moonTex.rgb * moonTex.a * moon * 1.5 + vec3(0.16, 0.20, 0.36) * moonHalo;
    stars *= 1.0 - moon;

    vec3 color = skyGradient(rd, dome) + stars + moonColor + meteor(dome, 0.19) + meteor(dome, 0.67);
    color *= ColorStrength;

    fragColor = vec4(mix(base.rgb, clamp(color, 0.0, 1.35), skyStrength), base.a);
}
