package anomalies;

import arc.util.Time;
import mindustry.game.Team;

public class ZigzagBall extends TurretBall {
   static final float AMP = 42.0F;
   static final float HALF = 14.0F;
   static final float SPD = 1.8F;
   float base;
   float zt = 7.0F;

   public void launch(float var1, Team var2) {
      super.launch(var1, var2);
      this.base = var1;
   }

   public void spawned() {
   }

   public void update() {
      this.zt += Time.delta;
      float var1 = this.zt % 28.0F < 14.0F ? 1.0F : -1.0F;
      float var2 = this.base + 42.0F * var1;
      double var3 = Math.toRadians((double)var2);
      this.vx = (float)Math.cos(var3) * 1.8F;
      this.vy = (float)Math.sin(var3) * 1.8F;
      this.rotation = var2;
      super.update();
   }
}
