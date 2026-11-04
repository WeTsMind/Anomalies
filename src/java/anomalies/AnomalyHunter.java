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
import arc.math.geom.Vec2;
import arc.util.Time;
import java.util.Map;
import java.util.WeakHashMap;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.AIController;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

public class AnomalyHunter extends UnitType {
   static final Color glow = Color.valueOf("1f6bff");
   static final Color mid = Color.valueOf("4aa3ff");
   static final Color core = Color.valueOf("d8edff");
   static final Color rift = Color.valueOf("58FFF9");
   static final Color riftCore = Color.valueOf("d2fffc");
   static final float laserRange = 110.0F;
   static final float killTime = 1160.0F;
   static final float muzzle = 6.0F;
   /** the sprites share one 256x256 canvas (4 px = 1 world unit), the unit centre is the canvas centre */
   static final float CENTER = 128.0F;
   static final float CANVAS = 64.0F;
   /** rear ends of the wings (left, right), sprite pixels (y down) */
   static final float[][] WING_TIPS = new float[][]{{82.5F, 167.5F}, {173.5F, 167.5F}};
   static final int WING_TRAIL_LEN = 34;
   static final float WING_TRAIL_HALF = 0.5F;
   /** palette of the wings: the trail is painted with it */
   static final Color trailTail = Color.valueOf("5757c1");
   static final Color trailHead = Color.valueOf("8aa3f4");
   static final Color trailCore = new Color();
   static final Color tmpTail = new Color(), tmpHead = new Color();
   static final float[] tmp = new float[2];
   static final Map<Unit, SeraphUnit.WingTrail[]> trails = new WeakHashMap();
   /** burning an anomaly by hand: while a player sits in the hunter and holds the fire button */
   static final Map<Unit, Manual> manual = new WeakHashMap();
   static final float[][] coils = new float[][]{{-1.8F, 6.5F}, {1.8F, 6.5F}, {-6.9F, 1.5F}, {6.9F, 1.5F}, {-5.6F, -3.0F}, {5.6F, -3.0F}};
   static final int[][] links = new int[][]{{0, 2}, {2, 4}, {1, 3}, {3, 5}};
   static final Map<Unit, float[]> beams = new WeakHashMap();
   static TextureRegion symbol;
   TextureRegion wingRegion;

   public AnomalyHunter(String var1) {
      super(var1);
      this.constructor = UnitEntity::create;
      this.controller = (var0) -> new HunterAI();
      this.alwaysUnlocked = true;
      this.flying = true;
      this.lowAltitude = true;
      this.health = 320.0F;
      this.speed = 2.4F;
      this.accel = 0.08F;
      this.drag = 0.05F;
      this.hitSize = 14.0F;
      this.rotateSpeed = 9.0F;
      this.range = 110.0F;
      this.maxRange = 110.0F;
      this.engineOffset = 9.0F;
      this.engineSize = 3.0F;
      this.engineColor = Color.valueOf("7fdcff");
      this.outlines = false;
      this.outlineColor = Color.valueOf("141a2c");
      this.drawCell = false;

      // invisible weapon with a harmless bullet: it only lets the game treat the fire button as "shooting" for a player inside the hunter
      Weapon fire = new Weapon();
      fire.x = 0.0F;
      fire.y = 0.0F;
      fire.mirror = false;
      fire.rotate = false;
      fire.reload = 20.0F;
      fire.shootSound = Sounds.none;
      fire.ejectEffect = Fx.none;
      fire.bullet = new BulletType(0.0F, 0.0F) {
         {
            this.lifetime = 1.0F;
            this.collides = false;
            this.hittable = false;
            this.absorbable = false;
            this.reflectable = false;
            this.despawnEffect = Fx.none;
            this.hitEffect = Fx.none;
            this.shootEffect = Fx.none;
            this.smokeEffect = Fx.none;
         }
      };
      this.weapons.add(fire);
   }

   public void load() {
      super.load();
      this.wingRegion = Core.atlas.find(this.name + "-wing");
      if (Core.atlas.has(this.name + "-icon")) {
         TextureRegion icon = Core.atlas.find(this.name + "-icon");
         this.fullIcon = icon;
         this.uiIcon = icon;
      }
   }

   /** the spent charge of a hand-held laser */
   static class Manual {
      Anomaly target;
      float beam;
      final float[] state = new float[3];
   }

