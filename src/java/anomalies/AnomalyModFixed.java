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
   public static UnitType crocus;
   public static UnitType geranium;
   public static UnitType nestor;
   public static UnitType lavanda;
   public static Block demolisher;
   public static Block cutter;
   public static Block empTower;
   public static Block goldWall;
   public static OphanCore ophanCore;
   public static SeraphCore seraphCore;
   public static Block laserBlock;
   public static Block dataBlock;
   public static Block decryptor;
   public static Block absorber;

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
         absorber = new AnomalyAbsorber("anomaly-absorber");
      } catch (Throwable var7) {
         Log.err(var7);
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

      try {
         crocus = new CrocusUnit("crocus");
      } catch (Throwable var8) {
         Log.err(var8);
      }

      try {
         demolisher = new DemolisherTurret("demolisher");
      } catch (Throwable var11) {
         Log.err(var11);
      }

      try {
         cutter = new CutterTurret("cutter");
      } catch (Throwable var12) {
         Log.err(var12);
      }

      try {
         empTower = new EmpTower("emp-tower");
      } catch (Throwable var13) {
         Log.err(var13);
      }

      try {
         goldWall = new GoldWall("gold-wall");
      } catch (Throwable var14) {
         Log.err(var14);
      }

      try {
         geranium = new GeraniumUnit("geranium");
      } catch (Throwable var10) {
         Log.err(var10);
      }

      try {
         nestor = new NestorUnit("nestor");
      } catch (Throwable var15) {
         Log.err(var15);
      }

      try {
         lavanda = new LavandaUnit("lavanda");
      } catch (Throwable var16) {
         Log.err(var16);
      }

   }

   public void init() {
      super.init();
      AnomalyShaders.load();
      DataSystem.init();
      fix("anomalies-alloy");
      fix("anomalies-golden-ratio");
      fix("anomalies-arux");

      try {
         CrocusUnit.addPlan(crocus);
      } catch (Throwable var9) {
         Log.err(var9);
      }
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
