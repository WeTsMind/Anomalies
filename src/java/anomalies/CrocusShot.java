package anomalies;

import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;

/**
 * Crocus shot: a Stell-style bolt in yellow. When it hits something, yellow lightning starts to strike from the point of impact
 * for a short time: every enemy unit within 1.5 tiles of the centre takes 50 damage per second (units only, no buildings).
 */
public class CrocusShot extends BasicBulletType {
   /** 1.5 tiles */
   public static final float RADIUS = 12.0F;
   /** damage per second to every enemy unit inside the radius */
   public static final float DPS = 50.0F;
   /** how long the lightning keeps striking, in ticks */
   public static final float DURATION = 120.0F;
   /** the lightning strikes every this many ticks */
   static final float STEP = 5.0F;
   static final Color yellow = Color.valueOf("ffd21f");
   static final Color pale = Color.valueOf("fff8d0");

   /** Built here, in the constructor, so that the game loads it together with the rest of the content. */
   final BulletType field = new Field();

   public CrocusShot() {
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

   @Override
   public void hit(Bullet b, float x, float y) {
      super.hit(b, x, y);
      Bullet f = this.field.create(b.owner, b.team, x, y, 0.0F);
      if (f != null) f.data = new float[]{0.0F};
   }

   /** The lightning storm at the point of impact: invisible bullet, the lightning itself is drawn by ShockBolt.boltFx. */
   static class Field extends BulletType {
      Field() {
         super(0.001F, 0.0F);
         this.lifetime = DURATION;
         this.collides = false;
         this.collidesTiles = false;
         this.collidesAir = false;
         this.collidesGround = false;
         this.hittable = false;
         this.absorbable = false;
         this.reflectable = false;
         this.keepVelocity = false;
         this.shootEffect = Fx.none;
         this.smokeEffect = Fx.none;
         this.hitEffect = Fx.none;
         this.despawnEffect = Fx.none;
      }

      @Override
      public void draw(Bullet b) {
      }

      @Override
      public void update(Bullet b) {
         super.update(b);
         if (!(b.data instanceof float[])) b.data = new float[]{0.0F};
         float[] last = (float[])b.data;
         float dt = b.time - last[0];
         if (dt < STEP) return;
         last[0] = b.time;
         final float damage = DPS * dt / 60.0F;
         final boolean server = !Vars.net.client();
         final float cx = b.x, cy = b.y;
         // one bolt to every enemy unit in reach (and the damage), they hit the units themselves
         Units.nearbyEnemies(b.team, cx - RADIUS, cy - RADIUS, RADIUS * 2.0F, RADIUS * 2.0F, u -> {
            if (!u.isValid() || !u.within(cx, cy, RADIUS + u.hitSize / 2.0F)) return;
            if (server) AlloyDmg.hit(u, damage);
            ShockBolt.boltFx.at(cx, cy, 0.0F, new float[]{u.x, u.y});
         });
         // one more bolt into a random direction, so the storm is always alive
         float a = Mathf.random(360.0F), d = RADIUS * Mathf.random(0.6F, 1.0F);
         ShockBolt.boltFx.at(cx, cy, a, new float[]{cx + Angles.trnsx(a, d), cy + Angles.trnsy(a, d)});
      }
   }
}
