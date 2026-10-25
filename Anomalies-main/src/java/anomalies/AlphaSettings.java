package anomalies;

import arc.math.Mathf;
import mindustry.Vars;

public class AlphaSettings {
   static final String KEY = "anom.alpha";
   static boolean force;
   static final float alphaChance = 25.0F;

   public static boolean enabled() {
      try {
         return !"0".equals(Vars.state.rules.tags.get("anom.alpha"));
      } catch (Throwable var1) {
         return false;
      }
   }

   public static void set(boolean var0) {
      Vars.state.rules.tags.put("anom.alpha", var0 ? "1" : "0");
   }

   public static boolean roll() {
      return force || enabled() && Mathf.random(100.0F) < 25.0F;
   }

   public static float scale() {
      return 2.0F + Mathf.random(1.0F);
   }
}
