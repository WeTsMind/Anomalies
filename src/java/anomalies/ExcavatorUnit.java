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
import mindustry.Vars;
import mindustry.ai.types.CommandAI;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Building;
import mindustry.gen.Teamc;
import mindustry.graphics.Layer;
import mindustry.gen.Mechc;
import mindustry.gen.MechUnit;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import arc.Events;
import arc.math.geom.Vec2;
import arc.struct.IntFloatMap;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.StaticWall;

import java.util.WeakHashMap;

/**
 * Excavator: a ground walker on four legs (front left, front right, rear left, rear right). It cannot climb walls: it walks
 * like any ground unit and goes around them. Sprites are cut from excavator.ase (4 px = 1 world unit):
 * - excavator.png the body, excavator-cell.png the cell (the three white pieces, team colour);
 * - excavator-gun.png the gun on top: turns, small recoil, 15 damage every 0.8 s, range 7 blocks;
 * - excavator-saw.png the three saws on the front edge: they spin while working;
 * - excavator-leg-upper.png / excavator-leg-foot.png one leg (the upper part and the foot), drawn at all four corners.
 * Saws: while working they hurt everything they touch (1 damage every 0.1 s) and throw bright orange sparks. Walls are cut down
 * by the saws in one minute whatever their health: either command the unit to attack the wall (it walks up to it and starts
 * cutting), or drive it yourself right up to the wall and hold the fire button.
 */
public class ExcavatorUnit extends UnitType {
   /** saw centres (local units: x to the side, y forward) */
   static final float[][] SAW = {{-3.5F, 5.875F}, {0.0F, 5.625F}, {3.5F, 5.875F}};
   /** how far the teeth reach in front of a saw centre, world units */
   static final float SAW_REACH = 2.6F;
   /** saw damage and its interval: 1 damage every 0.1 s */
   static final float SAW_DAMAGE = 1.0F, SAW_INTERVAL = 6.0F;
   /** a wall is cut down in this many ticks (1 minute) */
   static final float WALL_TIME = 60.0F * 60.0F;
   /** saw spin while working, degrees per tick */
   static final float SAW_SPIN = 18.0F;

   /** legs: hip (local units), leg direction relative to the body (degrees), distance of the upper part and of the foot */
   static final float[][] HIP = {{-3.5F, 3.0F}, {3.5F, 3.0F}, {-3.5F, -4.0F}, {3.5F, -4.0F}};
   static final float[] LEG_DIR = {45.0F, -45.0F, 135.0F, -135.0F};
   /** the upper part starts well under the body, the foot starts well under the upper part (drawn first, so it is under it) */
   static final float UPPER = 2.5F, FOOT = 7.5F;
   /** walking: how far the legs swing, degrees, and the stride (world units per full step cycle) */
   static final float SWING = 14.0F, STRIDE = 22.0F;

