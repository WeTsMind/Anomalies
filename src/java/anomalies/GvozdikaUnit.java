package anomalies;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
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
 * Gvozdika: tier 2 ground hover unit, the upgrade of the Lavanda (additive reconstructor). Sprites are cut from GVOZDIKA.ase
 * (4 px = 1 world unit), every piece is centred on its own pivot:
 * - gvozdika.png the hull, gvozdika-cell.png the cell (the three white pieces, team colour);
 * - gvozdika-gun.png the gun: fixed to the hull (the whole unit turns to the target), it kicks back a little at every shot;
 *   it fires the Demolisher shell in glowing violet (60 damage, once a second);
 * - gvozdika-leg-0..3.png the 4 diagonal legs (left front, left rear, right front, right rear), they work like the Fialka's:
 *   while the unit moves each leg turns backwards around the point where it enters the hull, and while the unit turns all legs
 *   swing towards the turn; a hover ring (the Elude effect) sits under every foot and moves with it.
 */
public class GvozdikaUnit extends UnitType {
   /** legs: pivot (local units: x to the side, y forward) and the foot centre relative to the pivot */
   static final float[][] LEG_PIVOT = {{-1.25F, 1.5F}, {-1.5F, -1.25F}, {1.25F, 1.5F}, {1.5F, -1.25F}};
   static final float[][] LEG_FOOT = {{-4.27F, 4.01F}, {-4.05F, -4.3F}, {4.3F, 4.05F}, {4.07F, -4.33F}};
   /** how far the legs turn back while moving, degrees */
   static final float LEG_TILT = 5.0F;
   /** how far all the legs swing towards the side the unit is turning to, degrees (at full turn speed) */
   static final float LEG_TURN = 5.0F;
   /** how fast a leg turns to its angle, degrees per tick */
   static final float LEG_SPEED = 4.0F;
   static final Color HOVER = Color.valueOf("bf92f9");
   static final WeakHashMap<Unit, Legs> LEGS = new WeakHashMap<>();

   TextureRegion[] legs = new TextureRegion[4];
   Weapon main;

   /** per unit leg state */
   static class Legs {
      /** current turn of every leg relative to the hull, degrees */
      float[] leg = new float[4];
      /** how fast the unit is turning: -1 (right, full speed) .. 1 (left, full speed), smoothed */
      float turn;
      /** the unit rotation on the previous update */
      float lastRot = Float.NaN;
   }

   public GvozdikaUnit(String name) {
      super(name);
      this.constructor = ElevationMoveUnit::create; // the same kind of ground hover unit as the Elude / Lavanda
      this.alwaysUnlocked = true;
      this.flying = false;
      this.hovering = true;
      this.canDrown = false;
      this.health = 550.0F;
      this.armor = 4.0F;
      this.speed = 0.85F;
      this.hitSize = 15.0F;
      this.rotateSpeed = 4.0F;
      this.faceTarget = true; // the gun is fixed to the hull: the whole unit turns to the target
      this.targetAir = true;
      this.targetGround = true;
      this.outlines = false;
      this.drawCell = true;
      this.engineSize = 0.0F;

      // the gun: fixed to the hull (does not turn by itself), small recoil, the Demolisher shell in violet
      Weapon gun = new Weapon("anomalies-gvozdika-gun");
      gun.x = 0.0F;
      gun.y = 0.0F;
      gun.shootY = 10.75F; // the muzzle
      gun.mirror = false;
      gun.rotate = false;
      gun.reload = 60.0F; // 1 s
      gun.recoil = 0.75F;
      gun.recoilTime = 14.0F;
      gun.shootCone = 8.0F;
      gun.ejectEffect = Fx.none;
      gun.shootSound = ModSounds.demolisherShot();
      gun.bullet = GvozdikaShot.main();
      this.weapons.add(gun);
      this.main = gun;
   }

   public void load() {
      super.load();
      for (int i = 0; i < 4; i++) this.legs[i] = Core.atlas.find(this.name + "-leg-" + i);
      if (Core.atlas.has(this.name + "-icon")) {
         TextureRegion icon = Core.atlas.find(this.name + "-icon");
         this.fullIcon = icon;
         this.uiIcon = icon;
      }
   }

   static float signedDiff(float to, float from) {
      return ((to - from) % 360.0F + 540.0F) % 360.0F - 180.0F;
   }

   @Override
   public void update(Unit unit) {
      super.update(unit);
      Legs l = LEGS.get(unit);
      if (l == null) LEGS.put(unit, l = new Legs());
      float v = unit.deltaLen() / Math.max(Time.delta, 0.001F);
      // turning: the angle the hull turned this tick, against the most it can turn (left = positive)
      float turned = Float.isNaN(l.lastRot) ? 0.0F : signedDiff(unit.rotation, l.lastRot) / Math.max(Time.delta, 0.001F);
      l.lastRot = unit.rotation;
      l.turn = Mathf.lerpDelta(l.turn, Mathf.clamp(turned / this.rotateSpeed, -1.0F, 1.0F), 0.15F);
      // every leg turns to its angle (backwards while moving + towards the turn) at most LEG_SPEED degrees per tick
      for (int i = 0; i < 4; i++) {
         float side = LEG_PIVOT[i][0] < 0.0F ? 1.0F : -1.0F; // backwards: left legs counter-clockwise, right legs clockwise
         float want = side * LEG_TILT * (v > 0.15F ? 1.0F : 0.0F) + LEG_TURN * l.turn;
         l.leg[i] = Mathf.approachDelta(l.leg[i], want, LEG_SPEED);
      }
      // a unit under a player's command (CommandAI) never turns to its target by itself: when it stands still and the gun has
      // a target, the hull turns to it, so the fixed gun can look at it
      if (!unit.isPlayer() && !unit.moving()) {
         for (WeaponMount m : unit.mounts) {
            if (m.weapon == this.main && m.shoot) unit.lookAt(m.aimX, m.aimY);
         }
      }
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

   public void draw(Unit unit) {
      // every piece goes on the body layer, in drawing order (see UnitZ), so the unit never mixes with other units
      float z = UnitZ.of(this, unit);
      float rot = unit.rotation - 90.0F;
      Legs l = unit.isAdded() ? LEGS.get(unit) : null;
      if (l == null) l = new Legs(); // legs at rest (payload, icon)

      // hover rings under the feet, then the legs
      for (int i = 0; i < 4; i++) {
         Tmp.v1.set(LEG_PIVOT[i][0], LEG_PIVOT[i][1]).rotate(rot);
         Tmp.v2.set(LEG_FOOT[i][0], LEG_FOOT[i][1]).rotate(rot + l.leg[i]);
         float fx = unit.x + Tmp.v1.x + Tmp.v2.x, fy = unit.y + Tmp.v1.y + Tmp.v2.y;
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
      for (int i = 0; i < 4; i++) {
         Tmp.v1.set(LEG_PIVOT[i][0], LEG_PIVOT[i][1]).rotate(rot);
         Draw.rect(this.legs[i], unit.x + Tmp.v1.x, unit.y + Tmp.v1.y, rot + l.leg[i]);
      }

      // hull, cell and gun (with its recoil) over the legs
      TurretPatch.shadowOff();
      try {
         super.draw(unit);
      } finally {
         TurretPatch.shadowOn();
      }
      Draw.reset();
      Draw.z(z);
   }
}
