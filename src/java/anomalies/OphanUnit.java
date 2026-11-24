package anomalies;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.LegsUnit;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.weapons.RepairBeamWeapon;
import mindustry.world.blocks.storage.CoreBlock;

/**
 * Ophan: a six-legged ground core unit, legs like the Merui's (front, middle and rear pair). It walks over buildings, builds and mines,
 * and while the fire button (LMB) is held two repair beams from the front of the hull heal the buildings towards the cursor.
 * Sprites are cut from ophan.ase (4 px = 1 world unit): ophan.png the hull, ophan-cell.png the team-coloured parts,
 * ophan-leg.png the inner leg part (hull to joint), ophan-leg-base.png the outer part with the foot (joint to foot).
 */
public class OphanUnit extends UnitType {
   public OphanUnit(String name) {
      super(name);
      this.constructor = LegsUnit::create;
      this.alwaysUnlocked = true;
      this.health = 450.0F;
      this.armor = 2.0F;
      this.speed = 0.9F;
      this.drag = 0.11F;
      this.hitSize = 11.0F;
      this.rotateSpeed = 4.0F;
      this.faceTarget = true;
      this.outlines = false;
      this.drawCell = true;
      this.engineSize = 0.0F;

      // legs built like the Merui: three pairs (front, middle and rear of each side, at 30, 90 and 150 degrees from the nose),
      // walking in two groups of three, the inner part tucked under the hull and the outer part (with the foot) over the
      // inner one; the legs are short like the Merui's, so both parts are squeezed along their length (never stretched)
      // and the legs look bent down to the ground: inner 26 px into legLength / 2 = 4, outer 29 px into
      // legLength / 2 + legExtension = 5
      this.legCount = 6;
      this.legLength = 8.0F;
      this.legGroupSize = 3;
      this.lockLegBase = true;
      this.legContinuousMove = true;
      this.legExtension = 1.0F;
      this.legBaseOffset = 3.0F;
      this.legMaxLength = 1.1F;
      this.legMinLength = 0.2F;
      this.legLengthScl = 0.96F;
      this.legForwardScl = 1.1F;
      this.legMoveSpace = 1.0F;
      this.legStraightness = 0.3F;
      this.legBaseUnder = false;
      this.legPhysicsLayer = false;
      this.allowLegStep = true;
      this.hovering = true;
      this.rippleScale = 0.2F;
      this.stepShake = 0.0F;
      this.shadowElevation = 0.1F;
      this.groundLayer = Layer.legUnit - 1.0F;

      // builder / miner like the Emanate
      this.buildSpeed = 3.0F;
      this.mineTier = 3;
      this.mineSpeed = 3.0F;
      this.mineWalls = true;

      // two repair beams from the front of the hull, used with the fire button like the Evoke's
      this.weapons.add(new RepairBeamWeapon("anomalies-ophan-beam") {{
         this.x = 2.25F;
         this.y = 4.5F;
         this.shootY = 0.0F;
         this.mirror = true;
         this.alternate = false;
         this.rotate = true;
         this.rotateSpeed = 12.0F;
         this.reload = 20.0F;
         this.beamWidth = 0.6F;
         this.widthSinMag = 0.11F;
         this.repairSpeed = 3.3F;
         this.fractionRepairSpeed = 0.06F;
         this.shootCone = 15.0F;
         this.targetUnits = false;
         this.targetBuildings = true;
         this.autoTarget = false;
         this.controllable = true;
         this.laserColor = Pal.accent;
         this.healColor = Pal.accent;
         this.bullet = new BulletType() {{
            this.maxRange = 65.0F;
         }};
      }});
   }

   /** The mining beam: yellow laser with a white core, see MineBeam. */
   @Override
   public void drawMiningBeam(Unit unit, float px, float py) {
      MineBeam.draw(unit, px, py);
   }

   @Override
   public Color cellColor(Unit unit) {
      return TeamTint.cell(unit);
   }

   @Override
   public void update(Unit unit) {
      super.update(unit);
      // while the lids open the unit sits inside the core (the core draws it); then it walks out
      float age = SeraphAnim.age(unit);
      if (age < SeraphAnim.OPEN_END) {
         CoreBlock.CoreBuild c = unit.closestCore();
         if (c != null) unit.set(c.x, c.y);
         unit.vel.setZero();
         unit.isShooting(false);
      } else if (age < 1.0E8F && SeraphAnim.released.add(unit)) {
         unit.impulse(0.0F, 1.5F);
      }
   }

   @Override
   public void draw(Unit unit) {
      if (SeraphAnim.age(unit) < SeraphAnim.OPEN_END) return; // still inside the core, the core draws it
      super.draw(unit);
   }

   /** The hull resting inside the core (drawn by the core, facing up). */
   void drawInCore(float x, float y, Team team, float alpha) {
      Draw.color(Color.white, alpha);
      Draw.rect(this.region, x, y);
      if (this.cellRegion.found()) {
         Draw.color(team.color, alpha);
         Draw.rect(this.cellRegion, x, y);
      }
      Draw.reset();
   }
}
