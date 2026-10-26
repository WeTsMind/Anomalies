package anomalies;

import arc.graphics.Blending;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;

/**
 * Instant short strike of the Arux shockers: the same jagged yellow lightning (glow, body and white core, with side
 * branches) that the ball lightning draws when it strikes. Hits the closest enemy within 3 tiles.
 */
public class ShockBolt extends BulletType {
   static final float RANGE = 24.0F;

   /** The strike picture: the layered jagged bolt of the ball lightning and a flash at its end. */
   static final Effect boltFx = new Effect(14.0F, 120.0F, e -> {
      if (!(e.data instanceof float[])) return;
      float[] p = (float[])e.data;
      float fade = Math.min(1.0F, e.fout() * 1.5F);
      int seed = e.id * 977 + (int)(Time.time / 3.0F) * 13;
      Draw.blend(Blending.additive);
      for (int i = 0; i < 4; ++i) {
         Draw.color(i == 3 ? BallLightningAnomaly.core : (i == 2 ? BallLightningAnomaly.body : BallLightningAnomaly.glow), BallLightningAnomaly.AL[i] * fade);
         bolt(e.x, e.y, p[0], p[1], seed, 3.0F, BallLightningAnomaly.SW[i] * 0.6F);
      }
      Draw.color(BallLightningAnomaly.glow, 0.5F * e.fout());
      Fill.circle(p[0], p[1], 4.0F * e.fout());
      Draw.color(BallLightningAnomaly.core, e.fout());
      Fill.circle(p[0], p[1], 1.6F * e.fout());
      Draw.blend();
      Draw.reset();
   });

   public ShockBolt(float damage) {
      super(0.01F, damage);
      this.lifetime = 1.0F;
      this.collides = false;
      this.collidesTiles = false;
      this.collidesAir = false;
      this.collidesGround = false;
      this.hittable = false;
      this.absorbable = false;
      this.reflectable = false;
      this.keepVelocity = false;
      this.rangeOverride = RANGE;
      this.shootEffect = Fx.none;
      this.smokeEffect = Fx.none;
      this.hitEffect = Fx.none;
      this.despawnEffect = Fx.none;
   }

   public void init(Bullet b) {
      super.init(b);
      float ex, ey;
      Teamc t = Units.closestTarget(b.team, b.x, b.y, RANGE, u -> true, bd -> true);
      if (t != null) {
         ex = t.getX();
         ey = t.getY();
         if (!Vars.net.client()) {
            if (t instanceof Unit) AlloyDmg.hit((Unit)t, this.damage);
            else if (t instanceof Building) AlloyDmg.hit((Building)t, this.damage);
         }
      } else { // nothing in reach: the bolt strikes into the air
         float r = b.rotation() * arc.math.Mathf.degRad;
         ex = b.x + (float)Math.cos(r) * RANGE;
         ey = b.y + (float)Math.sin(r) * RANGE;
      }
      boltFx.at(b.x, b.y, b.rotation(), new float[]{ex, ey});
   }

   public void draw(Bullet b) {
   }

   /** Jagged bolt with side branches, same as BallLightningAnomaly.bolt. */
   static void bolt(float x1, float y1, float x2, float y2, int seed, float jag, float width) {
      float dx = x2 - x1, dy = y2 - y1;
      float len = (float)Math.sqrt(dx * dx + dy * dy);
      if (len < 0.01F) return;
      float nx = -dy / len, ny = dx / len;
      float ang = (float)Math.atan2(dy, dx);
      int n = Math.max(4, (int)(len / 5.0F));
      float px = x1, py = y1;
      for (int i = 1; i <= n; ++i) {
         float f = (float)i / (float)n;
         float off = i == n ? 0.0F : (BallLightningAnomaly.hash(seed + i * 7) - 0.5F) * 2.0F * jag;
         float cx = x1 + dx * f + nx * off;
         float cy = y1 + dy * f + ny * off;
         float w = width * (0.15F + 0.85F * (float)Math.sin(Math.PI * (double)((float)i - 0.5F) / (double)n));
         Lines.stroke(w);
         Lines.line(px, py, cx, cy);
         if (i > 1 && i < n && BallLightningAnomaly.hash(seed + i * 31) > 0.72F) {
            float ba = ang + (BallLightningAnomaly.hash(seed + i * 5) > 0.5F ? 0.9F : -0.9F) + (BallLightningAnomaly.hash(seed + i * 3) - 0.5F) * 0.5F;
            float bl = len / (float)n * (1.5F + BallLightningAnomaly.hash(seed + i * 11) * 1.5F);
            Lines.stroke(w * 0.6F);
            Lines.line(cx, cy, cx + (float)Math.cos(ba) * bl, cy + (float)Math.sin(ba) * bl);
         }
         px = cx;
         py = cy;
      }
   }
}
