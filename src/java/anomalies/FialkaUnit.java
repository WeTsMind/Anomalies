package anomalies;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.ElevationMoveUnit;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

import java.util.WeakHashMap;

/**
 * Fialka: a ground hover unit with six legs and a bending tail. Sprites are cut from fialka.ase (4 px = 1 world unit), every
 * piece is centred on its own pivot:
 * - fialka.png the body, fialka-cell.png the cell (team colour);
 * - fialka-leg-0..5.png the legs (left front, left middle, left rear, right front, right middle, right rear): while the unit moves
 *   each leg turns 30 degrees backwards around the point where it enters the body, and while the unit turns all legs swing
 *   up to 30 degrees towards the turn (turning left: all of them to the left, and the other way round); a hover ring (the Elude
 *   effect) sits under every leg foot and moves with it;
 * - fialka-gun.png the barrel of the main gun (turns, recoils), fialka-gun-base.png its base drawn on top (turns, no recoil);
 * - fialka-tail-1..3.png the tail segments and fialka-tail-gun.png the tail gun base, a chain that bends like TEST01;
 *   fialka-tail-barrel.png the tail gun muzzle: part of the tail gun base (does not bend), it only recoils.
 * When the unit shoots, the tail curls round via the left until the tail gun looks forward, then the tail gun fires
 * (fialka-bullet.png, glowing violet with a trail, 150 damage, every second); the tail gets a small kick at every tail shot.
 * The main gun fires the Demolisher shell in glowing violet (110 damage, every 0.85 s).
 */
public class FialkaUnit extends UnitType {
   /** legs: pivot (local units: x to the side, y forward) and the foot centre relative to the pivot */
   static final float[][] LEG_PIVOT = {{-2.0F, 2.0F}, {-2.25F, 0.0F}, {-2.0F, -2.25F}, {2.0F, 2.0F}, {2.25F, 0.0F}, {2.0F, -2.25F}};
   static final float[][] LEG_FOOT = {{-5.68F, 5.68F}, {-8.23F, 0.0F}, {-5.80F, -5.52F}, {5.72F, 5.74F}, {8.31F, 0.0F}, {5.85F, -5.58F}};
   /** how far the legs turn back while moving, degrees */
   static final float LEG_TILT = 30.0F;
   /** how far all the legs swing towards the side the unit is turning to, degrees (at full turn speed) */
   static final float LEG_TURN = 30.0F;
   /** where the tail is attached: this far behind the unit centre */
   static final float TAIL_ROOT = 8.0F;
   /** distance from one tail joint to the next (segment 1 -> 2 -> 3 -> gun) */
   static final float[] TAIL_LEN = {4.0F, 3.5F, 2.25F};
   /** how fast each tail piece follows the previous one */
   static final float[] FOLLOW = {0.22F, 0.19F, 0.16F, 0.14F};
   /** how much every joint bends when the tail is fully curled: 4 x 45 = 180 degrees, so the tail gun looks forward */
   static final float[] CURL_BEND = {45.0F, 45.0F, 45.0F, 45.0F};
   /** how far the tail gun may bend towards the target on top of the curl, degrees */
   static final float GUN_BEND = 35.0F;
   /** main gun pivot (behind the unit centre) */
   static final float GUN_Y = -5.0F;

   static final WeakHashMap<Unit, Tail> TAILS = new WeakHashMap<>();
   static final Color HOVER = Color.valueOf("bf92f9");

   TextureRegion[] legs = new TextureRegion[6];
   TextureRegion[] tail = new TextureRegion[4];
   TextureRegion tailBarrel, gunBase;
   Weapon main;
   TailWeapon tailGun;

   /** per unit tail and leg state */
   static class Tail {
      /** absolute direction of every tail piece, pointing away from the body */
      float[] ang = new float[4];
      /** joint positions (world) */
      float[] jx = new float[4], jy = new float[4];
      /** 0 = straight back, 1 = curled round so the tail gun looks forward */
      float curl;
      /** tail kick after a tail shot, 1 -> 0 */
      float kick;
      /** leg tilt while moving, 0..1 */
      float tilt;
      /** how fast the unit is turning: -1 (right, full speed) .. 1 (left, full speed), smoothed */
      float turn;
      /** the unit rotation on the previous update */
      float lastRot = Float.NaN;
   }

