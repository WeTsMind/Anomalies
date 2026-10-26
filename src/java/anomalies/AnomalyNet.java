package anomalies;

import arc.math.Mathf;
import mindustry.Vars;
import mindustry.gen.Call;

public class AnomalyNet {
   static final String TAG = "anom-cmd";
   public static Anomaly last;

   public static void init() {
      Vars.netServer.addPacketHandler("anom-cmd", (var0, var1) -> handle(var1));
   }

   public static void send(String var0) {
      if (Vars.net.client()) {
         Call.serverPacketReliable("anom-cmd", var0);
      } else {
         handle(var0);
      }

   }

   public static void set(String var0, String var1) {
      send("set|" + var0 + "|" + var1);
   }

   static void handle(String var0) {
      try {
         if (Vars.net.client() || !Vars.state.isGame()) {
            return;
         }

         String[] var1 = var0.split("\\|");
         if (var1[0].equals("set") && var1[1].startsWith("anom.")) {
            Vars.state.rules.tags.put(var1[1], var1[2]);
            if (Vars.net.server()) {
               Call.setRules(Vars.state.rules);
            }
         } else if (var1[0].equals("spawn")) {
            int var2 = Math.max(0, Math.min(AnomalyType.all.length - 1, Integer.parseInt(var1[1])));
            float var3 = (float)Integer.parseInt(var1[2]) * 8.0F;
            float var4 = (float)Integer.parseInt(var1[3]) * 8.0F;
            int var5 = Integer.parseInt(var1[4]);
            AlphaSettings.force = var1[5].equals("1");

            Anomaly var6;
            try {
               var6 = AnomalyManager.spawn(AnomalyType.all[var2], var3, var4);
            } finally {
               AlphaSettings.force = false;
            }

            if (var6 != null && var5 > 0) {
               var6.age = 0.0F;
               var6.life = Math.max(60.0F, (float)var5 * 60.0F);
            }

            last = var6;
         } else if (var1[0].equals("unlock")) {
            if (!Vars.state.isCampaign()) {
               return;
            }
            mindustry.world.Block var16 = Vars.content.block(var1[1]);
            if (var16 != null) {
               var16.unlock();
               Vars.state.rules.researched.add(var16);
               if (Vars.net.server()) {
                  Call.setRules(Vars.state.rules);
               }
            }
         } else if (var1[0].equals("del")) {
            float var12 = (float)Integer.parseInt(var1[1]) * 8.0F;
            float var13 = (float)Integer.parseInt(var1[2]) * 8.0F;

            for(int var14 = 0; var14 < AnomalyManager.list.size; ++var14) {
               Anomaly var15 = (Anomaly)AnomalyManager.list.get(var14);
               if (Mathf.dst(var15.x, var15.y, var12, var13) < 12.0F) {
                  var15.dead = true;
                  var15.life = Math.min(var15.life, var15.age + 1.0F);
               }
            }
         }
      } catch (Throwable var11) {
      }

   }
}
