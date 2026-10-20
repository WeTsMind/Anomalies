package anomalies;

import mindustry.gen.Building;
import mindustry.gen.Unit;

public class AlloyDmg {
   public static float mul = 1.0F;

   public static void hit(Building var0, float var1) {
      var0.damage(var1 * mul);
   }

   public static void hit(Unit var0, float var1) {
      var0.damagePierce(var1 * mul);
   }
}
