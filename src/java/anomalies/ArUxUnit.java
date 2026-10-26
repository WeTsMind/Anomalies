package anomalies;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import mindustry.entities.Leg;
import mindustry.content.Blocks;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ContinuousBulletType;
import mindustry.entities.bullet.ContinuousFlameBulletType;
import mindustry.entities.bullet.LightningBulletType;
import mindustry.gen.LegsUnit;
import mindustry.gen.Legsc;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import arc.audio.Sound;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ContinuousTurret;
import mindustry.world.blocks.defense.turrets.Turret;

/**
 * Arux: upgrade of Alloy in the Multiplicative Reconstructor. Six legs (three per side), every leg is made of three sprites:
 * the foot (arux-leg-foot), the middle part (arux-leg-mid) lies over the foot, the upper part (arux-leg-upper) lies over the
 * middle part and the hull lies over the upper part. Sprites are drawn as given on the right side and mirrored on the left.
 * Sprite layout (all pieces cut from one 256x256 canvas, 4 px = 1 world unit, unit centre = centre of arux.png):
 * - yellow Sublimate-style laser from the slit in the middle of the hull,
 * - cannon in the slit (white rear part is painted in the team colour), fires regular ball lightning,
 * - two shockers on the front horns: short yellow lightning (3 blocks), they jerk back after every shot.
 */
public class ArUxUnit extends UnitType {
   /** cannon centre, world units from the unit centre (forward = +y) */
   static final float CANNON_Y = -1.75F;
   static final float LASER_LEN = 100.0F;
   static final Color yellow = Color.valueOf("ffd21f");
   static final Color legDark = Color.valueOf("313238");
   static final Color legOrange = Color.valueOf("d4816b");
   TextureRegion cannonTeam;
   final TextureRegion[] legSprites = new TextureRegion[3];
   static final float[] fwd = new float[6];
   static final float[] rgt = new float[6];

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
      this.legLength = 12.0F;
      this.legBaseOffset = 5.0F;
      this.legMoveSpace = 1.0F;
      this.legForwardScl = 0.7F;
      this.legContinuousMove = true;
      this.legStraightness = 0.25F;
      this.legMaxLength = 1.1F;
      this.legMinLength = 0.2F;
      this.legLengthScl = 0.96F;
      this.range = LASER_LEN;
      this.maxRange = LASER_LEN;

      // 1) the yellow laser out of the slit: the very same continuous flame as the Sublimate turret, only yellow-tuned damage / length
      BulletType flame = null;
      try {
         if (Blocks.sublimate instanceof ContinuousTurret) {
            BulletType b = ((ContinuousTurret)Blocks.sublimate).shootType;
            if (b != null) flame = b.copy();
         }
      } catch (Throwable ignored) {
      }
      if (flame == null) {
         ContinuousFlameBulletType f = new ContinuousFlameBulletType();
         f.colors = new Color[]{Color.valueOf("ff9e2f").a(0.55F), Color.valueOf("ffc83a").a(0.7F), Color.valueOf("ffe14a").a(0.85F), Color.valueOf("fff2a8"), Color.white};
         flame = f;
      }
      flame.damage = 18.0F;
      flame.lifetime = 100.0F;
      if (flame instanceof ContinuousBulletType) {
         ((ContinuousBulletType)flame).length = LASER_LEN;
      }
      Weapon laser = new Weapon();
      laser.x = 0.0F;
      laser.y = 4.0F;
      laser.shootY = 0.0F;
      laser.mirror = false;
      laser.rotate = false;
      laser.continuous = true;
      laser.reload = 260.0F;
      laser.recoil = 0.0F;
      laser.shootSound = soundOf(Blocks.sublimate, Sounds.shoot);
      laser.bullet = flame;
      this.weapons.add(laser);

