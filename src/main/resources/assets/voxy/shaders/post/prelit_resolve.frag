#version 430
layout(binding=0) uniform sampler2D prelitLayer;
layout(location=0) out vec4 colour;
void main() {
    // Both textures are Metal attachments with identical coordinates. No GL bridge flip.
    colour = texelFetch(prelitLayer, ivec2(gl_FragCoord.xy), 0);
}
