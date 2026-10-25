package anomalies;

import arc.Events;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.world.Tile;

public class RiftSpawner {
   static final float interval = 3600.0F;
   static float timer;

   public static void init() {
      Events.run(Trigger.update, () -> {
         if (Vars.state.isPlaying() && !Vars.net.client()) {
            timer += Time.delta;
            if (!(timer < 3600.0F)) {
               timer = 0.0F;
               if (AnomalyManager.list.size < 30) {
                  spawn();
               }

            }
         }
      });
   }

   static AnomalyType pick() {
      int var0 = 0;

      for(AnomalyType var4 : AnomalyType.all) {
         var0 += Math.max(0, AnomalySettings.chance(var4));
      }

      if (var0 <= 0) {
         return null;
      } else {
         int var7 = Mathf.random(var0 - 1);

         for(AnomalyType var5 : AnomalyType.all) {
            int var6 = Math.max(0, AnomalySettings.chance(var5));
            if (var7 < var6) {
               return var5;
            }

            var7 -= var6;
         }

         return null;
      }
   }

   static void spawn() {
      if (AnomalySettings.enabled()) {
         AnomalyType var0 = pick();
         if (var0 != null) {
            Anomaly var1 = (Anomaly)var0.prov.get();
            int var2 = Math.max(1, var1.tileRadius);
            int var3 = Vars.world.width();
            int var4 = Vars.world.height();
            if (var3 > 2 * var2 + 3 && var4 > 2 * var2 + 3) {
               for(int var5 = 0; var5 < 80; ++var5) {
                  int var6 = Mathf.random(var2 + 1, var3 - var2 - 2);
                  int var7 = Mathf.random(var2 + 1, var4 - var2 - 2);
                  if (clear(var6, var7, var2)) {
                     AnomalyManager.place(var1, (float)var6 * 8.0F, (float)var7 * 8.0F);
                     return;
                  }
               }

            }
         }
      }
   }

   static boolean clear(int var0, int var1, int var2) {
      if (!Spacing.ok(var0, var1, var2)) {
         return false;
      } else {
         for(int var3 = -var2; var3 <= var2; ++var3) {
            for(int var4 = -var2; var4 <= var2; ++var4) {
               if (var3 * var3 + var4 * var4 <= var2 * var2 + 1) {
                  Tile var5 = Vars.world.tile(var0 + var3, var1 + var4);
                  if (var5 == null || var5.solid()) {
                     return false;
                  }
               }
            }
         }

         return true;
      }
   }
}
