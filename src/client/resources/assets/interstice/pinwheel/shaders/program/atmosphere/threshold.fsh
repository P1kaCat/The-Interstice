// Bright pass for bloom: keeps only lamp cores and emissive highlights.
uniform sampler2D DiffuseSampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 c = texture(DiffuseSampler0, texCoord).rgb;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float k = smoothstep(0.5, 0.92, l);
    fragColor = vec4(c * k, 1.0);
}
