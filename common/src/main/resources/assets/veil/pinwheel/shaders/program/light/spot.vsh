#veil:buffer veil:camera VeilCamera

layout (location = 0) in vec3 Position;
layout (location = 1) in vec3 LightPosition;
layout (location = 2) in vec3 LightDirection;
layout (location = 3) in vec3 Color;
layout (location = 4) in float Range;
layout (location = 5) in float InnerAngle;
layout (location = 6) in float OuterAngle;
layout (location = 7) in float Occluded;

out vec3 lightPos;
out vec3 lightDirection;
out vec3 lightColor;
out float lightRange;
out float innerAngle;
out float outerAngle;
out float occluded;

void main() {
    vec3 forward = normalize(LightDirection);
    vec3 up = abs(forward.y) > 0.999 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
    vec3 right = normalize(cross(up, forward));
    vec3 coneUp = cross(forward, right);

    float coneRadius = tan(clamp(OuterAngle, 0.001, 1.55334)) * Range;
    vec3 localPos = vec3(Position.xy * coneRadius, Position.z * Range);
    vec3 worldPos = LightPosition + right * localPos.x + coneUp * localPos.y + forward * localPos.z;

    gl_Position = VeilCamera.ProjMat * VeilCamera.ViewMat * vec4(worldPos - VeilCamera.CameraPosition, 1.0);

    lightPos = LightPosition;
    lightDirection = forward;
    lightColor = Color;
    lightRange = Range;
    innerAngle = InnerAngle;
    outerAngle = OuterAngle;
    occluded = Occluded;
}
