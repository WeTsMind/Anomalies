package anomalies;

public class AlloyBall extends TurretBall {
   public AlloyBall(float var1) {
      super(var1);
   }

   public void update() {
      float var1 = AlloyDmg.mul;
      AlloyDmg.mul = 2.5F;

      try {
         super.update();
      } finally {
         AlloyDmg.mul = var1;
      }

   }
}
