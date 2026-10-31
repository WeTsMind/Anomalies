package anomalies;

import arc.Core;
import arc.Events;
import arc.graphics.Gl;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.util.Log;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.game.EventType;

/**
 * Screen-space distortion for anomalies. The frame drawn so far is copied into a texture and one shader re-draws a disc
 * (or an oriented box, for mirrors) of it with the picture bent: lens, swirl, spiral ripples, shards, chromatic aberration.
 * Each anomaly type has its own {@link Style}. The whole thing is a client-side graphics option ("Anomaly space distortion")
 * and is guarded: if the shader fails (or pixelation is on) anomalies keep their plain glowing look.
 */
public class AnomalyShaders {
   static final String VERT = "attribute vec4 a_position;\n"
      + "attribute vec4 a_color;\n"
      + "attribute vec2 a_texCoord0;\n"
      + "attribute vec4 a_mix_color;\n"
      + "uniform mat4 u_projTrans;\n"
      + "uniform vec2 u_center;\n"
      + "varying vec2 v_rel;\n"
      + "void main(){\n"
      + "  v_rel = a_position.xy - u_center;\n"
      + "  gl_Position = u_projTrans * a_position;\n"
      + "}\n";

   // no GL_ES / precision lines here: arc's preprocessor adds them itself and rejects shaders that contain them
   static final String FRAG = "uniform sampler2D u_screen;\n"
      + "uniform vec2 u_uvCenter;\n"
      + "uniform vec2 u_uvScale;\n"
      + "uniform float u_radius;\n"
      + "uniform highp float u_time;\n"
      + "uniform float u_fade;\n"
      + "uniform vec4 u_a;\n"      // lens, swirl, swirlSpeed, aberration
      + "uniform vec4 u_b;\n"      // rippleAmp, rippleFreq, rippleSpeed, spiralArms
      + "uniform vec4 u_c;\n"      // shatter, shatterCell, rim, darkCenter
      + "uniform vec4 u_tint;\n"   // rgb, amount
      + "uniform vec4 u_box;\n"    // halfW, halfH, cos, sin (halfW <= 0: circle)
      + "uniform vec4 u_e;\n"      // desaturation, smear, twistWave, twistWaveSpeed
      + "uniform vec4 u_f;\n"      // invert, innerRadius (0..1, 0 = none), innerLens, innerRimGlow
      + "uniform vec4 u_g;\n"      // spiral shading, opacity, -, -
      + "uniform vec4 u_h;\n"      // bevel shading, -, -, -
      + "varying vec2 v_rel;\n"
      + "vec3 at(vec2 rel){ return texture2D(u_screen, u_uvCenter + rel * u_uvScale).rgb; }\n"
      + "void main(){\n"
      + "  float r;\n"
      + "  vec2 dir;\n"
      + "  float len = length(v_rel);\n"
      + "  if(u_box.x > 0.0){\n"
      + "    vec2 q = vec2(u_box.z * v_rel.x + u_box.w * v_rel.y, -u_box.w * v_rel.x + u_box.z * v_rel.y);\n"
      + "    r = max(abs(q.x) / u_box.x, abs(q.y) / u_box.y);\n"
      + "    dir = vec2(-u_box.w, u_box.z);\n"
      + "  } else {\n"
      + "    r = len / u_radius;\n"
      + "    dir = v_rel / max(len, 0.001);\n"
      + "  }\n"
      + "  if(r >= 1.0) discard;\n"
      + "  float dist = r * u_radius;\n"
      + "  float edge = 1.0 - r;\n"
      + "  float core = edge * edge;\n"
      + "  float osc = u_a.z < 0.0 ? 1.0 + 0.25 * sin(u_time * -u_a.z) : sin(u_time * u_a.z);\n"
      + "  float ang = core * u_a.y * osc + core * u_e.z * sin(dist * 0.3 + u_time * u_e.w);\n"
      + "  float cs = cos(ang);\n"
      + "  float sn = sin(ang);\n"
      + "  vec2 rel = vec2(cs * v_rel.x - sn * v_rel.y, sn * v_rel.x + cs * v_rel.y);\n"
      + "  rel *= mix(u_a.x, u_g.z, r * r);\n"
      + "  float phase = dist * u_b.y - u_time * u_b.z + u_b.w * atan(v_rel.y, v_rel.x + 0.0001);\n"
      + "  rel += dir * sin(phase) * u_b.x * edge;\n"
      + "  if(u_c.x > 0.0){\n"
      + "    vec2 cell = floor(rel / u_c.y);\n"
      + "    float h1 = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453);\n"
      + "    float h2 = fract(sin(dot(cell, vec2(39.346, 11.135))) * 24634.6345);\n"
      + "    rel += (vec2(h1, h2) - 0.5) * u_c.x * (0.3 + core);\n"
      + "  }\n"
      + "  float ca = u_a.w * edge;\n"
      + "  vec3 col;\n"
      + "  if(u_e.y > 0.0){\n"
      + "    vec3 acc = vec3(0.0);\n"
      + "    for(int k = 0; k < 6; k++){\n"
      + "      float sa = (float(k) - 2.5) * u_e.y * core;\n"
      + "      acc += at(vec2(cos(sa) * rel.x - sin(sa) * rel.y, sin(sa) * rel.x + cos(sa) * rel.y));\n"
      + "    }\n"
      + "    col = acc / 6.0;\n"
      + "  } else {\n"
      + "    col.r = at(rel * (1.0 + ca)).r;\n"
      + "    col.g = at(rel).g;\n"
      + "    col.b = at(rel * (1.0 - ca)).b;\n"
      + "  }\n"
      + "  if(u_f.y > 0.0){\n"
      + "    float inner = 1.0 - smoothstep(u_f.y * 0.93, u_f.y, r);\n"
      + "    float kk = min(r / u_f.y, 1.0);\n"
      + "    vec3 ci = at(v_rel * mix(u_f.z, 1.0, kk * kk));\n"
      + "    ci = mix(ci, vec3(1.0) - ci, u_f.x);\n"
      + "    col = mix(col, ci, inner);\n"
      + "    float re = (r - u_f.y) / 0.05;\n"
      + "    col += vec3(0.35, 0.55, 1.0) * u_f.w * exp(-re * re);\n"
      + "  }\n"
      + "  col *= 1.0 + u_g.x * sin(phase) * edge;\n"
      + "  float gray = dot(col, vec3(0.299, 0.587, 0.114));\n"
      + "  col = mix(col, vec3(gray), u_e.x * smoothstep(0.0, 0.22, edge));\n"
      + "  col *= 1.0 - u_c.w * pow(core, 1.5);\n"
      + "  col = mix(col, u_tint.rgb, u_tint.a * core);\n"
      + "  col += u_tint.rgb * pow(r, 6.0) * u_c.z;\n"
      + "  col *= 1.0 - u_g.w * smoothstep(0.55, 1.0, r);\n"
      + "  col *= 1.0 + u_h.x * dot(v_rel / u_radius, vec2(-0.5, 0.65)) * (0.4 + r);\n"
      + "  float a = u_g.y * u_fade * (1.0 - smoothstep(0.88, 1.0, r));\n"
      + "  gl_FragColor = vec4(col, a);\n"
      + "}\n";

