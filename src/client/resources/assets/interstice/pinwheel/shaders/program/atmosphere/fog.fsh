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
uniform float RenderDist;
uniform float VertStart;
uniform float VertEnd;

// Dust in the light beams (BeamDust.java): up to 6 cones, the flashlight first.
// BeamNPos: apex; BeamNDir: unit axis; BeamNParams: (cos half-angle, length, strength, -); BeamNColor: rgb.
uniform int BeamCount;
uniform float PixelAngle;
uniform float DustDensity;
uniform vec3 Beam0Pos; uniform vec3 Beam0Dir; uniform vec4 Beam0Params; uniform vec3 Beam0Color;
uniform vec3 Beam1Pos; uniform vec3 Beam1Dir; uniform vec4 Beam1Params; uniform vec3 Beam1Color;
uniform vec3 Beam2Pos; uniform vec3 Beam2Dir; uniform vec4 Beam2Params; uniform vec3 Beam2Color;
uniform vec3 Beam3Pos; uniform vec3 Beam3Dir; uniform vec4 Beam3Params; uniform vec3 Beam3Color;
uniform vec3 Beam4Pos; uniform vec3 Beam4Dir; uniform vec4 Beam4Params; uniform vec3 Beam4Color;
uniform vec3 Beam5Pos; uniform vec3 Beam5Dir; uniform vec4 Beam5Params; uniform vec3 Beam5Color;

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

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

// The part [t0, t1] of the view ray inside a light cone (apex a, axis d, cos half-angle c, length len); t0 > t1 if none.
vec2 coneSpan(vec3 ro, vec3 rd, vec3 a, vec3 d, float c, float len) {
    vec3 co = ro - a;
    float dv = dot(rd, d), dco = dot(co, d), c2 = c * c;
    float A = dv * dv - c2;
    float B = 2.0 * (dv * dco - c2 * dot(rd, co));
    float C = dco * dco - c2 * dot(co, co);
    // the slab between the apex and the end of the cone, along the axis
    float s0 = -1e9, s1 = 1e9;
    if (abs(dv) > 1e-5) {
        float ta = -dco / dv, tb = (len - dco) / dv;
        s0 = min(ta, tb); s1 = max(ta, tb);
    } else if (dco < 0.0 || dco > len) {
        return vec2(1.0, 0.0);
    }
    float disc = B * B - 4.0 * A * C;
    if (abs(A) < 1e-6) return vec2(1.0, 0.0);
    if (A < 0.0) {
        if (disc < 0.0) return vec2(1.0, 0.0);
        float q = sqrt(disc);
        float r0 = (-B + q) / (2.0 * A), r1 = (-B - q) / (2.0 * A);
        return vec2(max(min(r0, r1), s0), min(max(r0, r1), s1));
    }
    if (disc < 0.0) return vec2(s0, s1);
    float q = sqrt(disc);
    float r0 = (-B - q) / (2.0 * A), r1 = (-B + q) / (2.0 * A);
    vec2 lo = vec2(s0, min(s1, min(r0, r1)));
    vec2 hi = vec2(max(s0, max(r0, r1)), s1);
    if (lo.x <= lo.y && hi.x <= hi.y) return vec2(lo.x, hi.y);
    return lo.x <= lo.y ? lo : hi;
}

