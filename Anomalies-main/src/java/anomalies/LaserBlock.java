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
 * Laser Block (up to {@link #maxLinks} lasers per block). A glowing purple four-pointed star sits on the block. Two Laser Blocks of the same team
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
   /** How many blocks one Laser Block can shoot at. */
   public static final int maxLinks = 3;
   /** Ticks between two damage pulses. */
   static final float hitInterval = 6.0F;
   /** Damage per pulse to units (10 per 6 ticks = 100/s). */
   static final float unitDamage = 10.0F;
   /** Damage per pulse to buildings (8 per 6 ticks = 80/s). */
   static final float buildDamage = 8.0F;
   /** Half thickness of the beam used for hit detection, world units. */
   static final float beamHit = 3.0F;

   public static final Color col = Color.valueOf("d38bef");
   public static final Color hot = Color.valueOf("f3dcff");

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
      // Integer: toggles a link to that tile position (-1 clears all links). Point2: relative link (schematics). Point2[]: whole link list.
      this.config(Integer.class, (LaserBuild b, Integer i) -> b.toggleLink(i));
      this.config(Point2.class, (LaserBuild b, Point2 p) -> b.addLink(Point2.pack(p.x + b.tileX(), p.y + b.tileY())));
      this.config(Point2[].class, (LaserBuild b, Point2[] ps) -> {
         b.clearLinks();
         for (Point2 p : ps) b.addLink(Point2.pack(p.x + b.tileX(), p.y + b.tileY()));
      });
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
      /** Tile positions of the blocks this one shoots at (-1 = free slot). */
      public final int[] links = {-1, -1, -1};
      /** 0..1 growth of the star after the block is placed. */
      float starScale;
      /** 0..1 growth of each laser. */
      final float[] beamProg = new float[maxLinks];
      float hitTimer;
      /** Last known end of each laser, so it can shrink back after the link is lost. */
      final float[] tx = new float[maxLinks];
      final float[] ty = new float[maxLinks];

      boolean hasLink(int pos) {
         for (int i = 0; i < maxLinks; ++i) if (this.links[i] == pos) return true;
         return false;
      }

      void clearLinks() {
         for (int i = 0; i < maxLinks; ++i) this.links[i] = -1;
      }

      /** Adds a link into the first free slot; does nothing if it exists already or all slots are used. */
      void addLink(int pos) {
         if (pos == -1 || this.hasLink(pos)) return;
         for (int i = 0; i < maxLinks; ++i) {
            if (this.links[i] == -1) {
               this.links[i] = pos;
               return;
            }
         }
      }

      /** Same code runs on the server and on every client, so the result is identical everywhere. */
      void toggleLink(int pos) {
         if (pos == -1) {
            this.clearLinks();
         } else if (this.hasLink(pos)) {
            for (int i = 0; i < maxLinks; ++i) if (this.links[i] == pos) this.links[i] = -1;
         } else {
            this.addLink(pos);
         }
      }

      int linkCount() {
         int n = 0;
         for (int i = 0; i < maxLinks; ++i) if (this.links[i] != -1) ++n;
         return n;
      }

      /** The valid block at the other end of laser slot i, or null. */
      LaserBuild target(int i) {
         if (this.links[i] == -1 || !this.enabled) return null;
         Building b = Vars.world.build(this.links[i]);
         if (!(b instanceof LaserBuild) || b == this || b.team != this.team) return null;
         if (Mathf.dst(this.x, this.y, b.x, b.y) > range * 8.0F + 0.5F) return null;
         return (LaserBuild)b;
      }

      float maxBeam() {
         float m = 0.0F;
         for (int i = 0; i < maxLinks; ++i) m = Math.max(m, this.beamProg[i]);
         return m;
      }

      public void updateTile() {
         this.starScale = Mathf.approachDelta(this.starScale, 1.0F, 0.03F);

         boolean ready = false;
         for (int i = 0; i < maxLinks; ++i) {
            LaserBuild t = this.target(i);
            if (t != null) {
               this.tx[i] = t.x;
               this.ty[i] = t.y;
               this.beamProg[i] = Mathf.approachDelta(this.beamProg[i], 1.0F, 0.05F);
               if (this.beamProg[i] >= 0.99F) ready = true;
            } else {
               this.beamProg[i] = Mathf.approachDelta(this.beamProg[i], 0.0F, 0.1F);
            }
         }

         if (!ready) {
            this.hitTimer = 0.0F;
            return;
         }
         this.hitTimer += Time.delta;
         if (this.hitTimer >= hitInterval) {
            this.hitTimer %= hitInterval;
            for (int i = 0; i < maxLinks; ++i) {
               if (this.beamProg[i] >= 0.99F && this.target(i) != null) this.zap(this.tx[i], this.ty[i]);
            }
         }
      }

      /**
       * Finds everything under one laser. Runs on every peer so sparks show up for everybody,
       * but only the server / single player actually deals damage.
       */
      void zap(final float x2, final float y2) {
         final float x1 = this.x;
         final float y1 = this.y;
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
         for (int i = 0; i < maxLinks; ++i) {
            if (this.beamProg[i] > 0.001F) this.drawBeam(i, pulse);
         }
         this.drawStar(pulse);
         Draw.reset();
      }

      void drawStar(float pulse) {
         float s = Mathf.clamp(this.starScale);
         s = s * s * (3.0F - 2.0F * s);
         if (s <= 0.001F) return;
         float boost = 1.0F + 0.3F * Math.min(this.linkCountShown(), 3);
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

      /** Sum of laser growth values: the star glows brighter with every active laser. */
      float linkCountShown() {
         float n = 0.0F;
         for (int i = 0; i < maxLinks; ++i) n += this.beamProg[i];
         return n;
      }

      void drawBeam(int slot, float pulse) {
         float p = this.beamProg[slot];
         float ex = this.x + (this.tx[slot] - this.x) * p;
         float ey = this.y + (this.ty[slot] - this.y) * p;
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

         // jittering electric arcs along the beam (same look on every client, seeded by time, block id and slot)
         Mathf.randomSeed((long)this.id * 131L + (long)slot * 17L + (long)(Time.time / 2.5F));
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
         Vars.indexer.eachBlock(this, range * 8.0F, (b) -> b != this && b.block == this.block, (b) -> {
            boolean linked = this.hasLink(b.pos());
            Drawf.select(b.x, b.y, (float)b.block.size * 4.0F + 2.0F + (linked ? 0.0F : Mathf.absin(Time.time, 4.0F, 1.0F)), linked ? Pal.place : Pal.breakInvalid);
         });
         for (int i = 0; i < maxLinks; ++i) {
            LaserBuild t = this.target(i);
            if (t != null) Drawf.dashLine(Pal.place, this.x, this.y, t.x, t.y);
         }
         Draw.reset();
      }

      /** Tap another Laser Block while this one is selected to link / unlink it (up to maxLinks at once). */
      public boolean onConfigureBuildTapped(Building other) {
         if (other instanceof LaserBuild && other != this && other.team == this.team
            && Mathf.dst(this.x, this.y, other.x, other.y) <= range * 8.0F + 0.5F) {
            if (this.hasLink(other.pos())) {
               this.configure(other.pos()); // toggles it off
            } else if (this.linkCount() < maxLinks) {
               this.configure(other.pos());
               // a pair only needs one laser: drop the reverse link
               if (((LaserBuild)other).hasLink(this.pos())) other.configure(this.pos());
            }
            return false;
         }
         return this != other;
      }

      public void pickedUp() {
         this.clearLinks();
      }

      public Point2[] config() {
         int n = this.linkCount();
         if (n == 0) return null;
         Point2[] out = new Point2[n];
         int k = 0;
         for (int i = 0; i < maxLinks; ++i) {
            if (this.links[i] != -1) out[k++] = Point2.unpack(this.links[i]).sub(this.tile.x, this.tile.y);
         }
         return out;
      }

      public void write(Writes w) {
         super.write(w);
         for (int i = 0; i < maxLinks; ++i) w.i(this.links[i]);
      }

      public void read(Reads r, byte revision) {
         super.read(r, revision);
         for (int i = 0; i < maxLinks; ++i) this.links[i] = r.i();
      }
   }
}
