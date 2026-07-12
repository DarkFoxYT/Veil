uniform sampler3D BlockGrid;
uniform vec3 GridOrigin;
uniform float GridCellSize;

#define VOXELSHADOW_GRID_SIZE 160
#define VOXELSHADOW_MAX_STEPS 384
#define VOXELSHADOW_DENSITY 4.35
#define VOXELSHADOW_START_SKIP 0.055
#define VOXELSHADOW_END_SKIP 0.095

bool voxelshadowClipToGrid(inout vec3 startG, inout vec3 endG) {
    vec3 delta = endG - startG;
    float tMin = 0.0;
    float tMax = 1.0;

    for (int axis = 0; axis < 3; axis++) {
        if (abs(delta[axis]) < 1e-5) {
            if (startG[axis] < 0.0 || startG[axis] >= float(VOXELSHADOW_GRID_SIZE)) {
                return false;
            }
            continue;
        }

        float invDelta = 1.0 / delta[axis];
        float nearT = (0.0 - startG[axis]) * invDelta;
        float farT = (float(VOXELSHADOW_GRID_SIZE) - 0.001 - startG[axis]) * invDelta;
        if (nearT > farT) {
            float tmp = nearT;
            nearT = farT;
            farT = tmp;
        }

        tMin = max(tMin, nearT);
        tMax = min(tMax, farT);
        if (tMin > tMax) {
            return false;
        }
    }

    vec3 clippedStart = startG + delta * tMin;
    endG = startG + delta * tMax;
    startG = clippedStart;
    return true;
}

float voxelshadowOccupancy(ivec3 cell) {
    return texelFetch(BlockGrid, cell, 0).r;
}

float voxelshadowTraceVisibility(vec3 fragPos, vec3 lightPos, float densityScale) {
    float cellSize = max(GridCellSize, 0.03125);
    vec3 startG = (fragPos - GridOrigin) / cellSize;
    vec3 endG = (lightPos - GridOrigin) / cellSize;
    if (!voxelshadowClipToGrid(startG, endG)) return 1.0;

    vec3 delta = endG - startG;
    float rayLenGrid = length(delta);
    if (rayLenGrid < 0.001) return 1.0;

    vec3 rDir = delta / rayLenGrid;
    ivec3 cell = ivec3(floor(startG));
    ivec3 iStep = ivec3(sign(rDir));

    vec3 invAbs = 1.0 / max(abs(rDir), vec3(1e-5));
    vec3 tDelta = invAbs;

    vec3 cellF = vec3(cell);
    vec3 tMax;
    tMax.x = (rDir.x >= 0.0) ? (cellF.x + 1.0 - startG.x) * invAbs.x : (startG.x - cellF.x) * invAbs.x;
    tMax.y = (rDir.y >= 0.0) ? (cellF.y + 1.0 - startG.y) * invAbs.y : (startG.y - cellF.y) * invAbs.y;
    tMax.z = (rDir.z >= 0.0) ? (cellF.z + 1.0 - startG.z) * invAbs.z : (startG.z - cellF.z) * invAbs.z;

    float minT = min(VOXELSHADOW_START_SKIP / cellSize, rayLenGrid * 0.35);
    float maxT = max(minT, rayLenGrid - min(VOXELSHADOW_END_SKIP / cellSize, rayLenGrid * 0.25));
    float traveled = 0.0;
    float opticalDepth = 0.0;
    float hardBlocker = 0.0;
    float firstBlocker = rayLenGrid;

    for (int i = 0; i < VOXELSHADOW_MAX_STEPS; i++) {
        if (any(lessThan(cell, ivec3(0))) || any(greaterThanEqual(cell, ivec3(VOXELSHADOW_GRID_SIZE)))) break;

        float nextT = min(tMax.x, min(tMax.y, tMax.z));
        float segmentStart = max(traveled, minT);
        float segmentEnd = min(min(nextT, rayLenGrid), maxT);
        if (segmentEnd > segmentStart) {
            float occupancy = voxelshadowOccupancy(cell);
            if (occupancy > 0.01) {
                hardBlocker = max(hardBlocker, occupancy);
                firstBlocker = min(firstBlocker, segmentStart);

                float density = smoothstep(0.025, 0.92, occupancy);
                opticalDepth += density * (segmentEnd - segmentStart) * cellSize * VOXELSHADOW_DENSITY * densityScale;
                if (opticalDepth >= 5.5) break;
            }
        }

        if (nextT >= rayLenGrid || traveled >= maxT) break;
        traveled = nextT;

        if (tMax.x < tMax.y && tMax.x < tMax.z) {
            tMax.x += tDelta.x;
            cell.x += iStep.x;
        } else if (tMax.y < tMax.z) {
            tMax.y += tDelta.y;
            cell.y += iStep.y;
        } else {
            tMax.z += tDelta.z;
            cell.z += iStep.z;
        }
    }

    float visibility = exp(-opticalDepth);
    if (hardBlocker > 0.90) {
        float blockerDistance = clamp(firstBlocker / max(rayLenGrid, 0.001), 0.0, 1.0);
        float contact = 1.0 - smoothstep(0.06, 0.38, blockerDistance);
        visibility = min(visibility, mix(0.12, 0.015, contact));
    } else if (hardBlocker > 0.42) {
        float blockerDistance = clamp(firstBlocker / max(rayLenGrid, 0.001), 0.0, 1.0);
        float contact = 1.0 - smoothstep(0.04, 0.42, blockerDistance);
        visibility *= mix(1.0, 0.72, contact * smoothstep(0.42, 0.88, hardBlocker));
    }
    return clamp(visibility, 0.0, 1.0);
}

