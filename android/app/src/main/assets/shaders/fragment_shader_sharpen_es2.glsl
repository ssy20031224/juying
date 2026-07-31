#version 100
precision highp float;

uniform sampler2D uTexSampler;
uniform float uTexelWidth;
uniform float uTexelHeight;
uniform float uSharpenStrength;
varying vec2 vTexSamplingCoord;

void main() {
  vec2 dx = vec2(uTexelWidth, 0.0);
  vec2 dy = vec2(0.0, uTexelHeight);
  vec4 center = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 neighbors =
      texture2D(uTexSampler, vTexSamplingCoord - dx).rgb +
      texture2D(uTexSampler, vTexSamplingCoord + dx).rgb +
      texture2D(uTexSampler, vTexSamplingCoord - dy).rgb +
      texture2D(uTexSampler, vTexSamplingCoord + dy).rgb;
  vec3 sharpened =
      center.rgb * (1.0 + 4.0 * uSharpenStrength) -
      neighbors * uSharpenStrength;
  gl_FragColor = vec4(clamp(sharpened, 0.0, 1.0), center.a);
}
