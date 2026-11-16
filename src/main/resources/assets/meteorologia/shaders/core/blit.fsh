#version 150

// Copies the (premultiplied alpha) low-resolution storm / sky target onto the screen with bilinear filtering.
uniform sampler2D DiffuseSampler;

in vec2 ndc;
out vec4 fragColor;

void main() {
    fragColor = texture(DiffuseSampler, ndc * 0.5 + 0.5);
}
