package anomalies;

import arc.Core;
import arc.util.Time;

public class AlphaSync {
   public static String append(String var0, Anomaly var1) {
      try {
         return var0 + "|" + enc(var1);
      } catch (Throwable var3) {
         return var0;
      }
   }

   static String enc(Anomaly var0) {
      String var1 = "";
      Class var2 = var0.getClass();
      if (var2 == MirrorAnomaly.class) {
         MirrorAnomaly var3 = (MirrorAnomaly)var0;
         var1 = var3.hw + "," + var3.hh;
      } else if (var2 == BallLightningAnomaly.class) {
         var1 = Float.toString(((BallLightningAnomaly)var0).s);
      } else if (var2 == BlackHoleAnomaly.class) {
         var1 = Float.toString(((BlackHoleAnomaly)var0).s);
      } else if (var2 == WarpAnomaly.class) {
         var1 = Float.toString(((WarpAnomaly)var0).s);
      }

      return var0.rotation + ";" + var1;
   }

   public static Anomaly fix(String[] var0, Anomaly var1) {
      try {
         if (var0.length < 8) {
            return var1;
         } else {
            AnomalyType var2 = AnomalyType.all[Integer.parseInt(var0[2])];
            String[] var3 = var0[7].split(";", -1);
            String[] var4 = var3[1].isEmpty() ? new String[0] : var3[1].split(",");
            Object var5 = var1;
            if (var2 == AnomalyType.mirror && var4.length == 2) {
               var5 = new MirrorAnomaly(Float.parseFloat(var4[0]), Float.parseFloat(var4[1]));
            } else if (var2 == AnomalyType.ball && var4.length == 1) {
               var5 = new BallLightningAnomaly(Float.parseFloat(var4[0]));
            } else if (var2 == AnomalyType.blackhole && var4.length == 1) {
               var5 = new BlackHoleAnomaly(Float.parseFloat(var4[0]));
            } else if (var2 == AnomalyType.warp && var4.length == 1) {
               var5 = new WarpAnomaly(Float.parseFloat(var4[0]));
            }

            ((Anomaly)var5).rotation = Float.parseFloat(var3[0]);
            return (Anomaly)var5;
         }
      } catch (Throwable var6) {
         return var1;
      }
   }

   public static float dt() {
      float var0 = Time.delta;

      try {
         if (Core.graphics != null) {
            float var1 = Core.graphics.getDeltaTime() * 60.0F;
            if (var1 > 0.0F && var1 < var0) {
               var0 = var1;
            }
         }
      } catch (Throwable var2) {
      }

      return var0;
   }
}
