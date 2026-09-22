#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;

uniform float TubeRadius;
uniform float TubeSoftness;
uniform float TubeSpacing;
uniform float TubeMerge;
uniform float ChromaticAberration;

in vec2 texCoord;
out vec4 fragColor;

// Плавное объединение двух расстояний (сглаженный minimum) — убирает шов/полоску
float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

void main() {
    vec2 uv = texCoord;
    float aspect = InSize.x / InSize.y;

    vec2 p = uv - 0.5;
    p.x *= aspect;

    vec2 pLeft  = p + vec2(TubeSpacing * aspect, 0.0);
    vec2 pRight = p - vec2(TubeSpacing * aspect, 0.0);

    // Гладкое слияние двух кругов вместо жёсткого min()
    float dist = smin(length(pLeft), length(pRight), TubeMerge);

    // Мягкая растушёванная маска
    float mask = smoothstep(TubeRadius, TubeRadius - TubeSoftness, dist);

    float off = ChromaticAberration / InSize.x;
    float r = texture(DiffuseSampler, uv + vec2(off, 0.0)).r;
    float g = texture(DiffuseSampler, uv).g;
    float b = texture(DiffuseSampler, uv - vec2(off, 0.0)).b;

    vec3 sceneColor = vec3(r, g, b);
    fragColor = vec4(sceneColor * mask, 1.0);
}