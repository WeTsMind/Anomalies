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
import mindustry.entities.pattern.ShootBarrel;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.BuildVisibility;

/**
 * Destroyer: a 2x2 item turret fed with copper, graphite or silicon; fires from both slits of the barrel at once every 0.55 s,
 * range 10 blocks. Sprites are cut from destroyer.ase, all on a 128x128 canvas (4 px = 1 world unit) whose centre is the
 * rotation centre; the base is base002.png (the same as the Demolisher's):
 * - destroyer.png the head (body); destroyer-barrel.png the barrel with its two slits, under the head;
 * - destroyer-gear.png the two small front gears beside the barrel (under it): they spin at every shot;
 * - destroyer-gear-big.png the two big rear gears under the head: they turn while the turret turns.
 * The whole head (barrel and gears with it) kicks back a little at every shot.
 * Ammo (per bullet, two bullets per shot): copper 20, graphite 30, silicon 25 and homing.
 */
public class DestroyerTurret extends ItemTurret {
   /** the two slits of the barrel: side offset, world units */
   static final float SLIT_X = 1.875F;
   /** front gears (local units: x to the side, y forward) */
   static final float FRONT_X = 5.125F, FRONT_Y = 7.125F;
   /** rear gears: the left and the right one sit a pixel apart in the drawing */
   static final float REAR_LX = -4.375F, REAR_LY = -2.375F, REAR_RX = 4.125F, REAR_RY = -2.625F;
   /** front gear spin right after a shot, degrees per tick, and how fast it dies down (per tick) */
   static final float FRONT_SPIN = 16.0F, FRONT_SLOW = 0.045F;
   /** rear gears turn this many degrees per degree the turret turns */
   static final float REAR_RATIO = 3.0F;

   TextureRegion barrelRegion, gearRegion, bigGearRegion;

   public DestroyerTurret(String name) {
      super(name);
      this.size = 2;
      this.health = 900;
      this.outlineIcon = false; // the sprites have their own outline
      this.requirements(Category.turret, BuildVisibility.shown, ItemStack.with(new Object[]{Items.copper, 120, Items.graphite, 60, Items.silicon, 40}));
      this.reload = 33.0F; // 0.55 s
      this.range = 80.0F; // 10 blocks
      this.rotateSpeed = 5.0F;
      this.recoil = 1.25F; // a small kick of the whole head
      this.recoilTime = 16.0F;
      this.shootY = 10.5F; // the opening of the slits
      this.inaccuracy = 0.0F;
      this.targetAir = true;
      this.targetGround = true;
      this.shootSound = Sounds.shoot;
      this.ammoUseEffect = Fx.casing2;
      this.ammoPerShot = 1;
      this.maxAmmo = 40;
      // both slits at once
      ShootBarrel pattern = new ShootBarrel();
      pattern.barrels = new float[]{-SLIT_X, 0.0F, 0.0F, SLIT_X, 0.0F, 0.0F};
      pattern.shots = 2;
      pattern.shotDelay = 0.0F;
      this.shoot = pattern;

      this.ammo(new Object[]{
         Items.copper, shot(20.0F, 3.4F, Color.valueOf("f9a27a"), Color.valueOf("d99d73")),
         Items.graphite, heavy(),
         Items.silicon, homing()
      });
      this.buildType = () -> new DestroyerBuild();
   }

   /** a regular bullet that just reaches a little past the range */
   static BasicBulletType shot(float damage, float speed, Color front, Color back) {
      BasicBulletType b = new BasicBulletType(speed, damage);
      b.width = 8.0F;
      b.height = 11.0F;
      b.lifetime = 86.0F / speed;
      b.frontColor = front;
      b.backColor = back;
      b.hitColor = back;
      b.shootEffect = Fx.shootSmall;
      b.smokeEffect = Fx.shootSmallSmoke;
      b.hitEffect = Fx.hitBulletColor;
      b.despawnEffect = Fx.hitBulletColor;
      return b;
   }

   /** graphite: 30 damage, a heavier bullet */
   static BasicBulletType heavy() {
      BasicBulletType b = shot(30.0F, 3.6F, Color.valueOf("b5b8c9"), Color.valueOf("5e6170"));
      b.width = 9.0F;
      b.height = 13.0F;
      return b;
   }

