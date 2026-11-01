package anomalies;

import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;

/**
 * The shot of the Geranium: the same ball lightning as the one of the Golden Ratio (a {@link TurretBall}),
 * but it flies straight, without zigzagging. The bullet itself is invisible and lives one tick; when it ends,
 * the ball lightning is released at that place and flies in the direction of the shot.
 */
public class StraightShot extends BulletType {
   /** how far from the muzzle the ball flies before it vanishes */
   public static final float BALL_RANGE = 160.0F;
   /** how long the ball lives, in ticks */
   public static final float BALL_LIFE = 90.0F;

   public StraightShot() {
      // speed * lifetime = the range the unit uses to look for targets
      super(4.0F, 0.0F);
      this.lifetime = 40.0F;
      this.collides = false;
      this.collidesTiles = false;
      this.collidesAir = false;
      this.collidesGround = false;
      this.absorbable = false;
      this.hittable = false;
      this.reflectable = false;
      this.scaleLife = false;
      this.shootEffect = Fx.none;
      this.smokeEffect = Fx.none;
      this.hitEffect = Fx.none;
      this.despawnEffect = Fx.none;
   }

   public void init(Bullet b) {
      super.init(b);
      b.lifetime = 1.0F;
   }

   public void draw(Bullet b) {
   }

   public void despawned(Bullet b) {
      super.despawned(b);
      if (!Vars.net.client()) {
         TurretBall ball = new TurretBall();
         ball.launch(b.rotation(), b.team);
         AnomalyManager.place(ball, b.x, b.y);
         ball.age = 0.0F;
         ball.life = BALL_LIFE;
         ball.origin(b.owner, b.x, b.y);
         ball.orad = BALL_RANGE;
      }
   }
}
