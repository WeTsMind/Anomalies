package anomalies;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.meta.BuildVisibility;

/** Powered saw that cuts through any wall in front of it after 30 seconds. */
public class WallBreaker extends Block {
   static final float breakTime = 1800.0F; // 30 seconds at 60 ticks/sec
   public TextureRegion sawRegion;

   public WallBreaker(String name) {
      super(name);
      this.size = 2;
      this.solid = true;
      this.rotate = true;
      this.update = true;
      this.hasPower = true;
      this.destructible = true;
      this.health = 400;
      this.requirements(Category.effect, BuildVisibility.shown, ItemStack.with(new Object[]{Items.plastanium, 50, Items.surgeAlloy, 5, Items.silicon, 20}));
      this.consumePower(2.0F);
      this.buildType = () -> new BreakerBuild();
   }

   public void load() {
      super.load();
      this.sawRegion = Core.atlas.find(this.name + "-saw");
   }

   static boolean breakable(Tile t) {
      if (t == null || t.block() == Blocks.air) return false;
      return t.block().isStatic() || t.block() instanceof Wall;
   }

   public class BreakerBuild extends Building {
      float progress;
      float sawAngle;
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
            this.sawAngle += 18.0F * eff * Time.delta;
            Tile t = this.findTarget();
            if (t != this.target) {
               this.target = t;
               this.progress = 0.0F;
            }
            if (t != null) {
               this.progress += Time.delta * eff;
               if (this.progress >= 1800.0F) {
                  this.progress = 0.0F;
                  this.target = null;
                  if (!Vars.net.client()) {
                     t.setNet(Blocks.air);
                  }
               }
            }
         } else {
            this.progress = Math.max(0.0F, this.progress - Time.delta * 2.0F);
         }
      }

      public boolean shouldConsume() {
         return this.findTarget() != null;
      }

      public void draw() {
         // Sprite is drawn facing up, so offset by -90 from Mindustry's default (right).
         Draw.rect(this.block.region, this.x, this.y, this.rotdeg() - 90.0F);
         Draw.z(Layer.block + 0.1F);
         Draw.rect(WallBreaker.this.sawRegion, this.x, this.y, this.sawAngle);
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
