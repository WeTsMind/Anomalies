package anomalies;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.util.Eachable;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.meta.BuildVisibility;

/** Powered laser that burns through any wall in front of it after 30 seconds. */
public class WallBreaker extends Block {
   static final float breakTime = 1800.0F; // 30 seconds at 60 ticks/sec
   static final Color laserCol = Color.valueOf("5DFF7A");

   public WallBreaker(String name) {
      super(name);
      this.size = 2;
      this.solid = true;
      this.rotate = true;
      this.update = true;
      this.hasPower = true;
      this.destructible = true;
      this.alwaysUnlocked = true;
      this.health = 400;
      this.requirements(Category.effect, BuildVisibility.shown, ItemStack.with(new Object[]{Items.plastanium, 50, Items.surgeAlloy, 5, Items.silicon, 20}));
      this.consumePower(2.0F);
      this.buildType = () -> new BreakerBuild();
   }

   // The sprite faces up, so the placement preview needs the same -90 offset as draw() below.
   @Override
   public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list) {
      Draw.rect(this.region, plan.drawx(), plan.drawy(), (float)plan.rotation * 90.0F - 90.0F);
   }

   static boolean breakable(Tile t) {
      if (t == null || t.block() == Blocks.air) return false;
      return t.block().isStatic() || t.block() instanceof Wall;
   }

   public class BreakerBuild extends Building {
      float progress;
      /** 0..1: how far the laser has grown out of the block towards the wall. */
      float beam;
      Tile target;

      /** The first breakable wall tile in front of the block (two tiles wide). */
      Tile findTarget() {
         int r = this.rotation;
         int bx = this.tile.x;
         int by = this.tile.y;
         for (int i = 0; i < 2; ++i) {
            Tile t;
            switch (r) {
               case 0: t = Vars.world.tile(bx + 2, by + i); break;
               case 1: t = Vars.world.tile(bx + i, by + 2); break;
               case 2: t = Vars.world.tile(bx - 1, by + i); break;
               default: t = Vars.world.tile(bx + i, by - 1); break;
            }
            if (WallBreaker.breakable(t)) return t;
         }
         return null;
      }

      public void updateTile() {
         float eff = this.power.status;
         if (eff > 0.01F) {
            Tile t = this.findTarget();
            if (t != this.target) {
               this.target = t;
               this.progress = 0.0F;
            }
            if (t != null) {
               // The laser first grows out of the block, then keeps burning into the wall.
               this.beam = Mathf.approachDelta(this.beam, 1.0F, 0.06F * eff);
               if (this.beam >= 1.0F) {
                  this.progress += Time.delta * eff;
               }
               if (this.progress >= 1800.0F) {
                  float wx = t.worldx();
                  float wy = t.worldy();
                  this.progress = 0.0F;
                  this.target = null;
                  this.beam = 0.0F;
                  Fx.explosion.at(wx, wy);
                  Fx.blockExplosionSmoke.at(wx, wy);
                  if (!Vars.net.client()) {
                     t.setNet(Blocks.air);
                  }
               }
            } else {
               this.beam = Mathf.approachDelta(this.beam, 0.0F, 0.12F);
            }
         } else {
            this.progress = Math.max(0.0F, this.progress - Time.delta * 2.0F);
            this.beam = Mathf.approachDelta(this.beam, 0.0F, 0.12F);
         }
      }

      public boolean shouldConsume() {
         return this.findTarget() != null;
      }

      public void draw() {
         // Sprite is drawn facing up, so offset by -90 from Mindustry's default (right).
         Draw.rect(this.block.region, this.x, this.y, this.rotdeg() - 90.0F);
         Draw.reset();
         this.drawLaser();
      }

      /** Short green laser: grows from the block into the wall, glows hotter as the wall gets cut. */
      void drawLaser() {
         Tile t = this.target;
         if (t == null || this.beam <= 0.001F) return;

         int dx = Geometry.d4x(this.rotation);
         int dy = Geometry.d4y(this.rotation);
         float wx = t.worldx();
         float wy = t.worldy();
         // Emitter sits on the block's centre line, lined up with the wall tile being cut.
         float sx = wx - dx * 12.0F;
         float sy = wy - dy * 12.0F;
         float ex = sx + (wx - sx) * this.beam;
         float ey = sy + (wy - sy) * this.beam;
         float heat = Mathf.clamp(this.progress / 1800.0F);
         float pulse = 0.8F + 0.2F * (float)Math.sin(Time.time * 0.6F);
         boolean hit = this.beam >= 1.0F;

         Draw.z(Layer.effect);
         Draw.blend(Blending.additive);

         // wall slowly glowing as it gets cut through
         if (hit) {
            Draw.color(WallBreaker.laserCol, 0.15F + 0.45F * heat);
            Fill.square(wx, wy, 4.0F);
         }

         // beam: wide soft glow + thin bright core
         for (int i = 0; i < 2; ++i) {
            Lines.stroke((i == 0 ? 3.4F : 1.3F) * (0.7F + 0.5F * heat) * this.beam);
            Draw.color(i == 0 ? WallBreaker.laserCol : Color.white, (i == 0 ? 0.35F : 0.95F) * pulse);
            Lines.line(sx, sy, ex, ey);
         }

         // emitter glow
         Draw.color(WallBreaker.laserCol, 0.6F * this.beam);
         Fill.circle(sx, sy, 2.6F);
         Draw.color(Color.white, 0.9F * this.beam);
         Fill.circle(sx, sy, 1.2F);

         // impact point with flying sparks
         if (hit) {
            float ix = wx - dx * 4.0F;
            float iy = wy - dy * 4.0F;
            Draw.color(WallBreaker.laserCol, 0.5F * pulse);
            Fill.circle(ix, iy, 3.0F + 2.5F * heat);
            Draw.color(Color.white, 0.9F);
            Fill.circle(ix, iy, 1.4F + 0.8F * heat);

            Mathf.randomSeed((long)this.id * 31L + (long)(Time.time / 3.0F));
            int sparks = 3 + (int)(heat * 4.0F);
            Lines.stroke(1.0F);
            for (int i = 0; i < sparks; ++i) {
               float ang = Mathf.random(360.0F);
               float len = Mathf.random(3.0F, 7.0F + 4.0F * heat);
               Draw.color(WallBreaker.laserCol, Mathf.random(0.5F, 1.0F));
               Lines.lineAngle(ix, iy, ang, len);
            }
         }

         Draw.blend();
         Draw.reset();
      }

      public void write(Writes w) {
         super.write(w);
         w.f(this.progress);
      }

      public void read(Reads r, byte revision) {
         super.read(r, revision);
         this.progress = r.f();
      }
   }
}
