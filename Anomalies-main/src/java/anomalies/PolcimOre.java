package anomalies;

import arc.Events;
import arc.math.Mathf;
import arc.util.Time;
import java.util.ArrayList;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.game.EventType.Trigger;
import mindustry.world.Block;
import mindustry.world.Tile;

public class PolcimOre {
   static final float chance = 35.0F;
   static final float life = 72000.0F;
   static final String TAG = "anom.polcim";
   static final String CLOCK = "anom.polcim.clock";
   static double clock;
   static float acc;
   static final ArrayList<double[]> entries = new ArrayList();

   public static void init() {
      Events.on(EventType.WorldLoadEvent.class, (var0) -> load());
      Events.run(Trigger.update, PolcimOre::tick);
   }

   static void load() {
      entries.clear();
      clock = (double)0.0F;

      try {
         String var0 = (String)Vars.state.rules.tags.get("anom.polcim.clock");
         if (var0 != null) {
            clock = Double.parseDouble(var0);
         }

         String var1 = (String)Vars.state.rules.tags.get("anom.polcim");
         if (var1 != null && !var1.isEmpty()) {
            for(String var5 : var1.split(";")) {
               String[] var6 = var5.split(",");
               if (var6.length == 3) {
                  entries.add(new double[]{Double.parseDouble(var6[0]), Double.parseDouble(var6[1]), Double.parseDouble(var6[2])});
               }
            }
         }
      } catch (Throwable var7) {
      }

   }

   static void save() {
      try {
         StringBuilder var0 = new StringBuilder();

         for(double[] var2 : entries) {
            var0.append((int)var2[0]).append(',').append((int)var2[1]).append(',').append((long)var2[2]).append(';');
         }

         Vars.state.rules.tags.put("anom.polcim", var0.toString());
         Vars.state.rules.tags.put("anom.polcim.clock", String.valueOf((long)clock));
      } catch (Throwable var3) {
      }

   }

   static void setOverlay(Tile var0, Block var1) {
      try {
         var0.setOverlayNet(var1);
      } catch (Throwable var5) {
         try {
            var0.setOverlay(var1);
         } catch (Throwable var4) {
         }
      }

   }

   public static void drop(float var0, float var1) {
      if (!Vars.net.client() && !(Mathf.random(100.0F) >= 35.0F)) {
         int var2 = Math.round(var0 / 8.0F);
         int var3 = Math.round(var1 / 8.0F);
         int[] var4 = new int[9];

         for(int var5 = 0; var5 < 9; var4[var5] = var5++) {
         }

         for(int var10 = 8; var10 > 0; --var10) {
            int var6 = Mathf.random(var10);
            int var7 = var4[var10];
            var4[var10] = var4[var6];
            var4[var6] = var7;
         }

         int var11 = 0;

         for(int var12 = 0; var12 < 9 && var11 < 3; ++var12) {
            Tile var13 = Vars.world.tile(var2 + var4[var12] % 3 - 1, var3 + var4[var12] / 3 - 1);
            if (var13 != null && !var13.solid() && var13.overlay() == Blocks.air) {
               try {
                  if (var13.floor().isDeep()) {
                     continue;
                  }
               } catch (Throwable var9) {
               }

               setOverlay(var13, AnomalyMod.polcimOre);
               entries.add(new double[]{(double)var13.x, (double)var13.y, clock + (double)72000.0F});
               ++var11;
            }
         }

         if (var11 > 0) {
            save();
         }

      }
   }

   static void tick() {
      if (Vars.state.isPlaying()) {
         clock += (double)Time.delta;
         acc += Time.delta;
         if (!(acc < 300.0F)) {
            acc = 0.0F;
            boolean var0 = false;

            for(int var1 = entries.size() - 1; var1 >= 0; --var1) {
               double[] var2 = (double[])entries.get(var1);
               if (!(clock < var2[2])) {
                  Tile var3 = Vars.world.tile((int)var2[0], (int)var2[1]);
                  if (var3 != null && var3.overlay() == AnomalyMod.polcimOre) {
                     setOverlay(var3, Blocks.air);
                  }

                  entries.remove(var1);
                  var0 = true;
               }
            }

            if (var0 || entries.size() > 0) {
               save();
            }

         }
      }
   }
}
