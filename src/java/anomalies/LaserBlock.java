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
import arc.math.geom.Intersector;
import arc.math.geom.Point2;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.core.World;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

/**
 * Laser Block. A glowing purple four-pointed star sits on the block. Two Laser Blocks of the same team
 * closer than {@link #range} tiles can be linked (select one, tap the other) and a purple laser appears between
 * them that shocks every enemy unit and building under it.
 *
 * Multiplayer notes:
 *  - the link is a normal Mindustry block config (Integer / Point2), so it is sent to the server and broadcast
 *    to every client by the game itself, and it is saved/loaded with the world (so joining players get it too);
 *  - damage is applied only where {@code !Vars.net.client()} (server / single player), unit and building health
 *    reach the clients through the normal game sync;
 *  - everything visual (star, glow, beam, spark effects) is drawn / spawned locally on every client from the
 *    synced link, and never runs on a headless server.
 */
public class LaserBlock extends Block {
   /** Maximum distance between two linked blocks, in tiles. */
   public static final float range = 17.0F;
   /** Ticks between two damage pulses. */
   static final float hitInterval = 6.0F;
   /** Damage per pulse to units (10 per 6 ticks = 100/s). */
   static final float unitDamage = 10.0F;
   /** Damage per pulse to buildings (8 per 6 ticks = 80/s). */
   static final float buildDamage = 8.0F;
   /** Half thickness of the beam used for hit detection, world units. */
   static final float beamHit = 3.0F;

   static final Color col = Color.valueOf("d38bef");
   static final Color hot = Color.valueOf("f3dcff");

