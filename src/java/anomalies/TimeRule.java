package anomalies;

import arc.Core;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;

public class TimeRule {
   static final String KEY = "anom.timekill";

   public static boolean kills() {
      try {
         String var0 = (String)Vars.state.rules.tags.get("anom.timekill");
         return var0 == null || !var0.equals("0");
      } catch (Throwable var1) {
         return true;
      }
   }

   public static void set(boolean var0) {
      try {
         Vars.state.rules.tags.put("anom.timekill", var0 ? "1" : "0");
      } catch (Throwable var2) {
      }

   }

   public static void despawnOrRestore(Unit var0) {
      if (kills()) {
         Call.unitDespawn(var0);
      } else {
         try {
            AnomalyManager.Track var1 = (AnomalyManager.Track)AnomalyManager.tracks.get(var0.id);
            if (var1 == null) {
               return;
            }

            int var2 = var1.count < 61 ? 0 : var1.idx;
            float var3 = var1.xs[var2];
            float var4 = var1.ys[var2];
            var0.set(var3, var4);
            var0.vel.setZero();
            var1.cooldown = 2;
            AnomalyFx.pulse.at(var3, var4, var0.hitSize + 10.0F, Pal.heal);
         } catch (Throwable var5) {
         }

      }
   }

   public static void addButton(Table var0) {
      try {
         var0.row();
         var0.button(label(), Styles.togglet, () -> AnomalyNet.set("anom.timekill", kills() ? "0" : "1")).update((var0x) -> var0x.setText(label())).checked((var0x) -> kills()).size(250.0F, 40.0F);
         var0.row();
         UiHint.add(var0, "anomalies.timekill.hint", "When on, the Time anomaly also kills units.", 1);
      } catch (Throwable var3) {
      }


   }

   static String label() {
      String var10000 = Core.bundle.get("anomalies.timekill", "Time anomaly destroys units");
      return var10000 + ": " + UiHint.onOff(kills());
   }
}
