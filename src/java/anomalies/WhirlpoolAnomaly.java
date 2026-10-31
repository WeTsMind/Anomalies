package anomalies;

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
 * Whirlpool: like the Vortex ("Выверт"), but it only appears on liquid tiles. It looks like a small spot of white waves
 * running around the centre (2x2 tiles). A ground or naval unit that comes within reach is grabbed, spun down the funnel
 * and thrown out at the end, and the whirlpool is gone.
 * The capture logic only runs on the server / single player (like every other anomaly); the picture is drawn on every client.
 */
public class WhirlpoolAnomaly extends Anomaly {
   /** grab radius, world units */
   static final float R = 10.0F;
   /** radius of the picture: 2x2 tiles = 16 world units across */
   static final float VIS = 8.0F;
   static final float SPIRAL_TICKS = 100.0F;
   /** rings of waves: radius, number of waves, length of a wave in degrees, speed in degrees per tick (clockwise, the inner rings are faster) */
   static final float[] RING_R = {1.8F, 3.6F, 5.4F};
   static final int[] RING_N = {2, 3, 4};
   static final float[] RING_LEN = {100.0F, 80.0F, 62.0F};
   static final float[] RING_SPEED = {5.4F, 3.7F, 2.6F};
   static final int SEGS = 9;
   static final Color deep = Color.valueOf("0d3b8c");
   static final Color mid = Color.valueOf("2f84e0");
   static final Color cyan = Color.valueOf("7fe3ff");
   static final Color foam = Color.valueOf("f2fcff");
   Unit target;
   float startR;
   float phase;
   float angle;

   public WhirlpoolAnomaly() {
      this.tileRadius = 1;
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
      AnomalyShaders.distort(this.x, this.y, 10.0F, this.fade(), AnomalyShaders.WHIRLPOOL, 88.0F);
      float f = this.fade();
      if (AnomalyShaders.usable()) {
         f *= 0.7F; // semi-transparent: the twisted liquid underneath must stay visible
      }
      float t = Time.time;

      // only white waves, no pool: small curved crests with a wavy edge, running clockwise around the centre (2x2 tiles)
      for(int ring = 0; ring < RING_R.length; ++ring) {
         for(int j = 0; j < RING_N[ring]; ++j) {
            float head = ring * 41.0F + j * (360.0F / RING_N[ring]) - t * RING_SPEED[ring];
            float breathe = 0.3F * Mathf.sin(t * 0.05F + ring * 1.9F + j * 2.3F);
            float px = 0.0F, py = 0.0F;

            for(int i = 0; i <= SEGS; ++i) {
               float k = (float)i / SEGS; // 0 = head, 1 = tail
               float a = head + k * RING_LEN[ring]; // the tail trails behind the head
               // the crest itself ripples (a small wave along the wave), and its tail drifts outwards like a piece of a spiral
               float rr = RING_R[ring] + breathe + k * 0.8F + 0.35F * Mathf.sin(k * 9.0F - t * 0.18F + j * 1.7F);
               float qx = this.x + Mathf.cosDeg(a) * rr;
               float qy = this.y + Mathf.sinDeg(a) * rr;
               if (i > 0) {
                  float al = (1.0F - k) * (1.0F - k * 0.3F);
                  // soft wide underlay, then a bright white core
                  Lines.stroke(1.5F + 1.2F * (1.0F - k));
                  Draw.color(foam, (0.06F + 0.16F * al) * f);
                  Lines.line(px, py, qx, qy);
                  Lines.stroke(0.7F + 0.9F * (1.0F - k));
                  Draw.color(Color.white, (0.15F + 0.85F * al) * f);
                  Lines.line(px, py, qx, qy);
               }

               px = qx;
               py = qy;
            }

            // a tiny foam bead at the head of every crest
            Draw.color(Color.white, 0.9F * f);
            Fill.circle(this.x + Mathf.cosDeg(head) * (RING_R[ring] + breathe), this.y + Mathf.sinDeg(head) * (RING_R[ring] + breathe), 0.7F);
         }
      }

      Drawf.light(this.x, this.y, 22.0F, cyan, 0.12F * f);
      Draw.reset();
   }
}
