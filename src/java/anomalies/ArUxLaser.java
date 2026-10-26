package anomalies;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.entities.bullet.ContinuousFlameBulletType;
import mindustry.gen.Bullet;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

/**
 * The Arux laser: the damage logic of the Sublimate flame (ContinuousFlameBulletType), but drawn as a thin layered beam
 * that narrows to a sharp point at its far end, with a small star at the muzzle.
 */
public class ArUxLaser extends ContinuousFlameBulletType {
   /** half width of the beam at the muzzle, world units */
   static final float HALF_WIDTH = 1.5F;
   static final int PARTS = 14;

   public void draw(Bullet b) {
      float f = Mathf.clamp(b.time / 4.0F) * Mathf.clamp((b.lifetime - b.time) / 16.0F);
      if (f <= 0.01F) return;
      float ang = b.rotation();
      float cos = Mathf.cosDeg(ang), sin = Mathf.sinDeg(ang);
      float nx = -sin, ny = cos;
      float len = this.length;
      Draw.z(Layer.bullet);
      for (int i = 0; i < this.colors.length; ++i) {
         float k = 1.0F - i * 0.2F; // outer layer is the widest, the white core the thinnest
         Draw.color(this.colors[i]);
         for (int s = 0; s < PARTS; ++s) {
            float t0 = (float)s / PARTS, t1 = (float)(s + 1) / PARTS;
            float w0 = halfWidth(t0, k, f), w1 = halfWidth(t1, k, f);
            float x0 = b.x + cos * len * t0, y0 = b.y + sin * len * t0;
            float x1 = b.x + cos * len * t1, y1 = b.y + sin * len * t1;
            Fill.quad(x0 + nx * w0, y0 + ny * w0, x0 - nx * w0, y0 - ny * w0, x1 - nx * w1, y1 - ny * w1, x1 + nx * w1, y1 + ny * w1);
         }
      }
      // small star at the muzzle
      Draw.z(Layer.bullet + 0.01F);
      float rot = Time.time * 1.5F;
      Draw.color(ArUxUnit.yellow);
      for (int k = 0; k < 4; ++k) Drawf.tri(b.x, b.y, 1.0F * f, 4.5F * f, rot + k * 90.0F);
      Draw.color(arc.graphics.Color.white);
      for (int k = 0; k < 4; ++k) Drawf.tri(b.x, b.y, 0.5F * f, 2.5F * f, rot + k * 90.0F);
      Drawf.light(b.x, b.y, b.x + cos * len, b.y + sin * len, 20.0F, ArUxUnit.yellow, 0.6F * f);
      Draw.reset();
   }

   /** Beam half width at the fraction t of its length: narrows to a sharp point. */
   static float halfWidth(float t, float layer, float fade) {
      float wobble = 1.0F + 0.08F * Mathf.sin(Time.time * 0.7F + t * 9.0F);
      return HALF_WIDTH * layer * fade * wobble * (float)Math.pow(1.0F - t, 1.7F);
   }
}
