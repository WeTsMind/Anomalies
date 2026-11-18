package anomalies;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.BuildVisibility;

/**
 * Flinger: a cheap 1x1 item turret (30 copper) fed with copper, scrap or graphite; fires every 0.85 s, range 7 blocks.
 * Sprites are cut from flinger.ase, all on a 64x64 canvas (4 px = 1 world unit) whose centre is the rotation centre:
 * - flinger-base.png the base; flinger.png the head (does not move);
 * - flinger-barrel.png the barrel, drawn under the head, kicks back a little at every shot;
 * - flinger-gear.png the two gears beside the barrel (13x13, the centre of the sprite is the pivot), drawn under the barrel;
 *   they spin at every shot (the left one and the right one in opposite directions, like meshing gears) and slow down.
 * Ammo: copper 8 damage, scrap 7 damage + shards, graphite 13 damage.
 */
public class FlingerTurret extends ItemTurret {
   /** how far the barrel kicks back, world units */
   static final float KICK = 1.5F;
   /** how long the kick lasts, ticks */
   static final float KICK_TIME = 18.0F;
   /** gear pivots (local units: x to the side, y forward) */
   static final float GEAR_X = 2.375F, GEAR_Y = 2.375F;
   /** gear spin right after a shot, degrees per tick; it slows down to 0 */
   static final float GEAR_SPIN = 14.0F;
   /** how fast the gear spin dies down, per tick */
   static final float GEAR_SLOW = 0.04F;

   TextureRegion barrelRegion, gearRegion;

   public FlingerTurret(String name) {
      super(name);
      this.size = 1; // the base is 32x32 (one block), the head overhangs it a little, like the Duo
      this.health = 160;
      this.outlineIcon = false; // no dark outline generated around the head; the sprite is drawn as it is
      this.requirements(Category.turret, BuildVisibility.shown, ItemStack.with(new Object[]{Items.copper, 30}));
      this.reload = 51.0F; // 0.85 s
      this.range = 56.0F; // 7 blocks
      this.rotateSpeed = 8.0F;
      this.recoil = 0.0F; // the head stays in place, only the barrel kicks back
      this.shootY = 6.0F; // the tip of the barrel
      this.inaccuracy = 1.0F;
      this.targetAir = true;
      this.targetGround = true;
      this.shootSound = Sounds.shoot;
      this.ammoUseEffect = Fx.casing1;
      this.maxAmmo = 30;

      this.ammo(new Object[]{
         Items.copper, shot(8.0F, 2.6F, Color.valueOf("f9a27a"), Color.valueOf("d99d73")),
         Items.scrap, scrapShot(),
         Items.graphite, graphiteShot()
      });
      this.buildType = () -> new FlingerBuild();
   }

   /** a regular bullet that flies a bit further than the range */
   static BasicBulletType shot(float damage, float speed, Color front, Color back) {
      BasicBulletType b = new BasicBulletType(speed, damage);
      b.width = 7.0F;
      b.height = 9.0F;
      b.lifetime = 60.0F / speed;
      b.frontColor = front;
      b.backColor = back;
      b.hitColor = back;
      b.shootEffect = Fx.shootSmall;
      b.smokeEffect = Fx.shootSmallSmoke;
      b.hitEffect = Fx.hitBulletColor;
      b.despawnEffect = Fx.hitBulletColor;
      return b;
   }

   /** scrap: 7 damage, breaks into 3 small shards when it hits or ends */
   static BasicBulletType scrapShot() {
      BasicBulletType b = shot(7.0F, 2.5F, Color.valueOf("d1c5b9"), Color.valueOf("7a6e64"));
      BasicBulletType shard = new BasicBulletType(2.4F, 3.0F);
      shard.width = 4.0F;
      shard.height = 6.0F;
      shard.lifetime = 9.0F;
      shard.frontColor = Color.valueOf("d1c5b9");
      shard.backColor = Color.valueOf("7a6e64");
      shard.hitColor = shard.backColor;
      shard.hitEffect = Fx.hitBulletColor;
      shard.despawnEffect = Fx.none;
      b.fragBullet = shard;
      b.fragBullets = 3;
      b.fragRandomSpread = 70.0F;
      b.fragVelocityMin = 0.7F;
      b.fragVelocityMax = 1.1F;
      return b;
   }

   /** graphite: a heavier bullet, 13 damage */
   static BasicBulletType graphiteShot() {
      BasicBulletType b = shot(13.0F, 3.0F, Color.valueOf("b5b8c9"), Color.valueOf("5e6170"));
      b.width = 9.0F;
      b.height = 12.0F;
      return b;
   }

   public void load() {
      super.load();
      this.barrelRegion = Core.atlas.find(this.name + "-barrel");
      this.gearRegion = Core.atlas.find(this.name + "-gear");
      if (Core.atlas.has(this.name + "-icon")) {
         TextureRegion icon = Core.atlas.find(this.name + "-icon");
         this.fullIcon = icon;
         this.uiIcon = icon;
      }
   }

   public class FlingerBuild extends ItemTurret.ItemTurretBuild {
      float lastHeat;
      float shotTime = -100000.0F;
      /** gear angle and its current spin speed, degrees / degrees per tick */
      float gearRot, gearSpin;

      public FlingerBuild() {
         super();
      }

      @Override
      public void updateTile() {
         super.updateTile();
         this.gearSpin = Mathf.approachDelta(this.gearSpin, 0.0F, GEAR_SPIN * GEAR_SLOW);
         this.gearRot = Mathf.mod(this.gearRot + this.gearSpin * Time.delta, 360.0F);
      }

      public void draw() {
         // a shot: the heat of the turret jumps up
         if (this.heat - this.lastHeat > 0.5F) {
            this.shotTime = Time.time;
            this.gearSpin = GEAR_SPIN;
         }
         this.lastHeat = this.heat;
         float k = (float)Math.pow((double)Mathf.clamp(1.0F - (Time.time - this.shotTime) / KICK_TIME), 1.6D) * KICK;
         float r = this.rotation - 90.0F;
         float c = Mathf.cosDeg(this.rotation), s = Mathf.sinDeg(this.rotation);

         // gears and barrel go on the head's own layer, right before the head: under their own head, never under a neighbour
         float z = Draw.z();
         Draw.z(Layer.turret);
         if (FlingerTurret.this.gearRegion.found()) {
            for (int side = -1; side <= 1; side += 2) {
               // local (x to the side, y forward) -> world
               float gx = this.x + c * GEAR_Y + s * GEAR_X * side, gy = this.y + s * GEAR_Y - c * GEAR_X * side;
               Draw.rect(FlingerTurret.this.gearRegion, gx, gy, r + side * this.gearRot);
            }
         }
         if (FlingerTurret.this.barrelRegion.found()) {
            Draw.rect(FlingerTurret.this.barrelRegion, this.x - c * k, this.y - s * k, r);
         }
         Draw.z(z);

         // base and head; no shadow of the tower
         TurretPatch.shadowOff();
         super.draw();
         TurretPatch.shadowOn();
      }
   }
}
