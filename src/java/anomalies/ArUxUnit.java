package anomalies;

import arc.Core;
import arc.audio.Sound;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.entities.Leg;
import mindustry.entities.bullet.ContinuousFlameBulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.LegsUnit;
import mindustry.gen.Legsc;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;

/**
 * Arux: upgrade of Alloy in the Multiplicative Reconstructor.
 * Sprites are cut from one 256x256 canvas (4 px = 1 world unit); the unit centre is the centre of arux.png.
 * - the yellow laser (the Sublimate flame in yellow) comes out of the slit in the middle of the hull,
 * - the cannon sits in the slit and turns towards the target, it fires the regular ball lightning,
 *   its white rear part is painted in the team colour and turns together with the cannon,
 * - two shockers under the hull: a short yellow lightning strike like the ball lightning one (3 tiles), they jerk back a little after every shot,
 * - six legs (three per side), every leg is three sprites lying on top of each other:
 *   foot (bottom), knee part over the foot, upper part over the knee part, the hull over everything.
 */
public class ArUxUnit extends UnitType {
   /** cannon centre, world units from the unit centre (forward = +y) */
   static final float CANNON_Y = -1.75F;
   /** the laser starts at the beginning of the cut in the hull (marked by the user), 17 px above the hull centre (the red dot of the user's picture) */
   static final float LASER_Y = 4.25F;
   static final Color yellow = Color.valueOf("ffd21f");
   /** centre of the leg hub, distance from the hip along the leg */
   static final float HUB_ARC = 3.0F;
   TextureRegion cannonTeam;

   public ArUxUnit(String name) {
      super(name);
      this.constructor = LegsUnit::create;
      this.alwaysUnlocked = true;
      this.health = 7500.0F;
      this.armor = 8.0F;
      this.speed = 0.55F;
      this.accel = 0.2F;
      this.drag = 0.11F;
      this.hitSize = 18.0F;
      this.rotateSpeed = 3.5F;
      this.faceTarget = true;
      this.outlines = false;
      this.drawCell = false;
      this.targetAir = true;
      // six legs: 3 per side (front, middle, rear)
      this.hovering = true;
      this.allowLegStep = true;
      this.lockLegBase = true;
      this.legCount = 6;
      this.legGroupSize = 3;
      this.legLength = 18.0F;
      this.legBaseOffset = 5.0F;
      this.legMoveSpace = 1.0F;
      this.legForwardScl = 0.7F;
      this.legContinuousMove = true;
      this.legStraightness = 0.25F;
      this.legMaxLength = 1.1F;
      this.legMinLength = 0.2F;
      this.legLengthScl = 0.96F;

      // 1) the laser: the Sublimate flame (same bullet type), yellow, 5 tiles long, 260 damage per second, wide beam with a smooth taper to a point. It must be a fresh bullet (not a copy of the
      // turret's one): a copy is never loaded by the game and draws the "Oh no" missing texture.
      ArUxLaser flame = new ArUxLaser();
      flame.damage = 260.0F / 12.0F; // hits every 5 ticks: 12 hits per second = 260 damage per second
      flame.length = 40.0F; // 5 tiles
      flame.knockback = 1.0F;
      flame.pierceCap = 2;
      flame.buildingDamageMultiplier = 1.0F;
      flame.colors = new Color[]{
         Color.valueOf("ff9e2f").a(0.55F), Color.valueOf("ffc83a").a(0.7F), Color.valueOf("ffe14a").a(0.8F), Color.valueOf("fff2a8"), Color.white};
      flame.lightColor = yellow;
      flame.hitColor = yellow;
      flame.lifetime = 120.0F; // kept alive by the weapon while the fire button is held
      float laserLen = flame.length;
      this.range = laserLen;
      this.maxRange = laserLen;
      Weapon laser = new Weapon();
      laser.x = 0.0F;
      laser.y = LASER_Y;
      laser.shootY = 0.0F;
      laser.mirror = false;
      laser.rotate = false;
      laser.continuous = true;
      laser.alwaysContinuous = true; // the laser stays on as long as the fire button is held
      laser.reload = 30.0F; // short pause after release
      laser.recoil = 0.0F;
      laser.shootSound = soundOf(Blocks.sublimate, Sounds.shoot);
      laser.bullet = flame;
      this.weapons.add(laser);

      // 2) the cannon in the slit: turns towards the target, fires the regular ball lightning (the one of the Anomaly Turret)
      Weapon cannon = new Weapon("anomalies-arux-cannon") {
         @Override
         public void draw(Unit unit, WeaponMount mount) {
            float z = Draw.z();
            super.draw(unit, mount);
            // the white rear part is painted in the team colour and turns together with the cannon
            ArUxUnit type = (ArUxUnit)unit.type;
            float base = unit.rotation - 90.0F;
            float wr = base + mount.rotation;
            float wx = unit.x + Angles.trnsx(base, this.x, this.y);
            float wy = unit.y + Angles.trnsy(base, this.x, this.y);
            Draw.z(z + this.layerOffset + 0.001F);
            Draw.color(unit.team.color);
            Draw.rect(type.cannonTeam, wx, wy, wr);
            Draw.reset();
            Draw.z(z);
         }
      };
      cannon.x = 0.0F;
      cannon.y = CANNON_Y;
      cannon.shootY = 5.75F; // half of the sprite length
      cannon.mirror = false;
      cannon.rotate = true;
      cannon.rotateSpeed = 8.0F;
      cannon.recoil = 0.0F;
      cannon.shootCone = 15.0F;
      cannon.reload = 120.0F;
      cannon.shootSound = Sounds.shoot;
      cannon.ejectEffect = Fx.none;
      AnomalyTurret.OrbShot ball = new AnomalyTurret.OrbShot();
      ball.rangeOverride = 110.0F;
      cannon.bullet = ball;
      this.weapons.add(cannon);

      // 3) shockers under the hull: short yellow lightning (3 tiles), a small jerk back after the shot
      Weapon shocker = new Weapon("anomalies-arux-shocker");
      shocker.x = 5.5F;
      shocker.y = 5.25F;
      shocker.shootY = 2.5F;
      shocker.mirror = true;
      shocker.alternate = false; // both shockers fire together
      shocker.rotate = false;
      shocker.top = false; // lies under the hull, only the tips look out
      shocker.shootCone = 40.0F;
      shocker.reload = 36.0F;
      shocker.recoil = 1.5F; // small jerk back, they stay visible
      shocker.recoilTime = 10.0F;
      shocker.shootSound = soundOf(Blocks.arc, Sounds.shoot);
      shocker.bullet = new ShockBolt(122.0F);
      this.weapons.add(shocker);
   }

   /** Shoot sound of a vanilla turret (falls back to the given sound). */
   static Sound soundOf(Block turret, Sound fallback) {
      try {
         if (turret instanceof Turret) {
            Sound snd = ((Turret)turret).shootSound;
            if (snd != null) return snd;
         }
      } catch (Throwable ignored) {
      }
      return fallback;
   }

   public void load() {
      super.load();
      this.cannonTeam = Core.atlas.find(this.name + "-cannon-team");
      // legs are drawn by the game itself, exactly like Alloy: hip->knee piece, knee->foot piece and the foot
      this.legRegion = Core.atlas.find(this.name + "-leg-upper");
      this.legBaseRegion = Core.atlas.find(this.name + "-leg-mid");
      this.footRegion = Core.atlas.find(this.name + "-leg-foot");
      if (Core.atlas.has(this.name + "-icon")) {
         TextureRegion icon = Core.atlas.find(this.name + "-icon");
         this.fullIcon = icon;
         this.uiIcon = icon;
      }
   }
}
