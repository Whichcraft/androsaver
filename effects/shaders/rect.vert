// vertex shader
#version 330 core
in vec2 in_vert;
out vec2 v_texcoord;

void main() {
    gl_Position = vec4(in_vert, 0.0, 1.0);
    v_texcoord = in_vert; // Pass texcoords for potential color mapping
}
