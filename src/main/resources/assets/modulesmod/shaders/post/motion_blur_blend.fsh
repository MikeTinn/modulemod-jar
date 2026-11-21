#version 330

uniform sampler2D CurrentSampler;
uniform sampler2D PreviousSampler;

layout(std140) uniform BlendConfig {
    float Blend;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 current = texture(CurrentSampler, texCoord).rgb;
    vec3 previous = texture(PreviousSampler, texCoord).rgb;
    fragColor = vec4(mix(current, previous, Blend), 1.0);
}
