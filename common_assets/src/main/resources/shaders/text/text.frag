#version 300 es

precision mediump float;

in vec2 vTexCoord;

uniform sampler2D uTexDiffuse;

layout (location = 0) out vec4 oFragColor;

void main() {
    oFragColor = texture(uTexDiffuse, vTexCoord);
    if (oFragColor.r == 1.0 && oFragColor.g == 1.0 && oFragColor.b == 1.0) {
        discard;
    }
}