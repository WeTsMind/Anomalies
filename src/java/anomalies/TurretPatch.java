package anomalies;

import arc.audio.Sound;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import mindustry.content.Blocks;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;

public class TurretPatch {
   private static float savedAlpha = 0.22F;
   private static boolean shadowHidden = false;
   private static float curFade = 0.0F;
   private static float prog = 0.0F;
   private static float arcF = 0.0F;

   public static void foreshadowSound(Turret var0) {
      try {
         Block var2 = Blocks.foreshadow;
         if (var2 instanceof Turret) {
            Turret var1 = (Turret)var2;
            Sound var4 = var1.shootSound;
            if (var4 != null) {
               var0.shootSound = var4;
            }
         }
      } catch (Throwable var3) {
      }

   }

   public static void shadowOff() {
      if (!shadowHidden) {
         savedAlpha = Pal.shadow.a;
         shadowHidden = true;
      }

      Pal.shadow.a = 0.0F;
   }

   public static void shadowOn() {
      if (shadowHidden) {
         Pal.shadow.a = savedAlpha;
         shadowHidden = false;
      }

   }

   private static float clamp01(float var0) {
      return var0 < 0.0F ? 0.0F : (var0 > 1.0F ? 1.0F : var0);
   }

   public static float begin(Building var0, float var1) {
      float var2 = 150.0F;

      try {
         Block var4 = var0.block;
         if (var4 instanceof Turret) {
            Turret var3 = (Turret)var4;
            if (var3.reload > 1.0F) {
               var2 = var3.reload;
            }
         }
      } catch (Throwable var5) {
      }

      curFade = 1.0F - var1 / 26.0F;
      prog = clamp01(var1 / var2);
      return !(curFade > 0.0F) && !(prog > 0.0F) ? 0.0F : 1.0F;
   }

   public static float arcBegin(int var0) {
      float var1 = clamp01(prog * 3.0F - (float)var0);
      var1 = var1 * var1 * (3.0F - 2.0F * var1);
      arcF = Math.max(Math.max(curFade, 0.0F), var1);
      return AnomalyTurret.BUMP_F[var0];
   }

   public static float mulArc(float var0) {
      return var0 * arcF;
   }

   public static float mulFade(float var0) {
      return var0 * Math.max(curFade, 0.0F);
   }

   public static void colorArc(Color var0) {
      Draw.color(var0, arcF);
   }

   public static float sparkHash(int var0) {
      return curFade > 0.0F ? BallLightningAnomaly.hash(var0) : -1.0F;
   }

   public static void layeredFade(BallLightningAnomaly var0, float var1, float var2, float var3, float var4, int var5, float var6) {
      var0.layered(var1, var2, var3, var4, var5, var6, Math.max(curFade, 0.0F));
   }
}
