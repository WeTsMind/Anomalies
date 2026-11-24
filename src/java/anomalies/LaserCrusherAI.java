package anomalies;

import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ai.types.CommandAI;
import mindustry.content.Fx;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.world.Tile;

/**
 * The command AI of the laser crusher, separate from the Excavator's: the laser works from a distance, so the unit does not
 * need a tile next to the wall. Sent to a terrain wall (the move command clicked on the wall) it walks to the nearest free
 * tile from which the wall is within the laser's reach, stops there, turns to the wall and crushes it. With a zone order it
 * takes the walls of the zone one by one (the walls another cutter, excavator or laser crusher, has taken are left to it).
 * A wall it cannot get to (the unit makes no progress towards it) is given up: in a zone it is tried again later.
 */
public class LaserCrusherAI extends CommandAI implements ExcavatorUnit.WallCutter {
   /** the terrain wall tile it is crushing (or going to), null = none */
   public Tile cutTile;
   /** the free tile it walks to, null = none */
   public Tile stand;
   /** the zone it clears, null = none */
   public ExcavatorUnit.Zone zone;
   /** the laser reach from the unit centre to the wall tile centre, world units */
   final float reach;
   /** the wall was within reach once: it is its own until it is gone */
   boolean arrived;
   Tile lastCut;
   final IntSet skip = new IntSet();
   float skipTimer, pickTimer;
   /** progress check while walking: the distance to the wall at the last check and the time since then */
   float checkDist = Float.MAX_VALUE, checkTimer;
   int stuck;

   public LaserCrusherAI(float reach) {
      this.reach = reach;
   }

   @Override
   public Tile cutTile() {
      return this.cutTile;
   }

   @Override
   public ExcavatorUnit.Zone zone() {
      return this.zone;
   }

   /** working distance: a little inside the reach, so a leg step does not take the wall out of it */
   float workDist() {
      return this.reach - 4.0F;
   }

   void setCut(Tile t) {
      if (t != this.cutTile) {
         this.arrived = false;
         this.checkDist = Float.MAX_VALUE;
         this.checkTimer = 0.0F;
         this.stuck = 0;
      }
      this.cutTile = t;
   }

   void drop() {
      this.cutTile = null;
      this.stand = null;
      this.arrived = false;
      this.pickTimer = 0.0F;
   }

   void walkTo(Tile st) {
      super.commandPosition(new Vec2(st.worldx(), st.worldy()), false);
   }

