package anomalies;

import mindustry.world.Block;

public class AnomalyModTurret extends AnomalyModSync {
   public static Block ballTurret;
   public static Block protector2;

   public void loadContent() {
      super.loadContent();
      ballTurret = new AnomalyTurret("anomaly-turret");
      protector2 = new AnomalyProtector("anomaly-protector");
   }
}
