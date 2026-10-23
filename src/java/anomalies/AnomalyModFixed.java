package anomalies;

import arc.util.Log;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.ctype.MappableContent;
import mindustry.type.UnitType;

public class AnomalyModFixed extends AnomalyModGolden {
   public static UnitType seraph;
   public static SeraphCore seraphCore;

   public void loadContent() {
      super.loadContent();

      try {
         seraph = new SeraphUnit("seraph");
         seraphCore = new SeraphCore("seraph-core");
         seraphCore.unitType = seraph;
      } catch (Throwable var1) {
         Log.err(var1);
      }

   }

   public void init() {
      super.init();
      fix("anomalies-alloy");
      fix("anomalies-golden-ratio");
   }

   static void fix(String var0) {
      try {
         MappableContent var1 = Vars.content.getByName(ContentType.unit, var0);
         if (!(var1 instanceof UnitType)) {
            return;
         }

         UnitType.class.getField("legPhysicsLayer").setBoolean(var1, false);
      } catch (Throwable var2) {
         Log.err(var2);
      }

   }
}