      // 2) the cannon in the slit: regular ball lightning
      Weapon cannon = new Weapon("anomalies-arux-cannon");
      cannon.x = 0.0F;
      cannon.y = CANNON_Y;
      cannon.shootY = 5.75F; // muzzle sits at +4 from the unit centre
      cannon.mirror = false;
      cannon.rotate = false;
      cannon.recoil = 0.0F;
      cannon.shootCone = 12.0F;
      cannon.reload = 120.0F;
      cannon.shootSound = Sounds.shoot;
      cannon.bullet = new ArUxShot();
      this.weapons.add(cannon);

      // 3) shockers: short yellow lightning (3 blocks), hard recoil after the shot
      LightningBulletType bolt = new LightningBulletType();
      bolt.damage = 22.0F;
      bolt.lightningLength = 4;
      bolt.lightningColor = yellow;
      bolt.rangeOverride = 24.0F;
      Weapon shocker = new Weapon("anomalies-arux-shocker");
      shocker.x = 5.5F;
      shocker.y = 11.75F;
      shocker.shootY = 4.5F;
      shocker.mirror = true;
      shocker.alternate = false; // both shockers fire together
      shocker.rotate = false;
      shocker.shootCone = 40.0F;
      shocker.reload = 34.0F;
      shocker.recoil = 5.0F;
      shocker.recoilTime = 12.0F;
      shocker.shootSound = soundOf(Blocks.arc, Sounds.shoot);
      shocker.bullet = bolt;
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
      // the default leg / foot pictures are replaced by our own sprites (drawn in draw()), so make the default ones invisible
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
      // white rear part of the cannon in the team colour
      float rot = unit.rotation - 90.0F;
      float wx = unit.x + Angles.trnsx(rot, 0.0F, CANNON_Y);
      float wy = unit.y + Angles.trnsy(rot, 0.0F, CANNON_Y);
      Draw.z(z + 0.02F);
      Draw.color(unit.team.color);
      Draw.rect(this.cannonTeam, wx, wy, rot);
      Draw.reset();
   }

   /** Segmented legs: foot, then the middle part over it, then the upper part over the middle one (the hull is drawn over all of them). */
   void drawLegSprites(Unit unit, float z) {
      if (!(unit instanceof Legsc)) return;
      Leg[] legs = ((Legsc)unit).legs();
      if (legs == null) return;
      TextureRegion upper = this.legSprites[0], mid = this.legSprites[1], foot = this.legSprites[2];
      float cr = Mathf.cosDeg(unit.rotation), sr = Mathf.sinDeg(unit.rotation);
      // pass 0 = feet (bottom), 1 = middle parts, 2 = upper parts (top)
      for (int pass = 0; pass < 3; ++pass) {
         Draw.z(z - 0.04F + 0.01F * pass);
         for (Leg leg : legs) {
            float fx = leg.base.x, fy = leg.base.y;
            float jx = leg.joint.x, jy = leg.joint.y;
            float ang = Angles.angle(unit.x, unit.y, fx, fy);
            float rx = unit.x + Angles.trnsx(ang, this.legBaseOffset), ry = unit.y + Angles.trnsy(ang, this.legBaseOffset);
            float side = ((fx - unit.x) * sr - (fy - unit.y) * cr) >= 0.0F ? 1.0F : -1.0F;
            if (pass == 0) {
               float a = Angles.angle(jx, jy, fx, fy);
               Draw.rect(foot, fx, fy, foot.width * Draw.scl, foot.height * Draw.scl * side, a);
            } else if (pass == 1) {
               // from the knee to the foot: its outer end lies over the foot
               seg(mid, jx, jy, fx, fy, side);
            } else {
               // from the hull to the knee: its outer end lies over the middle part
               seg(upper, rx, ry, jx, jy, side);
            }
         }
      }
      Draw.reset();
   }

   /** Draws a sprite stretched between two points (sprite tip = the second point). */
   static void seg(TextureRegion r, float x1, float y1, float x2, float y2, float side) {
      float len = Mathf.dst(x1, y1, x2, y2);
      float a = Angles.angle(x1, y1, x2, y2);
      Draw.rect(r, (x1 + x2) * 0.5F, (y1 + y2) * 0.5F, Math.max(len, 1.0F), r.height * Draw.scl * side, a);
   }
}
