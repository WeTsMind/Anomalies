package anomalies;

import java.util.Arrays;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.world.Tile;

public class SpawnGuard {
   static final float RADIUS = 10.0F;
   static int[] spawns = new int[0];
   static Tile marker;

   public static boolean ok(int var0, int var1, int var2) {
      return near(var0, var1, var2) ? false : Spacing.ok(var0, var1, var2);
   }

   static boolean near(int var0, int var1, int var2) {
      try {
         refresh();
         float var3 = 10.0F + (float)var2;
         var3 *= var3;

         for(int var4 = 0; var4 + 1 < spawns.length; var4 += 2) {
            float var5 = (float)(var0 - spawns[var4]);
            float var6 = (float)(var1 - spawns[var4 + 1]);
            if (var5 * var5 + var6 * var6 < var3) {
               return true;
            }
         }

         return false;
      } catch (Throwable var7) {
         return false;
      }
   }

   static void refresh() {
      int var0 = Vars.world.width();
      int var1 = Vars.world.height();
      Tile var2 = Vars.world.tile(0, 0);
      if (var2 != marker || var2 == null) {
         int[] var3 = new int[16];
         int var4 = 0;

         for(int var5 = 0; var5 < var0; ++var5) {
            for(int var6 = 0; var6 < var1; ++var6) {
               Tile var7 = Vars.world.tile(var5, var6);
               if (var7 != null && var7.overlay() == Blocks.spawn) {
                  if (var4 + 2 > var3.length) {
                     var3 = Arrays.copyOf(var3, var3.length * 2);
                  }

                  var3[var4++] = var5;
                  var3[var4++] = var6;
               }
            }
         }

         spawns = Arrays.copyOf(var3, var4);
         marker = var2;
      }
   }
}
