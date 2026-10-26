package anomalies;

import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;

/** Regular ball lightning shot (same ball as the Anomaly Turret fires), used by the Arux cannon. */
public class ArUxShot extends BulletType {
   public ArUxShot() {
      super(2.2F, 0.0F);
      this.lifetime = 1.0F;
      this.collides = false;
      this.collidesTiles = false;
      this.collidesAir = false;
      this.collidesGround = false;
      this.absorbable = false;
      this.hittable = false;
      this.reflectable = false;
      this.scaleLife = false;
      this.rangeOverride = 110.0F;
      this.shootEffect = Fx.none;
      this.smokeEffect = Fx.none;
      this.hitEffect = Fx.none;
      this.despawnEffect = Fx.none;
   }

   public void draw(Bullet var1) {
   }

   public void despawned(Bullet var1) {
      super.despawned(var1);
      if (!Vars.net.client()) {
         TurretBall var2 = new TurretBall();
         var2.launch(var1.rotation(), var1.team);
         AnomalyManager.place(var2, var1.x, var1.y);
         var2.age = 0.0F;
         var2.life = 50.0F;
         var2.origin(var1.owner, var1.x, var1.y);
      }
   }
}
