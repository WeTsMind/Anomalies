package anomalies;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import java.util.Objects;
import mindustry.entities.Units;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.world.Tile;

/**
 * Whirlpool: like the Vortex ("Выверт"), but it only appears on liquid tiles. A ground or naval unit that comes
 * within reach is grabbed, spun down the funnel and thrown out at the end, and the whirlpool is gone.
 * The capture logic only runs on the server / single player (like every other anomaly); the picture is drawn on every client.
 */
public class WhirlpoolAnomaly extends Anomaly {
   static final float R = 32.0F;
   static final float SPIRAL_TICKS = 100.0F;
   static final int FOAM = 40;
   static final Color deep = Color.valueOf("0d3b8c");
   static final Color mid = Color.valueOf("2f84e0");
   static final Color cyan = Color.valueOf("7fe3ff");
   static final Color foam = Color.valueOf("e6f9ff");
   final float[] dPh = new float[FOAM];
   final float[] dAng = new float[FOAM];
   final float[] dSp = new float[FOAM];
   final float[] dSz = new float[FOAM];
   Unit target;
   float startR;
   float phase;
   float angle;

   public WhirlpoolAnomaly() {
      this.tileRadius = 2;

      for(int i = 0; i < FOAM; ++i) {
         this.dPh[i] = Mathf.random();
         this.dAng[i] = Mathf.random(360.0F);
         this.dSp[i] = Mathf.random(0.7F, 1.4F);
         this.dSz[i] = Mathf.random(0.8F, 2.0F);
      }

   }

   public Color color() {
      return mid;
   }

   /** True if the 3x3 area around the tile is all liquid floor (where a whirlpool is allowed to appear). */
   static boolean onLiquid(int tx, int ty) {
      for(int dx = -1; dx <= 1; ++dx) {
         for(int dy = -1; dy <= 1; ++dy) {
            Tile t = mindustry.Vars.world.tile(tx + dx, ty + dy);
            if (t == null || !t.floor().isLiquid) {
               return false;
            }
         }
      }

      return true;
   }

   public void update() {
      if (this.target != null) {
         if (!this.target.isValid()) {
            this.target = null;
         } else {
            this.phase += Time.delta / SPIRAL_TICKS;
            this.angle += (5.0F + 28.0F * this.phase) * Time.delta;
            float r = this.startR * Mathf.pow(Math.max(1.0F - this.phase, 0.0F), 1.3F);
            this.target.set(this.x + Mathf.cosDeg(this.angle) * r, this.y + Mathf.sinDeg(this.angle) * r);
            this.target.vel.setZero();
            this.target.rotation += 20.0F * Time.delta;
            if (this.phase >= 1.0F) {
               float a = Mathf.random(360.0F);
               this.target.set(this.x + Mathf.cosDeg(a) * 4.0F, this.y + Mathf.sinDeg(a) * 4.0F);
               this.target.vel.trns(a, 14.0F);
               this.target.damagePierce(20.0F);
               AnomalyFx.pulse.at(this.x, this.y, 40.0F, cyan);
               this.target = null;
               this.dead = true;
            }

         }
      } else {
         AnomalyManager.tmp.clear();
         Seq<Unit> list = AnomalyManager.tmp;
         Units.nearby(this.x - R - 16.0F, this.y - R - 16.0F, (R + 16.0F) * 2.0F, (R + 16.0F) * 2.0F, list::add);

         for(Unit u : list) {
            if (u.isValid() && !u.isFlying() && u.within(this.x, this.y, R)) {
               this.target = u;
               this.startR = Math.max(u.dst(this.x, this.y), 6.0F);
               this.angle = Angles.angle(this.x, this.y, u.x, u.y);
               this.phase = 0.0F;
               break;
            }
         }

      }
   }

   public void draw() {
      float f = this.fade();
      float t = Time.time;

      // water disc and rim
      Draw.color(mid, 0.10F * f);
      Fill.circle(this.x, this.y, R);
      Lines.stroke(1.2F);
      Draw.color(cyan, 0.18F * f);
      Lines.circle(this.x, this.y, R);

      // dark funnel: concentric rings that get darker towards the middle
      for(int i = 0; i < 4; ++i) {
         float k = (float)(i + 1) / 4.0F;
         Draw.color(deep, (0.55F - 0.38F * k) * f);
         Fill.circle(this.x, this.y, R * 0.62F * k);
      }

      Draw.blend(Blending.additive);
      // four spiral arms turning clockwise and curling into the centre
      for(int arm = 0; arm < 4; ++arm) {
         float a0 = (float)arm * 90.0F - t * 2.6F;
         float px = this.x + Mathf.cosDeg(a0) * R;
         float py = this.y + Mathf.sinDeg(a0) * R;

         for(int i = 1; i <= 16; ++i) {
            float k = (float)i / 16.0F;
            float a = a0 + k * 240.0F;
            float rr = R * (1.0F - k);
            float qx = this.x + Mathf.cosDeg(a) * rr;
            float qy = this.y + Mathf.sinDeg(a) * rr;
            Lines.stroke(2.4F * (1.0F - 0.5F * k));
            Draw.color(i % 2 == 0 ? cyan : mid, 0.34F * f * (0.35F + 0.65F * k));
            Lines.line(px, py, qx, qy);
            px = qx;
            py = qy;
         }
      }

      // foam flecks sliding down the spiral
      for(int i = 0; i < FOAM; ++i) {
         float p = (t * 0.007F * this.dSp[i] + this.dPh[i]) % 1.0F;
         float rr = 3.0F + (R - 3.0F) * (1.0F - p);
         float a = this.dAng[i] + p * 400.0F + t * 0.9F;
         float al = f * Mathf.clamp(p * 6.0F) * Mathf.clamp((1.0F - p) * 3.0F);
         Draw.color(foam, 0.9F * al);
         Fill.circle(this.x + Mathf.cosDeg(a) * rr, this.y + Mathf.sinDeg(a) * rr, this.dSz[i] * (0.5F + (1.0F - p) * 0.5F));
      }

      // bright eye
      float pulse = 0.85F + 0.15F * Mathf.sin(t / 6.0F);
      Draw.color(cyan, 0.45F * f * pulse);
      Fill.circle(this.x, this.y, 6.0F);
      Draw.color(foam, 0.9F * f);
      Fill.circle(this.x, this.y, 2.0F + Mathf.absin(t, 7.0F, 0.8F));
      Draw.blend();
      Drawf.light(this.x, this.y, 44.0F, mid, 0.3F * f);
      Draw.reset();
   }
}
