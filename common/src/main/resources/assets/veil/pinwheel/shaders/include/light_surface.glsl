bool veil_light_valid_albedo(vec4 albedoColor) {
    bool missingMagenta = albedoColor.r > 0.85 && albedoColor.g < 0.25 && albedoColor.b > 0.85;
    return albedoColor.a > 0.0001 && !missingMagenta;
}

vec3 veil_light_base_color(vec4 mainColor) {
    return max(mainColor.rgb, vec3(0.0));
}

vec3 veil_light_base_color(vec4 mainColor, vec4 albedoColor) {
    if (veil_light_valid_albedo(albedoColor)) {
        return max(albedoColor.rgb, vec3(0.0));
    }
    return veil_light_base_color(mainColor);
}

vec3 veil_light_normal_vs(vec3 worldPos) {
    vec3 dx = dFdx(worldPos);
    vec3 dy = dFdy(worldPos);
    vec3 normalWS = cross(dx, dy);
    if (dot(normalWS, normalWS) > 0.000001) {
        normalWS = normalize(normalWS);
        vec3 viewDir = normalize(VeilCamera.CameraPosition - worldPos);
        if (dot(normalWS, viewDir) < 0.0) {
            normalWS = -normalWS;
        }
        return normalize((VeilCamera.ViewMat * vec4(normalWS, 0.0)).xyz);
    }

    return vec3(0.0, 0.0, 1.0);
}

vec3 veil_light_normal_vs(vec3 worldPos, vec4 albedoColor, vec3 normalSample) {
    if (veil_light_valid_albedo(albedoColor) && dot(normalSample, normalSample) > 0.0001) {
        return normalize(normalSample);
    }
    return veil_light_normal_vs(worldPos);
}
