package anomalies;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.input.KeyCode;
import arc.struct.IntSet;
import arc.struct.Seq;
import mindustry.core.World;
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
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Building;
import mindustry.gen.Teamc;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.entities.part.HoverPart;
import mindustry.gen.ElevationMoveUnit;
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
 * Excavator: a ground hover unit (like the Elude) with 6 orange hover rings, 3 along each side. It cannot climb walls: it goes
 * around them like any ground unit. Sprites are cut from excavator.ase (4 px = 1 world unit):
 * - excavator.png the body, excavator-cell.png the cell (the three white pieces, team colour);
 * - no gun: the saws are its only weapon;
 * - excavator-saw.png the three saws on the front edge: they spin while working.
 * Saws: while working they hurt everything they touch (1 damage every 0.1 s) and throw bright orange sparks. Walls are cut down
 * by the saws in 30 seconds whatever their health: either command the unit to attack the wall (it walks up to it and starts
 * cutting), or drive it yourself right up to the wall and hold the fire button.
 */
public class ExcavatorUnit extends UnitType {
   /** saw centres (local units: x to the side, y forward) */
   static final float[][] SAW = {{-3.5F, 5.625F}, {0.0F, 5.375F}, {3.5F, 5.625F}};
   /** how far the teeth reach in front of a saw centre, world units */
   static final float SAW_REACH = 2.6F;
   /** saw damage and its interval: 1 damage every 0.1 s */
   static final float SAW_DAMAGE = 1.0F, SAW_INTERVAL = 6.0F;
   /** a wall is cut down in this many ticks (30 seconds) */
   static final float WALL_TIME = 30.0F * 60.0F;
   /** saw spin while working, degrees per tick */
   static final float SAW_SPIN = 18.0F;

