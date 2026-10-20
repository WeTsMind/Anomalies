package anomalies;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import java.util.ArrayList;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;

public class BallLightningAnomaly extends Anomaly {
   static final Color glow = Color.valueOf("ffd21f");
   static final Color body = Color.valueOf("ffe95e");
   static final Color core = Color.valueOf("fff8d0");
   static final float damage = 150.0F;
   static final float interval = 40.0F;
   static final float strikeTime = 14.0F;
   static final int MAXB = 9;
   final float s;
   final int R;
   final float half;
   float cd;
   float act;
   int salt;
   final float[] sx;
   final float[] sy;
   final float[] st;
   final int[] sd;
   static final float[] SW = new float[]{4.5F, 2.6F, 1.5F, 0.8F};
   static final float[] AL = new float[]{0.12F, 0.3F, 0.7F, 1.0F};

   public BallLightningAnomaly() {
      this(1.0F);
   }

   public BallLightningAnomaly(float var1) {
      this.cd = 20.0F;
      this.sx = new float[9];
      this.sy = new float[9];
      this.st = new float[9];
      this.sd = new int[9];
      this.s = var1;
      this.R = Math.max(1, Math.round(var1));
      this.half = ((float)this.R + 0.5F) * 8.0F;
      this.tileRadius = Math.max(2, this.R + 1);
   }

   public Color color() {
      return glow;
   }

   public void update() {
      for(int var1 = 0; var1 < 9; ++var1) {
         if (this.st[var1] > 0.0F) {
            float[] var10000 = this.st;
            var10000[var1] -= Time.delta;
         }
      }

      this.act = Math.max(0.0F, this.act - Time.delta / 60.0F);
      this.cd -= Time.delta;
      if (!(this.cd > 0.0F)) {
         int var11 = Math.round(this.x / 8.0F);
         int var2 = Math.round(this.y / 8.0F);
         boolean var3 = Vars.world.build(var11, var2) != null;
         if (!var3) {
            boolean[] var4 = new boolean[]{false};
            Units.nearby((float)var11 * 8.0F - 4.0F, (float)var2 * 8.0F - 4.0F, 8.0F, 8.0F, (var1x) -> {
               if (var1x.isValid()) {
                  var4[0] = true;
               }

            });
            var3 = var4[0];
         }

         if (!var3) {
            this.cd = 5.0F;
         } else {
            this.act = 1.0F;
            ArrayList var12 = new ArrayList();
            Units.nearby((float)var11 * 8.0F - this.half, (float)var2 * 8.0F - this.half, this.half * 2.0F, this.half * 2.0F, (var1x) -> {
               if (var1x.isValid()) {
                  var12.add(var1x);
               }

            });

            for(int var5 = -this.R; var5 <= this.R; ++var5) {
               for(int var6 = -this.R; var6 <= this.R; ++var6) {
                  Building var7 = Vars.world.build(var11 + var5, var2 + var6);
                  if (var7 != null && !var12.contains(var7)) {
                     var12.add(var7);
                  }
               }
            }

            if (var12.isEmpty()) {
               this.cd = 5.0F;
            } else {
               this.cd = 40.0F;
               int var13 = Math.min(var12.size(), (1 + Mathf.random(2)) * this.R);

               for(int var14 = 0; var14 < var13; ++var14) {
                  Object var15 = var12.remove(Mathf.random(var12.size() - 1));
                  float var8;
                  float var9;
                  if (var15 instanceof Building) {
                     Building var10 = (Building)var15;
                     var8 = var10.x;
                     var9 = var10.y;
                     var10.damage(150.0F);
                  } else {
                     Unit var16 = (Unit)var15;
                     var8 = var16.x;
                     var9 = var16.y;
                     var16.damagePierce(150.0F);
                  }

                  this.sx[var14] = var8;
                  this.sy[var14] = var9;
                  this.st[var14] = 14.0F;
                  this.sd[var14] = this.salt += 977;
               }

            }
         }
      }
   }

   static float hash(int var0) {
      double var1 = Math.sin((double)var0 * 12.9898) * 43758.5453;
      return (float)(var1 - Math.floor(var1));
   }