   static final Color SPARK = Color.valueOf("ffb347"), SPARK_HOT = Color.valueOf("fff1c2");
   static final Effect sparks = new Effect(16.0F, e -> {
      Draw.color(SPARK_HOT, SPARK, e.fin());
      Lines.stroke(1.1F * e.fout() + 0.2F);
      Angles.randLenVectors(e.id, 4, 2.0F + e.finpow() * 9.0F, e.rotation, 55.0F, (x, y) -> {
         Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1.0F + e.fout() * 2.6F);
      });
      Draw.reset();
   });

   static final WeakHashMap<Unit, State> STATES = new WeakHashMap<>();

   static class State {
      float sawRot, sawSpin, sawTimer, sparkTimer, walk, move;
      /** the terrain tile being cut right now (Tile.pos), -1 = none */
      int cutPos = -1;
   }

   /** how far every terrain tile is cut (Tile.pos -> 0..1); cleared on every map load */
   static final IntFloatMap CUT = new IntFloatMap();

   static {
      Events.on(EventType.WorldLoadEvent.class, e -> CUT.clear());
   }

   TextureRegion sawRegion, legUpper, legFoot;
   /** vanilla 1x1 block cracks, 8 stages */
   static final int CRACKS = 8;
   final TextureRegion[] cracks = new TextureRegion[CRACKS];

   public ExcavatorUnit(String name) {
      super(name);
      this.constructor = MechUnit::create; // a ground walker: blocked by walls like any ground unit
      this.controller = u -> !this.playerControllable || (u.team.isAI() && !u.team.rules().rtsAi) ? this.aiController.get() : new ExcavatorAI();
      this.alwaysUnlocked = true;
      this.flying = false;
      this.hovering = false;
      this.health = 600.0F;
      this.armor = 4.0F;
      this.speed = 0.55F;
      this.hitSize = 12.0F;
      this.rotateSpeed = 4.0F;
      this.mechSideSway = 0.0F;
      this.mechFrontSway = 0.0F;
      this.mechStepParticles = false;
      this.stepShake = 0.0F;
      this.outlines = false;
      this.drawCell = true;
      this.targetAir = true;
      this.targetGround = true;

      Weapon gun = new Weapon("anomalies-excavator-gun");
      gun.x = 0.0F;
      gun.y = -1.0F;
      gun.shootY = 3.0F;
      gun.mirror = false;
      gun.rotate = true;
      gun.rotateSpeed = 6.0F;
      gun.reload = 48.0F; // 0.8 s
      gun.recoil = 1.0F;
      gun.recoilTime = 14.0F;
      gun.shootCone = 8.0F;
      gun.ejectEffect = Fx.casing1;
      gun.shootSound = Sounds.shoot;
      BasicBulletType b = new BasicBulletType(3.0F, 15.0F);
      b.lifetime = 19.0F; // range ~57 = 7 blocks
      b.width = 7.0F;
      b.height = 10.0F;
      b.frontColor = Color.valueOf("ffd2a8");
      b.backColor = Color.valueOf("f0693a");
      b.hitColor = b.backColor;
      b.keepVelocity = false;
      b.shootEffect = Fx.shootSmall;
      b.smokeEffect = Fx.shootSmallSmoke;
      b.hitEffect = Fx.hitBulletColor;
      b.despawnEffect = Fx.hitBulletColor;
      gun.bullet = b;
      this.weapons.add(gun);
   }

   public void load() {
      super.load();
      this.sawRegion = Core.atlas.find(this.name + "-saw");
      this.legUpper = Core.atlas.find(this.name + "-leg-upper");
      this.legFoot = Core.atlas.find(this.name + "-leg-foot");
      for (int i = 0; i < CRACKS; i++) this.cracks[i] = Core.atlas.find("cracks-1-" + i);
      if (Core.atlas.has(this.name + "-icon")) {
         TextureRegion icon = Core.atlas.find(this.name + "-icon");
         this.fullIcon = icon;
         this.uiIcon = icon;
      }
   }

   static State state(Unit unit) {
      State s = STATES.get(unit);
      if (s == null) STATES.put(unit, s = new State());
      return s;
   }

   /** a terrain wall (stone, sand, ice... walls of the map itself), the only thing the saws cut out */
   static boolean terrainWall(Tile t) {
      return t != null && t.build == null && t.block() instanceof StaticWall;
   }

   /** a building of an enemy team: the saws only hurt it like any other target */
   static boolean enemyBuild(Unit unit, Building b) {
      return b != null && b.team != unit.team && b.team != Team.derelict && b.isValid();
   }

   /** world position of a saw tip (i = saw index), written to Tmp.v3 */
   static void tip(Unit unit, int i, float extra) {
      Tmp.v3.set(SAW[i][0], SAW[i][1] + SAW_REACH * extra).rotate(unit.rotation - 90.0F).add(unit.x, unit.y);
   }

   @Override
   public void update(Unit unit) {
      super.update(unit);
      State s = state(unit);
      s.move = Mathf.approachDelta(s.move, unit.moving() ? 1.0F : 0.0F, 0.08F);
      s.walk += unit.deltaLen();

      // the terrain wall in front of the saws (if any); the commanded one is preferred when the saws touch it
      Tile cutTile = unit.controller() instanceof ExcavatorAI ? ((ExcavatorAI)unit.controller()).cutTile : null;
      Tile wall = null;
      for (int i = 0; i < SAW.length; i++) {
         for (float ex = 0.5F; ex <= 1.01F; ex += 0.5F) {
            tip(unit, i, ex);
            Tile t = Vars.world.tileWorld(Tmp.v3.x, Tmp.v3.y);
            if (terrainWall(t) && (wall == null || t == cutTile)) wall = t;
         }
      }
      boolean commanded = wall != null && cutTile != null;
      boolean playerSaw = unit.isPlayer() && unit.isShooting();

      // enemies (units and buildings) touching the saws
      boolean touching = false;
      for (int i = 0; i < SAW.length && !touching; i++) {
         tip(unit, i, 0.6F);
         float tx = Tmp.v3.x, ty = Tmp.v3.y;
         if (Units.closestEnemy(unit.team, tx, ty, SAW_REACH, u -> u.checkTarget(this.targetAir, this.targetGround)) != null) touching = true;
         Building b = Vars.world.buildWorld(tx, ty);
         if (enemyBuild(unit, b)) touching = true;
      }

      boolean cutWall = wall != null && (playerSaw || commanded);
      s.cutPos = cutWall ? wall.pos() : -1;
      boolean working = playerSaw || touching || cutWall;
      s.sawSpin = Mathf.approachDelta(s.sawSpin, working ? SAW_SPIN : 0.0F, working ? 1.5F : 0.4F);
      s.sawRot = Mathf.mod(s.sawRot + s.sawSpin * Time.delta, 360.0F);
      if (!working) return;

      // sparks where the teeth meet something
      if ((touching || cutWall) && (s.sparkTimer += Time.delta) >= 3.0F) {
         s.sparkTimer = 0.0F;
         int i = Mathf.random(SAW.length - 1);
         tip(unit, i, 0.9F);
         sparks.at(Tmp.v3.x, Tmp.v3.y, unit.rotation + 180.0F + Mathf.range(40.0F));
      }
      if (Vars.net.client()) return;

      // the terrain wall: one tile is cut out in one minute (several excavators on one tile cut it faster)
      if (cutWall) {
         float p = CUT.get(wall.pos(), 0.0F) + Time.delta / WALL_TIME;
         if (p >= 1.0F) {
            CUT.remove(wall.pos(), 0.0F);
            Fx.breakBlock.at(wall.worldx(), wall.worldy(), 1.0F);
            wall.setNet(Blocks.air);
         } else {
            CUT.put(wall.pos(), p);
         }
      }
      // everything else touching the saws: 1 damage every 0.1 s
      if ((s.sawTimer += Time.delta) >= SAW_INTERVAL) {
         s.sawTimer -= SAW_INTERVAL;
         for (int i = 0; i < SAW.length; i++) {
            tip(unit, i, 0.6F);
            float tx = Tmp.v3.x, ty = Tmp.v3.y;
            Units.nearbyEnemies(unit.team, tx - SAW_REACH, ty - SAW_REACH, SAW_REACH * 2.0F, SAW_REACH * 2.0F, u -> {
               if (u.within(tx, ty, SAW_REACH + u.hitSize / 2.0F) && u.checkTarget(this.targetAir, this.targetGround)) u.damage(SAW_DAMAGE);
            });
            Building b = Vars.world.buildWorld(tx, ty);
            if (enemyBuild(unit, b)) b.damage(SAW_DAMAGE);
         }
      }
   }

   /** the vanilla mech legs are not used: the four legs are drawn in draw() */
   @Override
   public void drawMech(Mechc mech) {
   }

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
      float z = UnitZ.of(this, unit); // every piece on the body layer, in order (see UnitZ)
      State s = unit.isAdded() ? state(unit) : new State();
      float rot = unit.rotation - 90.0F;
      Draw.z(z);

      // legs: diagonal pairs swing together while walking (front left + rear right, front right + rear left)
      float phase = s.walk / STRIDE * 360.0F;
      for (int i = 0; i < 4; i++) {
         float pair = (i == 0 || i == 3) ? 0.0F : 180.0F;
         float swing = Mathf.sinDeg(phase + pair) * SWING * s.move;
         float stretch = Mathf.cosDeg(phase + pair) * 0.8F * s.move;
         float a = unit.rotation + LEG_DIR[i] + swing;
         Tmp.v1.set(HIP[i][0], HIP[i][1]).rotate(rot).add(unit.x, unit.y);
         float hx = Tmp.v1.x, hy = Tmp.v1.y;
         Draw.rect(this.legFoot, hx + Mathf.cosDeg(a) * (FOOT + stretch), hy + Mathf.sinDeg(a) * (FOOT + stretch), a + 90.0F);
         Draw.rect(this.legUpper, hx + Mathf.cosDeg(a) * UPPER, hy + Mathf.sinDeg(a) * UPPER, a + 90.0F);
      }

      // saws (under the body): the middle one turns the other way, like meshing gears
      for (int i = 0; i < SAW.length; i++) {
         Tmp.v1.set(SAW[i][0], SAW[i][1]).rotate(rot);
         Draw.rect(this.sawRegion, unit.x + Tmp.v1.x, unit.y + Tmp.v1.y, rot + (i == 1 ? -s.sawRot : s.sawRot));
      }

      // the tile being cut gets cracks that grow with the cut (the vanilla block cracks)
      if (s.cutPos >= 0 && unit.isAdded()) {
         Tile t = Vars.world.tile(s.cutPos);
         float p = CUT.get(s.cutPos, 0.0F);
         if (t != null && p > 0.0F && this.cracks[0] != null) {
            int stage = Math.min((int)(p * CRACKS), CRACKS - 1);
            Draw.z(Layer.blockCracks);
            Draw.colorl(0.2F, 0.15F + p * 0.6F);
            Draw.rect(this.cracks[stage], t.worldx(), t.worldy());
            Draw.color();
            Draw.z(z);
         }
      }

      // body, cell and gun
      TurretPatch.shadowOff();
      try {
         super.draw(unit);
      } finally {
         TurretPatch.shadowOn();
      }
      Draw.reset();
   }

   /**
    * The command AI of the Excavator: when it is sent to a terrain wall (the move command clicked on the wall), it walks to the
    * free tile next to that wall that is closest to it, then presses its saws against the wall and cuts it out; when the tile
    * is gone it is done. Any other command cancels the cutting.
    */
   public static class ExcavatorAI extends CommandAI {
      /** the terrain wall tile it was sent to cut, null = none */
      public Tile cutTile;

      @Override
      public void commandPosition(Vec2 pos, boolean stopWhenInRange) {
         Tile t = pos == null ? null : Vars.world.tileWorld(pos.x, pos.y);
         if (terrainWall(t)) {
            this.cutTile = t;
            Tile stand = this.standTile(t);
            super.commandPosition(stand != null ? new Vec2(stand.worldx(), stand.worldy()) : pos, false);
            return;
         }
         this.cutTile = null;
         super.commandPosition(pos, stopWhenInRange);
      }

      @Override
      public void commandTarget(Teamc moveTo, boolean stopAtTarget) {
         this.cutTile = null;
         super.commandTarget(moveTo, stopAtTarget);
      }

      @Override
      public void clearCommands() {
         this.cutTile = null;
         super.clearCommands();
      }

      /** the free (passable) tile next to the wall that is closest to the unit */
      Tile standTile(Tile wall) {
         Tile best = null;
         float bestScore = Float.MAX_VALUE;
         for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
               if (dx == 0 && dy == 0) continue;
               Tile n = Vars.world.tile(wall.x + dx, wall.y + dy);
               if (n == null || n.solid() || !this.unit.canPass(n.x, n.y)) continue;
               // sides first, corners only if no side is free
               float score = this.unit.dst2(n.worldx(), n.worldy()) + (dx != 0 && dy != 0 ? 1.0E7F : 0.0F);
               if (score < bestScore) {
                  bestScore = score;
                  best = n;
               }
            }
         }
         return best;
      }

      @Override
      public void defaultBehavior() {
         if (this.cutTile != null && !terrainWall(this.cutTile)) this.cutTile = null; // cut out (or changed): done
         super.defaultBehavior();
         if (this.cutTile != null && this.targetPos == null) {
            // arrived: turn the saws to the wall and press against it (the wall stops the unit)
            this.unit.lookAt(this.cutTile.worldx(), this.cutTile.worldy());
            if (Angles.within(this.unit.rotation, this.unit.angleTo(this.cutTile.worldx(), this.cutTile.worldy()), 20.0F)) {
               this.moveTo(this.cutTile, 0.0F);
            }
         }
      }
   }
}
