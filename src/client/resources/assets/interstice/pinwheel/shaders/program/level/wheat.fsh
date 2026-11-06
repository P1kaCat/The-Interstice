// Level 02, The Wheat Farm: warm evening light on the stalks, a sheen where the gusts bend them, warm haze far off.

uniform sampler2D WheatTex;
uniform vec3 FogColor;
uniform float FogEnd;
uniform float Light;

in vec2 texCoord;
in vec3 tint;
in float height;
in float dist;
in float sheen;

out vec4 fragColor;

void main() {
    vec4 c = texture(WheatTex, texCoord);
    if (c.a < 0.5) discard;
    vec3 col = c.rgb * tint;
    // darker down in the stalks, lit at the ears; the bent tops catch the low sun
    col *= mix(0.42, 1.08, height) * Light;
    col += sheen * 0.10 * vec3(1.0, 0.86, 0.55);
    float f = smoothstep(FogEnd * 0.35, FogEnd, dist);
    fragColor = vec4(mix(col, FogColor, f), 1.0);
}
