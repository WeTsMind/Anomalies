package anomalies;

import arc.util.Log;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.ctype.MappableContent;
import mindustry.type.UnitType;
import mindustry.world.Block;

public class AnomalyModFixed extends AnomalyModGolden {
   public static UnitType seraph;
   public static UnitType ophan;
   public static UnitType arux;
   public static OphanCore ophanCore;
   public static SeraphCore seraphCore;
   public static Block laserBlock;
   public static Block dataBlock;
   public static Block decryptor;

   public void loadContent() {
      super.loadContent();

      try {
         seraph = new SeraphUnit("seraph");
         seraphCore = new SeraphCore("seraph-core");
         seraphCore.unitType = seraph;
      } catch (Throwable var1) {
         Log.err(var1);
      }

      try {
         laserBlock = new LaserBlock("laser-block");
      } catch (Throwable var2) {
         Log.err(var2);
      }

      try {
         dataBlock = new DataBlock("data-block");
         decryptor = new DecryptorBlock("decryptor");
      } catch (Throwable var3) {
         Log.err(var3);
      }

      try {
         ophan = new OphanUnit("ophan");
         ophanCore = new OphanCore("ophan-core");
         ophanCore.unitType = ophan;
      } catch (Throwable var5) {
         Log.err(var5);
      }

      try {
         arux = new ArUxUnit("arux");
      } catch (Throwable var6) {
         Log.err(var6);
      }

   }

   public void init() {
      super.init();
      DataSystem.init();
      fix("anomalies-alloy");
      fix("anomalies-golden-ratio");
      fix("anomalies-arux");
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