   public FialkaUnit(String name) {
      super(name);
      this.constructor = ElevationMoveUnit::create; // levitates like the Elude
      this.alwaysUnlocked = true;
      this.flying = false;
      this.hovering = true;
      this.canDrown = false;
      this.health = 1300.0F;
      this.armor = 5.0F;
      this.speed = 20.0F * 8.0F / 60.0F; // 20 tiles per second
      this.hitSize = 20.0F;
      this.rotateSpeed = 2.5F; // smooth, not jerky turns
      this.faceTarget = true; // the body turns to the target, so the curled tail gun can look at it
      this.targetAir = true;
      this.targetGround = true;
      this.outlines = false;
      this.drawCell = true;
      this.engineSize = 0.0F;

      // the main gun: turns, its muzzle recoils a little
      Weapon gun = new Weapon("anomalies-fialka-gun");
      gun.x = 0.0F;
      gun.y = GUN_Y;
      gun.shootY = 7.25F;
      gun.mirror = false;
      gun.rotate = true;
      gun.rotateSpeed = 5.0F;
      gun.reload = 51.0F; // 0.85 s
      gun.recoil = 1.25F;
      gun.recoilTime = 18.0F;
      gun.shootCone = 6.0F;
      gun.ejectEffect = Fx.none;
      gun.shootSound = ModSounds.demolisherShot();
      gun.bullet = FialkaShot.main();
      this.weapons.add(gun);
      this.main = gun;

      // the tail gun: sits on the end of the tail, fires only when the tail is curled forward
      TailWeapon t = new TailWeapon("anomalies-fialka-tail");
      t.mirror = false;
      t.rotate = false;
      t.reload = 60.0F; // 1 s
      t.recoil = 1.5F;
      t.recoilTime = 16.0F;
      t.shootY = 6.0F;
      t.shootCone = 20.0F;
      t.ejectEffect = Fx.none;
      t.shootSound = ModSounds.lavandaShot();
      t.bullet = FialkaShot.tail();
      this.weapons.add(t);
      this.tailGun = t;
   }

   public void load() {
      super.load();
      for (int i = 0; i < 6; i++) this.legs[i] = Core.atlas.find(this.name + "-leg-" + i);
      for (int i = 0; i < 3; i++) this.tail[i] = Core.atlas.find(this.name + "-tail-" + (i + 1));
      this.tail[3] = Core.atlas.find(this.name + "-tail-gun");
      this.tailBarrel = Core.atlas.find(this.name + "-tail-barrel");
      this.gunBase = Core.atlas.find(this.name + "-gun-base");
      if (Core.atlas.has(this.name + "-icon")) {
         TextureRegion icon = Core.atlas.find(this.name + "-icon");
         this.fullIcon = icon;
         this.uiIcon = icon;
      }
   }

   static Tail state(Unit unit) {
      Tail t = TAILS.get(unit);
      if (t == null) {
         t = new Tail();
         for (int i = 0; i < 4; i++) t.ang[i] = unit.rotation + 180.0F;
         TAILS.put(unit, t);
         pose(unit, t);
      }
      return t;
   }

   /** joint positions from the tail angles; the tail kick squeezes the tail towards the body */
   static void pose(Unit unit, Tail t) {
      float x = unit.x - Mathf.cosDeg(unit.rotation) * TAIL_ROOT, y = unit.y - Mathf.sinDeg(unit.rotation) * TAIL_ROOT;
      float k = 1.0F - 0.2F * t.kick;
      for (int i = 0; i < 4; i++) {
         t.jx[i] = x;
         t.jy[i] = y;
         if (i < 3) {
            x += Mathf.cosDeg(t.ang[i]) * TAIL_LEN[i] * k;
            y += Mathf.sinDeg(t.ang[i]) * TAIL_LEN[i] * k;
         }
      }
   }

