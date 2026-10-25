package anomalies;

import arc.Events;
import mindustry.Vars;
import mindustry.game.EventType;

public class AnomalyDefaults {
   public static void init() {
      Events.on(EventType.WorldLoadEvent.class, (var0) -> apply());
   }

   static int base(AnomalyType var0) {
      switch (var0.name()) {
         case "mirror" -> {
            return 80;
         }
         case "distortion" -> {
            return 10;
         }
         case "time" -> {
            return 5;
         }
         case "vortex" -> {
            return 20;
         }
         case "ball" -> {
            return 15;
         }
         case "blackhole" -> {
            return 1;
         }
         case "warp" -> {
            return 5;
         }
         case "fire" -> {
            return 25;
         }
         case "toxic" -> {
            return 15;
         }
         case "whirlpool" -> {
            return 20;
         }
         default -> {
            return 0;
         }
      }
   }

   static void apply() {
      try {
         for(AnomalyType var3 : AnomalyType.all) {
            String var4 = "anom.c." + var3.name();
            if (Vars.state.rules.tags.get(var4) == null) {
               Vars.state.rules.tags.put(var4, String.valueOf(base(var3)));
            }
         }
      } catch (Throwable var5) {
      }

   }
}