   /** Shattered-mirror look: the outline of the anomaly is cut into glass triangles, each one a small convex lens. No glow at all. */
   static final String FRAG_MIRROR = "uniform sampler2D u_screen;\n"
      + "uniform vec2 u_uvCenter;\n"
      + "uniform vec2 u_uvScale;\n"
      + "uniform highp float u_time;\n"
      + "uniform float u_fade;\n"
      + "uniform vec2 u_size;\n"      // halfW, halfH of the anomaly
      + "uniform vec2 u_rot;\n"       // cos, sin of its rotation
      + "uniform vec3 u_rip0;\n"      // recent hits: x, y (world axes, relative to the centre), age in ticks
      + "uniform vec3 u_rip1;\n"
      + "uniform vec3 u_rip2;\n"
      + "uniform vec3 u_rip3;\n"
      + "uniform float u_lw[15];\n"   // left / right half widths along the length (the real outline of the anomaly)
      + "uniform float u_rw[15];\n"
      + "varying vec2 v_rel;\n"
      + "vec2 hash2(vec2 p){\n"
      + "  p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));\n"
      + "  return fract(sin(p) * 43758.5453);\n"
      + "}\n"
      + "vec3 at(vec2 rel){ return texture2D(u_screen, u_uvCenter + rel * u_uvScale).rgb; }\n"
      + "vec2 rip(vec3 r, vec2 q){\n"
      + "  if(r.z > 120.0) return vec2(0.0);\n"
      + "  vec2 rl = vec2(u_rot.x * r.x + u_rot.y * r.y, -u_rot.y * r.x + u_rot.x * r.y);\n"
      + "  vec2 dv = q - rl;\n"
      + "  float dd = length(dv);\n"
      + "  float front = r.z * 0.9;\n"
      + "  float m = 1.0 - smoothstep(0.0, 5.0, dd - front);\n"
      + "  float amp = 3.0 * exp(-r.z * 0.03) * exp(-dd * 0.02) * m;\n"
      + "  return dv / max(dd, 0.001) * sin((dd - front) * 0.8) * amp;\n"
      + "}\n"
      + "float prof(float t, bool left){\n"
      + "  float f = clamp(t, 0.0, 1.0) * 14.0;\n"
      + "  float s = 0.0;\n"
      + "  for(int k = 0; k < 15; k++){\n"
      + "    float w = max(0.0, 1.0 - abs(f - float(k)));\n"
      + "    s += w * (left ? u_lw[k] : u_rw[k]);\n"
      + "  }\n"
      + "  return s;\n"
      + "}\n"
      + "void main(){\n"
      + "  vec2 q = vec2(u_rot.x * v_rel.x + u_rot.y * v_rel.y, -u_rot.y * v_rel.x + u_rot.x * v_rel.y);\n"
      + "  float t = (q.y + u_size.y) / (2.0 * u_size.y);\n"
      + "  if(t < 0.0 || t > 1.0) discard;\n"
      + "  float w = prof(t, q.x < 0.0);\n"
      + "  float d = w - abs(q.x);\n"
      + "  if(d <= 0.0) discard;\n"
      + "  vec2 qs = q + rip(u_rip0, q) + rip(u_rip1, q) + rip(u_rip2, q) + rip(u_rip3, q);\n"
      + "  float cellS = u_size.x * 0.65;\n"
      + "  vec2 p = qs / cellS;\n"
      + "  p += 0.22 * vec2(sin(p.y * 1.7 + 1.3), sin(p.x * 1.9 + 0.7)) + 0.05 * vec2(sin(u_time * 0.01 + p.y), cos(u_time * 0.011 + p.x));\n"
      + "  vec2 sk = p + (p.x + p.y) * 0.366025;\n"
      + "  vec2 i = floor(sk);\n"
      + "  vec2 f = sk - i;\n"
      + "  bool lower = f.x > f.y;\n"
      + "  vec2 cs = i + (lower ? vec2(0.66667, 0.33333) : vec2(0.33333, 0.66667));\n"
      + "  vec2 c = cs - (cs.x + cs.y) * 0.211325;\n"
      + "  float edge = lower ? min(f.y, min(1.0 - f.x, f.x - f.y)) : min(f.x, min(1.0 - f.y, f.y - f.x));\n"
      + "  vec2 h = hash2(i + (lower ? 0.0 : 31.7));\n"
      + "  vec2 rel = p - c;\n"
      + "  float tt = min(length(rel) / 0.48, 1.0);\n"
      + "  float k = mix(0.5, 0.78, h.x) + 0.05 * sin(u_time * 0.03 + h.y * 6.2831);\n"
      + "  vec2 srcL = qs + rel * (mix(k, 1.0, tt * tt) - 1.0) * cellS + (h - 0.5) * 0.25 * cellS;\n"
      + "  vec2 srcW = vec2(u_rot.x * srcL.x - u_rot.y * srcL.y, u_rot.y * srcL.x + u_rot.x * srcL.y);\n"
      + "  vec3 col = at(srcW);\n"
      + "  col *= vec3(0.82, 0.93, 1.0);\n"
      + "  col *= 1.0 + 0.3 * dot(rel / 0.48, vec2(-0.6, 0.8)) * (0.6 + 0.4 * h.x);\n"
      + "  col *= 0.9 + 0.2 * h.y;\n"
      + "  col *= 1.0 - 0.5 * (1.0 - smoothstep(0.0, 0.12, edge));\n"
      + "  col *= 1.0 - 0.4 * (1.0 - smoothstep(0.0, 1.2, d));\n"
      + "  float a = u_fade * smoothstep(0.0, 0.6, d);\n"
      + "  gl_FragColor = vec4(col, a);\n"
      + "}\n";

