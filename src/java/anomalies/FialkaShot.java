package anomalies;

import arc.graphics.Color;

/**
 * Fialka shots, both in the glowing violet colours of the Lavanda:
 * - main(): the Demolisher shell (demolisher-bullet sprite), 110 damage;
 * - tail(): the bullet14m sprite (fialka-bullet.png), 150 damage, leaves a glowing trail.
 */
public class FialkaShot {
   static final Color violet = LavandaShot.violet;

   public static WreckBullet main() {
      WreckBullet b = new WreckBullet(6.0F, 110.0F, violet);
      b.lifetime = 30.0F; // range ~180
      b.lightColor = violet;
      return b;
   }

   public static WreckBullet tail() {
      WreckBullet b = new WreckBullet(5.0F, 150.0F, violet);
      b.spriteName = "anomalies-fialka-bullet";
      b.spriteScale = 0.8F;
      b.lifetime = 36.0F; // range ~180
      b.hitSize = 7.0F;
      b.trailLength = 16;
      b.trailWidth = 2.6F;
      b.lightColor = violet;
      return b;
   }
}
