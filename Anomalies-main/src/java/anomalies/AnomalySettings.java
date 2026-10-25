package anomalies;

import arc.math.Mathf;
import mindustry.Vars;

public class AnomalySettings {
   public static int chance(AnomalyType var0) {
      return geti("anom.c." + var0.name(), AnomalyDefaults.base(var0), 0, 100);
   }

   public static void setChance(AnomalyType var0, int var1) {
      Vars.state.rules.tags.put("anom.c." + var0.name(), String.valueOf(Math.max(0, Math.min(100, var1))));
   }

   public static int interval() {
      return geti("anom.interval", 60, 1, 100000);
   }

   public static void setInterval(int var0) {
      Vars.state.rules.tags.put("anom.interval", String.valueOf(Math.max(1, var0)));
   }

   /** How many times every anomaly type is rolled each interval. */
   public static int count() {
      return geti("anom.count", 1, 1, 50);
   }

   public static void setCount(int var0) {
      Vars.state.rules.tags.put("anom.count", String.valueOf(Math.max(1, Math.min(50, var0))));
   }

   public static boolean enabled() {
      return !"0".equals(Vars.state.rules.tags.get("anom.enabled"));
   }

   public static void setEnabled(boolean var0) {
      Vars.state.rules.tags.put("anom.enabled", var0 ? "1" : "0");
   }

   public static boolean destroyWorld() {
      return !"0".equals(Vars.state.rules.tags.get("anom.destroy"));
   }

   public static void setDestroyWorld(boolean var0) {
      Vars.state.rules.tags.put("anom.destroy", var0 ? "1" : "0");
   }

   static int geti(String var0, int var1, int var2, int var3) {
      try {
         String var4 = (String)Vars.state.rules.tags.get(var0);
         return var4 == null ? var1 : Math.max(var2, Math.min(var3, Integer.parseInt(var4)));
      } catch (Throwable var5) {
         return var1;
      }
   }

   static void roll() {
      if (enabled()) {
         int var0 = count();

         for(int var1 = 0; var1 < var0; ++var1) {
            for(AnomalyType var3 : AnomalyType.all) {
               int var4 = chance(var3);
               if (var4 > 0 && Mathf.random(100.0F) < (float)var4) {
                  AnomalyManager.spawnRandom(var3);
               }
            }
         }

      }
   }
}
