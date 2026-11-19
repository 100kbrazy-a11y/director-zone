package com.directorzone.engine

object Shaders {
    const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vUv;
void main() {
  gl_Position = aFramePosition;
  vUv = aFramePosition.xy * 0.5 + 0.5;
}
"""

    const val COMMON = """
precision highp float;
uniform sampler2D uTexSampler;
varying vec2 vUv;
uniform vec2 uRes;
uniform float uTime;
float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
vec2 mirrorUv(vec2 uv) { return abs(mod(uv + 1.0, 2.0) - 1.0); }
vec3 tex(vec2 uv) { return texture2D(uTexSampler, mirrorUv(uv)).rgb; }
"""

    val GRADE = COMMON + """
uniform float uExposure, uContrast, uSat, uWarm, uTint, uHigh, uShad, uVig, uGrain, uFade, uHue, uGlow, uChroma, uKen, uFx, uDuo, uSharp;
uniform vec3 uDuoA, uDuoB;
vec3 hueRot(vec3 c, float a) {
  float cs = cos(a), sn = sin(a);
  mat3 m = mat3(0.299 + 0.701 * cs + 0.168 * sn, 0.587 - 0.587 * cs + 0.330 * sn, 0.114 - 0.114 * cs - 0.497 * sn,
                0.299 - 0.299 * cs - 0.328 * sn, 0.587 + 0.413 * cs + 0.035 * sn, 0.114 - 0.114 * cs + 0.292 * sn,
                0.299 - 0.300 * cs + 1.250 * sn, 0.587 - 0.588 * cs - 1.050 * sn, 0.114 + 0.886 * cs - 0.203 * sn);
  return c * m;
}
void main() {
  vec2 uv = (vUv - 0.5) / (1.0 + uKen) + 0.5;
  if (uFx > 0.5 && uFx < 1.5) {
    float j = step(0.985, hash(vec2(floor(vUv.y * 90.0), floor(uTime * 9.0)))) * 0.03;
    uv.x += j;
  }
  vec3 c = tex(uv);
  if (uChroma > 0.0) {
    vec2 d = (uv - 0.5) * uChroma * 0.03;
    c.r = tex(uv + d).r;
    c.b = tex(uv - d).b;
  }
  if (uGlow > 0.0) {
    vec3 g = vec3(0.0);
    for (int i = 0; i < 8; i++) {
      float a = float(i) * 0.785398;
      g += tex(uv + vec2(cos(a), sin(a)) * 0.012) + tex(uv + vec2(cos(a), sin(a)) * 0.028);
    }
    g /= 16.0;
    c += uGlow * max(g - 0.45, 0.0) * 1.3;
  }
  if (uSharp > 0.0) {
    vec2 px = 1.0 / uRes;
    vec3 bl = (tex(uv + vec2(px.x, 0.0)) + tex(uv - vec2(px.x, 0.0)) + tex(uv + vec2(0.0, px.y)) + tex(uv - vec2(0.0, px.y))) * 0.25;
    c += (c - bl) * uSharp * 2.2;
  }
  c *= pow(2.0, uExposure);
  c.r += uWarm * 0.09; c.b -= uWarm * 0.09;
  c.g -= uTint * 0.07; c.r += uTint * 0.03; c.b += uTint * 0.03;
  c = (c - 0.5) * (1.0 + uContrast) + 0.5;
  float l = dot(c, vec3(0.299, 0.587, 0.114));
  c += uHigh * 0.25 * smoothstep(0.5, 1.0, l);
  c += uShad * 0.25 * (1.0 - smoothstep(0.0, 0.5, l));
  l = dot(c, vec3(0.299, 0.587, 0.114));
  c = mix(vec3(l), c, 1.0 + uSat);
  if (abs(uHue) > 0.001) c = hueRot(c, uHue);
  c = c * (1.0 - uFade * 0.55) + uFade * 0.11;
  if (uDuo > 0.001) {
    float dl = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(c, mix(uDuoA, uDuoB, clamp(dl, 0.0, 1.0)), uDuo);
  }
  if (uFx > 0.5 && uFx < 1.5) c *= 0.93 + 0.07 * sin(vUv.y * uRes.y * 1.4);
  float v = distance(vUv, vec2(0.5)) * 1.35;
  c *= 1.0 - uVig * smoothstep(0.35, 1.0, v);
  c += (hash(vUv * uRes + fract(uTime) * 91.7) - 0.5) * uGrain * 0.3;
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""

    val TRANSITION = COMMON + """
uniform float uMode, uQ, uIn;
vec3 blurDir(vec2 uv, vec2 dir) {
  vec3 s = vec3(0.0);
  for (int i = 0; i < 12; i++) { float f = float(i) / 11.0 - 0.5; s += tex(uv + dir * f); }
  return s / 12.0;
}
vec2 rot(vec2 p, float a) { float c = cos(a), s = sin(a); return vec2(c * p.x - s * p.y, s * p.x + c * p.y); }
float wv(float pos, float e) {
  float s = 0.012;
  float ee = e * (1.0 + 2.0 * s) - s;
  if (uIn > 0.5) return 1.0 - smoothstep(1.0 - ee - s, 1.0 - ee + s, pos);
  return smoothstep(ee - s, ee + s, pos);
}
void main() {
  vec2 uv = vUv;
  float q = clamp(uQ, 0.0, 1.0);
  float e = q * q * (3.0 - 2.0 * q);
  float sg = uIn > 0.5 ? -1.0 : 1.0;
  float asp = uRes.x / uRes.y;
  int m = int(uMode + 0.5);
  vec3 c = vec3(0.0);
  if (m == 0) { c = tex(uv); }
  else if (m == 1) { c = tex(uv) * (1.0 - e); }
  else if (m == 2) { c = mix(tex(uv), vec3(1.0), e * e); }
  else if (m == 3) {
    vec2 d = uv - 0.5; vec3 s = vec3(0.0);
    for (int i = 0; i < 10; i++) { float k = float(i) / 9.0; s += tex(0.5 + d * (1.0 - e * 0.55 * k)); }
    c = mix(s / 10.0, vec3(1.0), e * e * 0.5);
  }
  else if (m == 4) {
    vec2 d = uv - 0.5; vec3 s = vec3(0.0);
    for (int i = 0; i < 10; i++) { float k = float(i) / 9.0; s += tex(0.5 + d * (1.0 + e * 0.6 * k)); }
    c = s / 10.0 * (1.0 - e * 0.5);
  }
  else if (m >= 5 && m <= 8) {
    vec2 dir = m == 5 ? vec2(-1.0, 0.0) : (m == 6 ? vec2(1.0, 0.0) : (m == 7 ? vec2(0.0, 1.0) : vec2(0.0, -1.0)));
    c = blurDir(uv - dir * sg * e * 0.5, dir * e * 0.35);
  }
  else if (m == 9) {
    vec2 p = (uv - 0.5) * vec2(asp, 1.0);
    p = rot(p, sg * e * 2.4) / (1.0 + e * 0.7);
    vec2 u2 = p / vec2(asp, 1.0) + 0.5;
    c = blurDir(u2, rot(vec2(0.05, 0.0), uv.x * 6.0) * e) * (1.0 - e * 0.4);
  }
  else if (m == 10) {
    float by = floor(uv.y * 16.0);
    float r = hash(vec2(by, floor(uTime * 24.0)));
    float on = step(0.55, r) * e;
    vec2 off = vec2((hash(vec2(by, 3.0 + floor(uTime * 24.0))) - 0.5) * 0.35 * on, 0.0);
    float sp = 0.035 * e;
    c = vec3(tex(uv + off + vec2(sp, 0.0)).r, tex(uv + off).g, tex(uv + off - vec2(sp, 0.0)).b);
    c += step(0.97, hash(uv * uRes * 0.05 + uTime)) * e * 0.5;
  }
  else if (m == 11) {
    vec2 d = vec2(e * 0.06, 0.0);
    c = vec3(tex(uv + d).r, tex(uv).g, tex(uv - d).b);
    c = mix(c, c.gbr * 1.2, e * 0.2) * (1.0 - e * 0.25);
  }
  else if (m == 12) {
    float rr = e * 0.07;
    c = (blurDir(uv, vec2(rr, 0.0)) + blurDir(uv, vec2(0.0, rr * asp))) * 0.5 * (1.0 - e * 0.35);
  }
  else if (m == 13) {
    float blk = mix(1.0, 70.0, e);
    vec2 pu = (floor(uv * uRes / blk) + 0.5) * blk / uRes;
    c = mix(tex(pu), vec3(1.0), e * e * 0.3);
  }
  else if (m == 14) {
    float f = floor(uTime * 30.0);
    vec2 off = vec2(hash(vec2(f, 1.0)) - 0.5, hash(vec2(f, 2.0)) - 0.5) * e * 0.14;
    c = blurDir(uv + off, off * 0.8);
    c = mix(c, vec3(1.0), smoothstep(0.75, 1.0, q) * 0.6);
  }
  else if (m == 15) {
    vec2 p = (uv - 0.5) * vec2(asp, 1.0);
    float r = length(p);
    float a = sg * e * 5.0 * clamp(1.0 - r / 0.8, 0.0, 1.0);
    p = rot(p, a);
    c = tex(p / vec2(asp, 1.0) + 0.5) * (1.0 - e * 0.5);
  }
  else if (m == 16) {
    vec2 d = (uv - 0.5) * vec2(asp, 1.0);
    float a = atan(d.y, d.x), r = length(d);
    float seg = 6.28318 / 6.0;
    a = abs(mod(a, seg) - seg * 0.5);
    vec2 k = vec2(cos(a), sin(a)) * r;
    vec2 u2 = mix(d, k, e) / vec2(asp, 1.0) + 0.5;
    c = tex(u2) * (1.0 - e * 0.3);
  }
  else if (m == 17) {
    c = tex(uv);
    float n = 0.5 + 0.5 * sin(uv.y * 5.0 + uTime * 6.0) * sin(uv.x * 3.0 - uTime * 4.0);
    c += vec3(1.0, 0.42, 0.08) * e * (0.6 + n) * 1.3;
    c = mix(c, vec3(1.0, 0.85, 0.6), e * e);
  }
  else if (m == 18) {
    vec2 p = (uv - 0.5) * vec2(asp, 1.0);
    float rad = (1.0 - e) * (asp > 1.0 ? 1.2 : 0.95 * 1.25);
    float mk = smoothstep(rad, rad - 0.03, length(p));
    c = tex(uv) * mk;
  }
  else if (m == 19) {
    vec2 u2 = uv - vec2(-1.0, 0.0) * sg * e;
    c = (u2.x >= 0.0 && u2.x <= 1.0) ? texture2D(uTexSampler, u2).rgb : vec3(0.0);
  }
  else if (m == 20) {
    c = tex(uv);
    float band = 0.5 + 0.5 * sin(uv.x * 2.5 + uv.y * 1.5 + uTime * 2.0);
    vec3 leak = mix(vec3(1.0, 0.35, 0.1), vec3(1.0, 0.8, 0.4), band);
    c += leak * e * (0.5 + 0.5 * (1.0 - uv.y)) * 1.1;
  }
  else if (m == 21) {
    vec2 u2 = vec2(uv.x, 0.5 + (uv.y - 0.5) * (1.0 + e * 1.4));
    c = tex(u2) * (1.0 - e * 0.55);
    c = mix(c, vec3(1.0), smoothstep(0.85, 1.0, q) * 0.4);
  }
  else if (m >= 22 && m <= 28) {
    float pos = uv.x;
    if (m == 23) pos = 1.0 - uv.x;
    else if (m == 24) pos = 1.0 - uv.y;
    else if (m == 25) pos = uv.y;
    else if (m == 26) pos = (uv.x + 1.0 - uv.y) * 0.5;
    else if (m == 27) pos = atan(uv.x - 0.5, uv.y - 0.5) / 6.28318 + 0.5;
    else if (m == 28) pos = abs(uv.x - 0.5) * 2.0;
    c = tex(uv) * wv(pos, e);
  }
  else if (m == 29) {
    float band = step(0.88, hash(vec2(floor(uv.y * 36.0), floor(uTime * 12.0))));
    vec2 off = vec2((hash(vec2(floor(uv.y * 120.0), floor(uTime * 20.0))) - 0.5) * 0.06 * e * (0.4 + band * 2.0), 0.0);
    float sp = 0.012 * e;
    c = vec3(tex(uv + off + vec2(sp, 0.0)).r, tex(uv + off).g, tex(uv + off - vec2(sp, 0.0)).b);
    c += (hash(uv * uRes + uTime) - 0.5) * 0.35 * e;
    c = mix(c, vec3(dot(c, vec3(0.33))), e * 0.4) * (1.0 - 0.45 * e);
  }
  else if (m == 30) {
    float v = (uv.y - 0.5) / max(1.0 - e, 0.015) + 0.5;
    if (v < 0.0 || v > 1.0) c = vec3(0.0);
    else c = tex(vec2(uv.x, v)) * (1.0 + e * e * 1.6) + vec3(e * e * 0.4);
  }
  else if (m == 31) {
    c = blurDir(uv, vec2(e * 0.22, 0.0));
    float streak = exp(-abs(uv.y - 0.5) * 16.0);
    c += vec3(0.45, 0.7, 1.0) * streak * e * 1.5;
    c *= 1.0 - 0.2 * e;
  }
  else if (m == 32) {
    vec2 d = (uv - 0.5) * vec2(asp, 1.0);
    float r = length(d);
    vec2 off = normalize(d + vec2(0.00001)) * sin(r * 42.0 - uTime * 10.0) * 0.022 * e;
    c = tex(uv + off / vec2(asp, 1.0)) * (1.0 - e * 0.4);
  }
  else if (m == 33) {
    vec2 d = uv - 0.5;
    float r = length(d);
    float f = 1.0 - e * 1.3 * (0.7 - min(r, 0.7));
    c = tex(0.5 + d * f) * (1.0 - e * 0.45);
  }
  else if (m == 34) {
    float rr = e * 0.08;
    c = (blurDir(uv, vec2(rr, 0.0)) + blurDir(uv, vec2(0.0, rr * asp))) * 0.5;
    c = c * (1.0 + e * 1.2) + vec3(e * e * 0.5);
  }
  else { c = tex(uv); }
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""

    val PHONE = COMMON + """
uniform vec3 uRot, uPos, uFrame, uBack, uBg1, uBg2, uBg3;
uniform float uScale, uVid, uIsland, uCorner, uBezel, uBgMode, uShadow, uDepth, uGlare, uLight;
#define TH (0.062 * uDepth)
const float PW = 0.243;
mat3 rotY(float a) { float c = cos(a), s = sin(a); return mat3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c); }
mat3 rotX(float a) { float c = cos(a), s = sin(a); return mat3(1.0, 0.0, 0.0, 0.0, c, s, 0.0, -s, c); }
mat3 rotZ(float a) { float c = cos(a), s = sin(a); return mat3(c, s, 0.0, -s, c, 0.0, 0.0, 0.0, 1.0); }
float rr(vec2 p, vec2 b, float r) { vec2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
float cov(float d, float aa) { return clamp(0.5 - d / aa, 0.0, 1.0); }

vec3 bgCol(vec2 uv, float t) {
  vec2 p = (uv - 0.5) * vec2(uRes.x / uRes.y, 1.0);
  int m = int(uBgMode + 0.5);
  vec3 c = uBg1;
  if (m == 0) {
    float a = sin(p.x * 3.0 + t * 0.5 + sin(p.y * 2.0 + t * 0.3) * 1.2) * 0.5 + 0.5;
    float b = sin(p.y * 2.5 - t * 0.4 + sin(p.x * 2.0 - t * 0.2) * 1.5) * 0.5 + 0.5;
    c = mix(uBg1, uBg2, a * smoothstep(-0.9, 0.5, p.y + 0.2));
    c = mix(c, uBg3, b * b * 0.75 * smoothstep(0.7, -0.5, p.y));
  } else if (m == 1) {
    vec2 c1 = vec2(sin(t * 0.35) * 0.45, cos(t * 0.27) * 0.55);
    vec2 c2 = vec2(cos(t * 0.31 + 2.0) * 0.5, sin(t * 0.23 + 1.0) * 0.6);
    float w1 = exp(-dot(p - c1, p - c1) * 2.6);
    float w2 = exp(-dot(p - c2, p - c2) * 2.6);
    c = uBg1 + (uBg2 - uBg1) * w1 * 1.3 + (uBg3 - uBg1) * w2 * 1.3;
  } else if (m == 2) {
    c = mix(uBg2, uBg1, smoothstep(0.0, 0.95, length(p)));
    c += uBg3 * 0.28 * exp(-length(p - vec2(0.0, 0.3)) * 2.2) * (0.75 + 0.25 * sin(t));
    vec2 g = floor((p + 2.0) * 38.0);
    float st = step(0.985, hash(g)) * (0.5 + 0.5 * sin(t * 2.0 + hash(g + 3.0) * 6.0));
    c += vec3(st) * 0.7;
  } else if (m == 3) {
    c = mix(uBg3, uBg2, smoothstep(0.0, 1.0, length(p - vec2(0.0, 0.12))));
  } else if (m == 4) {
    c = mix(uBg1, uBg2 * 0.45, smoothstep(0.7, -0.1, p.y));
    float hz = -0.06;
    if (p.y < hz) {
      float z = 0.32 / (hz - p.y);
      float gx = abs(fract(p.x * z * 2.2) - 0.5);
      float gy = abs(fract(z * 1.6 + t * 0.6) - 0.5);
      float ln = smoothstep(0.45, 0.5, max(gx, gy));
      c += uBg3 * ln * clamp(1.4 / (z * 0.55 + 1.0), 0.0, 1.0) * 0.9;
    } else {
      c += uBg2 * 0.5 * exp(-(p.y - hz) * 6.0);
    }
  } else if (m == 5) {
    vec2 f = fract(p * 18.0) - 0.5;
    vec2 id = floor(p * 18.0);
    float rad = 0.16 + 0.12 * sin(t * 1.6 + length(id) * 0.55);
    c = mix(uBg1, uBg2, smoothstep(rad + 0.04, rad - 0.04, length(f)));
    c = mix(c, uBg3, smoothstep(rad + 0.04, rad - 0.04, length(f)) * (0.5 + 0.5 * sin(length(p) * 7.0 - t * 2.2)) * 0.55);
  } else if (m == 6) {
    c = mix(uBg1, uBg1 * 0.4, smoothstep(-0.5, 0.6, p.y));
    for (int i = 0; i < 7; i++) {
      float fi = float(i);
      vec2 cp = vec2(sin(t * 0.17 * (1.0 + fi * 0.3) + fi * 2.1) * 0.55, cos(t * 0.13 * (1.0 + fi * 0.2) + fi * 1.3) * 0.7);
      float r = 0.12 + 0.07 * fract(fi * 0.37);
      float d = length(p - cp);
      c += mix(uBg2, uBg3, fract(fi * 0.43)) * smoothstep(r, r * 0.7, d) * 0.28;
    }
  } else {
    float a = atan(p.y, p.x);
    float rays = 0.5 + 0.5 * sin(a * 14.0 + t * 0.5);
    c = mix(uBg1, mix(uBg2, uBg3, rays), smoothstep(1.1, 0.0, length(p)) * (0.4 + 0.3 * rays));
  }
  return c;
}

void main() {
  float aspect = uRes.x / uRes.y;
  float tanH = 0.2465;
  float aa = 1.6 / (uRes.y * 0.78) / max(uScale, 0.3);
  vec2 ndc = (vUv - 0.5) * 2.0;
  vec3 dir = normalize(vec3(ndc.x * aspect * tanH, ndc.y * tanH, -1.0));
  vec3 org = vec3(0.0, 0.0, 2.6 + uPos.z);
  mat3 R = rotZ(uRot.z) * rotX(uRot.y) * rotY(uRot.x);
  vec3 o = ((org - vec3(uPos.xy, 0.0)) * R) / uScale;
  vec3 d = dir * R;
  vec3 col = bgCol(vUv, uTime);
  vec2 hb = vec2(PW, 0.5);
  float rad = uCorner * 0.6;

  if (uShadow > 0.5 && abs(d.z) > 0.0001) {
    float ts = (-TH * 0.5 - o.z) / d.z;
    vec2 sp = (o + d * ts).xy - vec2(0.0, -0.04);
    float sd = rr(sp, hb * 0.96, rad);
    col *= 1.0 - 0.5 * (1.0 - smoothstep(-0.02, 0.16, sd)) * (0.55 + 0.45 * clamp(abs(d.z), 0.0, 1.0));
  }

  bool frontSeen = d.z < 0.0;
  vec2 bs = hb - vec2(uBezel);
  float rs = max(rad - uBezel, 0.012);
  for (int j = 0; j < 15; j++) {
    float fi = frontSeen ? float(14 - j) : float(j);
    float z = -TH * fi / 14.0;
    if (abs(d.z) < 0.00005) continue;
    float t = (z - o.z) / d.z;
    if (t <= 0.0) continue;
    vec2 p = (o + d * t).xy;
    float so = rr(p, hb, rad);
    float cv = cov(so, aa);
    if (cv <= 0.0) continue;
    vec3 pc;
    if (fi < 0.5 && frontSeen) {
      float ds = rr(p, bs, rs);
      vec3 rim = uFrame * (0.82 + 0.18 * smoothstep(-0.02, 0.0, so));
      vec3 glass = vec3(0.012);
      pc = mix(rim, glass, cov(so + 0.0042, aa));
      vec2 su = p / (2.0 * bs) + 0.5;
      float sa = bs.x / bs.y;
      if (uVid > sa) su.x = (su.x - 0.5) * (sa / uVid) + 0.5; else su.y = (su.y - 0.5) * (uVid / sa) + 0.5;
      vec3 sc = texture2D(uTexSampler, clamp(su, 0.0, 1.0)).rgb;
      sc += 0.05 * uGlare * smoothstep(0.35, 1.0, (p.x / bs.x) * 0.55 - (p.y / bs.y) * 0.35 + 0.5 + uRot.x * 0.3) * 0.7;
      if (uIsland > 0.5 && uIsland < 1.5) sc = mix(sc, vec3(0.0), cov(rr(p - vec2(0.0, bs.y - 0.032), vec2(0.052, 0.0135), 0.0135), aa));
      if (uIsland > 1.5 && uIsland < 2.5) sc = mix(sc, vec3(0.0), cov(rr(p - vec2(0.0, bs.y - 0.012), vec2(0.085, 0.0175), 0.014), aa));
      if (uIsland > 2.5) sc = mix(sc, vec3(0.0), cov(length(p - vec2(0.0, bs.y - 0.03)) - 0.0105, aa));
      pc = mix(pc, sc, cov(ds, aa));
    } else if (fi > 13.5 && !frontSeen) {
      pc = uBack * (1.0 + (0.9 + 0.1 * (p.y + 0.5) - 1.0) * uLight);
      float bump = rr(p - vec2(0.105, 0.365), vec2(0.088), 0.045);
      pc = mix(pc, uBack * 0.72, cov(bump, aa));
      for (int k = 0; k < 3; k++) {
        vec2 lc = vec2(0.105, 0.365) + vec2(k == 1 ? 0.045 : -0.0, 0.0) + (k == 0 ? vec2(-0.03, 0.03) : (k == 2 ? vec2(-0.03, -0.03) : vec2(0.035, 0.0)));
        float lens = length(p - lc);
        pc = mix(pc, vec3(0.03), cov(lens - 0.026, aa));
        pc = mix(pc, vec3(0.12, 0.16, 0.22), cov(lens - 0.017, aa));
      }
      pc = mix(uFrame * 0.9, pc, cov(so + 0.004, aa));
    } else {
      float sh = 1.0 + (0.62 + 0.38 * cos(fi * 0.45 + p.y * 3.0 + uRot.x) - 1.0) * uLight;
      pc = uFrame * sh + 0.07 * smoothstep(-0.006, 0.0, so);
    }
    col = mix(col, pc, cv);
  }
  gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""
}