   /** How one anomaly bends the picture. All numbers are tunable. */
   public static class Style {
      /** Scale of the sampled picture at the centre: <1 magnifies, >1 sucks the picture inwards. */
      public float lens = 1.0F;
      public float swirl, swirlSpeed;
      public float rippleAmp, rippleFreq, rippleSpeed, arms;
      public float aberration;
      public float shatter, shatterCell = 3.0F;
      public float r, g, b, tint;
      public float rim, dark;
      /** 0..1: how much the colours inside the radius are drained to grey. */
      public float desat;
      /** Tangential smear (radians per tap): stretches the picture into arcs around the centre. */
      public float smear;
      /** Twist waves running along the radius (amplitude in radians, speed; negative speed = flowing inwards). */
      public float wave, waveSpeed;
      /** A separate convex centre: colour inversion (0..1), its radius as a fraction of the whole, magnification (<1) and rim glow. */
      public float invert, innerR, innerLens = 1.0F, innerGlow;
      /** Brightness banding along the spiral (0 = off) and overall opacity of the distorted disc (1 = opaque). */
      public float shade, opacity = 1.0F;
      /** Scale of the sampled picture at the rim (>1 squeezes the outside into the rim like a glass ball) and rim darkening. */
      public float edgeLens = 1.0F, vignette;
      /** Glass-ball shading: lighter on the upper left, darker on the lower right (0 = off). */
      public float bevel;

