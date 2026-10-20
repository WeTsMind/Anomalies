package anomalies;

import mindustry.world.Block;

public class AnomalyModTurret extends AnomalyModSync {
   public static Block ballTurret;

   public void loadContent() {
      super.loadContent();
      ballTurret = new AnomalyTurret("anomaly-turret");
   }
}
