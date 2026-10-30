package anomalies;

import arc.Core;
import arc.graphics.Gl;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;

/**
 * Screen-space distortion for anomalies. The already drawn frame is copied into a texture and a shader re-draws a disc
 * of it with the picture bent (lens + swirl + outgoing ripples + chromatic aberration), so the space really looks warped.
 * Everything is guarded: if the shader fails to compile (or pixelation is on) anomalies fall back to the plain look.
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

   static final String FRAG = "#ifdef GL_ES\n"
      + "precision mediump float;\n"
      + "precision mediump int;\n"
      + "#endif\n"
      + "uniform sampler2D u_screen;\n"
      + "uniform vec2 u_uvCenter;\n"
      + "uniform vec2 u_uvScale;\n"
      + "uniform float u_radius;\n"
      + "uniform float u_time;\n"
      + "uniform float u_fade;\n"
      + "varying vec2 v_rel;\n"
      + "vec3 at(vec2 rel){ return texture2D(u_screen, u_uvCenter + rel * u_uvScale).rgb; }\n"
      + "void main(){\n"
      + "  float dist = length(v_rel);\n"
      + "  float r = dist / u_radius;\n"
      + "  if(r >= 1.0) discard;\n"
      + "  vec2 dir = v_rel / max(dist, 0.001);\n"
      + "  float edge = 1.0 - r;\n"
      + "  float core = edge * edge;\n"
      + "  float ang = core * 1.4 * sin(u_time * 0.015);\n"
      + "  float cs = cos(ang);\n"
      + "  float sn = sin(ang);\n"
      + "  vec2 rel = vec2(cs * v_rel.x - sn * v_rel.y, sn * v_rel.x + cs * v_rel.y);\n"
      + "  rel *= mix(0.45, 1.0, r * r);\n"
      + "  rel += dir * sin(dist * 0.75 - u_time * 0.12) * 1.6 * edge;\n"
      + "  float ca = 0.05 * edge;\n"
      + "  vec3 col;\n"
      + "  col.r = at(rel * (1.0 + ca)).r;\n"
      + "  col.g = at(rel).g;\n"
      + "  col.b = at(rel * (1.0 - ca)).b;\n"
      + "  col = mix(col, vec3(0.65, 0.36, 1.0), 0.10 * core);\n"
      + "  col += vec3(0.55, 0.4, 1.0) * pow(r, 6.0) * 0.25;\n"
      + "  gl_FragColor = vec4(col, u_fade * smoothstep(1.0, 0.88, r));\n"
      + "}\n";

   public static DistortShader distort;
   static FrameBuffer screen;
   static long lastFrame = -1L;

   public static void load() {
      if (Vars.headless) {
         return;
      }

      try {
         distort = new DistortShader();
      } catch (Throwable var1) {
         Log.err("Anomalies: distortion shader failed to load, using the plain look", var1);
         distort = null;
      }
   }

   public static boolean usable() {
      return distort != null && !Vars.headless && !Core.settings.getBool("pixelate", false);
   }

   /** Copy the frame drawn so far (once per frame) into a texture. Must run inside a Draw.draw task. */
   static void capture() {
      long id = Core.graphics.getFrameId();
      int w = Core.graphics.getWidth();
      int h = Core.graphics.getHeight();
      if (screen == null) {
         screen = new FrameBuffer(w, h);
      } else if (screen.getWidth() != w || screen.getHeight() != h) {
         screen.resize(w, h);
      }

      if (id != lastFrame) {
         lastFrame = id;
         Draw.flush();
         Texture t = screen.getTexture();
         t.bind(1);
         Gl.copyTexSubImage2D(Gl.texture2d, 0, 0, 0, 0, 0, w, h);
         Gl.activeTexture(Gl.texture0);
      }
   }

   /** Draw one warped disc of the captured frame. Called from a Draw.draw task. */
   public static void drawDistortion(float cx, float cy, float radius, float fade) {
      if (distort == null) {
         return;
      }

      try {
         capture();
         distort.cx = cx;
         distort.cy = cy;
         distort.radius = radius;
         distort.fade = fade;
         Draw.shader(distort);
         Draw.color();
         Fill.rect(cx, cy, radius * 2.0F, radius * 2.0F);
         Draw.shader();
      } catch (Throwable var5) {
         Log.err("Anomalies: distortion pass failed, disabling it", var5);
         Draw.shader();
         distort = null;
      }
   }

   public static class DistortShader extends Shader {
      public float cx;
      public float cy;
      public float radius = 18.0F;
      public float fade = 1.0F;

      public DistortShader() {
         super(VERT, FRAG);
      }

      public void apply() {
         float camW = Core.camera.width;
         float camH = Core.camera.height;
         float left = Core.camera.position.x - camW / 2.0F;
         float bottom = Core.camera.position.y - camH / 2.0F;
         this.setUniformf("u_center", this.cx, this.cy);
         this.setUniformf("u_uvCenter", (this.cx - left) / camW, (this.cy - bottom) / camH);
         this.setUniformf("u_uvScale", 1.0F / camW, 1.0F / camH);
         this.setUniformf("u_radius", this.radius);
         this.setUniformf("u_time", Time.time);
         this.setUniformf("u_fade", this.fade);
         AnomalyShaders.screen.getTexture().bind(1);
         this.setUniformi("u_screen", 1);
         Gl.activeTexture(Gl.texture0);
      }
   }
}