   static float signedDiff(float to, float from) {
      return ((to - from) % 360.0F + 540.0F) % 360.0F - 180.0F;
   }

   @Override
   public void update(Unit unit) {
      super.update(unit);
      Tail t = state(unit);
      boolean shooting = unit.isShooting();
      t.curl = Mathf.approachDelta(t.curl, shooting ? 1.0F : 0.0F, 1.0F / 24.0F);
      t.kick = Mathf.approachDelta(t.kick, 0.0F, 1.0F / 14.0F);
      float v = unit.deltaLen() / Math.max(Time.delta, 0.001F);
      t.tilt = Mathf.approachDelta(t.tilt, v > 0.15F ? 1.0F : 0.0F, 1.0F / 10.0F);
      // turning: the angle the body turned this tick, against the most it can turn (left = positive)
      float turned = Float.isNaN(t.lastRot) ? 0.0F : signedDiff(unit.rotation, t.lastRot) / Math.max(Time.delta, 0.001F);
      t.lastRot = unit.rotation;
      t.turn = Mathf.lerpDelta(t.turn, Mathf.clamp(turned / this.rotateSpeed, -1.0F, 1.0F), 0.15F);

      // every piece follows the previous one bent by the curl (to the left: the angle goes down from "back" through "left" to "forward")
      float prev = unit.rotation + 180.0F;
      for (int i = 0; i < 4; i++) {
         float target = prev - t.curl * CURL_BEND[i];
         if (i == 3 && shooting) {
            // the tail gun bends a bit more towards the target
            float want = Angles.angle(t.jx[3], t.jy[3], unit.aimX(), unit.aimY());
            target += Mathf.clamp(signedDiff(want, target), -GUN_BEND, GUN_BEND) * t.curl;
         }
         t.ang[i] = Mathf.slerpDelta(t.ang[i], target, FOLLOW[i]);
         prev = t.ang[i];
      }
      pose(unit, t);
   }

   /** no shadow and no dark soft blob under the unit */
   @Override
   public void drawShadow(Unit unit) {
   }

   public void drawSoftShadow(Unit unit) {
   }

   public void drawSoftShadow(float x, float y, float rotation, float alpha) {
   }

   @Override
   public Color cellColor(Unit unit) {
      return TeamTint.cell(unit);
   }

   static WeaponMount mount(Unit unit, Weapon w) {
      for (WeaponMount m : unit.mounts) {
         if (m.weapon == w) return m;
      }
      return null;
   }

