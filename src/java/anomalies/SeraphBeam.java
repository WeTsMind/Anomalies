package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.util.Time;
import arc.util.Tmp;
import java.util.Map;
import java.util.WeakHashMap;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.core.World;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

/**
 * The Seraph's purple laser (same look and shock damage as the Laser Block). The weapon fires one of these every
 * {@link #pulse} ticks while the player holds fire; each one follows the unit, ends at the aimed point (or at the
 * maximum length), shocks every enemy unit and building under it and, if it ends on an anomaly, burns it:
 * 2.5 minutes of holding the laser on an anomaly removes it.
 *
 * Multiplayer: the beam is spawned and drawn on every peer, damage and anomaly burning are applied only where
 * {@code !Vars.net.client()}, exactly like the Laser Block.
 */
public class SeraphBeam extends BulletType {
   /** Maximum laser length, world units (about 16 tiles). */
   static final float LEN = 130.0F;
   /** Distance from the unit centre to the laser start, world units. */
   static final float MUZZLE = 8.0F;
   /** Ticks between two damage pulses (same as the Laser Block). */
   static final float pulse = 6.0F;
   /** Ticks of holding the laser on an anomaly until it disappears: 2.5 minutes. */
   static final float burnTime = 9000.0F;
   static final Map<Anomaly, float[]> burn = new WeakHashMap();
   static TextureRegion beamRegion;
   static TextureRegion orbRegion;

   public SeraphBeam() {
      super(0.0F, 0.0F);
      this.lifetime = 7.0F;
      this.collides = false;
      this.collidesTiles = false;
      this.hittable = false;
      this.absorbable = false;
      this.reflectable = false;
      this.keepVelocity = false;
      this.hitSize = 0.0F;
      this.drawSize = 360.0F;
      this.shootEffect = Fx.none;
      this.smokeEffect = Fx.none;
      this.hitEffect = Fx.none;
      this.despawnEffect = Fx.none;
   }

   public float calculateRange() {
      return LEN;
   }

   /** Laser start (0,1) and end (2,3) for a unit, from its current position, rotation and aim. */
   static void ends(Unit u, float[] out) {
      float sx = u.x + Angles.trnsx(u.rotation - 90.0F, 0.0F, MUZZLE);
      float sy = u.y + Angles.trnsy(u.rotation - 90.0F, 0.0F, MUZZLE);
      float ang = Angles.angle(sx, sy, u.aimX(), u.aimY());
      float len = Mathf.clamp(Mathf.dst(sx, sy, u.aimX(), u.aimY()), 10.0F, LEN);
      out[0] = sx;
      out[1] = sy;
      out[2] = sx + Angles.trnsx(ang, len);
      out[3] = sy + Angles.trnsy(ang, len);
   }

   public void init(Bullet b) {
      super.init(b);
      if (b.owner instanceof Unit) {
         float[] e = new float[4];
         ends((Unit)b.owner, e);
         boolean deal = !Vars.net.client();
         zap(b.team, e[0], e[1], e[2], e[3], deal);
         if (deal) burn(e[2], e[3]);
      }
   }

   public void update(Bullet b) {
      super.update(b);
      if (b.owner instanceof Unit && ((Unit)b.owner).isValid()) {
         float[] e = new float[4];
         ends((Unit)b.owner, e);
         b.set(e[0], e[1]);
         b.rotation(Angles.angle(e[0], e[1], e[2], e[3]));
         b.fdata = Mathf.dst(e[0], e[1], e[2], e[3]);
      } else {
         b.remove();
      }
   }

   /** Shocks every enemy unit and building under the segment. Same rules as the Laser Block. */
   static void zap(final Team me, final float x1, final float y1, final float x2, final float y2, final boolean deal) {
      float pad = 24.0F;
      Tmp.r1.set(Math.min(x1, x2) - pad, Math.min(y1, y2) - pad, Math.abs(x2 - x1) + pad * 2.0F, Math.abs(y2 - y1) + pad * 2.0F);
      Units.nearbyEnemies(me, Tmp.r1, (u) -> {
         if (u.dead() || u.team == Team.derelict || !u.targetable(me)) return;
         if (Intersector.distanceSegmentPoint(x1, y1, x2, y2, u.x, u.y) > u.hitSize / 2.0F + LaserBlock.beamHit) return;
         LaserBlock.hitFx.at(u.x, u.y);
         if (deal) {
            u.damage(LaserBlock.unitDamage);
            u.apply(StatusEffects.shocked, 40.0F);
         }
      });

      LaserBlock.seen.clear();
      LaserBlock.hits.clear();
      World.raycastEachWorld(x1, y1, x2, y2, (cx, cy) -> {
         Building b = Vars.world.build(cx, cy);
         if (b != null && b.team != me && b.team != Team.derelict && LaserBlock.seen.add(b.pos())) {
            LaserBlock.hits.add(b);
         }
         return false;
      });
      for (int i = 0; i < LaserBlock.hits.size; ++i) {
         Building b = LaserBlock.hits.get(i);
         LaserBlock.hitFx.at(b.x, b.y);
         if (deal) b.damage(me, LaserBlock.buildDamage);
      }
      LaserBlock.hits.clear();
   }