   /** hover rings (the Elude effect, orange): 3 along each side of the body, mirrored; local units */
   static final float HOVER_X = 4.5F;
   static final float[] HOVER_Y = {3.5F, -0.5F, -4.5F};
   static final Color HOVER = Color.valueOf("ff9a3c");

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
      float sawRot, sawSpin, sawTimer, sparkTimer;
      /** the terrain tile being cut right now (Tile.pos), -1 = none */
      int cutPos = -1;
   }

   /** how far every terrain tile is cut (Tile.pos -> 0..1); cleared on every map load */
   static final IntFloatMap CUT = new IntFloatMap();

   static {
      Events.on(EventType.WorldLoadEvent.class, e -> {
         CUT.clear();
         dragging = false;
      });
      Events.run(EventType.Trigger.update, ExcavatorUnit::updateZoneInput);
      Events.run(EventType.Trigger.draw, ExcavatorUnit::drawZones);
   }

   /** a rectangle of tiles to clear (inclusive tile coordinates), shared by all excavators sent there together */
   public static class Zone {
      public final int x1, y1, x2, y2;

      public Zone(int x1, int y1, int x2, int y2) {
         this.x1 = x1;
         this.y1 = y1;
         this.x2 = x2;
         this.y2 = y2;
      }

      public boolean contains(Tile t) {
         return t != null && t.x >= x1 && t.x <= x2 && t.y >= y1 && t.y <= y2;
      }

      public boolean hasWalls() {
         for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) if (terrainWall(Vars.world.tile(x, y))) return true;
         return false;
      }
   }

   /**
    * What the zone orders and the wall sharing need from a wall cutter's AI. The Excavator (ExcavatorAI) and the laser crusher
    * (LaserCrusherAI) have separate AIs; this is all they share.
    */
   public interface WallCutter {
      /** the wall it is cutting or going to, null = none */
      Tile cutTile();

      Zone zone();

      void setZone(Zone z);
   }

   /** the biggest zone side, tiles */
   static final int ZONE_MAX = 80;
   static final Color ZONE = Color.valueOf("ff9a3c");

   /** right-button drag in command mode with excavators selected: the zone being drawn (world coordinates of the start) */
   static boolean dragging;
   static float dragX, dragY;

   static boolean excavatorsSelected() {
      for (Unit u : Vars.control.input.selectedUnits) if (u.controller() instanceof WallCutter) return true; // excavators and laser crushers
      return false;
   }

   static int[] dragTiles() {
      float mx = Core.input.mouseWorldX(), my = Core.input.mouseWorldY();
      int ax = World.toTile(dragX), ay = World.toTile(dragY), bx = World.toTile(mx), by = World.toTile(my);
      int x1 = Mathf.clamp(Math.min(ax, bx), 0, Vars.world.width() - 1), x2 = Mathf.clamp(Math.max(ax, bx), 0, Vars.world.width() - 1);
      int y1 = Mathf.clamp(Math.min(ay, by), 0, Vars.world.height() - 1), y2 = Mathf.clamp(Math.max(ay, by), 0, Vars.world.height() - 1);
      x2 = Math.min(x2, x1 + ZONE_MAX - 1);
      y2 = Math.min(y2, y1 + ZONE_MAX - 1);
      return new int[]{x1, y1, x2, y2};
   }

   /**
    * Zone orders (desktop): in command mode, with excavators selected, drag with the right mouse button over the walls to
    * clear. The press itself is the usual move order; the release (when the box is at least 2 tiles long) turns it into the
    * zone order for every selected excavator.
    */
   static void updateZoneInput() {
      if (Vars.headless || Vars.control == null || !Vars.state.isGame() || !Vars.control.input.commandMode) {
         dragging = false;
         return;
      }
      if (Core.input.keyTap(KeyCode.mouseRight) && !Core.scene.hasMouse() && excavatorsSelected()) {
         dragging = true;
         dragX = Core.input.mouseWorldX();
         dragY = Core.input.mouseWorldY();
      }
      if (dragging && !Core.input.keyDown(KeyCode.mouseRight)) {
         dragging = false;
         int[] r = dragTiles();
         if (r[2] - r[0] < 1 && r[3] - r[1] < 1) return; // a click, not a box: the usual order
         Zone zone = new Zone(r[0], r[1], r[2], r[3]);
         for (Unit u : Vars.control.input.selectedUnits) {
            if (u.controller() instanceof WallCutter) ((WallCutter)u.controller()).setZone(zone);
         }
         Fx.attackCommand.at((r[0] + r[2]) / 2.0F * Vars.tilesize, (r[1] + r[3]) / 2.0F * Vars.tilesize);
      }
   }

   static void drawZoneRect(int x1, int y1, int x2, int y2, float fill) {
      float x = x1 * Vars.tilesize - Vars.tilesize / 2.0F, y = y1 * Vars.tilesize - Vars.tilesize / 2.0F;
      float w = (x2 - x1 + 1) * Vars.tilesize, h = (y2 - y1 + 1) * Vars.tilesize;
      Draw.color(ZONE, fill);
      Fill.crect(x, y, w, h);
      Draw.color(ZONE, 0.9F);
      Lines.stroke(1.0F);
      Lines.rect(x, y, w, h);
      Draw.reset();
   }

   /** command mode: the zones of the selected excavators and the box being dragged */
   static void drawZones() {
      if (Vars.headless || Vars.control == null || !Vars.state.isGame() || !Vars.control.input.commandMode) return;
      Draw.z(Layer.overlayUI - 0.1F);
      Zone last = null;
      for (Unit u : Vars.control.input.selectedUnits) {
         if (!(u.controller() instanceof WallCutter)) continue;
         Zone z = ((WallCutter)u.controller()).zone();
         if (z == null || z == last) continue;
         last = z;
         drawZoneRect(z.x1, z.y1, z.x2, z.y2, 0.08F);
      }
      if (dragging) {
         int[] r = dragTiles();
         if (r[2] - r[0] >= 1 || r[3] - r[1] >= 1) drawZoneRect(r[0], r[1], r[2], r[3], 0.18F);
      }
      Draw.reset();
   }

   TextureRegion sawRegion;
   /** vanilla 1x1 block cracks, 8 stages */
   static final int CRACKS = 8;
   final TextureRegion[] cracks = new TextureRegion[CRACKS];

   public ExcavatorUnit(String name) {
      super(name);
      this.constructor = ElevationMoveUnit::create; // levitates like the Elude; still blocked by walls like any ground unit
      this.controller = u -> !this.playerControllable || (u.team.isAI() && !u.team.rules().rtsAi) ? this.aiController.get() : new ExcavatorAI();
      this.alwaysUnlocked = true;
      this.flying = false;
      this.hovering = true;
      this.canDrown = false;
      this.health = 600.0F;
      this.armor = 4.0F;
      this.speed = 0.55F;
      this.hitSize = 12.0F;
      this.rotateSpeed = 4.0F;
      this.engineSize = 0.0F;
      this.outlines = false;

      // levitation: 6 orange hover rings, 3 along each side, they glow out from under the body
      for (float hy : HOVER_Y) {
         this.parts.add(new HoverPart() {{
            x = HOVER_X;
            y = hy;
            mirror = true;
            radius = 6.0F;
            phase = 90.0F;
            stroke = 2.0F;
            layerOffset = -0.001F;
            color = HOVER;
         }});
      }
      this.drawCell = true;
      this.targetAir = true;
      this.targetGround = true;
      // no gun: the excavator only has its saws
   }

   public void load() {
      super.load();
      this.sawRegion = Core.atlas.find(this.name + "-saw");
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
      // a commanded excavator cuts only its own wall, never the one next to it (that may be another excavator's)
      if (cutTile != null && wall != cutTile) wall = null;
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

      // the terrain wall: one tile is cut out in 30 seconds (several excavators on one tile cut it faster)
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

      // command mode: the wall this unit was sent to cut is marked like an attack target (the red target sign)
      if (unit.isAdded() && unit.controller() instanceof ExcavatorAI && ((ExcavatorAI)unit.controller()).cutTile != null
         && Vars.control != null && Vars.control.input.commandMode && Vars.control.input.selectedUnits.contains(unit)) {
         Tile t = ((ExcavatorAI)unit.controller()).cutTile;
         Draw.z(Layer.overlayUI);
         Drawf.target(t.worldx(), t.worldy(), 6.0F, Pal.remove);
         Draw.reset();
         Draw.z(z);
      }

      // body and cell
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
   public static class ExcavatorAI extends CommandAI implements WallCutter {
      @Override
      public Tile cutTile() {
         return this.cutTile;
      }

      @Override
      public Zone zone() {
         return this.zone;
      }

      /** the terrain wall tile it is cutting (or going to), null = none */
      public Tile cutTile;
      /** the free tile it stands on to cut cutTile (zone work), null = none */
      public Tile stand;
      /** the zone it clears, null = none */
      public Zone zone;
      /** the last tile it cut out in the zone: the next one is taken next to it, so every excavator digs its own lane */
      Tile lastCut;
      /** zone tiles it could not reach, tried again later */
      final IntSet skip = new IntSet();
      float skipTimer, pickTimer;
      /** it has reached cutTile once: from then on it never gives the tile up, it only walks back when pushed away */
      boolean arrived;

      void setCut(Tile t) {
         if (t != this.cutTile) this.arrived = false;
         this.cutTile = t;
      }

      @Override
      public void commandPosition(Vec2 pos, boolean stopWhenInRange) {
         this.zone = null;
         this.stand = null;
         Tile t = pos == null ? null : Vars.world.tileWorld(pos.x, pos.y);
         if (terrainWall(t)) {
            // the wall is the target: the unit walks to the free tile next to it and cuts it
            this.cutTile = t;
            this.arrived = false;
            Tile st = this.standTile(t, null);
            super.commandPosition(st != null ? new Vec2(st.worldx(), st.worldy()) : pos, false);
            if (!Vars.headless) Fx.attackCommand.at(t.worldx(), t.worldy()); // the same red mark as an attack order
            return;
         }
         this.cutTile = null;
         super.commandPosition(pos, stopWhenInRange);
      }

      @Override
      public void commandTarget(Teamc moveTo, boolean stopAtTarget) {
         this.cutTile = null;
         this.stand = null;
         this.zone = null;
         super.commandTarget(moveTo, stopAtTarget);
      }

      @Override
      public void clearCommands() {
         this.cutTile = null;
         this.stand = null;
         this.zone = null;
         super.clearCommands();
      }

      /** the zone order: clear every terrain wall inside the zone */
      @Override
      public void setZone(Zone z) {
         this.commandQueue.clear();
         this.targetPos = null;
         this.attackTarget = null;
         this.group = null;
         this.cutTile = null;
         this.stand = null;
         this.lastCut = null;
         this.skip.clear();
         this.pickTimer = 0.0F;
         this.zone = z;
      }

      /** how close to the wall centre the saws can work on it: from a neighbouring tile */
      float workDist() {
         return Vars.tilesize * 1.6F + this.unit.hitSize / 2.0F;
      }

      /** the other wall cutters of this team (excavators and laser crushers), so they share the walls of a zone */
      Seq<Unit> mates() {
         Seq<Unit> s = new Seq<>();
         for (Unit u : this.unit.team.data().units) if (u.controller() instanceof WallCutter) s.add(u);
         return s;
      }

      static ExcavatorAI ai(Unit u) {
         return u.controller() instanceof ExcavatorAI ? (ExcavatorAI)u.controller() : null;
      }

      static Tile cutOf(Unit u) {
         return u.controller() instanceof WallCutter ? ((WallCutter)u.controller()).cutTile() : null;
      }

      /** takes the next wall of the zone: free, reachable from a free side, away from the other diggers, next to its own lane */
      Tile pick() {
         Seq<Unit> mates = this.mates();
         Zone z = this.zone;
         float best = Float.MAX_VALUE;
         Tile bestTile = null, bestStand = null;
         for (int x = z.x1; x <= z.x2; x++) {
            for (int y = z.y1; y <= z.y2; y++) {
               Tile t = Vars.world.tile(x, y);
               if (!terrainWall(t) || this.skip.contains(t.pos())) continue;
               boolean taken = false;
               float crowd = 0.0F;
               for (Unit m : mates) {
                  Tile o = cutOf(m);
                  if (m == this.unit || o == null) continue;
                  if (o == t) {
                     taken = true;
                     break;
                  }
                  float d = Mathf.dst(o.x, o.y, t.x, t.y);
                  if (d < 4.0F) crowd += (4.0F - d) * Vars.tilesize * 6.0F; // keep away from the others: own lanes
               }
               if (taken) continue;
               Tile st = this.standTile(t, mates);
               if (st == null) continue;
               float score = this.unit.dst(st) + crowd;
               if (this.lastCut != null) {
                  int dx = t.x - this.lastCut.x, dy = t.y - this.lastCut.y;
                  if (Math.abs(dx) + Math.abs(dy) == 1) score -= Vars.tilesize * 4.0F; // keep digging the own lane
               }
               if (score < best) {
                  best = score;
                  bestTile = t;
                  bestStand = st;
               }
            }
         }
         this.stand = bestStand;
         return bestTile;
      }

      void updateZone() {
         if ((this.skipTimer += Time.delta) > 60.0F * 10.0F) {
            this.skipTimer = 0.0F;
            this.skip.clear();
         }
         if (this.cutTile != null) return;
         if ((this.pickTimer -= Time.delta) > 0.0F) return;
         this.pickTimer = 20.0F;
         Tile t = this.pick();
         if (t == null) {
            if (!this.zone.hasWalls()) this.zone = null; // all cleared: done
            return;
         }
         this.setCut(t);
         super.commandPosition(new Vec2(this.stand.worldx(), this.stand.worldy()), false);
      }

      /** the free (passable) tile next to the wall that is closest to the unit; with mates, not one another excavator stands on */
      Tile standTile(Tile wall, Seq<Unit> mates) {
         Tile best = null;
         float bestScore = Float.MAX_VALUE;
         for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
               if (dx == 0 && dy == 0) continue;
               Tile n = Vars.world.tile(wall.x + dx, wall.y + dy);
               if (n == null || n.solid() || !this.unit.canPass(n.x, n.y)) continue;
               if (mates != null) {
                  boolean taken = false;
                  for (Unit m : mates) {
                     ExcavatorAI o = ai(m);
                     if (m != this.unit && o != null && o.stand == n) taken = true;
                  }
                  if (taken) continue;
               }
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
         if (this.cutTile != null && !terrainWall(this.cutTile)) {
            // cut out (or changed): done with this tile
            this.lastCut = this.cutTile;
            this.cutTile = null;
            this.stand = null;
            this.pickTimer = 0.0F;
         }
         if (this.zone != null) this.updateZone();
         // when several units are sent together, the formation would spread them away from the wall: every excavator goes
         // straight to its own place next to the wall instead
         if (this.cutTile != null) this.group = null;
         // close enough to the wall to work on it: the path is done. The vanilla AI only ends it within 6 units of the stand
         // tile, which a unit never reaches when another one stands on it or blocks the way: it would walk there forever
         if (this.cutTile != null && this.targetPos != null && this.commandQueue.isEmpty()
            && this.unit.within(this.cutTile.worldx(), this.cutTile.worldy(), this.workDist())) {
            this.targetPos = null;
         }
         super.defaultBehavior();
         if (this.cutTile != null && this.targetPos == null) {
            boolean near = this.unit.within(this.cutTile.worldx(), this.cutTile.worldy(), Vars.tilesize * 2.3F + this.unit.hitSize / 2.0F);
            if (!this.arrived) {
               if (this.zone != null && !near && CUT.get(this.cutTile.pos(), 0.0F) <= 0.0F) {
                  // the path ended far from the wall (it cannot be reached): take another one, try this one later
                  this.skip.add(this.cutTile.pos());
                  this.cutTile = null;
                  this.stand = null;
                  this.pickTimer = 0.0F;
                  return;
               }
               this.arrived = true;
            } else if (!this.unit.within(this.cutTile.worldx(), this.cutTile.worldy(), Vars.tilesize * 3.5F + this.unit.hitSize / 2.0F)) {
               // pushed away from its wall (by the other excavators): walk back to it, the wall stays its own
               Tile st = this.stand != null ? this.stand : this.standTile(this.cutTile, null);
               if (st != null) {
                  super.commandPosition(new Vec2(st.worldx(), st.worldy()), false);
                  return;
               }
            }
            // arrived: turn the saws to the wall and press against it (the wall stops the unit)
            this.unit.lookAt(this.cutTile.worldx(), this.cutTile.worldy());
            if (Angles.within(this.unit.rotation, this.unit.angleTo(this.cutTile.worldx(), this.cutTile.worldy()), 20.0F)) {
               this.moveTo(this.cutTile, 0.0F);
            }
         }
      }
   }
}
