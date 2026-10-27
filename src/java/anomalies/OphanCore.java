package anomalies;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.game.Team;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BuildVisibility;

/** Core of the Ophan: the lids slide apart, the Ophan with folded wings is visible inside, it flies out and the core closes. */
public class OphanCore extends CoreBlock {
   /** how far the lids move between closed and open, world units */
   static final float SLIDE_L = 10.0F;
   static final float SLIDE_R = 9.5F;
   TextureRegion base, lidL, lidR, teamL, teamR;

   public OphanCore(String name) {
      super(name);
      this.size = 3;
      this.health = 2000;
      this.itemCapacity = 4000;
      this.unitCapModifier = 8;
      this.alwaysUnlocked = true;
      this.requirements(Category.effect, BuildVisibility.shown, ItemStack.with(new Object[]{Items.copper, 2000, Items.lead, 1500, Items.silicon, 800}));
      this.buildType = () -> new OphanBuild();
   }

   public void load() {
      super.load();
      this.base = Core.atlas.find(this.name + "-base");
      this.lidL = Core.atlas.find(this.name + "-lid-left");
      this.lidR = Core.atlas.find(this.name + "-lid-right");
      this.teamL = Core.atlas.find(this.name + "-lid-left-team");
      this.teamR = Core.atlas.find(this.name + "-lid-right-team");
      this.fixThrusters();
   }

   /** Landing thrusters: the mod has no own thruster sprites, without this the game draws the "Oh no" error texture in the centre of the core while it lands. */
   void fixThrusters() {
      this.thruster1 = thruster(this.name + "-thruster1", "core-shard-thruster1");
      this.thruster2 = thruster(this.name + "-thruster2", "core-shard-thruster2");
   }

   static TextureRegion thruster(String own, String vanilla) {
      if (Core.atlas.has(own)) return Core.atlas.find(own);
      if (Core.atlas.has(vanilla)) return Core.atlas.find(vanilla);
      return Core.atlas.find("clear");
   }

   public boolean canPlaceOn(Tile tile, Team team, int rotation) {
      if (tile == null) return false;
      if (Vars.state.isEditor()) return true;
      CoreBlock.CoreBuild c = team.core();
      return c != null && (Vars.state.rules.infiniteResources || c.items.has(this.requirements, Vars.state.rules.buildCostMultiplier));
   }

   public class OphanBuild extends CoreBlock.CoreBuild {
      public float openStart = -1.0E9F;

      public void draw() {
         float t = Time.time - this.openStart;
         float open = SeraphAnim.lids(t);
         Draw.rect(OphanCore.this.base, this.x, this.y);
         if (t >= 0.0F && t < SeraphAnim.OPEN_END && OphanCore.this.unitType instanceof OphanUnit) {
            ((OphanUnit)OphanCore.this.unitType).drawInCore(this.x, this.y, this.team, Mathf.clamp(open * 5.0F));
         }
         float dl = (1.0F - open) * SLIDE_L, dr = -(1.0F - open) * SLIDE_R;
         Draw.rect(OphanCore.this.lidL, this.x + dl, this.y);
         Draw.color(this.team.color);
         Draw.rect(OphanCore.this.teamL, this.x + dl, this.y);
         Draw.reset();
         Draw.rect(OphanCore.this.lidR, this.x + dr, this.y);
         Draw.color(this.team.color);
         Draw.rect(OphanCore.this.teamR, this.x + dr, this.y);
         Draw.reset();
      }
   }
}
