package anomalies;

public class AlloyBox {
   static final float EXTRA_W = 12.0F;
   static final float EXTRA_L = 6.0F;

   static boolean alloy(MirrorAnomaly var0) {
      return var0.dropped && var0.team != null;
   }

   public static float hw(MirrorAnomaly var0) {
      return alloy(var0) ? var0.hw + 12.0F : var0.hw;
   }

   public static float hh(MirrorAnomaly var0) {
      return alloy(var0) ? var0.hh + 6.0F : var0.hh;
   }
}