   /** silicon: 25 damage, turns towards the target */
   static BasicBulletType homing() {
      BasicBulletType b = shot(25.0F, 3.4F, Color.valueOf("eef1ff"), Color.valueOf("8c90a8"));
      b.homingPower = 0.35F;
      b.homingRange = 60.0F;
      b.trailLength = 6;
      b.trailWidth = 1.5F;
      b.trailColor = b.backColor;
      return b;
   }

   public void load() {
      super.load();
      TurretPatch.base002(this);
      this.barrelRegion = Core.atlas.find(this.name + "-barrel");
      this.gearRegion = Core.atlas.find(this.name + "-gear");
      this.bigGearRegion = Core.atlas.find(this.name + "-gear-big");
      if (Core.atlas.has(this.name + "-icon")) {
         TextureRegion icon = Core.atlas.find(this.name + "-icon");
         this.fullIcon = icon;
         this.uiIcon = icon;
      }
   }

   public class DestroyerBuild extends ItemTurret.ItemTurretBuild {
      float lastHeat;
      /** front gears: angle and current spin; rear gears: angle; the turret rotation on the previous update */
      float frontRot, frontSpin, rearRot;
      float lastRotation = Float.NaN;

      public DestroyerBuild() {
         super();
      }

      @Override
      public void updateTile() {
         super.updateTile();
         // the rear gears follow the turning of the turret
         if (!Float.isNaN(this.lastRotation)) {
            this.rearRot = Mathf.mod(this.rearRot + DestroyerTurret.signed(this.rotation, this.lastRotation) * REAR_RATIO, 360.0F);
         }
         this.lastRotation = this.rotation;
         this.frontSpin = Mathf.approachDelta(this.frontSpin, 0.0F, FRONT_SPIN * FRONT_SLOW);
         this.frontRot = Mathf.mod(this.frontRot + this.frontSpin * Time.delta, 360.0F);
      }

      public void draw() {
         // a shot: the heat of the turret jumps up -> the front gears get a spin
         if (this.heat - this.lastHeat > 0.5F) {
            this.frontSpin = FRONT_SPIN;
         }
         this.lastHeat = this.heat;
         float r = this.rotation - 90.0F;
         float c = Mathf.cosDeg(this.rotation), s = Mathf.sinDeg(this.rotation);
         float ox = this.x + this.recoilOffset.x, oy = this.y + this.recoilOffset.y; // everything kicks back with the head

         // gears and barrel go on the head's own layer, right before the head: under their own head, never under a neighbour
         float z = Draw.z();
         Draw.z(Layer.turret);
         // front gears (bottom), the left and the right one turn in opposite directions like meshing gears
         if (DestroyerTurret.this.gearRegion.found()) {
            for (int side = -1; side <= 1; side += 2) {
               Draw.rect(DestroyerTurret.this.gearRegion, ox + c * FRONT_Y + s * FRONT_X * side, oy + s * FRONT_Y - c * FRONT_X * side, r + side * this.frontRot);
            }
         }
         // the barrel
         if (DestroyerTurret.this.barrelRegion.found()) {
            Draw.rect(DestroyerTurret.this.barrelRegion, ox, oy, r);
         }
         // rear gears
         if (DestroyerTurret.this.bigGearRegion.found()) {
            Draw.rect(DestroyerTurret.this.bigGearRegion, ox + c * REAR_LY + s * REAR_LX, oy + s * REAR_LY - c * REAR_LX, r - this.rearRot);
            Draw.rect(DestroyerTurret.this.bigGearRegion, ox + c * REAR_RY + s * REAR_RX, oy + s * REAR_RY - c * REAR_RX, r + this.rearRot);
         }
         Draw.z(z);

         // base and head; no shadow of the tower
         TurretPatch.shadowOff();
         super.draw();
         TurretPatch.shadowOn();
      }
   }

   /** signed angle from b to a, -180..180 */
   static float signed(float a, float b) {
      return ((a - b) % 360.0F + 540.0F) % 360.0F - 180.0F;
   }
}
