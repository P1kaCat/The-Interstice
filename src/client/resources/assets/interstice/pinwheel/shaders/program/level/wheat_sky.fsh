#include veil:space_helper

// Level 02, The Wheat Farm: volumetric clouds and god rays.
// Clouds: a slab between 230 and 330 blocks up, ray-marched (28 steps, jittered) through drifting 3D fbm noise,
// each sample lit by one step towards the sun (dark undersides, gold where the sun gets in, a silver rim facing it),
// far clouds fading into the horizon haze. God rays: a radial blur of the bright sky towards the sun's screen
// position, so the turbines, the house and the clouds' gaps cut shafts of light.

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;

uniform float Time;
uniform vec3 SunDir;
uniform vec3 Horizon;

in vec2 texCoord;
out vec4 fragColor;

const float BASE = 230.0, TOP = 330.0;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x), mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x), mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s;
}

float cloud(vec3 p) {
    float h = (p.y - BASE) / (TOP - BASE);
    if (h < 0.0 || h > 1.0) return 0.0;
    vec3 q = p * 0.0032 + vec3(Time * 0.010, 0.0, Time * 0.0075);
    float n = fbm(q);
    float shape = smoothstep(0.0, 0.22, h) * smoothstep(1.0, 0.5, h);
    return clamp((n - 0.5) * 2.6 * shape, 0.0, 1.0);
}

void main() {
    vec3 col = texture(DiffuseSampler0, texCoord).rgb;
    float depth = texture(DiffuseDepthSampler, texCoord).r;
    vec3 cam = VeilCamera.CameraPosition;
    vec3 wpos = screenToWorldSpace(texCoord, depth).xyz;
    vec3 ray = wpos - cam;
    float dist = length(ray);
    vec3 dir = ray / max(dist, 1e-4);
    bool sky = depth >= 0.99999;
    if (sky) dist = 1e6;
    vec3 c = col;

    // clouds
    if (dir.y > 0.012 && cam.y < TOP) {
        float t0 = max(0.0, (BASE - cam.y) / dir.y);
        float t1 = min((TOP - cam.y) / dir.y, min(dist, 7000.0));
        if (t1 > t0) {
            const int N = 28;
            float stepLen = (t1 - t0) / float(N);
            float jitter = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
            float trans = 1.0;
            vec3 light = vec3(0.0);
            float facing = max(dot(dir, SunDir), 0.0);
            for (int i = 0; i < N; i++) {
                vec3 p = cam + dir * (t0 + (float(i) + jitter) * stepLen);
                float d = cloud(p);
                if (d > 0.001) {
                    float toward = cloud(p + SunDir * 28.0);
                    float lit = exp(-toward * 2.8);
                    vec3 sunCol = vec3(1.0, 0.80, 0.55) * (0.75 + 0.9 * pow(facing, 5.0));
                    vec3 shade = vec3(0.47, 0.41, 0.50);
                    vec3 L = mix(shade, sunCol * 1.25, lit);
                    float a = d * stepLen * 0.018;
                    light += trans * (1.0 - exp(-a)) * L;
                    trans *= exp(-a);
                    if (trans < 0.03) break;
                }
            }
            float far = smoothstep(2200.0, 6500.0, t0);
            light = mix(light, Horizon * (1.0 - trans), far);
            trans = mix(trans, 1.0, far * 0.6);
            c = c * trans + light;
        }
    }

    // god rays
    vec4 sc = VeilCamera.ProjMat * VeilCamera.ViewMat * vec4(SunDir * 1000.0, 1.0);
    if (sc.w > 0.0) {
        vec2 sun = sc.xy / sc.w * 0.5 + 0.5;
        const int S = 36;
        vec2 delta = (sun - texCoord) / float(S);
        vec2 uv = texCoord;
        float illum = 0.0, decay = 1.0;
        for (int i = 0; i < S; i++) {
            uv += delta;
            if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) break;
            float d = texture(DiffuseDepthSampler, uv).r;
            float bright = max(0.0, dot(texture(DiffuseSampler0, uv).rgb, vec3(0.333)) - 0.62);
            illum += (d >= 0.99999 ? bright : 0.0) * decay;
            decay *= 0.965;
        }
        float face = pow(max(dot(dir, SunDir), 0.0), 2.5);
        c += vec3(1.0, 0.80, 0.52) * (illum / float(S)) * 2.4 * (0.25 + 0.75 * face);
    }
    fragColor = vec4(c, 1.0);
}
