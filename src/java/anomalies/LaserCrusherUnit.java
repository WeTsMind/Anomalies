package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.LegsUnit;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.world.Tile;

import java.util.WeakHashMap;

/**
 * Laser crusher ("Лазерный скалогрыз"): a six-legged unit that walks like the Merui (legs at the front, middle and rear of
 * each side, the outer part of every leg goes under the inner one and the legs overlap each other). Like the Excavator it
 * cuts out terrain walls, but with a red laser from up to 2 blocks away: one wall tile is crushed in 15 seconds.
 * It obeys the same orders as the Excavator (the move command on a wall tile, the right-button zone in command mode; both
 * kinds share the walls of a zone), or drive it yourself: aim at a wall within reach and hold the fire button.
 * Sprites are cut from laser_crusher*.ase (4 px = 1 world unit): laser-crusher.png the hull, laser-crusher-cell.png the cell
 * (team colour), laser-crusher-leg.png the inner leg part (hull to joint), laser-crusher-leg-base.png the outer part with the
 * red claw (joint to foot).
 */
public class LaserCrusherUnit extends UnitType {
   /** laser reach: 2 blocks from the edge of the hull to the edge of the wall tile, world units */
   static final float RANGE = 2.0F * 8.0F;
   /** one wall tile is crushed in this many ticks (15 seconds) */
   static final float WALL_TIME = 15.0F * 60.0F;
   /** where the laser leaves the hull (local units, forward) */
   static final float MUZZLE_Y = 5.0F;
   /** the laser only works when the hull looks at the wall within this angle, degrees */
   static final float CONE = 30.0F;
   static final Color LASER = Color.valueOf("ff2a3c"), LASER_CORE = Color.valueOf("ffd0d4");
   static final int CRACKS = 8;

