package anomalies;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.StatusEffects;
import mindustry.entities.Units;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

public class ToxicAnomaly extends Anomaly {
   static final Color sludge = Color.valueOf("2f7a1c");
   static final Color green = Color.valueOf("8cff3f");
   static final Color pale = Color.valueOf("d4ff8f");
   static final float STATUS_TIME = 300.0F;
   static final float HALF = 16.0F;
   static final float K = 3.5555556F;
   static final float SCAN = 19.5F;
   float scanTimer;

   public ToxicAnomaly() {
      this.tileRadius = 3;
   }

   public Color color() {
      return green;
   }

   public void update() {
      this.scanTimer += Time.delta;
      if (this.scanTimer >= 4.0F && this.fade() > 0.3F) {
         this.scanTimer = 0.0F;
         Units.nearby(this.x - 19.5F, this.y - 19.5F, 39.0F, 39.0F, this::poison);
      }

   }

   void poison(Unit var1) {
      if (var1.isValid() && !var1.isFlying()) {
         if (!(Math.abs(var1.x - this.x) > 16.0F) && !(Math.abs(var1.y - this.y) > 16.0F)) {
            var1.apply(StatusEffects.corroded, 300.0F);
         }
      }
   }

   static float hash(int var0) {
      double var1 = Math.sin((double)var0 * 12.9898) * 43758.5453;
      return (float)(var1 - Math.floor(var1));
   }

   public void draw() {
      float var1 = this.fade();
      float var2 = Time.time;
      Draw.blend();
      Draw.z(39.9F);
      Draw.color(sludge, 0.9F * var1);
      Fill.circle(this.x, this.y, 12.8F);

      for(int var3 = 0; var3 < 6; ++var3) {
         float var4 = (float)var3 * ((float)Math.PI / 3F) + Mathf.sin(var2 * 0.02F + (float)var3) * 0.3F;
         float var5 = (2.2F + 0.5F * Mathf.sin(var2 * 0.05F + (float)var3 * 1.7F)) * 3.5555556F;
         Fill.circle(this.x + Mathf.cos(var4) * 2.5F * 3.5555556F, this.y + Mathf.sin(var4) * 2.5F * 3.5555556F, var5);
      }

      Draw.color(green, 0.85F * var1);
      Fill.circle(this.x, this.y, (2.4F + 0.3F * Mathf.sin(var2 * 0.07F)) * 3.5555556F);

      for(int var8 = 0; var8 < 4; ++var8) {
         float var10 = (var2 * 0.018F + hash(var8 + 7)) % 1.0F;
         float var12 = this.x + (hash(var8) - 0.5F) * 5.0F * 3.5555556F;
         float var6 = this.y + (hash(var8 + 11) - 0.5F) * 5.0F * 3.5555556F;
         Draw.color(pale, (1.0F - var10) * 0.8F * var1);
         Fill.circle(var12, var6, (0.4F + var10 * 1.1F) * 3.5555556F);
      }

      Draw.z(Layer.effect);

      for(int var9 = 0; var9 < 8; ++var9) {
         float var11 = (var2 * 0.007F + hash(var9 + 20)) % 1.0F;
         float var13 = Math.min(var11 / 0.12F, 1.0F);
         float var14 = this.x + (hash(var9 + 30) - 0.5F) * 5.0F * 3.5555556F + Mathf.sin(var2 * 0.03F + (float)var9 * 2.1F) * 2.5F * 3.5555556F * var11;
         float var7 = this.y + var11 * 16.0F * 3.5555556F;
         Draw.color(var9 % 2 == 0 ? green : pale, (1.0F - var11) * var13 * 0.3F * var1);
         Fill.circle(var14, var7, (1.6F + var11 * 3.8F) * 3.5555556F);
      }

      Drawf.light(this.x, this.y, 78.22222F, green, 0.35F * var1);
      Draw.reset();
   }
}
