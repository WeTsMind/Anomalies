package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
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

/** Powered block with three lasers that burn through the walls in front of it, 30 seconds per wall tile. */
public class WallBreaker extends Block {
   static final float breakTime = 1800.0F; // 30 seconds at 60 ticks/sec
   static final Color laserCol = Color.valueOf("5DFF7A");
   public TextureRegion glowRegion;
   public TextureRegion linesRegion;
   public TextureRegion emitterRegion;
   public TextureRegion beamRegion;

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

   public void load() {
      super.load();
      this.glowRegion = Core.atlas.find(this.name + "-glow");
      this.linesRegion = Core.atlas.find(this.name + "-lines");
      this.emitterRegion = Core.atlas.find(this.name + "-emitter");
      this.beamRegion = Core.atlas.find(this.name + "-beam");
   }

   // The sprite faces up, so the placement preview needs the same -90 offset as draw() below.
   @Override
   public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list) {
      Draw.rect(this.region, plan.drawx(), plan.drawy());
      Draw.rect(this.linesRegion, plan.drawx(), plan.drawy(), (float)plan.rotation * 90.0F - 90.0F);
   }

   static boolean breakable(Tile t) {
      if (t == null || t.block() == Blocks.air) return false;
      return t.block().isStatic() || t.block() instanceof Wall;
   }

   public class BreakerBuild extends Building {
      /** Burn progress of the two wall tiles in front of the block (lane 0 and lane 1). */
      final float[] progress = new float[2];
      /** 0..1 growth of each laser: 0 = lane 0, 1 = centre, 2 = lane 1. */
      final float[] beam = new float[3];
      float glowFade;
      final Tile[] targets = new Tile[2];

      int laneX(int lane) {
         switch (this.rotation) {
            case 0: return this.tile.x + 2;
            case 1: return this.tile.x + lane;
            case 2: return this.tile.x - 1;
            default: return this.tile.x + lane;
         }
      }

      int laneY(int lane) {
         switch (this.rotation) {
            case 0: return this.tile.y + lane;
            case 1: return this.tile.y + 2;
            case 2: return this.tile.y + lane;
            default: return this.tile.y - 1;
         }
      }

      /** The wall tile in front of the block on the given lane, or null if there is nothing to break. */
      Tile laneTarget(int lane) {
         Tile t = Vars.world.tile(this.laneX(lane), this.laneY(lane));
         return WallBreaker.breakable(t) ? t : null;
      }

      public void updateTile() {
         float eff = this.power.status;
         boolean on = eff > 0.01F;

         for (int i = 0; i < 2; ++i) {
            Tile t = this.laneTarget(i);
            if (t != this.targets[i]) {
               this.targets[i] = t;
               this.progress[i] = 0.0F;
            }
         }

         // All three lasers work at the same time: left and right ones each cut their own tile, the centre one helps visually.
         boolean left = this.targets[0] != null;
         boolean right = this.targets[1] != null;
         for (int k = 0; k < 3; ++k) {
            boolean wanted = k == 0 ? left : (k == 2 ? right : (left || right));
            if (on && wanted) {
               this.beam[k] = Mathf.approachDelta(this.beam[k], 1.0F, 0.06F * eff);
            } else {
               this.beam[k] = Mathf.approachDelta(this.beam[k], 0.0F, 0.12F);
            }
         }

         for (int i = 0; i < 2; ++i) {
            Tile t = this.targets[i];
            int laser = i == 0 ? 0 : 2;
            if (on && t != null) {
               if (this.beam[laser] >= 1.0F) {
                  this.progress[i] += Time.delta * eff;
               }
               if (this.progress[i] >= 1800.0F) {
                  float wx = t.worldx();
                  float wy = t.worldy();
                  this.progress[i] = 0.0F;
                  this.targets[i] = null;
                  this.beam[laser] = 0.0F;
                  Fx.explosion.at(wx, wy);
                  Fx.blockExplosionSmoke.at(wx, wy);
                  if (!Vars.net.client()) {
                     t.setNet(Blocks.air);
                  }
               }
            } else {
               this.progress[i] = Math.max(0.0F, this.progress[i] - Time.delta * 2.0F);
            }
         }
      }

      public boolean shouldConsume() {
         return this.laneTarget(0) != null || this.laneTarget(1) != null;
      }

      public void draw() {
         // Sprite is drawn facing up, so offset by -90 from Mindustry's default (right).
         Draw.rect(this.block.region, this.x, this.y);
         Draw.rect(WallBreaker.this.linesRegion, this.x, this.y, this.rotdeg() - 90.0F);
         Draw.reset();
         this.drawGlow();
         this.drawLasers();
      }

      float maxBeam() {
         return Math.max(this.beam[0], Math.max(this.beam[1], this.beam[2]));
      }

      /** Pulsing green glow over the bright parts of the sprite, only while the block is powered and cutting. */
      void drawGlow() {
         float on = this.power == null ? 0.0F : Mathf.clamp(this.power.status);
         this.glowFade = Mathf.lerpDelta(this.glowFade, this.maxBeam() * on, 0.04F);
         float a = this.glowFade * this.glowFade * (3.0F - 2.0F * this.glowFade);
         if (this.glowFade <= 0.001F) return;
         float pulse = 0.75F + 0.25F * (float)Math.sin((double)(Time.time / 18.0F));
         Draw.z(Layer.effect);
         Draw.blend(Blending.additive);
         Draw.color(WallBreaker.laserCol, a * pulse);
         Draw.rect(WallBreaker.this.glowRegion, this.x, this.y, this.rotdeg() - 90.0F);
         Draw.blend();
         Draw.reset();
      }

      /** Three parallel lasers built from the emitter and beam sprites. */
      void drawLasers() {
         if (this.maxBeam() <= 0.001F) return;

         int dx = Geometry.d4x(this.rotation);
         int dy = Geometry.d4y(this.rotation);
         // sideways direction (perpendicular to the facing)
         float px = -dy;
         float py = dx;
         float ang = this.rotdeg();
         float pulse = 0.85F + 0.15F * (float)Math.sin(Time.time * 0.6F);
         float heat0 = Mathf.clamp(this.progress[0] / 1800.0F);
         float heat1 = Mathf.clamp(this.progress[1] / 1800.0F);

         Draw.z(Layer.effect);

         for (int k = 0; k < 3; ++k) {
            float b = this.beam[k];
            if (b <= 0.001F) continue;

            float lat;
            float heat;
            if (k == 1) {
               lat = 0.0F;
               heat = Math.max(heat0, heat1);
            } else {
               int lane = k == 0 ? 0 : 1;
               float wx = this.laneX(lane) * 8.0F;
               float wy = this.laneY(lane) * 8.0F;
               lat = ((wx - this.x) * px + (wy - this.y) * py) * 1.2F;
               heat = lane == 0 ? heat0 : heat1;
            }

            // distance along the facing: emitters sit on the sprite, the wall face is 8 units from the centre
            float f0 = k == 1 ? 5.5F : 2.5F;
            float f1 = f0 + (9.5F - f0) * b;
            float sx = this.x + dx * f0 + px * lat;
            float sy = this.y + dy * f0 + py * lat;
            float ex = this.x + dx * f1 + px * lat;
            float ey = this.y + dy * f1 + py * lat;
            boolean hit = b >= 1.0F;

            // wall tile slowly glowing as it gets cut through (side lasers only)
            if (hit && k != 1) {
               Draw.blend(Blending.additive);
               Draw.color(WallBreaker.laserCol, 0.15F + 0.45F * heat);
               Fill.square(this.x + dx * 12.0F + px * (lat / 1.2F), this.y + dy * 12.0F + py * (lat / 1.2F), 4.0F);
               Draw.blend();
            }

            // beam: the sprite bar stretched between emitter and impact (5px of 48 are empty at each end)
            float len = f1 - f0;
            Draw.color(Color.white, Math.min(1.0F, b * pulse));
            Draw.rect(WallBreaker.this.beamRegion, (sx + ex) / 2.0F, (sy + ey) / 2.0F, (2.4F + 1.2F * heat) * pulse, len * 48.0F / 38.0F, ang - 90.0F);

            // emitter ring
            Draw.color(Color.white, b);
            Draw.rect(WallBreaker.this.emitterRegion, sx, sy, 8.0F * b, 8.0F * b, ang);

            // impact ring and sparks
            if (hit) {
               float ix = ex - dx * 1.5F;
               float iy = ey - dy * 1.5F;
               float rs = 8.0F * (0.7F + 0.5F * heat);
               Draw.color(Color.white, 0.9F);
               Draw.rect(WallBreaker.this.emitterRegion, ix, iy, rs, rs, Time.time * 3.0F);

               Draw.blend(Blending.additive);
               Mathf.randomSeed((long)this.id * 31L + (long)k * 7L + (long)(Time.time / 3.0F));
               int sparks = 3 + (int)(heat * 4.0F);
               Lines.stroke(1.0F);
               for (int i = 0; i < sparks; ++i) {
                  float sa = Mathf.random(360.0F);
                  float sl = Mathf.random(3.0F, 7.0F + 4.0F * heat);
                  Draw.color(WallBreaker.laserCol, Mathf.random(0.5F, 1.0F));
                  Lines.lineAngle(ix, iy, sa, sl);
               }
               Draw.blend();
            }
         }

         Draw.reset();
      }

      public void write(Writes w) {
         super.write(w);
         w.f(this.progress[0]);
         w.f(this.progress[1]);
      }

      public void read(Reads r, byte revision) {
         super.read(r, revision);
         this.progress[0] = r.f();
         try {
            this.progress[1] = r.f();
         } catch (Throwable ignored) {
            // old save with a single progress value
            this.progress[1] = 0.0F;
         }
      }
   }
}