   static boolean validTarget(Anomaly a) {
      if (a instanceof TurretBall || AlloyUnit.alloyMirrors.contains(a)) return false;
      if (a.dead || a.life - a.age < 60.0F) return false;
      return !(a instanceof MirrorAnomaly) || ((MirrorAnomaly)a).team == null;
   }

   static void burnOut(Anomaly a) {
      if (a instanceof MirrorAnomaly && ((MirrorAnomaly)a).team != null) {
         a.dead = true;
      } else {
         a.life = Math.min(a.life, a.age + 40.0F);
      }
   }

   /** A player inside the hunter aims with the cursor and holds the fire button: the beam burns the anomaly under the cursor. */
   static void manualUpdate(Unit unit) {
      if (Vars.net.client()) return; // the server decides, the beam reaches the clients through the usual sync
      Manual m = manual.get(unit);
      if (m == null) {
         m = new Manual();
         manual.put(unit, m);
      }
      if (m.target != null && !validTarget(m.target)) {
         m.target = null;
         m.beam = 0.0F;
      }
      Anomaly best = null;
      if (unit.isShooting) {
         float bestD = Float.MAX_VALUE;
         for (int i = 0; i < AnomalyManager.list.size; ++i) {
            Anomaly a = (Anomaly)AnomalyManager.list.get(i);
            if (!validTarget(a)) continue;
            if (Mathf.dst(unit.x, unit.y, a.x, a.y) > 121.0F) continue;
            float d = Mathf.dst(unit.aimX, unit.aimY, a.x, a.y);
            // the cursor must be on the anomaly (a little slack)
            if (d > a.tileRadius * 8.0F + 12.0F) continue;
            if (a == m.target) d -= 1000.0F; // keep burning the same one
            if (d < bestD) {
               bestD = d;
               best = a;
            }
         }
      }
      if (best != m.target) {
         m.target = best;
         m.beam = 0.0F;
      }
      if (best != null) {
         m.beam = Math.min(killTime, m.beam + Time.delta);
         m.state[0] = best.x;
         m.state[1] = best.y;
         m.state[2] = Math.min(1.0F, m.beam / 20.0F);
         beams.put(unit, m.state);
         if (m.beam >= killTime) {
            burnOut(best);
            m.beam = 0.0F;
            m.target = null;
            beams.remove(unit);
         }
      } else {
         m.beam = Math.max(0.0F, m.beam - Time.delta * 2.0F);
         beams.remove(unit);
      }
   }

   public void update(Unit var1) {
      super.update(var1);

      try {
         if (var1.isPlayer()) {
            manualUpdate(var1);
         } else {
            if (manual.remove(var1) != null) {
               beams.remove(var1);
            }
            if (!(var1.controller() instanceof HunterAI)) {
               var1.controller(new HunterAI());
            }
         }
      } catch (Throwable var3) {
      }

      if (!Vars.headless) {
         SeraphUnit.WingTrail[] t = trails.get(var1);
         if (t == null) {
            t = new SeraphUnit.WingTrail[2];
            for (int i = 0; i < 2; ++i) t[i] = new SeraphUnit.WingTrail(WING_TRAIL_LEN);
            trails.put(var1, t);
         }
         for (int i = 0; i < 2; ++i) {
            tipPoint(var1, i, tmp);
            t[i].add(tmp[0], tmp[1]);
         }
      }

   }

   static float hash(int var0) {
      double var1 = Math.sin((double)var0 * 12.9898) * 43758.5453;
      return (float)(var1 - Math.floor(var1));
   }

   static float lx(Unit var0, float var1, float var2) {
      double var3 = Math.toRadians((double)var0.rotation);
      return var0.x + (float)(Math.cos(var3) * (double)var2 + Math.sin(var3) * (double)var1);
   }

   static float ly(Unit var0, float var1, float var2) {
      double var3 = Math.toRadians((double)var0.rotation);
      return var0.y + (float)(Math.sin(var3) * (double)var2 - Math.cos(var3) * (double)var1);
   }

