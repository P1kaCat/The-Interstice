// THE INTERSTICE - exposure, colour grading, vignette, grain, chromatic aberration.
uniform sampler2D DiffuseSampler0;
#ifdef USE_BLOOM
uniform sampler2D BloomSampler;
#endif

uniform float Time;
uniform float Exposure;
uniform float Vignette;
uniform float Desaturation;
uniform float Grain;
uniform float Aberration;
uniform float BloomStrength;
uniform float Emergency;
uniform float Silence;

in vec2 texCoord;
out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec2 uv = texCoord;
    vec2 dc = uv - 0.5;
    vec3 c;
    if (Aberration > 0.0) {
        vec2 off = dc * Aberration * 2.0;
        c.r = texture(DiffuseSampler0, uv + off).r;
        c.g = texture(DiffuseSampler0, uv).g;
        c.b = texture(DiffuseSampler0, uv - off).b;
    } else {
        c = texture(DiffuseSampler0, uv).rgb;
    }
#ifdef USE_BLOOM
    c += texture(BloomSampler, uv).rgb * BloomStrength;
#endif
    c *= Exposure;
    // lift the deepest shadows a touch so silhouettes against the fog stay readable
    c = c * 0.975 + vec3(0.010, 0.011, 0.013);

    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(c, vec3(l), Desaturation);
    // cold, slightly cyan shadows; sodium-warm highlights
    vec3 shadowTint = vec3(0.9, 1.0, 1.09);
    vec3 highTint = vec3(1.06, 1.0, 0.93);
    c *= mix(shadowTint, highTint, smoothstep(0.08, 0.7, l));
    c = mix(c, c * vec3(1.22, 0.84, 0.82), Emergency * 0.25);

    // gentle filmic shoulder
    c = c / (1.0 + c * 0.16) * 1.08;
    c = pow(max(c, 0.0), vec3(1.05));

    float v = smoothstep(0.88, 0.2, length(dc * vec2(1.12, 1.0)));
    c *= mix(1.0, v, clamp(Vignette, 0.0, 1.0));

    float g = hash12(uv * vec2(1931.0, 1087.0) + fract(Time * 13.0) * 97.0) - 0.5;
    c += g * Grain * (1.0 + Silence * 0.5);

    fragColor = vec4(c, 1.0);
}
