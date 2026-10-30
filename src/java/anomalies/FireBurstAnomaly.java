package anomalies;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.StatusEffects;
import mindustry.entities.Units;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;

public class FireBurstAnomaly extends Anomaly {
   static final Color deepRed = Color.valueOf("c8230a");
   static final Color orange = Color.valueOf("ff6a1a");
   static final Color amber = Color.valueOf("ffb03a");
   static final Color pale = Color.valueOf("fff1b0");
   static final float HALF = 8.0F;
   static final float R_TOP = 15.0F;
   static final float HOLD = 420.0F;
   static final int PARTS = 90;
   static final int EMBERS = 16;
   float spin;
   float active;
   float phase;
   float scanTimer;

   public FireBurstAnomaly() {
      this.tileRadius = 1;
   }

   public Color color() {
      return orange;
   }

   public void update() {
      float var1 = this.fade();
      if (this.active > 0.0F) {
         this.active -= Time.delta;
      }

      this.scanTimer += Time.delta;
      if (var1 > 0.9F && this.scanTimer >= 4.0F) {
         this.scanTimer = 0.0F;
         Units.nearby(this.x - 8.0F - 4.0F, this.y - 8.0F - 4.0F, 24.0F, 24.0F, this::scan);
      }

      if (this.spin > 0.4F && var1 > 0.9F) {
         Units.nearby(this.x - 15.0F, this.y - 15.0F, 30.0F, 30.0F, this::burn);
      }

      this.spin = Mathf.lerpDelta(this.spin, this.active > 0.0F ? 1.0F : 0.0F, this.active > 0.0F ? 0.035F : 0.02F);
      if (this.spin < 0.002F) {
         this.spin = 0.0F;
      }

      this.phase += Time.delta * (0.03F + this.spin * 0.16F);
   }

   // Client-side visual-only tick (no burning): update() is not run on clients, so the spin/active
   // animation never reacted to units standing in the fire.
   public void clientUpdate() {
      float var1 = this.fade();
      if (this.active > 0.0F) {
         this.active -= Time.delta;
      }

      this.scanTimer += Time.delta;
      if (var1 > 0.9F && this.scanTimer >= 4.0F) {
         this.scanTimer = 0.0F;
         Units.nearby(this.x - 8.0F - 4.0F, this.y - 8.0F - 4.0F, 24.0F, 24.0F, this::scan);
      }

      this.spin = Mathf.lerpDelta(this.spin, this.active > 0.0F ? 1.0F : 0.0F, this.active > 0.0F ? 0.035F : 0.02F);
      if (this.spin < 0.002F) {
         this.spin = 0.0F;
      }

      this.phase += Time.delta * (0.03F + this.spin * 0.16F);
   }

   void scan(Unit var1) {
      if (var1.isValid() && !var1.isFlying()) {
         if (!(Math.abs(var1.x - this.x) > 9.0F) && !(Math.abs(var1.y - this.y) > 9.0F)) {
            this.active = 420.0F;
         }
      }
   }

   void burn(Unit var1) {
      if (var1.isValid() && !var1.isFlying()) {
         if (!(Mathf.dst(var1.x, var1.y, this.x, this.y) > 15.0F)) {
            var1.apply(StatusEffects.burning, 90.0F);
         }
      }
   }

   static float hash(int var0) {
      double var1 = Math.sin((double)var0 * 12.9898) * 43758.5453;
      return (float)(var1 - Math.floor(var1));
   }