   static final Effect burn = new Effect(18.0F, e -> {
      Draw.color(LASER_CORE, LASER, e.fin());
      Lines.stroke(1.0F * e.fout() + 0.2F);
      Angles.randLenVectors(e.id, 3, 1.5F + e.finpow() * 7.0F, e.rotation, 70.0F, (x, y) -> {
         Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 0.8F + e.fout() * 2.2F);
      });
      Draw.reset();
   });

   static final WeakHashMap<Unit, State> STATES = new WeakHashMap<>();

   static class State {
      /** the terrain tile being crushed right now (Tile.pos), -1 = none */
      int cutPos = -1;
      /** laser strength 0..1 (fades in and out) */
      float power;
      float sparkTimer;
   }

   final TextureRegion[] cracks = new TextureRegion[CRACKS];

   public LaserCrusherUnit(String name) {
      super(name);
      this.constructor = LegsUnit::create;
      this.controller = u -> {
         if (!this.playerControllable || (u.team.isAI() && !u.team.rules().rtsAi)) return this.aiController.get();
         ExcavatorUnit.ExcavatorAI ai = new ExcavatorUnit.ExcavatorAI(false);
         ai.reach = this.reachFromCentre(); // keeps the wall within the laser's reach
         return ai;
      };
      this.alwaysUnlocked = true;
      this.health = 650.0F;
      this.armor = 4.0F;
      this.speed = 0.6F;
      this.drag = 0.11F;
      this.hitSize = 11.0F;
      this.rotateSpeed = 3.0F;
      this.outlines = false;
      this.drawCell = true;
      this.targetAir = false;
      this.targetGround = false;
      this.engineSize = 0.0F;

      // legs: like the Merui
      this.legCount = 6;
      this.legLength = 13.0F;
      this.legGroupSize = 3;
      this.lockLegBase = true;
      this.legContinuousMove = true;
      this.legExtension = -2.0F;
      this.legBaseOffset = 3.0F;
      this.legMaxLength = 1.1F;
      this.legMinLength = 0.2F;
      this.legLengthScl = 0.96F;
      this.legForwardScl = 1.1F;
      this.legMoveSpace = 1.0F;
      this.legStraightness = 0.3F;
      this.legBaseUnder = true; // the outer part of every leg goes under the inner one
      this.legPhysicsLayer = false;
      this.allowLegStep = true;
      this.hovering = true;
      this.rippleScale = 0.2F;
      this.stepShake = 0.0F;
      this.shadowElevation = 0.1F;
      this.groundLayer = Layer.legUnit - 1.0F;
   }

   public void load() {
      super.load();
      for (int i = 0; i < CRACKS; i++) this.cracks[i] = Core.atlas.find("cracks-1-" + i);
   }

   static State state(Unit unit) {
      State s = STATES.get(unit);
      if (s == null) STATES.put(unit, s = new State());
      return s;
   }

   /** the wall tile is within the laser's reach */
   boolean inReach(Unit unit, Tile t) {
      return unit.within(t.worldx(), t.worldy(), this.reachFromCentre());
   }

   /** the laser reach measured from the unit centre to the wall tile centre */
   float reachFromCentre() {
      return RANGE + this.hitSize / 2.0F + Vars.tilesize / 2.0F;
   }

   @Override
   public void update(Unit unit) {
      super.update(unit);
      State s = state(unit);
      Tile wall = null;
      if (unit.controller() instanceof ExcavatorUnit.ExcavatorAI) {
         // ordered: only its own wall
         Tile t = ((ExcavatorUnit.ExcavatorAI)unit.controller()).cutTile;
         if (ExcavatorUnit.terrainWall(t) && this.inReach(unit, t)) wall = t;
      } else if (unit.isPlayer() && unit.isShooting()) {
         // driven by a player: the wall under the cursor
         Tile t = Vars.world.tileWorld(unit.aimX(), unit.aimY());
         if (ExcavatorUnit.terrainWall(t) && this.inReach(unit, t)) wall = t;
      }
      if (wall != null && !Angles.within(unit.rotation, unit.angleTo(wall.worldx(), wall.worldy()), CONE)) {
         if (unit.isPlayer()) unit.lookAt(wall.worldx(), wall.worldy());
         wall = null; // turning to it first
      }
      s.cutPos = wall == null ? -1 : wall.pos();
      s.power = Mathf.approachDelta(s.power, wall != null ? 1.0F : 0.0F, 1.0F / 8.0F);
      if (wall == null) return;

      if ((s.sparkTimer += Time.delta) >= 4.0F) {
         s.sparkTimer = 0.0F;
         burn.at(wall.worldx() + Mathf.range(3.0F), wall.worldy() + Mathf.range(3.0F), unit.angleTo(wall.worldx(), wall.worldy()) + 180.0F);
      }
      if (Vars.net.client()) return;
      // one tile is crushed in 15 seconds (it shares the progress with excavators cutting the same tile)
      float p = ExcavatorUnit.CUT.get(wall.pos(), 0.0F) + Time.delta / WALL_TIME;
      if (p >= 1.0F) {
         ExcavatorUnit.CUT.remove(wall.pos(), 0.0F);
         Fx.breakBlock.at(wall.worldx(), wall.worldy(), 1.0F);
         wall.setNet(Blocks.air);
      } else {
         ExcavatorUnit.CUT.put(wall.pos(), p);
      }
   }

   @Override
   public Color cellColor(Unit unit) {
      return TeamTint.cell(unit);
   }

   @Override
   public void draw(Unit unit) {
      TurretPatch.shadowOff();
      try {
         super.draw(unit);
      } finally {
         TurretPatch.shadowOn();
      }
      if (!unit.isAdded()) return;
      State s = STATES.get(unit);

      // the tile being crushed gets the block cracks, growing with the progress
      if (s != null && s.cutPos >= 0) {
         Tile t = Vars.world.tile(s.cutPos);
         float p = ExcavatorUnit.CUT.get(s.cutPos, 0.0F);
         if (t != null && p > 0.0F && this.cracks[0] != null) {
            Draw.z(Layer.blockCracks);
            Draw.colorl(0.2F, 0.15F + p * 0.6F);
            Draw.rect(this.cracks[Math.min((int)(p * CRACKS), CRACKS - 1)], t.worldx(), t.worldy());
            Draw.reset();
         }
      }

      // the red laser from the head to the wall
      if (s != null && s.power > 0.01F) {
         Tile t = s.cutPos >= 0 ? Vars.world.tile(s.cutPos) : null;
         if (t != null) {
            Tmp.v1.set(0.0F, MUZZLE_Y).rotate(unit.rotation - 90.0F).add(unit.x, unit.y);
            float x1 = Tmp.v1.x, y1 = Tmp.v1.y, x2 = t.worldx(), y2 = t.worldy();
            float flick = 1.0F + Mathf.absin(Time.time, 1.6F, 0.25F);
            Draw.z(Layer.bullet);
            Draw.blend(Blending.additive);
            Draw.color(LASER, 0.55F * s.power);
            Lines.stroke(3.4F * flick * s.power);
            Lines.line(x1, y1, x2, y2);
            Draw.color(LASER, s.power);
            Lines.stroke(1.8F * flick * s.power);
            Lines.line(x1, y1, x2, y2);
            Draw.color(LASER_CORE, s.power);
            Lines.stroke(0.7F * s.power);
            Lines.line(x1, y1, x2, y2);
            Draw.color(LASER, 0.8F * s.power);
            Fill.circle(x1, y1, 1.6F * flick * s.power);
            Fill.circle(x2, y2, 2.4F * flick * s.power);
            Draw.blend();
            Draw.reset();
            Drawf.light(x2, y2, 18.0F * s.power, LASER, 0.7F);
            Drawf.light(x1, y1, x2, y2, 6.0F * s.power, LASER, 0.5F);
         }
      }

      // command mode: the wall this unit was sent to crush is marked like an attack target
      if (unit.controller() instanceof ExcavatorUnit.ExcavatorAI && ((ExcavatorUnit.ExcavatorAI)unit.controller()).cutTile != null
         && Vars.control != null && Vars.control.input.commandMode && Vars.control.input.selectedUnits.contains(unit)) {
         Tile t = ((ExcavatorUnit.ExcavatorAI)unit.controller()).cutTile;
         Draw.z(Layer.overlayUI);
         Drawf.target(t.worldx(), t.worldy(), 6.0F, Pal.remove);
         Draw.reset();
      }
   }
}