   @Override
   public void commandPosition(Vec2 pos, boolean stopWhenInRange) {
      this.zone = null;
      this.stand = null;
      Tile t = pos == null ? null : Vars.world.tileWorld(pos.x, pos.y);
      if (ExcavatorUnit.terrainWall(t)) {
         this.cutTile = null; // a new order always starts over, even on the same wall
         this.setCut(t);
         this.stand = this.standTile(t, null);
         if (this.stand != null) this.walkTo(this.stand);
         else super.commandPosition(pos, false);
         if (!Vars.headless) Fx.attackCommand.at(t.worldx(), t.worldy());
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

   @Override
   public void setZone(ExcavatorUnit.Zone z) {
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

   /** the other laser crushers of this team (to keep off the tiles they stand on) */
   Seq<LaserCrusherAI> crushers() {
      Seq<LaserCrusherAI> s = new Seq<>();
      for (Unit u : this.unit.team.data().units) {
         if (u != this.unit && u.controller() instanceof LaserCrusherAI) s.add((LaserCrusherAI)u.controller());
      }
      return s;
   }

   /** the free tile closest to the unit from which the wall is within the laser's reach (not one another crusher stands on) */
   Tile standTile(Tile wall, Seq<LaserCrusherAI> others) {
      int r = (int)Math.ceil(this.workDist() / Vars.tilesize);
      float max = this.workDist() - this.unit.hitSize / 2.0F;
      Tile best = null;
      float bestScore = Float.MAX_VALUE;
      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -r; dy <= r; dy++) {
            if (dx == 0 && dy == 0) continue;
            Tile n = Vars.world.tile(wall.x + dx, wall.y + dy);
            if (n == null || n.solid() || !this.unit.canPass(n.x, n.y)) continue;
            if (Mathf.dst(n.worldx(), n.worldy(), wall.worldx(), wall.worldy()) > max) continue;
            if (others != null) {
               boolean taken = false;
               for (LaserCrusherAI o : others) if (o.stand == n) taken = true;
               if (taken) continue;
            }
            float score = this.unit.dst2(n.worldx(), n.worldy());
            if (score < bestScore) {
               bestScore = score;
               best = n;
            }
         }
      }
      return best;
   }

   /** the next wall of the zone: free (no other cutter on it), with a free tile to work from, close to the unit and its lane */
   Tile pick() {
      Seq<Unit> mates = new Seq<>();
      for (Unit u : this.unit.team.data().units) if (u != this.unit && u.controller() instanceof ExcavatorUnit.WallCutter) mates.add(u);
      Seq<LaserCrusherAI> crushers = this.crushers();
      ExcavatorUnit.Zone z = this.zone;
      float best = Float.MAX_VALUE;
      Tile bestTile = null, bestStand = null;
      for (int x = z.x1; x <= z.x2; x++) {
         for (int y = z.y1; y <= z.y2; y++) {
            Tile t = Vars.world.tile(x, y);
            if (!ExcavatorUnit.terrainWall(t) || this.skip.contains(t.pos())) continue;
            boolean taken = false;
            float crowd = 0.0F;
            for (Unit m : mates) {
               Tile o = ((ExcavatorUnit.WallCutter)m.controller()).cutTile();
               if (o == null) continue;
               if (o == t) {
                  taken = true;
                  break;
               }
               float d = Mathf.dst(o.x, o.y, t.x, t.y);
               if (d < 4.0F) crowd += (4.0F - d) * Vars.tilesize * 6.0F;
            }
            if (taken) continue;
            Tile st = this.standTile(t, crushers);
            if (st == null) continue;
            float score = this.unit.dst(st) + crowd;
            if (this.lastCut != null && Math.abs(t.x - this.lastCut.x) + Math.abs(t.y - this.lastCut.y) == 1) score -= Vars.tilesize * 4.0F;
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
      this.walkTo(this.stand);
   }

   /** the wall cannot be reached: in a zone it is tried again later, an order is cancelled */
   void unreachable() {
      if (this.zone != null) this.skip.add(this.cutTile.pos());
      this.targetPos = null;
      this.drop();
   }

   @Override
   public void defaultBehavior() {
      if (this.cutTile != null && !ExcavatorUnit.terrainWall(this.cutTile)) {
         // crushed (or changed): done with this tile
         this.lastCut = this.cutTile;
         this.targetPos = null;
         this.drop();
      }
      if (this.zone != null) this.updateZone();

      Tile c = this.cutTile;
      if (c != null) {
         this.group = null; // no formation: every crusher goes to its own place
         boolean inWork = this.unit.within(c.worldx(), c.worldy(), this.workDist());
         if (this.targetPos != null && this.commandQueue.isEmpty()) {
            if (inWork) {
               // the wall is within reach: stop here, no need to reach the tile itself
               this.targetPos = null;
            } else if (!this.arrived && (this.checkTimer += Time.delta) >= 90.0F) {
               // walking to it: it must get at least a little closer every 1.5 s, otherwise it is blocked
               this.checkTimer = 0.0F;
               float d = this.unit.dst(c.worldx(), c.worldy());
               this.stuck = d > this.checkDist - 2.0F ? this.stuck + 1 : 0;
               this.checkDist = d;
               if (this.stuck >= 3) {
                  this.unreachable();
                  super.defaultBehavior();
                  return;
               }
            }
         }
      }

      super.defaultBehavior();

      c = this.cutTile;
      if (c == null || this.targetPos != null) return;
      if (this.unit.within(c.worldx(), c.worldy(), this.reach - 2.0F)) {
         this.arrived = true;
         this.unit.lookAt(c.worldx(), c.worldy());
         return;
      }
      if (!this.arrived) {
         // the walk ended out of reach: it cannot get there
         this.unreachable();
         return;
      }
      // pushed or stepped out of reach: step back towards the wall, straight when it is close, by path when it is far
      if (this.unit.within(c.worldx(), c.worldy(), this.reach + Vars.tilesize * 2.0F)) {
         this.moveTo(c, this.workDist() - 2.0F);
         this.unit.lookAt(c.worldx(), c.worldy());
      } else {
         this.stand = this.standTile(c, this.crushers());
         if (this.stand != null) this.walkTo(this.stand);
         else this.unreachable();
      }
   }
}
