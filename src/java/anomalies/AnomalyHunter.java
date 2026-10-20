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
import mindustry.entities.units.AIController;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.type.UnitType;

public class AnomalyHunter extends UnitType {
   static final Color glow = Color.valueOf("1f6bff");
   static final Color mid = Color.valueOf("4aa3ff");
   static final Color core = Color.valueOf("d8edff");
   static final Color rift = Color.valueOf("58FFF9");
   static final Color riftCore = Color.valueOf("d2fffc");
   static final float laserRange = 110.0F;
   static final float killTime = 150.0F;
   static final float muzzle = 7.0F;
   static final float[][] coils = new float[][]{{-1.3F, 5.7F}, {1.3F, 5.7F}, {-6.7F, 0.3F}, {6.7F, 0.3F}, {-5.6F, -5.3F}, {5.6F, -5.3F}};
   static final int[][] links = new int[][]{{0, 2}, {2, 4}, {1, 3}, {3, 5}};
   static final Map<Unit, float[]> beams = new WeakHashMap();
   static TextureRegion symbol;

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
      this.engineOffset = 6.5F;
      this.engineSize = 3.2F;
      this.engineColor = Color.valueOf("7fdcff");
      this.outlines = false;
      this.outlineColor = Color.valueOf("141a2c");
   }

   public void update(Unit var1) {
      super.update(var1);

      try {
         if (!var1.isPlayer() && !(var1.controller() instanceof HunterAI)) {
            var1.controller(new HunterAI());
         }
      } catch (Throwable var3) {
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

   public void draw(Unit var1) {
      super.draw(var1);
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
         float var15 = lx(var1, 0.0F, 5.5F);
         float var17 = ly(var1, 0.0F, 5.5F);
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
         if (!(var1 instanceof TurretBall) && !AlloyUnit.alloyMirrors.contains(var1)) {
            if (!var1.dead && !(var1.life - var1.age < 60.0F)) {
               return !(var1 instanceof MirrorAnomaly) || ((MirrorAnomaly)var1).team == null;
            } else {
               return false;
            }
         } else {
            return false;
         }
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
                  this.beam = Math.min(150.0F, this.beam + Time.delta);
                  this.state[0] = this.target.x;
                  this.state[1] = this.target.y;
                  this.state[2] = Math.min(1.0F, this.beam / 20.0F);
                  AnomalyHunter.beams.put(this.unit, this.state);
               } else {
                  this.beam = Math.max(0.0F, this.beam - Time.delta * 2.0F);
                  AnomalyHunter.beams.remove(this.unit);
               }

               if (this.beam >= 150.0F) {
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
