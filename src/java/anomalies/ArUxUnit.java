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
   /** the slit ends at the front edge of the hull: the laser starts there */
   static final float LASER_Y = 8.0F;
   static final Color yellow = Color.valueOf("ffd21f");
   TextureRegion cannonTeam;
   /** 0 = upper part (at the hull), 1 = knee part, 2 = foot */
   final TextureRegion[] legSprites = new TextureRegion[3];

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

      // 1) the laser: the Sublimate flame (same bullet type and values), yellow. It must be a fresh bullet (not a copy of the
      // turret's one): a copy is never loaded by the game and draws the "Oh no" missing texture.
      ContinuousFlameBulletType flame = new ContinuousFlameBulletType();
      flame.damage = 78.0F;
      flame.length = 125.0F;
      flame.knockback = 1.0F;
      flame.pierceCap = 2;
      flame.buildingDamageMultiplier = 0.3F;
      flame.colors = new Color[]{
         Color.valueOf("ff9e2f").a(0.55F), Color.valueOf("ffc83a").a(0.7F), Color.valueOf("ffe14a").a(0.8F), Color.valueOf("fff2a8"), Color.white};
      flame.lightColor = yellow;
      flame.hitColor = yellow;
      flame.lifetime = 120.0F; // one burst of the laser, then it reloads
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
      laser.reload = 240.0F;
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
      shocker.bullet = new ShockBolt(22.0F);
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
      this.legSprites[0] = Core.atlas.find(this.name + "-leg-upper");
      this.legSprites[1] = Core.atlas.find(this.name + "-leg-mid");
      this.legSprites[2] = Core.atlas.find(this.name + "-leg-foot");
      // our own leg sprites are drawn in draw(): make the default leg pictures invisible
      TextureRegion clear = Core.atlas.find("clear");
      this.legRegion = clear;
      this.legBaseRegion = clear;
      this.footRegion = clear;
      if (Core.atlas.has(this.name + "-icon")) {
         TextureRegion icon = Core.atlas.find(this.name + "-icon");
         this.fullIcon = icon;
         this.uiIcon = icon;
      }
   }

   public void draw(Unit unit) {
      float z = unit.elevation > 0.5F ? (this.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : this.groundLayer;
      drawLegSprites(unit, z);
      super.draw(unit);
   }

   /** Every leg: foot at the bottom, the knee part lies on the foot, the upper part lies on the knee part (the hull is drawn over all). */
   void drawLegSprites(Unit unit, float z) {
      if (!(unit instanceof Legsc)) return;
      Leg[] legs = ((Legsc)unit).legs();
      if (legs == null) return;
      TextureRegion upper = this.legSprites[0], mid = this.legSprites[1], foot = this.legSprites[2];
      float cr = Mathf.cosDeg(unit.rotation), sr = Mathf.sinDeg(unit.rotation);
      for (int pass = 0; pass < 3; ++pass) { // 0 = feet, 1 = knee parts, 2 = upper parts
         Draw.z(z - 0.04F + 0.01F * pass);
         for (Leg leg : legs) {
            float fx = leg.base.x, fy = leg.base.y;
            float jx = leg.joint.x, jy = leg.joint.y;
            float toFoot = Angles.angle(unit.x, unit.y, fx, fy);
            float hx = unit.x + Angles.trnsx(toFoot, this.legBaseOffset);
            float hy = unit.y + Angles.trnsy(toFoot, this.legBaseOffset);
            // +1 for legs on the right side of the unit, -1 on the left (the sprites are mirrored)
            float side = ((fx - unit.x) * sr - (fy - unit.y) * cr) >= 0.0F ? 1.0F : -1.0F;
            float a1 = Angles.angle(hx, hy, jx, jy); // upper part: hip -> knee
            float a2 = Angles.angle(jx, jy, fx, fy); // knee part and foot: knee -> foot
            if (pass == 0) {
               // the toe sits on the ground point, the inner end is hidden under the knee part
               float half = foot.width * Draw.scl * 0.5F - 1.0F;
               piece(foot, fx - Angles.trnsx(a2, half), fy - Angles.trnsy(a2, half), a2, side);
            } else if (pass == 1) {
               // starts a little before the knee and lies on the foot
               float half = mid.width * Draw.scl * 0.5F - 0.75F;
               piece(mid, jx + Angles.trnsx(a2, half), jy + Angles.trnsy(a2, half), a2, side);
            } else {
               // its outer end reaches a little over the knee and lies on the knee part, the inner end is under the hull
               float half = upper.width * Draw.scl * 0.5F - 0.75F;
               piece(upper, jx - Angles.trnsx(a1, half), jy - Angles.trnsy(a1, half), a1, side);
            }
         }
      }
      Draw.reset();
   }

   static void piece(TextureRegion r, float x, float y, float angle, float side) {
      Draw.rect(r, x, y, r.width * Draw.scl, r.height * Draw.scl * side, angle);
   }
}
