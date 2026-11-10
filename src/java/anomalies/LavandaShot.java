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
import mindustry.graphics.Drawf;

/**
 * Lavanda shot: a glowing violet bullet (60 damage). When it hits something it breaks into 3 violet glowing shards
 * (20 damage each) that fly to the left, straight ahead and to the right of the bullet's direction.
 */
public class LavandaShot extends BasicBulletType {
   static final Color violet = Color.valueOf("a566ff"), pale = Color.valueOf("e3d0ff");

   public LavandaShot() {
      super(5.0F, 60.0F);
      this.lifetime = 28.0F;
      this.width = 7.0F;
      this.height = 15.0F;
      this.backColor = violet;
      this.frontColor = pale;
      this.trailColor = violet;
      this.trailWidth = 2.0F;
      this.trailLength = 14;
      this.hitColor = violet;
      this.lightColor = violet;
      this.lightRadius = 28.0F;
      this.lightOpacity = 0.6F;
      this.shootEffect = Fx.sparkShoot;
      this.smokeEffect = Fx.shootBigSmoke;
      this.hitEffect = Fx.hitBulletColor;
      this.despawnEffect = Fx.hitBulletColor;
      // the break-up: left (-90), ahead (0), right (+90), only when the bullet hits something
      this.fragBullets = 3;
      this.fragSpread = 90.0F;
      this.fragAngle = 0.0F;
      this.fragRandomSpread = 0.0F;
      this.fragVelocityMin = 1.0F;
      this.fragVelocityMax = 1.0F;
      this.fragLifeMin = 1.0F;
      this.fragLifeMax = 1.0F;
      this.fragOnHit = true;
      this.fragBullet = new Shard();
   }

   public void draw(Bullet b) {
      super.draw(b);
      float pulse = 0.85F + 0.15F * Mathf.sin(Time.time * 0.5F + b.id);
      Draw.blend(Blending.additive);
      Draw.color(violet, 0.5F * pulse);
      Fill.circle(b.x, b.y, 5.5F);
      Draw.color(pale, 0.3F * pulse);
      Fill.circle(b.x, b.y, 2.8F);
      Draw.blend();
      Draw.reset();
   }

   /** a small glowing violet shard (20 damage) */
   public static class Shard extends BasicBulletType {
      public Shard() {
         super(4.2F, 20.0F);
         this.lifetime = 18.0F;
         this.width = 4.5F;
         this.height = 10.0F;
         this.backColor = violet;
         this.frontColor = pale;
         this.trailColor = violet;
         this.trailWidth = 1.4F;
         this.trailLength = 9;
         this.hitColor = violet;
         this.lightColor = violet;
         this.lightRadius = 16.0F;
         this.lightOpacity = 0.5F;
         this.shootEffect = Fx.none;
         this.smokeEffect = Fx.none;
         this.hitEffect = Fx.hitBulletColor;
         this.despawnEffect = Fx.hitBulletColor;
      }

      public void draw(Bullet b) {
         super.draw(b);
         float pulse = 0.85F + 0.15F * Mathf.sin(Time.time * 0.6F + b.id);
         Draw.blend(Blending.additive);
         Draw.color(violet, 0.5F * pulse);
         Fill.circle(b.x, b.y, 3.4F);
         Draw.blend();
         Draw.reset();
      }
   }
}