   public void draw(Unit unit) {
      boolean payload = !unit.isAdded();
      // every piece goes on the body layer, in drawing order (see UnitZ), so the unit never mixes with other units
      float z = UnitZ.of(this, unit);
      float rot = unit.rotation - 90.0F;
      Tail t;
      if (payload || TAILS.get(unit) == null) {
         t = new Tail(); // straight tail, legs at rest
         for (int i = 0; i < 4; i++) t.ang[i] = unit.rotation + 180.0F;
      } else {
         t = TAILS.get(unit);
      }
      pose(unit, t); // positions follow the drawn (interpolated) unit position

      // hover rings under the feet, then the legs; each leg turns back while moving and all of them swing towards the turn
      for (int i = 0; i < 6; i++) {
         float side = LEG_PIVOT[i][0] < 0.0F ? 1.0F : -1.0F; // backwards: left legs counter-clockwise, right legs clockwise
         float la = rot + side * LEG_TILT * t.tilt + LEG_TURN * t.turn;
         Tmp.v1.set(LEG_PIVOT[i][0], LEG_PIVOT[i][1]).rotate(rot);
         float px = unit.x + Tmp.v1.x, py = unit.y + Tmp.v1.y;
         Tmp.v2.set(LEG_FOOT[i][0], LEG_FOOT[i][1]).rotate(la);
         float fx = px + Tmp.v2.x, fy = py + Tmp.v2.y;

         Draw.z(z - 0.001F); // the glow lies on the ground, under the units (like the Elude hover part)
         Draw.color(HOVER);
         for (int c = 0; c < 2; c++) {
            float fin = (Time.time / 90.0F + c / 2.0F) % 1.0F;
            Lines.stroke((1.0F - fin) * 2.5F + 0.12F);
            Lines.poly(fx, fy, 4, 8.0F * fin, unit.rotation);
         }
         Draw.reset();
      }
      Draw.z(z);
      for (int i = 0; i < 6; i++) {
         float side = LEG_PIVOT[i][0] < 0.0F ? 1.0F : -1.0F;
         float la = rot + side * LEG_TILT * t.tilt + LEG_TURN * t.turn;
         Tmp.v1.set(LEG_PIVOT[i][0], LEG_PIVOT[i][1]).rotate(rot);
         Draw.rect(this.legs[i], unit.x + Tmp.v1.x, unit.y + Tmp.v1.y, la);
      }

      // the tail over the legs: from the root to the gun, every piece on top of the previous one
      for (int i = 0; i < 4; i++) {
         Draw.rect(this.tail[i], t.jx[i], t.jy[i], t.ang[i] + 90.0F);
      }
      WeaponMount tm = mount(unit, this.tailGun);
      float tk = tm == null ? 0.0F : (float)Math.pow((double)tm.recoil, 1.8D) * this.tailGun.recoil;
      Draw.rect(this.tailBarrel, t.jx[3] - Mathf.cosDeg(t.ang[3]) * tk, t.jy[3] - Mathf.sinDeg(t.ang[3]) * tk, t.ang[3] + 90.0F);

      // body, cell and the main gun barrel (with its recoil), on the same layer, above all of the above
      TurretPatch.shadowOff();
      try {
         super.draw(unit);
      } finally {
         TurretPatch.shadowOn();
      }

      // the base of the main gun on top of its barrel: turns with it, no recoil
      WeaponMount m = mount(unit, this.main);
      float gr = m == null ? rot : rot + m.rotation;
      Tmp.v1.set(0.0F, GUN_Y).rotate(rot);
      Draw.z(z);
      Draw.rect(this.gunBase, unit.x + Tmp.v1.x, unit.y + Tmp.v1.y, gr);
      Draw.reset();
      Draw.z(z);
   }

   /**
    * The tail gun: its position and direction are the end of the tail (set for every unit right before the vanilla update),
    * it holds fire until the tail is curled forward, and every shot kicks the tail.
    */
   public static class TailWeapon extends Weapon {
      public TailWeapon(String name) {
         super(name);
      }

      @Override
      public void update(Unit unit, WeaponMount mount) {
         Tail t = state(unit);
         Tmp.v1.set(t.jx[3] - unit.x, t.jy[3] - unit.y).rotate(-(unit.rotation - 90.0F));
         float ox = this.x, oy = this.y, ob = this.baseRotation;
         this.x = Tmp.v1.x;
         this.y = Tmp.v1.y;
         this.baseRotation = t.ang[3] - unit.rotation;
         if (t.curl < 0.97F) {
            mount.reload = Math.max(mount.reload, Time.delta * unit.reloadMultiplier + 0.01F); // hold the shot while the tail curls
         }
         float before = mount.reload;
         try {
            super.update(unit, mount);
         } finally {
            this.x = ox;
            this.y = oy;
            this.baseRotation = ob;
         }
         if (mount.reload > before + 0.001F) {
            t.kick = 1.0F;
         }
      }

      /** the shell leaves the muzzle straight along the tail gun */
      @Override
      protected float bulletRotation(Unit unit, WeaponMount mount, float bulletX, float bulletY) {
         return unit.rotation + this.baseRotation;
      }

      /** drawn by FialkaUnit together with the tail */
      @Override
      public void draw(Unit unit, WeaponMount mount) {
      }

      @Override
      public void drawOutline(Unit unit, WeaponMount mount) {
      }
   }
}