   static void bolt(float var0, float var1, float var2, float var3, int var4, float var5, float var6) {
      float var7 = var2 - var0;
      float var8 = var3 - var1;
      float var9 = (float)Math.sqrt((double)(var7 * var7 + var8 * var8));
      if (!(var9 < 0.01F)) {
         float var10 = -var8 / var9;
         float var11 = var7 / var9;
         byte var12 = 5;
         float var13 = var0;
         float var14 = var1;

         for(int var15 = 1; var15 <= var12; ++var15) {
            float var16 = (float)var15 / (float)var12;
            float var17 = var15 == var12 ? 0.0F : (hash(var4 + var15 * 7) - 0.5F) * 2.0F * var5;
            float var18 = var0 + var7 * var16 + var10 * var17;
            float var19 = var1 + var8 * var16 + var11 * var17;
            Lines.stroke(var6 * (0.2F + 0.8F * (float)Math.sin(Math.PI * (double)((float)var15 - 0.5F) / (double)var12)));
            Lines.line(var13, var14, var18, var19);
            var13 = var18;
            var14 = var19;
         }

      }
   }

   /** World position of a sprite pixel (y down, canvas centre = unit centre). */
   static void tipPoint(Unit unit, int i, float[] out) {
      float a = unit.rotation - 90.0F;
      float c = Mathf.cosDeg(a), s = Mathf.sinDeg(a);
      float lx = (WING_TIPS[i][0] - CENTER) / 4.0F;
      float ly = (CENTER - WING_TIPS[i][1]) / 4.0F;
      out[0] = unit.x + lx * c - ly * s;
      out[1] = unit.y + lx * s + ly * c;
   }

