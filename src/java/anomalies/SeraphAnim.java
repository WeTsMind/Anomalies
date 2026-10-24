package anomalies;

import arc.math.Mathf;
import arc.util.Time;
import java.util.Map;
import java.util.WeakHashMap;
import mindustry.gen.Unit;

/** Timeline of the Seraph spawn: lids open, unit rises with folded wings, wings unfold, lids close. */
public class SeraphAnim {
   static final float NONE = -1.0E9F;
   static final float OPEN_END = 36.0F;
   static final float WING_START = 46.0F;
   static final float WING_END = 90.0F;
   static final float CLOSE_START = 90.0F;
   static final float CLOSE_END = 126.0F;
   static final Map<Unit, Float> starts = new WeakHashMap();

   static float smooth(float var0) {
      var0 = Mathf.clamp(var0);
      return var0 * var0 * (3.0F - 2.0F * var0);
   }

   /** Ticks since the unit was spawned by a core (huge if it was not). */
   static float age(Unit var0) {
      Float var1 = (Float)starts.get(var0);
      if (var1 == null) {
         float var2 = NONE;

         try {
            if (var0.spawnedByCore() && var0.closestCore() != null && var0.dst(var0.closestCore()) < 24.0F) {
               var2 = Time.time;
               if (var0.closestCore() instanceof SeraphCore.SeraphBuild) {
                  ((SeraphCore.SeraphBuild)var0.closestCore()).openStart = var2;
               }
            }
         } catch (Throwable var3) {
         }

         var1 = var2;
         starts.put(var0, var1);
      }

      return Time.time - var1;
   }

   /** Wings fade in (folded) right after the lids are open. */
   static float wingAlpha(float var0) {
      return smooth((var0 - OPEN_END) / 10.0F);
   }

   /** 0 = wings folded back, 1 = fully spread. */
   static float wings(float var0) {
      return smooth((var0 - WING_START) / (WING_END - WING_START));
   }

   /** 0 = core closed, 1 = lids fully apart. */
   static float lids(float var0) {
      if (var0 < 0.0F) {
         return 0.0F;
      } else if (var0 < OPEN_END) {
         return smooth(var0 / OPEN_END);
      } else {
         return var0 < CLOSE_START ? 1.0F : 1.0F - smooth((var0 - CLOSE_START) / (CLOSE_END - CLOSE_START));
      }
   }
}
