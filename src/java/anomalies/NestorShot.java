package anomalies;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.entities.bullet.BasicBulletType;

/** Nestor side-gun shot: the same Stell-style bolt as the Crocus fires, in yellow (no lightning field). */
public class NestorShot extends BasicBulletType {
   static final Color yellow = Color.valueOf("ffd21f");

   public NestorShot() {
      super(4.2F, 18.0F);
      this.lifetime = 24.0F;
      this.width = 6.0F;
      this.height = 14.0F;
      this.backColor = yellow;
      this.frontColor = Color.white;
      this.trailColor = yellow;
      this.trailWidth = 1.6F;
      this.trailLength = 12;
      this.hitColor = yellow;
      this.lightColor = yellow;
      this.shootEffect = Fx.sparkShoot;
      this.smokeEffect = Fx.shootBigSmoke;
      this.hitEffect = Fx.hitBulletColor;
      this.despawnEffect = Fx.hitBulletColor;
   }
}