      public Style lens(float v) { this.lens = v; return this; }
      public Style swirl(float amount, float speed) { this.swirl = amount; this.swirlSpeed = speed; return this; }
      public Style ripple(float amp, float freq, float speed, float arms) { this.rippleAmp = amp; this.rippleFreq = freq; this.rippleSpeed = speed; this.arms = arms; return this; }
      public Style aberration(float v) { this.aberration = v; return this; }
      public Style shatter(float amount, float cell) { this.shatter = amount; this.shatterCell = cell; return this; }
      public Style tint(float r, float g, float b, float amount) { this.r = r; this.g = g; this.b = b; this.tint = amount; return this; }
      public Style rim(float v) { this.rim = v; return this; }
      public Style dark(float v) { this.dark = v; return this; }
      public Style desat(float v) { this.desat = v; return this; }
      public Style smear(float v) { this.smear = v; return this; }
      public Style wave(float amp, float speed) { this.wave = amp; this.waveSpeed = speed; return this; }
      public Style bevel(float v) { this.bevel = v; return this; }
      public Style edgeLens(float v) { this.edgeLens = v; return this; }
      public Style vignette(float v) { this.vignette = v; return this; }
      public Style shade(float v) { this.shade = v; return this; }
      public Style opacity(float v) { this.opacity = v; return this; }
      public Style inner(float invert, float radius, float lens, float glow) { this.invert = invert; this.innerR = radius; this.innerLens = lens; this.innerGlow = glow; return this; }
   }