   /** The two wings (under the hull) and the blue ribbons behind their rear ends. */
   void drawWingsAndTrails(Unit unit) {
      float z = unit.elevation > 0.5F ? (this.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : this.groundLayer;
      SeraphUnit.WingTrail[] t = trails.get(unit);
      if (t != null) {
         Draw.z(z - 0.035F);
         Draw.blend(Blending.additive);
         for (int i = 0; i < 2; ++i) {
            tipPoint(unit, i, tmp);
            t[i].live(tmp[0], tmp[1]);
            tmpTail.set(trailTail);
            tmpHead.set(trailHead);
            trailCore.set(trailHead).lerp(Color.white, 0.6F);
            t[i].draw(tmpTail, tmpHead, WING_TRAIL_HALF, 1.0F);
            t[i].draw(tmpHead, trailCore, WING_TRAIL_HALF * 0.5F, 1.0F);
         }
         Draw.blend();
         Draw.reset();
      }
      if (this.wingRegion != null && this.wingRegion.found()) {
         Draw.z(z - 0.03F);
         Draw.color(Color.white);
         Draw.rect(this.wingRegion, unit.x, unit.y, CANVAS, CANVAS, unit.rotation - 90.0F);
         Draw.reset();
      }
   }

   /** no shadow and no dark soft blob under the unit */
   @Override
   public void drawShadow(Unit unit) {
   }

   public void drawSoftShadow(Unit unit) {
   }

   public void drawSoftShadow(float x, float y, float rotation, float alpha) {
   }

   public void draw(Unit var1) {
      this.drawWingsAndTrails(var1);
      TurretPatch.shadowOff();
      try {
         super.draw(var1);
      } finally {
         TurretPatch.shadowOn();
      }
      float[] var2 = (float[])beams.get(var1);
      float var3 = var2 == null ? 0.0F : var2[2];
      Draw.z(116.0F);
      Draw.blend(Blending.additive);
      int var4 = (int)(Time.time / 3.0F);
      float var5 = 0.7F + 0.3F * hash(var4 * 7 + var1.id);
      float var6 = (0.5F + 0.5F * var3) * var5;

      for(int var7 = 0; var7 < links.length; ++var7) {
         float[] var8 = coils[links[var7][0]];
         float[] var9 = coils[links[var7][1]];
         float var10 = lx(var1, var8[0], var8[1]);
         float var11 = ly(var1, var8[0], var8[1]);
         float var12 = lx(var1, var9[0], var9[1]);
         float var13 = ly(var1, var9[0], var9[1]);
         int var14 = var4 * 31 + var7 * 101 + var1.id * 13;
         Draw.color(rift, 0.25F * var6);
         bolt(var10, var11, var12, var13, var14, 1.1F, 2.4F);
         Draw.color(rift, 0.8F * var6);
         bolt(var10, var11, var12, var13, var14, 1.1F, 1.2F);
         Draw.color(riftCore, var6);
         bolt(var10, var11, var12, var13, var14, 1.1F, 0.5F);
      }

      if (var2 != null && var3 > 0.01F) {
         float var15 = lx(var1, 0.0F, muzzle);
         float var17 = ly(var1, 0.0F, muzzle);
         float var18 = 0.8F + Mathf.absin(Time.time, 3.0F, 0.2F);
         float[] var19 = new float[]{6.0F, 3.2F, 1.4F};
         float[] var20 = new float[]{0.25F, 0.75F, 1.0F};

         for(int var21 = 0; var21 < 3; ++var21) {
            Lines.stroke(var19[var21] * (0.5F + 0.5F * var3) * var18);
            Draw.color(var21 == 2 ? core : (var21 == 1 ? mid : glow), var20[var21] * var3);
            Lines.line(var15, var17, var2[0], var2[1]);
         }

         Draw.color(glow, 0.35F * var3 * var18);
         Fill.circle(var2[0], var2[1], 7.0F * var3);
         Draw.color(core, var3);
         Fill.circle(var2[0], var2[1], 2.6F * var3);
         Draw.color(mid, 0.6F * var3);
         Fill.circle(var15, var17, 3.0F * var3);
      }

      Draw.blend();
      Draw.reset();
      if (symbol == null) {
         symbol = Core.atlas.find(var1.type.name + "-symbol");
      }

      Draw.z(117.0F);
      Draw.blend(Blending.additive);
      float var16 = 0.85F + 0.15F * Mathf.absin(Time.time, 20.0F, 1.0F);
      Draw.color(rift, 0.22F * var16);
      Draw.rect(symbol, var1.x, var1.y, 19.0F, 19.0F, Time.time * 2.0F);
      Draw.color(riftCore, 0.45F * var16);
      Draw.rect(symbol, var1.x, var1.y, 14.0F, 14.0F, Time.time * 2.0F);
      Draw.blend();
      Draw.reset();
   }

   public static class HunterAI extends AIController {
      final Vec2 tp = new Vec2();
      final float[] state = new float[3];
      Anomaly target;
      float beam;

      boolean valid(Anomaly var1) {
         return AnomalyHunter.validTarget(var1);
      }

      public void updateUnit() {
         if (this.unit != null) {
            if (this.target == null || !this.valid(this.target)) {
               this.target = null;
               float var2 = Float.MAX_VALUE;

               for(int var3 = 0; var3 < AnomalyManager.list.size; ++var3) {
                  Anomaly var5 = (Anomaly)AnomalyManager.list.get(var3);
                  float var4;
                  if (this.valid(var5) && (var4 = Mathf.dst(this.unit.x, this.unit.y, var5.x, var5.y)) < var2) {
                     var2 = var4;
                     this.target = var5;
                  }
               }

               this.beam = 0.0F;
            }

            if (this.target == null) {
               AnomalyHunter.beams.remove(this.unit);
            } else {
               this.tp.set(this.target.x, this.target.y);
               float var6 = Mathf.dst(this.unit.x, this.unit.y, this.target.x, this.target.y);
               this.moveTo(this.tp, 77.0F);
               float var7 = Angles.angle(this.unit.x, this.unit.y, this.target.x, this.target.y);
               this.unit.rotation = Angles.moveToward(this.unit.rotation, var7, this.unit.type.rotateSpeed * Time.delta);
               boolean var1 = var6 <= 121.0F && Angles.angleDist(this.unit.rotation, var7) < 25.0F;
               if (var1) {
                  this.beam = Math.min(AnomalyHunter.killTime, this.beam + Time.delta);
                  this.state[0] = this.target.x;
                  this.state[1] = this.target.y;
                  this.state[2] = Math.min(1.0F, this.beam / 20.0F);
                  AnomalyHunter.beams.put(this.unit, this.state);
               } else {
                  this.beam = Math.max(0.0F, this.beam - Time.delta * 2.0F);
                  AnomalyHunter.beams.remove(this.unit);
               }

               if (this.beam >= AnomalyHunter.killTime) {
                  if (this.target instanceof MirrorAnomaly && ((MirrorAnomaly)this.target).team != null) {
                     this.target.dead = true;
                  } else {
                     this.target.life = Math.min(this.target.life, this.target.age + 40.0F);
                  }

                  this.beam = 0.0F;
                  AnomalyHunter.beams.remove(this.unit);
                  this.target = null;
               }

            }
         }
      }
   }
}