   public void draw() {
      AnomalyShaders.distort(this.x, this.y, 16.0F, this.fade(), AnomalyShaders.FIRE, 39.9F);
      float var1 = this.fade();
      float var2 = Time.time;
      Draw.z(39.9F);
      Draw.blend(Blending.additive);
      Draw.color(deepRed, 0.16F * var1);
      Fill.circle(this.x, this.y, 7.5F);
      Draw.color(orange, 0.12F * var1);
      Fill.circle(this.x, this.y, 4.5F);
      float var3 = var1 * (1.0F - this.spin * 0.55F);

      for(int var4 = 0; var4 < 16; ++var4) {
         float var5 = (var2 * (0.012F + hash(var4 + 3) * 0.012F) + hash(var4)) % 1.0F;
         float var6 = hash(var4 + 20) * ((float)Math.PI * 2F) + Mathf.sin(var2 * 0.03F + (float)var4) * 0.4F;
         float var7 = (2.0F + hash(var4 + 40) * 5.5F) * (1.0F - var5 * 0.3F);
         float var8 = this.x + Mathf.cos(var6) * var7 + Mathf.sin(var2 * 0.07F + (float)var4 * 1.7F) * 0.8F;
         float var9 = this.y + Mathf.sin(var6) * var7 * 0.8F + var5 * 6.0F;
         float var10 = (1.5F - var5) * (0.7F + hash(var4 + 60) * 0.7F);
         if (!(var10 <= 0.05F)) {
            Color var11 = var5 < 0.3F ? pale : (var5 < 0.65F ? amber : orange);
            Draw.color(var11, var3 * (1.0F - var5 * var5));
            Fill.circle(var8, var9, var10);
         }
      }

      if (this.spin > 0.001F) {
         float var20 = this.spin * var1;
         Draw.z(39.9F);
         Draw.color(deepRed, 0.25F * var20);
         Fill.circle(this.x, this.y, 9.0F + 3.0F * var20);
         Draw.color(orange, 0.22F * var20);
         Fill.circle(this.x, this.y, 5.5F + 1.5F * var20);

         for(int var21 = 0; var21 < 2; ++var21) {
            Draw.z(var21 == 0 ? 39.9F : 116.0F);

            for(int var23 = 0; var23 < 90; ++var23) {
               float var25 = (hash(var23 * 3 + 1) + var2 * (0.006F + hash(var23 + 7) * 0.006F) * (0.5F + var20)) % 1.0F;
               float var27 = (2.5F + 19.0F * var25) * (0.8F + hash(var23 + 11) * 0.35F) * (0.35F + 0.65F * var20);
               float var28 = var23 % 5 == 0 ? -0.6F : 1.0F;
               float var29 = hash(var23) * ((float)Math.PI * 2F) + this.phase * var28 * (1.6F - var25 * 0.9F) * 1.0F + var25 * 2.2F;
               float var30 = Mathf.sin(var29);
               boolean var12 = var30 < 0.0F;
               if (var21 == 1 == var12) {
                  float var13 = var25 * 0.0F * var20;
                  float var14 = this.x + Mathf.cos(var29) * var27;
                  float var15 = this.y + var30 * var27 * 0.5F + var13;
                  float var16 = (1.9F - var25 * 0.9F) * (0.75F + hash(var23 + 31) * 0.8F);
                  float var17 = var20 * (var25 < 0.12F ? var25 / 0.12F : 1.0F) * (1.0F - Mathf.clamp((var25 - 0.7F) / 0.3F));
                  if (!(var17 <= 0.01F)) {
                     Color var18 = var25 < 0.25F ? pale : (var25 < 0.55F ? amber : (var25 < 0.8F ? orange : deepRed));
                     Draw.color(var18, var17 * 0.85F);
                     Fill.circle(var14, var15, var16);
                     float var19 = var29 - 0.18F * var28;
                     Draw.color(var18, var17 * 0.4F);
                     Fill.circle(this.x + Mathf.cos(var19) * var27, this.y + Mathf.sin(var19) * var27 * 0.5F + var13 - 0.6F, var16 * 0.7F);
                  }
               }
            }
         }

         Draw.z(116.0F);

         for(int var22 = 0; var22 < 7; ++var22) {
            float var24 = (var2 * 0.02F + (float)var22 / 7.0F) % 1.0F;
            float var26 = Mathf.sin(var2 * 0.2F + (float)var22 * 2.3F) * (1.2F + var24 * 2.5F);
            Draw.color(var24 < 0.5F ? pale : amber, var20 * (1.0F - var24) * 0.7F);
            Fill.circle(this.x + var26, this.y + var24 * 18.0F * var20, (2.4F - var24 * 1.6F) * (0.5F + 0.5F * var20));
         }
      }

      Draw.blend();
      Drawf.light(this.x, this.y, 22.0F + this.spin * 28.0F, orange, (0.28F + this.spin * 0.4F) * var1);
      Draw.reset();
   }
}
