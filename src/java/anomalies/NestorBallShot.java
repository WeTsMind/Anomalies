package anomalies;

import mindustry.Vars;
import mindustry.gen.Bullet;

/** The centre-slit shot of the Nestor: like the Geranium shot, but the released ball lightning is an alpha ball (bigger). */
public class NestorBallShot extends StraightShot {
   @Override
   public void despawned(Bullet b) {
      // not super.despawned of StraightShot: that would release a normal ball as well
      if (!Vars.net.client()) {
         TurretBall ball = new TurretBall(AlphaSettings.scale());
         ball.launch(b.rotation(), b.team);
         AnomalyManager.place(ball, b.x, b.y);
         ball.age = 0.0F;
         ball.life = BALL_LIFE;
         ball.origin(b.owner, b.x, b.y);
         ball.orad = BALL_RANGE;
      }
   }
}
