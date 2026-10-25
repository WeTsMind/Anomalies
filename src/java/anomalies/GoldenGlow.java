package anomalies;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.util.Time;
import java.util.WeakHashMap;
import mindustry.entities.Leg;
import mindustry.gen.Groups;
import mindustry.gen.Legsc;
import mindustry.gen.Unit;

final class GoldenGlow {
   static final String UNIT = "anomalies-golden-ratio";
   static Color glow;
   static Color core;
   static Color bGlow;
   static Color bBody;
   static Color bCore;
   static final float BOLT_TIME = 18.0F;
   static final WeakHashMap<Unit, float[]> shots = new WeakHashMap();

   static void shot(Unit var0, float var1, float var2, float var3) {
      try {
         float[] var4 = (float[])shots.get(var0);
         if (var4 == null) {
            var4 = new float[5];
            shots.put(var0, var4);
         }

         var4[0] = Time.time;
         var4[1] += 1.0F;
         var4[2] = var1;
         var4[3] = var2;
         var4[4] = var3;
      } catch (Throwable var5) {
      }

   }

   static float rnd(int var0) {
      double var1 = Math.sin((double)var0 * 12.9898 + 78.233) * 43758.5453;
      return (float)(var1 - Math.floor(var1));
   }

   static void draw() {
      try {
         if (glow == null) {
            glow = Color.valueOf("ffa63d");
            core = Color.valueOf("fff1c4");
            bGlow = Color.valueOf("ffd21f");
            bBody = Color.valueOf("ffe95e");
            bCore = Color.valueOf("fff8d0");
         }

         Draw.blend(Blending.additive);
         Groups.unit.each(GoldenGlow::one);
         Draw.blend();
         Draw.reset();
      } catch (Throwable var3) {
         try {
            Draw.blend();
            Draw.reset();
         } catch (Throwable var2) {
         }
      }

   }

   static void one(Unit var0) {
      try {
         if (var0 == null || var0.type == null || !"anomalies-golden-ratio".equals(var0.type.name) || !(var0 instanceof Legsc)) {
            return;
         }

         Leg[] var1 = ((Legsc)var0).legs();
         if (var1 == null) {
            return;
         }

         float var2;
         try {
            var2 = var0.type.groundLayer;
         } catch (Throwable var25) {
            var2 = 65.0F;
         }

         float var3 = var0.hitSize / 4000.0F;
         if (var3 < 0.0F) {
            var3 = 0.0F;
         }

         if (var3 > 0.01F) {
            var3 = 0.01F;
         }

         float var4 = var2 + var3 - 0.015F;
         float[] var5 = (float[])shots.get(var0);

         for(int var6 = 0; var6 < var1.length; ++var6) {
            Leg var7 = var1[var6];
            if (var7 != null && var7.base != null && var7.joint != null) {
               float var8 = var7.joint.x + (var7.base.x - var7.joint.x) * 0.55F;
               float var9 = var7.joint.y + (var7.base.y - var7.joint.y) * 0.55F;
               float var10 = 0.5F + 0.5F * (float)Math.sin((double)(Time.time * 0.12F + (float)var6 * 1.7F));
               float var11 = var7.moving ? 1.35F : 1.0F;
               Draw.z(var4);
               Draw.color(glow, (0.16F + 0.14F * var10) * var11);
               Fill.circle(var8, var9, 2.4F + 0.5F * var10);
               Draw.color(glow, 0.55F * var11);
               Fill.circle(var8, var9, 1.2F);
               Draw.color(core, 0.9F);
               Fill.circle(var8, var9, 0.55F + 0.15F * var10);
               if (var5 != null) {
                  float var12 = Time.time - var5[0];
                  if (var12 >= 0.0F && var12 < 18.0F) {
                     float var13 = 1.0F - var12 / 18.0F;
                     int var14 = (int)(var5[1] * 131.0F) + (int)(var12 / 3.0F) * 17 + var6 * 53;

                     for(int var15 = 0; var15 < 1; ++var15) {
                        float var16 = rnd(var14 + var15 * 7) * 360.0F;
                        float var17 = 4.0F + rnd(var14 + var15 * 11 + 1) * 5.0F;
                        float var18 = var8;
                        float var19 = var9;

                        for(int var20 = 1; var20 <= 3; ++var20) {
                           var16 += (rnd(var14 + var15 * 13 + var20 * 5) - 0.5F) * 70.0F;
                           double var21 = Math.toRadians((double)var16);
                           float var23 = var18 + (float)Math.cos(var21) * var17 / 3.0F;
                           float var24 = var19 + (float)Math.sin(var21) * var17 / 3.0F;
                           Draw.color(bGlow, 0.7F * var13);
                           Lines.stroke(1.7F * var13);
                           Lines.line(var18, var19, var23, var24);
                           Draw.color(bBody, 0.95F * var13);
                           Lines.stroke(0.95F * var13);
                           Lines.line(var18, var19, var23, var24);
                           Draw.color(bCore, var13);
                           Lines.stroke(0.4F * var13);
                           Lines.line(var18, var19, var23, var24);
                           var18 = var23;
                           var19 = var24;
                        }
                     }
                  }
               }
            }
         }

         if (var5 != null) {
            float var27 = Time.time - var5[0];
            if (var27 >= 0.0F && var27 < 18.0F) {
               float var28 = 1.0F - var27 / 18.0F;
               float var29 = var5[2];
               float var30 = var5[3];
               int var31 = (int)(var5[1] * 977.0F) + (int)(var27 / 3.0F) * 29;
               Draw.z(var2 + var3 + 0.02F);
               Draw.color(bBody, 0.6F * var28);
               Fill.circle(var29, var30, 3.2F * var28);
               Draw.color(bCore, 0.9F * var28);
               Fill.circle(var29, var30, 1.5F * var28);

               for(int var32 = 0; var32 < 3; ++var32) {
                  float var33 = var5[4] + (rnd(var31 + var32 * 7) - 0.5F) * 80.0F;
                  float var34 = 8.0F + rnd(var31 + var32 * 11 + 1) * 8.0F;
                  float var35 = var29;
                  float var36 = var30;

                  for(int var37 = 1; var37 <= 4; ++var37) {
                     var33 += (rnd(var31 + var32 * 13 + var37 * 5) - 0.5F) * 60.0F;
                     double var38 = Math.toRadians((double)var33);
                     float var39 = var35 + (float)Math.cos(var38) * var34 / 4.0F;
                     float var40 = var36 + (float)Math.sin(var38) * var34 / 4.0F;
                     Draw.color(bGlow, 0.7F * var28);
                     Lines.stroke(2.4F * var28);
                     Lines.line(var35, var36, var39, var40);
                     Draw.color(bBody, 0.95F * var28);
                     Lines.stroke(1.3F * var28);
                     Lines.line(var35, var36, var39, var40);
                     Draw.color(bCore, var28);
                     Lines.stroke(0.55F * var28);
                     Lines.line(var35, var36, var39, var40);
                     var35 = var39;
                     var36 = var40;
                  }
               }
            }
         }
      } catch (Throwable var26) {
      }

   }
}