   // barrier: a smooth convex bubble that magnifies the picture and drains the colours inside it
   public static final Style BARRIER = new Style().lens(0.3F).edgeLens(1.8F).vignette(0.3F).bevel(0.35F).swirl(0.35F, 0.02F).aberration(0.04F).tint(1.0F, 1.0F, 1.0F, 0.08F).rim(0.12F).desat(1.0F);
   public static final Style TIME = new Style().lens(0.8F).swirl(0.4F, 0.02F).ripple(0.8F, 0.9F, 0.15F, 0.0F).aberration(0.09F).shatter(2.4F, 3.0F).tint(0.55F, 1.0F, 0.8F, 0.12F).rim(0.3F);
   public static final Style VORTEX = new Style().lens(1.0F).swirl(2.6F, 0.02F).ripple(1.0F, 0.4F, 0.1F, 3.0F).aberration(0.03F).tint(0.7F, 0.8F, 1.0F, 0.05F).rim(0.1F);
   // black hole: the surroundings are twisted and smeared into arcs around the centre (a negative swirl speed = steady twist)
   public static final Style BLACKHOLE = new Style().lens(1.5F).swirl(3.2F, -0.05F).aberration(0.03F).tint(1.0F, 0.55F, 0.15F, 0.10F).smear(0.18F).wave(1.1F, -0.07F).ripple(2.0F, 0.3F, -0.12F, 2.0F);
   // warp: the same twisted surroundings as the black hole, but the centre is a convex bubble with inverted colours
   public static final Style WARP = new Style().lens(1.3F).swirl(3.0F, -0.05F).aberration(0.02F).smear(0.16F).wave(1.0F, -0.07F).ripple(1.6F, 0.3F, -0.12F, 2.0F).inner(1.0F, 0.38F, 0.55F, 0.9F);
   public static final Style FIRE = new Style().lens(1.0F).ripple(1.3F, 1.3F, 0.35F, 0.0F).aberration(0.02F).tint(1.0F, 0.5F, 0.2F, 0.08F);
   public static final Style BALL = new Style().lens(0.7F).swirl(0.6F, 0.05F).ripple(0.8F, 1.0F, 0.3F, 0.0F).aberration(0.05F).tint(0.5F, 0.7F, 1.0F, 0.08F).rim(0.2F);
   public static final Style TOXIC = new Style().lens(1.0F).ripple(1.0F, 0.5F, 0.08F, 0.0F).aberration(0.015F).tint(0.4F, 1.0F, 0.3F, 0.10F);
   // whirlpool: the liquid it appeared in is itself twisted into a funnel; semi-transparent, so the liquid stays visible
   public static final Style WHIRLPOOL = new Style().lens(1.35F).swirl(2.6F, -0.04F).ripple(1.4F, 0.55F, -0.2F, 3.0F).wave(0.7F, -0.09F).smear(0.1F).aberration(0.02F).tint(1.0F, 1.0F, 1.0F, 0.04F).rim(0.1F).dark(0.3F).shade(0.14F).opacity(0.75F);
   public static final Style MIRROR = new Style().lens(1.0F).ripple(1.2F, 0.5F, 0.1F, 0.0F).aberration(0.06F).tint(0.6F, 0.85F, 1.0F, 0.08F).rim(0.4F);

   public static DistortShader distort;
   public static MirrorShader mirror;
   /** Why the distortion is (not) active; shown in a toast when it is off. */
   public static String status = "not loaded";
   static FrameBuffer screen;
   static long frameId = -1L;
   static int passes;
   static boolean announced;
   /** Hard cap of shader passes per frame (each one copies the screen). */
   static final int MAX_PASSES = 8;