// Specks of dust caught in one beam along the view ray, up to the scene depth.
vec3 beamDust(vec3 ro, vec3 rd, float sceneDist, vec3 a, vec3 d, vec4 prm, vec3 color) {
    vec2 span = coneSpan(ro, rd, a, d, prm.x, prm.y);
    float t0 = max(span.x, 0.25), t1 = min(min(span.y, sceneDist - 0.05), 22.0);
    if (t0 >= t1) return vec3(0.0);
    const float CELL = 0.32;
    int n = int(min(56.0, ceil((t1 - t0) / (CELL * 0.45))));
    float stepLen = (t1 - t0) / float(n);
    vec3 acc = vec3(0.0);
    vec3 lastCell = vec3(-1e9);
    float sinA = sqrt(max(1.0 - prm.x * prm.x, 1e-4));
    for (int i = 0; i < 56; i++) {
        if (i >= n) break;
        vec3 p = ro + rd * (t0 + (float(i) + 0.5) * stepLen);
        vec3 cell = floor(p / CELL);
        if (cell == lastCell) continue;
        lastCell = cell;
        vec3 h = hash33(cell);
        if (h.x > DustDensity * prm.w) continue;
        // the speck drifts and turns slowly inside its cell
        vec3 h2 = hash33(cell + 17.0);
        vec3 wob = 0.22 * vec3(sin(Time * (0.21 + h2.x * 0.3) + h2.y * 6.28), sin(Time * (0.17 + h2.y * 0.25) + h2.z * 6.28),
                               sin(Time * (0.19 + h2.z * 0.3) + h2.x * 6.28));
        vec3 q = (cell + 0.5 + 0.5 * (h - 0.5) + wob) * CELL;
        vec3 w = q - ro;
        float tq = dot(w, rd);
        if (tq <= 0.0 || tq >= sceneDist) continue;
        float dperp = length(w - rd * tq);
        float size = 0.0045 + 0.006 * h.y;                    // a few millimetres
        float r = max(size, tq * PixelAngle * 0.9);           // at least about one pixel on screen
        if (dperp > r) continue;
        // how much light the beam gives it there
        vec3 qa = q - a;
        float ax = dot(qa, d);
        float rad = length(qa - d * ax);
        float edge = rad / max(ax * sinA / max(prm.x, 0.05), 1e-3);
        if (ax <= 0.0 || ax >= prm.y || edge >= 1.0) continue;
        float lit = sqrt(1.0 - edge) * sqrt(1.0 - ax / prm.y) * prm.z;
        float twinkle = 0.55 + 0.45 * sin(Time * (1.3 + h2.z * 2.5) + h.z * 6.28);
        float energy = min(1.0, (size * size) / (r * r)) * (1.0 - smoothstep(r * 0.4, r, dperp));
        acc += color * lit * twinkle * energy * (2.2 + 2.0 * h.z);
    }
    return acc;
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

    // Black fog (Corridor rework). Horizontal: fully closed before the render distance, whatever it is, so no
    // chunk edge is ever seen. Vertical: what is far above the viewer sinks into black (the walls have no top).
    float edge = smoothstep(RenderDist * 0.58, RenderDist * 0.88, dist);
    float above = wpos.y - cam.y;
    float vfog = smoothstep(VertStart, VertEnd, above);
    if (sky) { edge = 1.0; vfog = 1.0; }
    transmittance *= (1.0 - edge) * (1.0 - 0.985 * vfog);

    // the haze: a faint lift near the floor, nothing above; red in an emergency, dust in a quake
    float alt = max(cam.y + dir.y * min(dist, 220.0) - FloorY, 0.0);
    vec3 haze = FogColor * (1.6 * exp(-alt / 45.0) + 0.15) * (1.0 - vfog);
    haze = mix(haze, haze * vec3(1.6, 0.55, 0.5), Emergency * 0.3);
    haze += vec3(0.012, 0.010, 0.008) * Quake * exp(-alt / 30.0);

    vec3 outColor = mix(haze, col.rgb, transmittance);

    // dust in the light beams, in front of the fog (it is close to the eye)
    if (BeamCount > 0) {
        float sceneDist = sky ? 1e4 : dist;
        vec3 dust = vec3(0.0);
        dust += beamDust(cam, dir, sceneDist, Beam0Pos, Beam0Dir, Beam0Params, Beam0Color);
        if (BeamCount > 1) dust += beamDust(cam, dir, sceneDist, Beam1Pos, Beam1Dir, Beam1Params, Beam1Color);
        if (BeamCount > 2) dust += beamDust(cam, dir, sceneDist, Beam2Pos, Beam2Dir, Beam2Params, Beam2Color);
        if (BeamCount > 3) dust += beamDust(cam, dir, sceneDist, Beam3Pos, Beam3Dir, Beam3Params, Beam3Color);
        if (BeamCount > 4) dust += beamDust(cam, dir, sceneDist, Beam4Pos, Beam4Dir, Beam4Params, Beam4Color);
        if (BeamCount > 5) dust += beamDust(cam, dir, sceneDist, Beam5Pos, Beam5Dir, Beam5Params, Beam5Color);
        outColor += dust;
    }
    fragColor = vec4(outColor, 1.0);
}
