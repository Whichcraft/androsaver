#version 330 core
out vec4 frag_color;
in vec2 v_texcoord;

uniform vec4 u_color; // Uniform for line color

void main() {
    frag_color = u_color;
}