   public static void load() {
      if (Vars.headless) {
         status = "headless";
         return;
      }

      try {
         distort = new DistortShader();
         status = "ok";
         try {
            mirror = new MirrorShader();
         } catch (Throwable var2) {
            mirror = null;
            Log.err("Anomalies: mirror shader failed to load, mirrors use the simple distortion", var2);
         }
      } catch (Throwable var1) {
         String msg = String.valueOf(var1.getMessage());
         status = "shader compile failed: " + (msg.length() > 160 ? msg.substring(0, 160) : msg);
         Log.err("Anomalies: distortion shader failed to load, using the plain look", var1);
         distort = null;
      }

      Log.info("Anomalies: distortion shader status = @", status);
      Events.on(EventType.ClientLoadEvent.class, e -> {
         try {
            // the on/off switch lives in Settings -> Graphics (client-side, default on)
            Vars.ui.settings.graphics.checkPref("anom-shader", true);
         } catch (Throwable var3) {
            Log.err(var3);
         }

         if (!"ok".equals(status)) {
            Vars.ui.showInfoToast("[scarlet]Anomalies: distortion shader is off[]\n" + status, 10.0F);
         } else if (Core.settings.getBool("pixelate", false)) {
            Vars.ui.showInfoToast("[yellow]Anomalies: distortion is off while Pixelation is enabled[]", 8.0F);
         }
      });
   }

   public static boolean usable() {
      return distort != null && !Vars.headless && Core.settings.getBool("anom-shader", true) && !Core.settings.getBool("pixelate", false);
   }

   static boolean visible(float x, float y, float w, float h) {
      return Core.camera.bounds(Tmp.r1).overlaps(x - w, y - h, w * 2.0F, h * 2.0F);
   }

   /** Queue a round distortion at the given draw layer (a bit below it, so the anomaly's own drawing stays on top). */
   public static void distort(float x, float y, float radius, float fade, Style style, float z) {
      if (!usable() || !visible(x, y, radius, radius)) {
         return;
      }

      Draw.draw(z - 0.05F, () -> pass(x, y, radius, fade, style, 0.0F, 0.0F, 1.0F, 0.0F));
   }

   /** Same, but for an oriented box (mirror anomalies): half sizes along the local axes and rotation in degrees. */
   public static void distortBox(float x, float y, float halfW, float halfH, float rotation, float fade, Style style, float z) {
      float reach = Math.max(halfW, halfH);
      if (!usable() || !visible(x, y, reach, reach)) {
         return;
      }

      float c = (float)Math.cos(Math.toRadians((double)rotation));
      float s = (float)Math.sin(Math.toRadians((double)rotation));
      Draw.draw(z - 0.05F, () -> pass(x, y, reach, fade, style, halfW, halfH, c, s));
   }

   /** The shattered-glass mirror, drawn with the anomaly's real outline (lw / rw are its left and right half widths). */
   public static void distortMirror(float x, float y, float halfW, float halfH, float rotation, float[] lw, float[] rw, float[] rip, float fade, float z) {
      if (mirror == null) {
         distortBox(x, y, halfW, halfH, rotation, fade, MIRROR, z);
         return;
      }

      float reach = (float)Math.sqrt((double)(halfW * halfW + halfH * halfH));
      if (!usable() || !visible(x, y, reach, reach)) {
         return;
      }

      float c = (float)Math.cos(Math.toRadians((double)rotation));
      float s = (float)Math.sin(Math.toRadians((double)rotation));
      Draw.draw(z - 0.05F, () -> passMirror(x, y, halfW, halfH, c, s, lw, rw, rip, fade, reach));
   }

