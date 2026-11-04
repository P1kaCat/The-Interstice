#include veil:space_helper

// THE INTERSTICE - depth-aware volumetric height fog.
// Near field: ray-marched through animated 3D value noise (drifting fog banks).
// Far field: analytic exponential height-fog integral. Upward rays fade to black void.

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;

uniform float Time;
uniform vec3 FogColor;
uniform float FogDensity;
uniform float FogEnd;
uniform int FogSteps;
uniform float FloorY;
uniform float Emergency;
uniform float Quake;

in vec2 texCoord;
out vec4 fragColor;

const float HEIGHT_SCALE = 140.0;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash13(i);
    float b = hash13(i + vec3(1.0, 0.0, 0.0));
    float c = hash13(i + vec3(0.0, 1.0, 0.0));
    float d = hash13(i + vec3(1.0, 1.0, 0.0));
    float e = hash13(i + vec3(0.0, 0.0, 1.0));
    float g = hash13(i + vec3(1.0, 0.0, 1.0));
    float h = hash13(i + vec3(0.0, 1.0, 1.0));
    float k = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y), mix(mix(e, g, f.x), mix(h, k, f.x), f.y), f.z);
}

float densityAt(vec3 p) {
    float h = max(p.y - FloorY, 0.0);
    float falloff = exp(-h / HEIGHT_SCALE);
    float n = vnoise(p * 0.035 + vec3(Time * 0.03, Time * 0.004, Time * 0.05));
    float n2 = vnoise(p * 0.11 - vec3(0.0, Time * 0.05, 0.0));
    float banks = 0.5 + 1.0 * n * (0.7 + 0.3 * n2);
    // dust thrown up by earthquakes hangs close to the floor
    banks += Quake * 0.25 * exp(-h / 18.0);
    return falloff * banks;
}

void main() {
    vec4 col = texture(DiffuseSampler0, texCoord);
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    vec3 cam = VeilCamera.CameraPosition;
    vec3 wpos = screenToWorldSpace(texCoord, depth).xyz;
    vec3 ray = wpos - cam;
    float dist = length(ray);
    vec3 dir = ray / max(dist, 1e-4);
    bool sky = depth >= 0.99999;
    if (sky) dist = 1500.0;

    float sigma = 2.6 / max(FogEnd, 8.0);

    float marchLen = min(dist, 140.0);
    int steps = max(FogSteps, 1);
    float stepLen = marchLen / float(steps);
    float jitter = hash13(vec3(gl_FragCoord.xy, fract(Time) * 61.0));
    float optical = 0.0;
    for (int i = 0; i < 32; i++) {
        if (i >= steps) break;
        vec3 p = cam + dir * ((float(i) + jitter) * stepLen);
        optical += densityAt(p) * stepLen;
    }
    if (dist > marchLen) {
        float k = 1.0 / HEIGHT_SCALE;
        float y0 = max(cam.y + dir.y * marchLen - FloorY, 0.0);
        float len = dist - marchLen;
        float base = exp(-y0 * k);
        float dy = dir.y;
        float integral = abs(dy) > 1e-3 ? base * (1.0 - exp(-k * dy * len)) / (k * dy) : base * len;
        optical += max(integral, 0.0) * 0.9;
    }
    float transmittance = exp(-optical * sigma * FogDensity * 0.85);

    float alt = max(cam.y + dir.y * min(dist, 220.0) - FloorY, 0.0);
    vec3 haze = FogColor * (1.3 * exp(-alt / 240.0) + 0.04);
    haze = mix(haze, haze * vec3(1.6, 0.55, 0.5), Emergency * 0.3);
    haze += vec3(0.012, 0.010, 0.008) * Quake;

    if (sky) {
        float up = clamp(dir.y, 0.0, 1.0);
        col.rgb = mix(FogColor * 0.8, FogColor * 0.16, pow(up, 0.6));
    }
    fragColor = vec4(mix(haze, col.rgb, transmittance), 1.0);
}