   /** Purple sparks where the laser touches a unit or a building. Spawned locally by every client. */
   static final Effect hitFx = new Effect(20.0F, (e) -> {
      Draw.color(hot, col, e.fin());
      Lines.stroke(1.4F * e.fout() + 0.3F);
      Angles.randLenVectors((long)e.id, 5, 2.0F + 11.0F * e.finpow(), (x, y) -> Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1.0F + e.fout() * 4.0F));
      Draw.color(col, 0.5F * e.fout());
      Fill.circle(e.x, e.y, 2.5F * e.fout());
   });

   static final IntSet seen = new IntSet();
   static final Seq<Building> hits = new Seq<>();

   public TextureRegion starRegion;
   public TextureRegion beamRegion;
   public TextureRegion orbRegion;

   public LaserBlock(String name) {
      super(name);
      this.size = 1;
      this.solid = true;
      this.update = true;
      this.destructible = true;
      this.configurable = true;
      this.alwaysUnlocked = true;
      this.copyConfig = false;
      this.health = 250;
      this.requirements(Category.effect, BuildVisibility.shown, ItemStack.with(new Object[]{Items.silicon, 30, Items.titanium, 15, Items.lead, 20}));

      // The same two config types the game's own bridges use: Point2 is relative (schematics), Integer is an absolute tile position.
      this.config(Point2.class, (LaserBuild b, Point2 p) -> b.link = Point2.pack(p.x + b.tileX(), p.y + b.tileY()));
      this.config(Integer.class, (LaserBuild b, Integer i) -> b.link = i);
      this.buildType = () -> new LaserBuild();
   }

   public void load() {
      super.load();
      this.starRegion = Core.atlas.find(this.name + "-star");
      this.beamRegion = Core.atlas.find(this.name + "-beam");
      this.orbRegion = Core.atlas.find(this.name + "-orb");
   }

   public void init() {
      super.init();
      // keep the block drawn while only part of its laser is on screen
      this.updateClipRadius((range + 2.0F) * 8.0F);
   }

   public void drawPlace(int x, int y, int rotation, boolean valid) {
      super.drawPlace(x, y, rotation, valid);
      float wx = (float)x * 8.0F + this.offset;
      float wy = (float)y * 8.0F + this.offset;
      Drawf.dashCircle(wx, wy, range * 8.0F, col);
      Vars.indexer.eachBlock(Vars.player == null ? null : Vars.player.team(), wx, wy, range * 8.0F, (b) -> b.block == this, (b) -> Drawf.selected(b, Pal.place));
   }

   public class LaserBuild extends Building {
      /** Tile position of the block this one shoots at, or -1. */
      public int link = -1;
      /** 0..1 growth of the star after the block is placed. */
      float starScale;
      /** 0..1 growth of the laser. */
      float beamProg;
      float hitTimer;
      /** Last known end of the laser, so it can shrink back after the link is lost. */
      float tx;
      float ty;

      /** The valid block at the other end of the laser, or null. */
      LaserBuild target() {
         if (this.link == -1 || !this.enabled) return null;
         Building b = Vars.world.build(this.link);
         if (!(b instanceof LaserBuild) || b == this || b.team != this.team) return null;
         if (Mathf.dst(this.x, this.y, b.x, b.y) > range * 8.0F + 0.5F) return null;
         return (LaserBuild)b;
      }

      public void updateTile() {
         this.starScale = Mathf.approachDelta(this.starScale, 1.0F, 0.03F);

         LaserBuild t = this.target();
         if (t != null) {
            this.tx = t.x;
            this.ty = t.y;
            this.beamProg = Mathf.approachDelta(this.beamProg, 1.0F, 0.05F);
         } else {
            this.beamProg = Mathf.approachDelta(this.beamProg, 0.0F, 0.1F);
            this.hitTimer = 0.0F;
            return;
         }

         if (this.beamProg < 0.99F) return;
         this.hitTimer += Time.delta;
         if (this.hitTimer >= hitInterval) {
            this.hitTimer %= hitInterval;
            this.zap();
         }
      }

      /**
       * Finds everything under the laser. Runs on every peer so sparks show up for everybody,
       * but only the server / single player actually deals damage.
       */
      void zap() {
         final float x1 = this.x;
         final float y1 = this.y;
         final float x2 = this.tx;
         final float y2 = this.ty;
         final boolean deal = !Vars.net.client();
         final Team me = this.team;

         // units (ground and air)
         float pad = 24.0F;
         Tmp.r1.set(Math.min(x1, x2) - pad, Math.min(y1, y2) - pad, Math.abs(x2 - x1) + pad * 2.0F, Math.abs(y2 - y1) + pad * 2.0F);
         Units.nearbyEnemies(me, Tmp.r1, (u) -> {
            if (u.dead() || u.team == Team.derelict || !u.targetable(me)) return;
            if (Intersector.distanceSegmentPoint(x1, y1, x2, y2, u.x, u.y) > u.hitSize / 2.0F + beamHit) return;
            hitFx.at(u.x, u.y);
            if (deal) {
               u.damage(unitDamage);
               u.apply(StatusEffects.shocked, 40.0F);
            }
         });

         // buildings of other teams along the line
         seen.clear();
         hits.clear();
         World.raycastEachWorld(x1, y1, x2, y2, (cx, cy) -> {
            Building b = Vars.world.build(cx, cy);
            if (b != null && b.team != me && b.team != Team.derelict && seen.add(b.pos())) {
               hits.add(b);
            }
            return false;
         });
         for (int i = 0; i < hits.size; ++i) {
            Building b = hits.get(i);
            hitFx.at(b.x, b.y);
            if (deal) b.damage(me, buildDamage);
         }
         hits.clear();
      }

      public void draw() {
         Draw.rect(this.block.region, this.x, this.y);
         Draw.z(Layer.effect);
         float pulse = 0.8F + 0.2F * Mathf.sin(Time.time / 11.0F + (float)this.id * 0.37F);
         if (this.beamProg > 0.001F) this.drawBeam(pulse);
         this.drawStar(pulse);
         Draw.reset();
      }

      void drawStar(float pulse) {
         float s = Mathf.clamp(this.starScale);
         s = s * s * (3.0F - 2.0F * s);
         if (s <= 0.001F) return;
         float boost = 1.0F + 0.45F * this.beamProg;
         float tw = 0.94F + 0.06F * Mathf.sin(Time.time / 7.0F + (float)this.id);

         Draw.blend(Blending.additive);
         // soft halo
         Fill.light(this.x, this.y, 24, 17.0F * s * pulse * boost, Tmp.c1.set(col).a(0.5F * s), Tmp.c2.set(col).a(0.0F));
         Draw.blend();

         Draw.color(Color.white);
         Draw.rect(LaserBlock.this.starRegion, this.x, this.y, 16.0F * s * tw, 16.0F * s * tw);

         // glow on top of the star
         Draw.blend(Blending.additive);
         Draw.color(col, Math.min(1.0F, 0.75F * pulse * boost * s));
         Draw.rect(LaserBlock.this.starRegion, this.x, this.y, 19.0F * s * tw, 19.0F * s * tw);
         Draw.blend();
         Draw.reset();

         Drawf.light(this.x, this.y, 46.0F * s * boost, col, 0.65F * pulse * s);
      }

      void drawBeam(float pulse) {
         float p = this.beamProg;
         float ex = this.x + (this.tx - this.x) * p;
         float ey = this.y + (this.ty - this.y) * p;
         float len = Mathf.dst(this.x, this.y, ex, ey);
         if (len < 0.5F) return;
         float ang = Angles.angle(this.x, this.y, ex, ey);
         float w = 6.5F + Mathf.absin(Time.time, 3.0F, 0.8F);

         // glow: wide soft additive strokes around the beam
         Draw.blend(Blending.additive);
         Lines.stroke(w * 3.6F);
         Draw.color(col, 0.10F * p * pulse);
         Lines.line(this.x, this.y, ex, ey);
         Lines.stroke(w * 2.2F);
         Draw.color(col, 0.20F * p * pulse);
         Lines.line(this.x, this.y, ex, ey);
         Draw.blend();

         // body: the beam sprite stretched along the laser
         Draw.color(Color.white, p);
         Draw.rect(LaserBlock.this.beamRegion, (this.x + ex) / 2.0F, (this.y + ey) / 2.0F, len, w, ang);

         Draw.blend(Blending.additive);
         // hot core
         Lines.stroke(1.5F);
         Draw.color(hot, 0.85F * p);
         Lines.line(this.x, this.y, ex, ey);

         // jittering electric arcs along the beam (same look on every client, seeded by time and block id)
         Mathf.randomSeed((long)this.id * 131L + (long)(Time.time / 2.5F));
         int segs = Math.max(2, (int)(len / 9.0F));
         float nx = -Mathf.sinDeg(ang);
         float ny = Mathf.cosDeg(ang);
         Lines.stroke(1.0F);
         for (int a = 0; a < 2; ++a) {
            float px = this.x;
            float py = this.y;
            Draw.color(a == 0 ? hot : col, (a == 0 ? 0.8F : 0.6F) * p);
            for (int i = 1; i <= segs; ++i) {
               float f = (float)i / (float)segs;
               float taper = Math.min(1.0F, Math.min(f, 1.0F - f) * 6.0F);
               float off = i == segs ? 0.0F : Mathf.range(4.5F) * taper;
               float qx = this.x + (ex - this.x) * f + nx * off;
               float qy = this.y + (ey - this.y) * f + ny * off;
               Lines.line(px, py, qx, qy);
               px = qx;
               py = qy;
            }
         }

         // orbs on both ends
         Draw.color(Color.white, p);
         Draw.blend();
         float os = 5.5F * pulse; // small, so the block itself stays visible under it
         Draw.rect(LaserBlock.this.orbRegion, this.x, this.y, os, os);
         Draw.rect(LaserBlock.this.orbRegion, ex, ey, os, os);
         Draw.blend(Blending.additive);
         Fill.light(ex, ey, 20, 12.0F * pulse, Tmp.c1.set(col).a(0.55F * p), Tmp.c2.set(col).a(0.0F));
         Draw.blend();

         Drawf.light(this.x, this.y, ex, ey, 44.0F, col, 0.6F * p * pulse);
         Draw.reset();
      }

      public void drawSelect() {
         super.drawSelect();
         Drawf.dashCircle(this.x, this.y, range * 8.0F, col);
      }

      public void drawConfigure() {
         Drawf.select(this.x, this.y, (float)this.block.size * 4.0F + 2.0F, Pal.accent);
         Drawf.dashCircle(this.x, this.y, range * 8.0F, col);
         LaserBuild cur = this.target();
         Vars.indexer.eachBlock(this, range * 8.0F, (b) -> b != this && b.block == this.block, (b) -> {
            boolean linked = cur == b;
            Drawf.select(b.x, b.y, (float)b.block.size * 4.0F + 2.0F + (linked ? 0.0F : Mathf.absin(Time.time, 4.0F, 1.0F)), linked ? Pal.place : Pal.breakInvalid);
         });
         if (cur != null) Drawf.dashLine(Pal.place, this.x, this.y, cur.x, cur.y);
         Draw.reset();
      }

      /** Tap another Laser Block while this one is selected to link / unlink it. */
      public boolean onConfigureBuildTapped(Building other) {
         if (other instanceof LaserBuild && other != this && other.team == this.team
            && Mathf.dst(this.x, this.y, other.x, other.y) <= range * 8.0F + 0.5F) {
            if (this.link == other.pos()) {
               this.configure(-1);
            } else {
               this.configure(other.pos());
               // a pair only needs one laser: drop the reverse link
               if (((LaserBuild)other).link == this.pos()) other.configure(-1);
            }
            return false;
         }
         return this != other;
      }

      public void pickedUp() {
         this.link = -1;
      }

      public Point2 config() {
         return this.link == -1 ? null : Point2.unpack(this.link).sub(this.tile.x, this.tile.y);
      }

      public void write(Writes w) {
         super.write(w);
         w.i(this.link);
      }

      public void read(Reads r, byte revision) {
         super.read(r, revision);
         this.link = r.i();
      }
   }
}