   static void passMirror(float cx, float cy, float hw, float hh, float c, float s, float[] lw, float[] rw, float[] rip, float fade, float reach) {
      MirrorShader m = mirror;
      if (m == null) {
         return;
      }

      long id = Core.graphics.getFrameId();
      if (id != frameId) {
         frameId = id;
         passes = 0;
      }

      if (++passes > MAX_PASSES) {
         return;
      }

      try {
         capture();
         m.cx = cx;
         m.cy = cy;
         m.hw = hw;
         m.hh = hh;
         m.c = c;
         m.s = s;
         m.fade = fade;
         m.lw = lw;
         m.rw = rw;
         m.rip = rip;
         Draw.shader(m);
         Draw.color();
         float ext = reach + 2.0F;
         Fill.rect(cx, cy, ext * 2.0F, ext * 2.0F);
         Draw.shader();
      } catch (Throwable var14) {
         Log.err("Anomalies: mirror pass failed, mirrors fall back to the simple distortion", var14);
         Draw.shader();
         mirror = null;
         final String msg = String.valueOf(var14);
         Core.app.post(() -> Vars.ui.showInfoToast("[scarlet]Anomalies: mirror shader error[]\n" + msg, 12.0F));
      }
   }

   static void pass(float cx, float cy, float radius, float fade, Style style, float hw, float hh, float c, float s) {
      if (distort == null) {
         return;
      }

      long id = Core.graphics.getFrameId();
      if (id != frameId) {
         frameId = id;
         passes = 0;
      }

      if (++passes > MAX_PASSES) {
         return;
      }

      try {
         capture();
         DistortShader d = distort;
         d.cx = cx;
         d.cy = cy;
         d.radius = radius;
         d.fade = fade;
         d.style = style;
         d.hw = hw;
         d.hh = hh;
         d.c = c;
         d.s = s;
         Draw.shader(d);
         Draw.color();
         float ext = radius + 2.0F;
         Fill.rect(cx, cy, ext * 2.0F, ext * 2.0F);
         Draw.shader();
         if (!announced) {
            announced = true;
            Core.app.post(() -> Vars.ui.showInfoToast("[lime]Anomalies: distortion pass is running[]", 6.0F));
         }
      } catch (Throwable var12) {
         Log.err("Anomalies: distortion pass failed, disabling it", var12);
         Draw.shader();
         status = "draw pass failed: " + var12;
         distort = null;
         final String msg = status;
         Core.app.post(() -> Vars.ui.showInfoToast("[scarlet]Anomalies: distortion shader error[]\n" + msg, 15.0F));
      }
   }

   /** Copy the frame drawn so far into a texture (done per pass: anomalies sit on different layers). */
   static void capture() {
      int w = Core.graphics.getWidth();
      int h = Core.graphics.getHeight();
      if (screen == null) {
         screen = new FrameBuffer(w, h);
      } else if (screen.getWidth() != w || screen.getHeight() != h) {
         screen.resize(w, h);
      }

      Draw.flush();
      Texture t = screen.getTexture();
      t.bind(1);
      Gl.copyTexSubImage2D(Gl.texture2d, 0, 0, 0, 0, 0, w, h);
      Gl.activeTexture(Gl.texture0);
   }

   public static class DistortShader extends Shader {
      public float cx;
      public float cy;
      public float radius = 18.0F;
      public float fade = 1.0F;
      public float hw;
      public float hh;
      public float c = 1.0F;
      public float s;
      public Style style = BARRIER;

      public DistortShader() {
         super(VERT, FRAG);
      }

      void f1(String n, float a) {
         if (this.hasUniform(n)) {
            this.setUniformf(n, a);
         }
      }

      void f2(String n, float a, float b) {
         if (this.hasUniform(n)) {
            this.setUniformf(n, a, b);
         }
      }

      void f4(String n, float a, float b, float c, float d) {
         if (this.hasUniform(n)) {
            this.setUniformf(n, a, b, c, d);
         }
      }

