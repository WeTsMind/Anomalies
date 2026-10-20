package anomalies;

import arc.struct.Seq;

public class Spacing {
   public static final int GAP = 4;

   public static boolean ok(int var0, int var1, int var2) {
      if (AnomalyProtector.blocks(var0, var1, var2)) {
         return false;
      }

      Seq var3 = AnomalyManager.list;

      for(int var4 = 0; var4 < var3.size; ++var4) {
         Anomaly var5 = (Anomaly)var3.get(var4);
         if (var5 != null && !var5.dead) {
            float var6 = (float)var0 - var5.x / 8.0F;
            float var7 = (float)var1 - var5.y / 8.0F;
            float var8 = (float)(var2 + var5.tileRadius + 4);
            if (var6 * var6 + var7 * var7 < var8 * var8) {
               return false;
            }
         }
      }

      return true;
   }
}
