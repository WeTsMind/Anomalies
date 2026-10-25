package anomalies;

public class AnomalyModSync extends AnomalyMod {
   public void init() {
      super.init();
      AnomalySync.init();
      AnomalyNet.init();
   }
}
