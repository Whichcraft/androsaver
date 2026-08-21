#version 330 core
in vec2 in_vert; // Input vertex position (x, y) in normalized device coordinates
out vec2 v_texcoord; // Pass texture coordinates or vertex data to fragment shader

uniform mat4 mvp; // Model-view-projection matrix

void main() {
    // Transform vertex position using the MVP matrix
    gl_Position = mvp * vec4(in_vert, 0.0, 1.0);
    v_texcoord = in_vert; // Pass vertex data to fragment shader for color calculation
}
