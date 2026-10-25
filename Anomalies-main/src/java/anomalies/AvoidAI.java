package anomalies;

import arc.Core;
import arc.Events;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.ui.Styles;

public class AvoidAI {
   static final String KEY = "anom.avoid";
   static final float RING = 24.0F;
   static float[] zones = new float[0];
   static int zoneCount;

   public static boolean enabled() {
      try {
         String var0 = (String)Vars.state.rules.tags.get("anom.avoid");
         return var0 == null || !var0.equals("0");
      } catch (Throwable var1) {
         return true;
      }
   }

   public static void set(boolean var0) {
      try {
         Vars.state.rules.tags.put("anom.avoid", var0 ? "1" : "0");
      } catch (Throwable var2) {
      }

   }

   public static void init() {
      try {
         Events.run(Trigger.update, AvoidAI::update);
      } catch (Throwable var1) {
      }

   }

   static float hazard(Anomaly var0) {
      return 0.0F;
   }

   static void update() {
      try {
         if (!enabled() || !Vars.state.isGame() || Vars.net.client()) {
            return;
         }

         int var0 = AnomalyManager.list.size;
         if (zones.length < var0 * 3) {
            zones = new float[var0 * 3];
         }

         zoneCount = 0;

         for(int var1 = 0; var1 < var0; ++var1) {
            Anomaly var2 = (Anomaly)AnomalyManager.list.get(var1);
            if (var2 != null && !var2.dead) {
               float var3 = hazard(var2);
               if (!(var3 <= 0.0F)) {
                  zones[zoneCount * 3] = var2.x;
                  zones[zoneCount * 3 + 1] = var2.y;
                  zones[zoneCount * 3 + 2] = var3;
                  ++zoneCount;
               }
            }
         }

         if (zoneCount == 0) {
            return;
         }

         Groups.unit.each(AvoidAI::steer);
      } catch (Throwable var4) {
      }

   }

   static void steer(Unit var0) {
      try {
         if (!var0.isValid() || var0.isFlying() || var0.isPlayer()) {
            return;
         }

         for(int var1 = 0; var1 < zoneCount; ++var1) {
            float var2 = zones[var1 * 3];
            float var3 = zones[var1 * 3 + 1];
            float var4 = zones[var1 * 3 + 2] + var0.hitSize / 2.0F;
            float var5 = var0.x - var2;
            float var6 = var0.y - var3;
            float var7 = (float)Math.sqrt((double)(var5 * var5 + var6 * var6));
            if (!(var7 >= var4 + 24.0F)) {
               float var8 = 1.0F;
               float var9 = 0.0F;
               if (var7 > 0.001F) {
                  var8 = var5 / var7;
                  var9 = var6 / var7;
               }

               float var10 = var0.vel.x;
               float var11 = var0.vel.y;
               float var12 = var10 * var8 + var11 * var9;
               if (var12 < 0.0F) {
                  var10 -= var12 * var8;
                  var11 -= var12 * var9;
                  float var13 = -var9;
                  float var15 = var10 * var13 + var11 * var8;
                  float var16 = Math.abs(var15) > 0.01F ? Math.signum(var15) : ((var0.id & 1) == 0 ? 1.0F : -1.0F);
                  float var17 = -var12;
                  var10 += var13 * var16 * var17;
                  var11 += var8 * var16 * var17;
               }

               if (var7 < var4) {
                  float var21 = Math.min(2.0F, 0.3F + (var4 - var7) * 0.1F);
                  var10 += var8 * var21;
                  var11 += var9 * var21;
               }

               var0.vel.x = var10;
               var0.vel.y = var11;
            }
         }
      } catch (Throwable var18) {
      }

   }

   static String label() {
      String var10000 = Core.bundle.get("anomalies.avoid", "Units avoid anomalies");
      return var10000 + ": " + UiHint.onOff(enabled());
   }

   public static void addButton(Table var0) {
      try {
         var0.row();
         TextButton[] var1 = new TextButton[1];
         Cell<TextButton> var2 = var0.button(label(), Styles.togglet, () -> {
            if (Vars.net.client()) {
               Vars.ui.showInfoToast("Host only", 3.0F);
            } else {
               set(!enabled());
               if (var1[0] != null) {
                  var1[0].setText(label());
               }

            }
         });
         var2.update((var0x) -> var0x.setText(label())).checked((var0x) -> enabled()).size(250.0F, 40.0F);
         var1[0] = (TextButton)var2.get();
      } catch (Throwable var3) {
      }

   }
}
