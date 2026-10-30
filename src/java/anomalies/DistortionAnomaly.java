package anomalies;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import java.util.Objects;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;

public class DistortionAnomaly extends Anomaly {
   static final float R = 16.0F;
   static final Color violet = Color.valueOf("a65cff");
   static final Color pale = Color.valueOf("e7d4ff");

   public DistortionAnomaly() {
      this.tileRadius = 2;
   }

   public Color color() {
      return violet;
   }

   /**
    * A remote player's unit is client-authoritative: whatever the server does to its velocity is overwritten by the
    * client's next snapshot and only arrives back as a delayed push, so the player could walk into the field and
    * then get thrown out a moment later. So the owning client pushes its own unit (no delay) and the server skips it.
    */
   boolean ownedByRemotePlayer(Unit var1) {
      try {
         return Vars.net.server() && var1.isPlayer() && var1.getPlayer() != null && !var1.getPlayer().isLocal();
      } catch (Throwable var2) {
         return false;
      }
   }

   void push(Unit var2) {
      float var3 = 16.0F + var2.hitSize / 2.0F;
      float var4 = var2.dst(this.x, this.y);
      if (!(var4 > var3) && var2.isValid()) {
         float var5 = 0.9F + (1.0F - var4 / var3) * 1.6F;
         Tmp.v1.set(var2.x - this.x, var2.y - this.y);
         if (Tmp.v1.isZero()) {
            Tmp.v1.set(1.0F, 0.0F).rotate(Mathf.random(360.0F));
         }

         Tmp.v1.nor().scl(var5 * Time.delta);
         var2.vel.add(Tmp.v1);
      }
   }

   /** Client: push only the local player's own unit, every frame, exactly like the server would. */
   public void clientUpdate() {
      Unit var1 = Vars.player == null ? null : Vars.player.unit();
      if (var1 != null && var1.isValid()) {
         this.push(var1);
      }
   }

   public void update() {
      AnomalyManager.tmp.clear();
      float var10000 = this.x - 16.0F - 16.0F;
      float var10001 = this.y - 16.0F - 16.0F;
      Seq var10004 = AnomalyManager.tmp;
      Objects.requireNonNull(var10004);
      Units.nearby(var10000, var10001, 64.0F, 64.0F, var10004::add);

      for(Unit var2 : AnomalyManager.tmp) {
         if (!this.ownedByRemotePlayer(var2)) {
            this.push(var2);
         }
      }

   }

   public void draw() {
      float var1 = this.fade();
      float var2 = Time.time;
      boolean fx = AnomalyShaders.usable();
      if (fx) {
         // real space distortion: re-draw the frame behind the anomaly through the lens shader
         final float cx = this.x;
         final float cy = this.y;
         final float f = var1;
         Draw.draw(110.0F, () -> AnomalyShaders.drawDistortion(cx, cy, 18.0F, f));
         Draw.z(110.01F);
      }

      Draw.color(violet, (fx ? 0.03F : 0.14F) * var1);
      Fill.circle(this.x, this.y, 16.0F);
      Draw.blend(Blending.additive);

      for(int var3 = 0; var3 < 4; ++var3) {
         float var4 = (var2 / 90.0F + (float)var3 / 4.0F) % 1.0F;
         Draw.color(violet, var1 * (1.0F - var4) * 0.55F);
         Lines.stroke(1.4F);
         Lines.circle(this.x, this.y, 16.0F * (1.0F - var4) + 1.0F);
      }

      for(int var12 = 0; var12 < 3; ++var12) {
         float var14 = (float)var12 * 120.0F + var2 * 1.6F;
         float var5 = this.x;
         float var6 = this.y;

         for(int var7 = 1; var7 <= 12; ++var7) {
            float var8 = (float)var7 / 12.0F;
            float var9 = var14 + var8 * 160.0F;
            float var10 = this.x + Mathf.cosDeg(var9) * var8 * 16.0F;
            float var11 = this.y + Mathf.sinDeg(var9) * var8 * 16.0F;
            Draw.color(pale, var1 * (1.0F - var8 * 0.6F) * 0.6F);
            Lines.stroke(1.8F * (1.0F - var8 * 0.5F));
            Lines.line(var5, var6, var10, var11);
            var5 = var10;
            var6 = var11;
         }
      }

      byte var13 = 48;
      float var15 = 0.0F;
      float var16 = 0.0F;

      for(int var17 = 0; var17 < 2; ++var17) {
         Lines.stroke(var17 == 0 ? 4.0F : 1.3F);
         Draw.color(var17 == 0 ? violet : pale, var1 * (var17 == 0 ? 0.25F : 0.9F));

         for(int var18 = 0; var18 <= var13; ++var18) {
            float var19 = (float)var18 / (float)var13 * 360.0F;
            float var20 = 16.0F + Mathf.sinDeg(var19 * 6.0F + var2 * 2.0F) * 1.3F + Mathf.sinDeg(var19 * 3.0F - var2 * 1.3F) * 1.1F;
            float var21 = this.x + Mathf.cosDeg(var19) * var20;
            float var22 = this.y + Mathf.sinDeg(var19) * var20;
            if (var18 > 0) {
               Lines.line(var15, var16, var21, var22);
            }

            var15 = var21;
            var16 = var22;
         }
      }

      Draw.blend();
      Drawf.light(this.x, this.y, 48.0F, violet, 0.45F * var1);
      Draw.reset();
   }
}
