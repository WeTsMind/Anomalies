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

public class VortexAnomaly extends Anomaly {
   static final float R = 32.0F;
   static final float SPIRAL_TICKS = 100.0F;
   static final int DEBRIS = 44;
   static final int BLOB = 14;
   static final Color gray = Color.valueOf("cfd2d6");
   static final Color light = Color.valueOf("f2f3f5");
   static final Color dark = Color.valueOf("7d8087");
   static final Color sand = Color.valueOf("d6bb80");
   final float[] dPh = new float[44];
   final float[] dAng = new float[44];
   final float[] dSp = new float[44];
   final float[] dSz = new float[44];
   final boolean[] dSand = new boolean[44];
   Unit target;
   float startR;
   float phase;
   float angle;

   public VortexAnomaly() {
      this.tileRadius = 2;

      for(int var1 = 0; var1 < 44; ++var1) {
         this.dPh[var1] = Mathf.random();
         this.dAng[var1] = Mathf.random(360.0F);
         this.dSp[var1] = Mathf.random(0.7F, 1.4F);
         this.dSz[var1] = Mathf.random(1.0F, 2.4F);
         this.dSand[var1] = Mathf.chance((double)0.5F);
      }

   }

   public Color color() {
      return gray;
   }

   public void update() {
      if (this.target != null) {
         if (!this.target.isValid()) {
            this.target = null;
         } else {
            this.phase += Time.delta / 100.0F;
            this.angle += (5.0F + 28.0F * this.phase) * Time.delta;
            float var3 = this.startR * Mathf.pow(Math.max(1.0F - this.phase, 0.0F), 1.3F);
            this.target.set(this.x + Mathf.cosDeg(this.angle) * var3, this.y + Mathf.sinDeg(this.angle) * var3);
            this.target.vel.setZero();
            Unit var5 = this.target;
            var5.rotation += 20.0F * Time.delta;
            if (this.phase >= 1.0F) {
               float var4 = Mathf.random(360.0F);
               this.target.set(this.x + Mathf.cosDeg(var4) * 4.0F, this.y + Mathf.sinDeg(var4) * 4.0F);
               this.target.vel.trns(var4, 14.0F);
               this.target.damagePierce(20.0F);
               AnomalyFx.pulse.at(this.x, this.y, 40.0F, light);
               this.target = null;
               this.dead = true;
            }

         }
      } else {
         AnomalyManager.tmp.clear();
         float var10000 = this.x - 32.0F - 16.0F;
         float var10001 = this.y - 32.0F - 16.0F;
         Seq var10004 = AnomalyManager.tmp;
         Objects.requireNonNull(var10004);
         Units.nearby(var10000, var10001, 96.0F, 96.0F, var10004::add);

         for(Unit var2 : AnomalyManager.tmp) {
            if (var2.isValid() && var2.within(this.x, this.y, 32.0F)) {
               this.target = var2;
               this.startR = Math.max(var2.dst(this.x, this.y), 6.0F);
               this.angle = Angles.angle(this.x, this.y, var2.x, var2.y);
               this.phase = 0.0F;
               break;
            }
         }

      }
   }

   public void draw() {
      AnomalyShaders.distort(this.x, this.y, 34.0F, this.fade(), AnomalyShaders.VORTEX, 88.0F);
      float var1 = this.fade();
      float var2 = Time.time;
      Draw.color(gray, 0.07F * var1);
      Lines.stroke(1.0F);
      Lines.circle(this.x, this.y, 32.0F);

      for(int var3 = 0; var3 < 4; ++var3) {
         float var4 = (float)var3 * 90.0F - var2 * 2.2F;
         float var5 = this.x + Mathf.cosDeg(var4) * 32.0F;
         float var6 = this.y + Mathf.sinDeg(var4) * 32.0F;

         for(int var7 = 1; var7 <= 14; ++var7) {
            float var8 = (float)var7 / 14.0F;
            float var9 = var4 + var8 * 220.0F;
            float var10 = 32.0F * (1.0F - var8);
            float var11 = this.x + Mathf.cosDeg(var9) * var10;
            float var12 = this.y + Mathf.sinDeg(var9) * var10;
            Draw.color(gray, 0.16F * var1 * var8);
            Lines.stroke(1.2F);
            Lines.line(var5, var6, var11, var12);
            var5 = var11;
            var6 = var12;
         }
      }

      for(int var13 = 0; var13 < 44; ++var13) {
         float var15 = (var2 * 0.006F * this.dSp[var13] + this.dPh[var13]) % 1.0F;
         float var17 = 3.0F + 29.0F * (1.0F - var15);
         float var20 = this.dAng[var13] + var15 * 420.0F + var2 * 0.8F;
         float var23 = var1 * Mathf.clamp(var15 * 6.0F) * Mathf.clamp((1.0F - var15) * 3.0F);
         float var26 = this.x + Mathf.cosDeg(var20) * var17;
         float var27 = this.y + Mathf.sinDeg(var20) * var17;
         float var28 = this.dSz[var13] * (0.5F + (1.0F - var15) * 0.6F);
         if (this.dSand[var13]) {
            Draw.color(sand, var23);
            Fill.circle(var26, var27, var28 * 0.6F);
         } else {
            Draw.color(dark, var23);
            Fill.square(var26, var27, var28, var20 * 2.0F);
         }
      }

      float[] var14 = new float[14];
      float[] var16 = new float[14];

      for(int var18 = 0; var18 < 14; ++var18) {
         float var21 = (float)var18 * 25.714285F;
         float var24 = 6.2F + Mathf.sinDeg(var21 * 3.0F + var2 * 4.0F) * 1.5F + Mathf.sinDeg(var21 * 5.0F - var2 * 6.0F) * 1.1F + Mathf.range(0.5F);
         var14[var18] = this.x + Mathf.cosDeg(var21) * var24;
         var16[var18] = this.y + Mathf.sinDeg(var21) * var24;
      }

      float var19 = Mathf.chance(0.08) ? 0.6F : 1.0F;
      Draw.color(dark, 0.75F * var1 * var19);
      Fill.circle(this.x, this.y, 8.0F);
      Draw.color(gray, var1 * var19);

      for(int var22 = 0; var22 < 14; ++var22) {
         int var25 = (var22 + 1) % 14;
         Fill.tri(this.x, this.y, var14[var22], var16[var22], var14[var25], var16[var25]);
      }

      Draw.color(light, 0.9F * var1);
      Fill.circle(this.x, this.y, 2.8F + Mathf.absin(var2, 7.0F, 1.0F));
      Drawf.light(this.x, this.y, 40.0F, gray, 0.3F * var1);
      Draw.reset();
   }
}
