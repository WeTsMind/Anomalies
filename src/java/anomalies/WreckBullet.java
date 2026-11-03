package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;

/**
 * The glowing shell of the Demolisher turret (sprite demolisher-bullet.png, tinted and made to glow in the colour of the ammo).
 * Optional extras: pushing the enemy unit back (knockDist, world units) and a slowing status (set status / statusDuration).
 */
public class WreckBullet extends BulletType {
   static final Effect shootFx = new Effect(14.0F, e -> {
      Draw.color(Color.white, e.color, e.fin());
      Fill.circle(e.x, e.y, 2.2F * e.fout());
      Draw.alpha(0.6F * e.fout());
      Fill.circle(e.x, e.y, 4.5F * e.fout());
      Draw.reset();
   });
   static final Effect hitFx = new Effect(18.0F, e -> {
      Draw.color(Color.white, e.color, e.fin());
      Draw.alpha(e.fout());
      Fill.circle(e.x, e.y, 3.2F * e.fout());
      Draw.color(e.color);
      arc.graphics.g2d.Lines.stroke(1.2F * e.fout());
      arc.graphics.g2d.Lines.circle(e.x, e.y, 2.0F + e.fin() * 7.0F);
      Draw.reset();
   });
   public Color color;
   /** how far an enemy unit is pushed away when it is hit, world units (8 = one block) */
   public float knockDist = 0.0F;
   /** sprite of the shell, with the mod prefix */
   public String spriteName = "anomalies-demolisher-bullet";
   /** true: the sprite spins; false: it points where the shell flies (the sprite looks up) */
   public boolean spin = true;
   public float spriteScale = 1.3F;
   TextureRegion region;

   public WreckBullet(float speed, float damage, Color color) {
      super(speed, damage);
      this.color = color;
      this.lifetime = 34.0F;
      this.hitSize = 6.0F;
      this.collidesAir = true;
      this.collidesGround = true;
      this.pierce = false;
      this.trailLength = 9;
      this.trailWidth = 1.6F;
      this.trailColor = color;
      this.hitColor = color;
      this.shootEffect = shootFx;
      this.smokeEffect = mindustry.content.Fx.none;
      this.hitEffect = hitFx;
      this.despawnEffect = hitFx;
   }

   public void load() {
      super.load();
      this.region = Core.atlas.find(this.spriteName);
   }

   public void hitEntity(Bullet b, Hitboxc entity, float health) {
      super.hitEntity(b, entity, health);
      if (this.knockDist > 0.0F && entity instanceof Unit && !Vars.net.client()) {
         Unit u = (Unit)entity;
         u.move(Mathf.cosDeg(b.rotation()) * this.knockDist, Mathf.sinDeg(b.rotation()) * this.knockDist);
      }
   }

   public void draw(Bullet b) {
      super.draw(b);
      float spin = Time.time * 6.0F + b.id * 40.0F;
      float pulse = 0.85F + 0.15F * Mathf.sin(Time.time * 0.5F + b.id);
      if (this.region != null && this.region.found()) {
         float size = this.region.width * Draw.scl * this.spriteScale;
         float rot = this.spin ? spin : b.rotation() - 90.0F;
         Draw.color(this.color);
         Draw.rect(this.region, b.x, b.y, size, size, rot);
         Draw.blend(Blending.additive);
         Draw.color(this.color, 0.55F * pulse);
         Draw.rect(this.region, b.x, b.y, size * 1.35F, size * 1.35F, this.spin ? -spin * 0.7F : rot);
         Draw.color(this.color, 0.22F * pulse);
         Fill.circle(b.x, b.y, 5.5F);
         Draw.blend();
      } else {
         Draw.color(this.color);
         Fill.circle(b.x, b.y, 3.0F);
      }
      Draw.reset();
      Drawf.light(b.x, b.y, 22.0F, this.color, 0.6F);
   }
}
