package anomalies;

import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;

public class ZigzagShot extends BulletType {
   public static final float RANGE = 160.0F;

   public ZigzagShot() {
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

   public void init(Bullet var1) {
      super.init(var1);
      if (var1.owner instanceof Unit) {
         GoldenGlow.shot((Unit)var1.owner, var1.x, var1.y, var1.rotation());
      }

      var1.lifetime = 1.0F;
   }

   public void draw(Bullet var1) {
   }

   public void despawned(Bullet var1) {
      super.despawned(var1);
      if (!Vars.net.client()) {
         ZigzagBall var2 = new ZigzagBall();
         var2.launch(var1.rotation(), var1.team);
         AnomalyManager.place(var2, var1.x, var1.y);
         var2.age = 0.0F;
         var2.life = 110.0F;
         var2.origin(var1.owner, var1.x, var1.y);
         var2.orad = 150.0F;
      }
   }
}