float voxelshadowVisibility(vec3 fragPos, vec3 lightPos) {
    vec3 ray = lightPos - fragPos;
    float rayLen = length(ray);
    if (rayLen < 0.001) return 1.0;

    vec3 rayDir = ray / rayLen;
    vec3 up = abs(rayDir.y) < 0.82 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(rayDir, up));
    vec3 bitangent = normalize(cross(rayDir, tangent));
    float penumbra = clamp(rayLen * 0.018, 0.045, 0.32);

    float center = voxelshadowTraceVisibility(fragPos, lightPos, 1.00);
    float soft = center * 0.44;
    soft += voxelshadowTraceVisibility(fragPos + tangent * penumbra, lightPos - tangent * penumbra * 0.65, 0.82) * 0.14;
    soft += voxelshadowTraceVisibility(fragPos - tangent * penumbra, lightPos + tangent * penumbra * 0.65, 0.82) * 0.14;
    soft += voxelshadowTraceVisibility(fragPos + bitangent * penumbra, lightPos - bitangent * penumbra * 0.65, 0.82) * 0.14;
    soft += voxelshadowTraceVisibility(fragPos - bitangent * penumbra, lightPos + bitangent * penumbra * 0.65, 0.82) * 0.14;

    float litFloor = smoothstep(0.0, 0.24, center) * 0.035;
    return clamp(max(soft, litFloor), 0.0, 1.0);
}

float voxelshadowSpotVisibility(vec3 fragPos, vec3 lightPos) {
    vec3 ray = lightPos - fragPos;
    float rayLen = length(ray);
    if (rayLen < 0.001) return 1.0;

    vec3 rayDir = ray / rayLen;
    vec3 up = abs(rayDir.y) < 0.82 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(rayDir, up));
    vec3 bitangent = normalize(cross(rayDir, tangent));
    float penumbra = clamp(rayLen * 0.006, 0.012, 0.11);

    float center = voxelshadowTraceVisibility(fragPos, lightPos, 1.22);
    float shadow = center * 0.76;
    shadow += voxelshadowTraceVisibility(fragPos + tangent * penumbra, lightPos, 1.02) * 0.06;
    shadow += voxelshadowTraceVisibility(fragPos - tangent * penumbra, lightPos, 1.02) * 0.06;
    shadow += voxelshadowTraceVisibility(fragPos + bitangent * penumbra, lightPos, 1.02) * 0.06;
    shadow += voxelshadowTraceVisibility(fragPos - bitangent * penumbra, lightPos, 1.02) * 0.06;
    return clamp(shadow, 0.0, 1.0);
}

float voxelshadowSpotBeamVisibility(vec3 fragPos, vec3 lightPos) {
    return voxelshadowTraceVisibility(fragPos, lightPos, 0.82);
}

// Directional lights have no finite endpoint. Trace to the edge of the shared
// shadow map and use a compact cross filter to avoid the stair-stepped look of
// a single voxel ray. This keeps directional shadows on the same bounded map
// and update budget as every other Veil light.
float voxelshadowDirectionalVisibility(vec3 fragPos, vec3 toLight) {
    vec3 direction = normalize(toLight);
    float mapDistance = float(VOXELSHADOW_GRID_SIZE) * max(GridCellSize, 0.03125) * 1.75;
    vec3 lightPos = fragPos + direction * mapDistance;
    vec3 up = abs(direction.y) < 0.82 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 tangent = normalize(cross(direction, up));
    vec3 bitangent = normalize(cross(direction, tangent));
    float texel = max(GridCellSize, 0.03125) * 0.72;

    float visibility = voxelshadowTraceVisibility(fragPos, lightPos, 1.15) * 0.52;
    visibility += voxelshadowTraceVisibility(fragPos + tangent * texel, lightPos, 0.92) * 0.12;
    visibility += voxelshadowTraceVisibility(fragPos - tangent * texel, lightPos, 0.92) * 0.12;
    visibility += voxelshadowTraceVisibility(fragPos + bitangent * texel, lightPos, 0.92) * 0.12;
    visibility += voxelshadowTraceVisibility(fragPos - bitangent * texel, lightPos, 0.92) * 0.12;
    return clamp(visibility, 0.0, 1.0);
}
