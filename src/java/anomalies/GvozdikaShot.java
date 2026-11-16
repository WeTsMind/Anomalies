package anomalies;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Bullet;

/**
 * Gvozdika shots, in the glowing violet colours of the Lavanda:
 * - main(): the Demolisher shell (demolisher-bullet sprite), 60 damage;
 * - Side: a regular Mindustry bullet, 10 damage.
 */
public class GvozdikaShot {
   static final Color violet = LavandaShot.violet, pale = LavandaShot.pale;

   /** the shell of the main turret: the Demolisher shell painted violet and glowing */
   public static WreckBullet main() {
      WreckBullet b = new WreckBullet(6.0F, 60.0F, violet);
      b.lifetime = 27.0F; // range ~160
      b.trailColor = violet;
      b.hitColor = violet;
      b.lightColor = violet;
      return b;
   }

   /** the bullet of the side turrets: a regular bullet (like the copper one), violet and glowing */
   public static class Side extends BasicBulletType {
      public Side() {
         super(3.5F, 10.0F);
         this.lifetime = 44.0F; // range ~154
         this.width = 7.0F;
         this.height = 9.0F;
         this.backColor = violet;
         this.frontColor = pale;
         this.trailColor = violet;
         this.trailWidth = 1.2F;
         this.trailLength = 6;
         this.hitColor = violet;
         this.lightColor = violet;
         this.lightRadius = 14.0F;
         this.lightOpacity = 0.5F;
         this.shootEffect = Fx.shootSmall;
         this.smokeEffect = Fx.shootSmallSmoke;
         this.hitEffect = Fx.hitBulletColor;
         this.despawnEffect = Fx.hitBulletColor;
      }

      public void draw(Bullet b) {
         super.draw(b);
         float pulse = 0.85F + 0.15F * Mathf.sin(Time.time * 0.6F + b.id);
         Draw.blend(Blending.additive);
         Draw.color(violet, 0.45F * pulse);
         Fill.circle(b.x, b.y, 3.2F);
         Draw.blend();
         Draw.reset();
      }
   }
}
