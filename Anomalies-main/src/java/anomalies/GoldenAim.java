package anomalies;

import arc.Events;
import arc.util.Time;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

final class GoldenAim {
   static final float LIMIT = 45.0F;
   static final float BODY_MARGIN = 38.0F;
   static final float EASE = 0.2F;
   static final float MAX_STEP = 9.166667F;
   static boolean installed;

   static void install() {
      if (!installed) {
         Events.run(Trigger.update, GoldenAim::tick);
         installed = true;
      }
   }

   static void tick() {
      try {
         Groups.unit.each(GoldenAim::one);
      } catch (Throwable var1) {
      }

   }

   static float norm(float var0) {
      var0 %= 360.0F;
      if (var0 > 180.0F) {
         var0 -= 360.0F;
      }

      if (var0 < -180.0F) {
         var0 += 360.0F;
      }

      return var0;
   }

   static float clamp(float var0, float var1, float var2) {
      return var0 < var1 ? var1 : (var0 > var2 ? var2 : var0);
   }

   static void one(Unit var0) {
      try {
         if (var0 == null || var0.type == null || !"anomalies-golden-ratio".equals(var0.type.name)) {
            return;
         }

         WeaponMount[] var1 = var0.mounts;
         if (var1 == null) {
            return;
         }

         float var2 = Time.delta;

         for(int var3 = 0; var3 < var1.length; ++var3) {
            WeaponMount var4 = var1[var3];
            if (var4 != null) {
               float var5 = norm(var4.targetRotation);
               if (var4.shoot && Math.abs(var5) > 38.0F) {
                  float var6 = Math.abs(var5) - 38.0F;
                  float var7 = Math.min(var6, var0.type.rotateSpeed * var2);
                  var0.rotation += var5 > 0.0F ? var7 : -var7;
               }

               float var12 = clamp(var5, -45.0F, 45.0F);
               float var13 = norm(var4.rotation);
               float var8 = norm(var12 - var13);
               float var9 = var8 * 0.2F * var2;
               float var10 = 9.166667F * var2;
               var9 = clamp(var9, -var10, var10);
               if (Math.abs(var8) < 0.05F) {
                  var9 = var8;
               }

               var4.rotation = var13 + var9;
            }
         }
      } catch (Throwable var11) {
      }

   }
}
