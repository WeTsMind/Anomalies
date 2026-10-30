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
      + "uniform float u_debug;\n"
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
      + "  if(u_debug > 0.5 && u_debug < 1.5){ gl_FragColor = vec4(0.8, 0.0, 0.8, 0.8); return; }\n"
      + "  if(u_debug > 1.5){ gl_FragColor = vec4(at(v_rel) * vec3(0.5, 1.0, 0.5), 1.0); return; }\n"
      + "  float dist = r * u_radius;\n"
      + "  float edge = 1.0 - r;\n"
      + "  float core = edge * edge;\n"
      + "  float ang = core * u_a.y * sin(u_time * u_a.z);\n"
      + "  float cs = cos(ang);\n"
      + "  float sn = sin(ang);\n"
      + "  vec2 rel = vec2(cs * v_rel.x - sn * v_rel.y, sn * v_rel.x + cs * v_rel.y);\n"
      + "  rel *= mix(u_a.x, 1.0, r * r);\n"
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
      + "  col.r = at(rel * (1.0 + ca)).r;\n"
      + "  col.g = at(rel).g;\n"
      + "  col.b = at(rel * (1.0 - ca)).b;\n"
      + "  col *= 1.0 - u_c.w * pow(core, 1.5);\n"
      + "  col = mix(col, u_tint.rgb, u_tint.a * core);\n"
      + "  col += u_tint.rgb * pow(r, 6.0) * u_c.z;\n"
      + "  float a = u_fade * smoothstep(1.0, 0.88, r);\n"
      + "  gl_FragColor = vec4(col * a, a);\n"
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

      public Style lens(float v) { this.lens = v; return this; }
      public Style swirl(float amount, float speed) { this.swirl = amount; this.swirlSpeed = speed; return this; }
      public Style ripple(float amp, float freq, float speed, float arms) { this.rippleAmp = amp; this.rippleFreq = freq; this.rippleSpeed = speed; this.arms = arms; return this; }
      public Style aberration(float v) { this.aberration = v; return this; }
      public Style shatter(float amount, float cell) { this.shatter = amount; this.shatterCell = cell; return this; }
      public Style tint(float r, float g, float b, float amount) { this.r = r; this.g = g; this.b = b; this.tint = amount; return this; }
      public Style rim(float v) { this.rim = v; return this; }
      public Style dark(float v) { this.dark = v; return this; }
   }

   public static final Style BARRIER = new Style().lens(0.45F).swirl(1.4F, 0.015F).ripple(1.6F, 0.75F, 0.12F, 0.0F).aberration(0.05F).tint(0.65F, 0.36F, 1.0F, 0.10F).rim(0.25F);
   public static final Style TIME = new Style().lens(0.8F).swirl(0.4F, 0.02F).ripple(0.8F, 0.9F, 0.15F, 0.0F).aberration(0.09F).shatter(2.4F, 3.0F).tint(0.55F, 1.0F, 0.8F, 0.12F).rim(0.3F);
   public static final Style VORTEX = new Style().lens(1.0F).swirl(2.6F, 0.02F).ripple(1.0F, 0.4F, 0.1F, 3.0F).aberration(0.03F).tint(0.7F, 0.8F, 1.0F, 0.05F).rim(0.1F);
   public static final Style BLACKHOLE = new Style().lens(2.4F).swirl(1.8F, 0.04F).ripple(0.6F, 0.5F, 0.2F, 2.0F).aberration(0.07F).tint(0.5F, 0.35F, 1.0F, 0.05F).rim(0.3F).dark(0.9F);
   public static final Style WARP = new Style().lens(0.6F).swirl(0.5F, 0.03F).ripple(1.2F, 1.0F, 0.2F, 0.0F).aberration(0.07F).tint(0.7F, 0.4F, 1.0F, 0.10F).rim(0.2F);
   public static final Style FIRE = new Style().lens(1.0F).ripple(1.3F, 1.3F, 0.35F, 0.0F).aberration(0.02F).tint(1.0F, 0.5F, 0.2F, 0.08F);
   public static final Style BALL = new Style().lens(0.7F).swirl(0.6F, 0.05F).ripple(0.8F, 1.0F, 0.3F, 0.0F).aberration(0.05F).tint(0.5F, 0.7F, 1.0F, 0.08F).rim(0.2F);
   public static final Style TOXIC = new Style().lens(1.0F).ripple(1.0F, 0.5F, 0.08F, 0.0F).aberration(0.015F).tint(0.4F, 1.0F, 0.3F, 0.10F);
   public static final Style WHIRLPOOL = new Style().lens(0.8F).swirl(2.0F, 0.03F).ripple(1.0F, 0.6F, 0.2F, 2.0F).aberration(0.03F).tint(0.8F, 0.95F, 1.0F, 0.05F).rim(0.1F);
   public static final Style MIRROR = new Style().lens(1.0F).ripple(1.2F, 0.5F, 0.1F, 0.0F).aberration(0.06F).tint(0.6F, 0.85F, 1.0F, 0.08F).rim(0.4F);

   public static DistortShader distort;
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
            Vars.ui.settings.graphics.sliderPref("anom-shader-debug", 0, 0, 2, 1, v -> v == 0 ? "off" : (v == 1 ? "magenta disc" : "unwarped copy (green)"));
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
         this.f1("u_debug", (float)Core.settings.getInt("anom-shader-debug", 0));
         AnomalyShaders.screen.getTexture().bind(1);
         if (this.hasUniform("u_screen")) {
            this.setUniformi("u_screen", 1);
         }

         Gl.activeTexture(Gl.texture0);
      }
   }
}
