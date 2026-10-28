package anomalies;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.Damage;
import mindustry.entities.bullet.ContinuousFlameBulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

/**
 * The Arux laser: the damage logic of the Sublimate flame (ContinuousFlameBulletType), but drawn as a thin layered beam
 * that narrows to a sharp point at its far end, with a small star at the muzzle.
 */
public class ArUxLaser extends ContinuousFlameBulletType {
   /** half width of the beam at the muzzle, world units */
   static final float HALF_WIDTH = 4.5F;
   static final int PARTS = 32;

   /** how long the beam takes to die out after the owner stops shooting, ticks */
   static final float FADE_TICKS = 26.0F;
   /** how fast it flares up again when the owner starts shooting during the fade-out, ticks */
   static final float RELIGHT_TICKS = 4.0F;
   /** the owner must be not shooting for this long before the fade-out starts, so a one-tick blip never changes the beam */
   static final float HOLD_TICKS = 5.0F;

   /** per-bullet fade state, kept in Bullet.data (the game clears it when the bullet is recycled) */
   static final class Fade {
      float progress; // 0 = the beam burns at full size, 1 = gone
      float idle;     // ticks the owner has not been shooting
      float reach = -1.0F; // current visible length of the beam (shortened by an obstacle), -1 = not measured yet
   }

   static Fade fade(Bullet b) {
      if (!(b.data instanceof Fade)) b.data = new Fade();
      return (Fade)b.data;
   }

   @Override
   public void update(Bullet b) {
      Fade fd = fade(b);
      boolean off = b.owner instanceof Unit u && !u.isShooting();
      if (off) {
         fd.idle += Time.delta;
         if (fd.idle > HOLD_TICKS) fd.progress = Math.min(1.0F, fd.progress + Time.delta / FADE_TICKS);
      } else {
         fd.idle = 0.0F;
         fd.progress = Math.max(0.0F, fd.progress - Time.delta / RELIGHT_TICKS);
      }
      if (fd.progress >= 1.0F) {
         b.remove();
         return;
      }
      // an obstacle (a block, a unit) cuts the beam: it shortens to it quickly and grows back smoothly
      float target = reach(b, this.length);
      if (fd.reach < 0.0F) fd.reach = target;
      else {
         float k = Mathf.clamp(Time.delta * (target < fd.reach ? 0.6F : 0.25F));
         fd.reach += (target - fd.reach) * k;
         if (Math.abs(target - fd.reach) < 0.05F) fd.reach = target;
      }
      // the damage stops when the beam has already shrunk to about half
      if (fd.progress < 0.5F) super.update(b);
   }

   @Override
   public void draw(Bullet b) {
      float r = fade(b).progress;
      float e = r * r * (3.0F - 2.0F * r); // smooth fade-out progress
      float f = Mathf.clamp(b.time / 4.0F) * Mathf.clamp((b.lifetime - b.time) / 16.0F) * (1.0F - 0.75F * e);
      if (r > 0.0F) f *= 0.85F + 0.15F * Mathf.sin(Time.time * 2.3F); // only the dying beam flickers
      if (f <= 0.01F) return;
      float ang = b.rotation();
      float cos = Mathf.cosDeg(ang), sin = Mathf.sinDeg(ang);
      float nx = -sin, ny = cos;
      float len = (fade(b).reach < 0.0F ? this.length : fade(b).reach) * (1.0F - e); // cut by an obstacle, and pulled back into the muzzle while it fades
      Draw.z(Layer.bullet);
      for (int i = 0; i < this.colors.length; ++i) {
         float k = 1.0F - i * 0.2F; // outer layer is the widest, the white core the thinnest
         Draw.color(this.colors[i]);
         Fill.circle(b.x, b.y, halfWidth(0.0F, k, f)); // round cap: the beam does not look cut off at the start
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
      for (int k = 0; k < 4; ++k) Drawf.tri(b.x, b.y, 1.2F * f, 5.0F * f, rot + k * 90.0F);
      Draw.color(arc.graphics.Color.white);
      for (int k = 0; k < 4; ++k) Drawf.tri(b.x, b.y, 0.6F * f, 2.8F * f, rot + k * 90.0F);
      Drawf.light(b.x, b.y, b.x + cos * len, b.y + sin * len, 20.0F, ArUxUnit.yellow, 0.6F * f);
      Draw.reset();
   }

   static java.lang.reflect.Method pierceLength, findLength;
   static boolean looked;

   /** How far the beam really reaches: the engine's own measurement (the same one the damage uses), so the picture and the damage always end at the same place. */
   float reach(Bullet b, float len) {
      if (!looked) {
         looked = true;
         try {
            pierceLength = Damage.class.getMethod("findPierceLength", Bullet.class, int.class, float.class);
         } catch (Throwable ignored) {
         }
         try {
            findLength = Damage.class.getMethod("findLength", Bullet.class, float.class, boolean.class, int.class);
         } catch (Throwable ignored) {
         }
      }
      try {
         if (pierceLength != null) return Mathf.clamp((Float)pierceLength.invoke(null, b, this.pierceCap, len), 0.0F, len);
      } catch (Throwable t) {
         pierceLength = null;
      }
      try {
         if (findLength != null) return Mathf.clamp((Float)findLength.invoke(null, b, len, false, this.pierceCap), 0.0F, len);
      } catch (Throwable t) {
         findLength = null;
      }
      return rayLength(b, len);
   }

   /** Own measurement, used when the engine one is not available: the beam ends at the first enemy building. */
   static float rayLength(Bullet b, float len) {
      float cos = Mathf.cosDeg(b.rotation()), sin = Mathf.sinDeg(b.rotation());
      for (float d = 4.0F; d < len; d += 4.0F) {
         Building bd = Vars.world.buildWorld(b.x + cos * d, b.y + sin * d);
         if (bd != null && bd.team != b.team) return d;
      }
      return len;
   }

   /** Beam half width at the fraction t of its length: wide for most of it, then a smooth taper to a sharp point. */
   static float halfWidth(float t, float layer, float fade) {
      float wobble = 1.0F + 0.06F * Mathf.sin(Time.time * 0.7F + t * 9.0F);
      float tail = Mathf.clamp((1.0F - t) / 0.6F); // the last 60% of the beam narrow down
      float taper = 0.5F * tail * tail * (3.0F - 2.0F * tail) + 0.5F * tail; // smooth start, pointed end
      return HALF_WIDTH * layer * fade * wobble * (1.0F - 0.2F * t) * taper;
   }
}