   /** Same targets the Anomaly Hunter may burn: natural anomalies only (no ball lightning, no Alloy / projector mirrors). */
   static boolean valid(Anomaly a) {
      if (a instanceof TurretBall || AlloyUnit.alloyMirrors.contains(a)) return false;
      if (a.dead || a.life - a.age < 60.0F) return false;
      return !(a instanceof MirrorAnomaly) || ((MirrorAnomaly)a).team == null;
   }

   /** Called once per laser pulse with the laser end point; accumulates the time the laser is held on an anomaly. */
   static void burn(float ex, float ey) {
      for (int i = 0; i < AnomalyManager.list.size; ++i) {
         Anomaly a = AnomalyManager.list.get(i);
         if (!valid(a)) continue;
         float r = (float)a.tileRadius * 8.0F;
         if (Math.abs(ex - a.x) > r || Math.abs(ey - a.y) > r) continue;
         float[] s = burn.get(a);
         if (s == null) {
            s = new float[]{0.0F, Time.time};
            burn.put(a, s);
         }
         // progress drains twice as fast as it fills while the laser is off the anomaly
         float gap = Time.time - s[1] - pulse;
         if (gap > 2.0F) s[0] = Math.max(0.0F, s[0] - gap * 2.0F);
         s[0] += pulse;
         s[1] = Time.time;
         if (s[0] >= burnTime) {
            a.life = Math.min(a.life, a.age + 40.0F);
            burn.remove(a);
         }
      }
   }

   public void draw(Bullet b) {
      if (beamRegion == null) {
         beamRegion = Core.atlas.find("anomalies-laser-block-beam");
         orbRegion = Core.atlas.find("anomalies-laser-block-orb");
      }

      float len = b.fdata;
      if (len < 0.5F) return;
      float p = Mathf.clamp(b.fout() * 4.0F);
      float x1 = b.x;
      float y1 = b.y;
      float ang = b.rotation();
      float x2 = x1 + Angles.trnsx(ang, len);
      float y2 = y1 + Angles.trnsy(ang, len);
      float pulse = 0.8F + 0.2F * Mathf.sin(Time.time / 11.0F + (float)b.id * 0.37F);
      float w = 6.5F + Mathf.absin(Time.time, 3.0F, 0.8F);
      Color col = LaserBlock.col;
      Color hot = LaserBlock.hot;

      Draw.z(Layer.flyingUnit + 0.1F);
      // glow: wide soft additive strokes around the beam
      Draw.blend(Blending.additive);
      Lines.stroke(w * 3.6F);
      Draw.color(col, 0.10F * p * pulse);
      Lines.line(x1, y1, x2, y2);
      Lines.stroke(w * 2.2F);
      Draw.color(col, 0.20F * p * pulse);
      Lines.line(x1, y1, x2, y2);
      Draw.blend();

      // body: the laser sprite stretched along the beam
      Draw.color(Color.white, p);
      Draw.rect(beamRegion, (x1 + x2) / 2.0F, (y1 + y2) / 2.0F, len, w, ang);

      Draw.blend(Blending.additive);
      // hot core
      Lines.stroke(1.5F);
      Draw.color(hot, 0.85F * p);
      Lines.line(x1, y1, x2, y2);

      // jittering electric arcs along the beam
      Mathf.randomSeed((long)b.id * 131L + (long)(Time.time / 2.5F));
      int segs = Math.max(2, (int)(len / 9.0F));
      float nx = -Mathf.sinDeg(ang);
      float ny = Mathf.cosDeg(ang);
      Lines.stroke(1.0F);
      for (int a = 0; a < 2; ++a) {
         float px = x1;
         float py = y1;
         Draw.color(a == 0 ? hot : col, (a == 0 ? 0.8F : 0.6F) * p);
         for (int i = 1; i <= segs; ++i) {
            float f = (float)i / (float)segs;
            float taper = Math.min(1.0F, Math.min(f, 1.0F - f) * 6.0F);
            float off = i == segs ? 0.0F : Mathf.range(4.5F) * taper;
            float qx = x1 + (x2 - x1) * f + nx * off;
            float qy = y1 + (y2 - y1) * f + ny * off;
            Lines.line(px, py, qx, qy);
            px = qx;
            py = qy;
         }
      }

      // orbs on both ends: the start and the end of the laser
      Draw.color(Color.white, p);
      Draw.blend();
      float os = 5.5F * pulse;
      Draw.rect(orbRegion, x1, y1, os, os);
      Draw.rect(orbRegion, x2, y2, os, os);
      Draw.blend(Blending.additive);
      Fill.light(x2, y2, 20, 12.0F * pulse, Tmp.c1.set(col).a(0.55F * p), Tmp.c2.set(col).a(0.0F));
      Draw.blend();

      Drawf.light(x1, y1, x2, y2, 44.0F, col, 0.6F * p * pulse);
      Draw.reset();
   }
}