   void bolt(float var1, float var2, float var3, float var4, int var5, float var6, float var7) {
      float var8 = var3 - var1;
      float var9 = var4 - var2;
      float var10 = (float)Math.sqrt((double)(var8 * var8 + var9 * var9));
      if (!(var10 < 0.01F)) {
         float var11 = -var9 / var10;
         float var12 = var8 / var10;
         float var13 = (float)Math.atan2((double)var9, (double)var8);
         int var14 = Math.max(4, (int)(var10 / 5.0F));
         float var15 = var1;
         float var16 = var2;

         for(int var17 = 1; var17 <= var14; ++var17) {
            float var18 = (float)var17 / (float)var14;
            float var19 = var17 == var14 ? 0.0F : (hash(var5 + var17 * 7) - 0.5F) * 2.0F * var6;
            float var20 = var1 + var8 * var18 + var11 * var19;
            float var21 = var2 + var9 * var18 + var12 * var19;
            float var22 = var7 * (0.15F + 0.85F * (float)Math.sin(Math.PI * (double)((float)var17 - 0.5F) / (double)var14));
            Lines.stroke(var22);
            Lines.line(var15, var16, var20, var21);
            if (var17 > 1 && var17 < var14 && hash(var5 + var17 * 31) > 0.72F) {
               float var23 = var13 + (hash(var5 + var17 * 5) > 0.5F ? 0.9F : -0.9F) + (hash(var5 + var17 * 3) - 0.5F) * 0.5F;
               float var24 = var10 / (float)var14 * (1.5F + hash(var5 + var17 * 11) * 1.5F);
               Lines.stroke(var22 * 0.6F);
               Lines.line(var20, var21, var20 + (float)Math.cos((double)var23) * var24, var21 + (float)Math.sin((double)var23) * var24);
            }

            var15 = var20;
            var16 = var21;
         }

      }
   }

   void layered(float var1, float var2, float var3, float var4, int var5, float var6, float var7) {
      for(int var8 = 0; var8 < 4; ++var8) {
         Draw.color(var8 == 3 ? core : (var8 == 2 ? body : glow), AL[var8] * var7);
         this.bolt(var1, var2, var3, var4, var5, var6, SW[var8]);
      }

   }

   public void draw() {
      float var1 = this.fade();
      float var2 = Time.time;
      float var3 = 0.8F + 0.2F * (float)Math.sin((double)(var2 * 0.3F));
      float var4 = (float)Math.round(this.x / 8.0F) * 8.0F;
      float var5 = (float)Math.round(this.y / 8.0F) * 8.0F;
      float var6 = 1.0F - this.act;
      int var7 = (int)(var2 / (this.act > 0.5F ? 3.0F : 9.0F));
      float var8 = 0.7F + 0.3F * var6 + (0.3F - 0.3F * var6) * hash(var7 * 7 + 1);
      float var9 = 0.45F + 0.55F * this.act;
      Draw.blend(Blending.additive);
      Lines.stroke(1.0F);
      Draw.color(glow, (0.2F + 0.2F * this.act) * var1 * var3);
      Lines.line(var4 - 4.0F, var5 - 4.0F, var4 + 4.0F, var5 - 4.0F);
      Lines.line(var4 + 4.0F, var5 - 4.0F, var4 + 4.0F, var5 + 4.0F);
      Lines.line(var4 + 4.0F, var5 + 4.0F, var4 - 4.0F, var5 + 4.0F);
      Lines.line(var4 - 4.0F, var5 + 4.0F, var4 - 4.0F, var5 - 4.0F);
      float var10 = 0.9F + 0.1F * (float)Math.sin((double)(var2 * 0.08F));
      Draw.color(glow, (0.15F + 0.15F * this.act) * var1 * var3);
      Fill.circle(var4, var5, 6.5F * var10 * this.s);
      Draw.color(body, (0.4F + 0.3F * this.act) * var1);
      Fill.circle(var4, var5, 4.0F * var10 * this.s);
      Draw.color(core, (0.75F + 0.25F * this.act) * var1);
      Fill.circle(var4, var5, 2.2F * var10 * this.s);

      for(int var11 = 0; var11 < 6; ++var11) {
         int var12 = var7 * 31 + var11 * 211;
         if (!(hash(var12) < 0.25F)) {
            float var13 = var11 < 2 ? 1.0F : this.act;
            if (!(var13 < 0.05F)) {
               float var14 = hash(var12 + 1) * ((float)Math.PI * 2F);
               float var15 = (9.0F + hash(var12 + 2) * 11.0F) * (0.6F + 0.4F * this.act) * this.s;
               this.layered(var4, var5, var4 + (float)Math.cos((double)var14) * var15, var5 + (float)Math.sin((double)var14) * var15, var12 + 5, 3.5F, var1 * var8 * var9 * var13);
            }
         }
      }

      for(int var16 = 0; var16 < 9; ++var16) {
         if (!(this.st[var16] <= 0.0F)) {
            float var17 = this.st[var16] / 14.0F;
            int var18 = this.sd[var16] + var7 * 13;
            this.layered(var4, var5, this.sx[var16], this.sy[var16], var18, 6.0F * this.s, var1 * var8 * Math.min(1.0F, var17 * 1.5F));
            Draw.color(glow, 0.5F * var17 * var1);
            Fill.circle(this.sx[var16], this.sy[var16], 6.0F * var17);
            Draw.color(core, var17 * var1);
            Fill.circle(this.sx[var16], this.sy[var16], 2.5F * var17);
         }
      }

      Draw.blend();
      Drawf.light(var4, var5, 30.0F * this.s, glow, (0.35F + 0.25F * this.act) * var1);
      Draw.reset();
   }
}