      public void apply() {
         float camW = Core.camera.width;
         float camH = Core.camera.height;
         float left = Core.camera.position.x - camW / 2.0F;
         float bottom = Core.camera.position.y - camH / 2.0F;
         Style st = this.style;
         this.f2("u_center", this.cx, this.cy);
         this.f2("u_uvCenter", (this.cx - left) / camW, (this.cy - bottom) / camH);
         this.f2("u_uvScale", 1.0F / camW, 1.0F / camH);
         this.f1("u_radius", this.radius);
         this.f1("u_time", Time.time % 62831.85F);
         this.f1("u_fade", this.fade);
         this.f4("u_a", st.lens, st.swirl, st.swirlSpeed, st.aberration);
         this.f4("u_b", st.rippleAmp, st.rippleFreq, st.rippleSpeed, st.arms);
         this.f4("u_c", st.shatter, st.shatterCell, st.rim, st.dark);
         this.f4("u_tint", st.r, st.g, st.b, st.tint);
         this.f4("u_box", this.hw, this.hh, this.c, this.s);
         this.f4("u_e", st.desat, st.smear, st.wave, st.waveSpeed);
         this.f4("u_f", st.invert, st.innerR, st.innerLens, st.innerGlow);
         this.f4("u_g", st.shade, st.opacity, st.edgeLens, st.vignette);
         this.f4("u_h", st.bevel, 0.0F, 0.0F, 0.0F);
         AnomalyShaders.screen.getTexture().bind(1);
         if (this.hasUniform("u_screen")) {
            this.setUniformi("u_screen", 1);
         }

         Gl.activeTexture(Gl.texture0);
      }
   }

   public static class MirrorShader extends Shader {
      public float cx;
      public float cy;
      public float hw = 8.0F;
      public float hh = 16.0F;
      public float c = 1.0F;
      public float s;
      public float fade = 1.0F;
      public float[] lw;
      public float[] rw;
      /** Up to 4 recent hits: x, y (relative to the centre) and age in ticks. */
      public float[] rip = new float[12];

      public MirrorShader() {
         super(VERT, FRAG_MIRROR);
      }

      void f1(String n, float a) {
         if (this.hasUniform(n)) {
            this.setUniformf(n, a);
         }
      }

      void f2(String n, float a, float b) {
         if (this.hasUniform(n)) {
            this.setUniformf(n, a, b);
         }
      }

      public void apply() {
         float camW = Core.camera.width;
         float camH = Core.camera.height;
         float left = Core.camera.position.x - camW / 2.0F;
         float bottom = Core.camera.position.y - camH / 2.0F;
         this.f2("u_center", this.cx, this.cy);
         this.f2("u_uvCenter", (this.cx - left) / camW, (this.cy - bottom) / camH);
         this.f2("u_uvScale", 1.0F / camW, 1.0F / camH);
         this.f1("u_time", Time.time % 62831.85F);
         this.f1("u_fade", this.fade);
         this.f2("u_size", this.hw, this.hh);
         this.f2("u_rot", this.c, this.s);
         for(int i = 0; i < 4; ++i) {
            String n = "u_rip" + i;
            if (this.hasUniform(n)) {
               this.setUniformf(n, this.rip[i * 3], this.rip[i * 3 + 1], this.rip[i * 3 + 2]);
            }
         }

         // array elements are set by name; elements the compiler dropped are simply skipped
         for(int i = 0; i < 15; ++i) {
            try {
               this.setUniformf("u_lw[" + i + "]", this.lw[i]);
            } catch (Throwable var7) {
            }

            try {
               this.setUniformf("u_rw[" + i + "]", this.rw[i]);
            } catch (Throwable var8) {
            }
         }

         AnomalyShaders.screen.getTexture().bind(1);
         if (this.hasUniform("u_screen")) {
            this.setUniformi("u_screen", 1);
         }

         Gl.activeTexture(Gl.texture0);
      }
   }
}
