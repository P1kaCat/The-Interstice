#veil:buffer veil:camera VeilCamera

// Level 02, The Wheat Farm: the drawn wheat (WheatRenderer). Positions are world coordinates; the height up the
// stalk comes from the texture row.

layout (location = 0) in vec3 Position;
layout (location = 1) in vec2 UV0;
layout (location = 2) in vec4 Color;

uniform float Time;
uniform float Radius;

out vec2 texCoord;
out vec3 tint;
out float height;
out float dist;
out float sheen;

const vec2 WIND = vec2(0.8, 0.6);

void main() {
    vec3 world = Position;
    // how far up the stalk: from the texture row (v = 1 at the ground, 0 at the ears). The vertex colour is not used:
    // its bytes did not arrive in the order the shader expected (teal stalks, whole stalks swaying like their tops).
    float w = 1.0 - UV0.y;
    // long waves travelling across the field with the wind, modulated by slow broad gusts
    float phase = dot(world.xz, WIND) * 0.075 - Time * 1.35;
    float wave = sin(phase) * 0.5 + 0.5;
    float gust = sin(dot(world.xz, vec2(0.011, 0.019)) - Time * 0.32) * 0.5 + 0.5;
    float lean = (0.10 + 0.42 * wave * (0.35 + 0.65 * gust)) * w * w;
    // each ear trembles on its own
    float flutter = sin(Time * 5.3 + world.x * 1.9 + world.z * 1.3) * 0.035 * w;
    world.xz += WIND * lean + vec2(flutter, -flutter * 0.6);
    world.y -= lean * lean * 0.55;
    // towards the edge of the drawn area the stalks sink into the field surface (no visible border)
    vec3 rel = world - VeilCamera.CameraPosition;
    float d = length(rel.xz);
    float sink = smoothstep(Radius * 0.72, Radius, d);
    world.y -= sink * w * 1.4;
    rel = world - VeilCamera.CameraPosition;
    gl_Position = VeilCamera.ProjMat * VeilCamera.ViewMat * vec4(rel, 1.0);
    texCoord = UV0;
    tint = vec3(0.98, 0.95, 0.90);
    height = w;
    dist = length(rel);
    sheen = wave * gust * w;
}
