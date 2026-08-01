#version 100
precision highp float;

uniform sampler2D uTexSampler;
uniform float uTexelWidth;
uniform float uTexelHeight;
uniform float uEdgeStrength;
uniform float uLineStrength;
varying vec2 vTexSamplingCoord;

float luma(vec3 color) {
  return dot(color, vec3(0.299, 0.587, 0.114));
}

void main() {
  vec2 dx = vec2(uTexelWidth, 0.0);
  vec2 dy = vec2(0.0, uTexelHeight);

  vec4 centerSample = texture2D(uTexSampler, vTexSamplingCoord);
  vec3 center = centerSample.rgb;
  vec3 left = texture2D(uTexSampler, vTexSamplingCoord - dx).rgb;
  vec3 right = texture2D(uTexSampler, vTexSamplingCoord + dx).rgb;
  vec3 up = texture2D(uTexSampler, vTexSamplingCoord - dy).rgb;
  vec3 down = texture2D(uTexSampler, vTexSamplingCoord + dy).rgb;
  vec3 upperLeft = texture2D(uTexSampler, vTexSamplingCoord - dx - dy).rgb;
  vec3 lowerRight = texture2D(uTexSampler, vTexSamplingCoord + dx + dy).rgb;

  vec3 crossAverage = (left + right + up + down) * 0.25;
  float gradientX = luma(right) - luma(left);
  float gradientY = luma(down) - luma(up);
  float diagonal = abs(luma(lowerRight) - luma(upperLeft));
  float edge = clamp((abs(gradientX) + abs(gradientY) + diagonal) * 2.5, 0.0, 1.0);

  vec3 neighborhoodMin = min(center, min(min(left, right), min(up, down)));
  vec3 neighborhoodMax = max(center, max(max(left, right), max(up, down)));
  vec3 restored = center + (center - crossAverage) * uEdgeStrength;
  restored = clamp(restored, neighborhoodMin, neighborhoodMax);
  restored = mix(center, restored, edge);

  float centerLuma = luma(center);
  float neighborLuma = luma(crossAverage);
  float darkLine = clamp(neighborLuma - centerLuma, 0.0, 1.0) * edge;
  restored *= 1.0 - darkLine * uLineStrength;

  gl_FragColor = vec4(clamp(restored, 0.0, 1.0), centerSample.a);
}
