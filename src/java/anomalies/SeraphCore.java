package anomalies;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.game.Team;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BuildVisibility;

public class SeraphCore extends CoreBlock {
   TextureRegion seraphBase;
   TextureRegion seraphLidL;
   TextureRegion seraphLidR;
   /** the light lines of the lids, drawn in the team colour */
   TextureRegion seraphTeamL;
   TextureRegion seraphTeamR;

   public SeraphCore(String var1) {
      super(var1);
      this.size = 2;
      this.health = 1500;
      this.itemCapacity = 4000;
      this.unitCapModifier = 8;
      this.alwaysUnlocked = true;
      this.requirements(Category.effect, BuildVisibility.shown, ItemStack.with(new Object[]{Items.copper, 1500, Items.lead, 1000, Items.silicon, 500}));
      this.buildType = () -> new SeraphBuild();
   }

   public void load() {
      super.load();
      this.seraphBase = Core.atlas.find(this.name + "-base");
      this.seraphLidL = Core.atlas.find(this.name + "-lid-left");
      this.seraphLidR = Core.atlas.find(this.name + "-lid-right");
      this.seraphTeamL = Core.atlas.find(this.name + "-lid-left-team");
      this.seraphTeamR = Core.atlas.find(this.name + "-lid-right-team");
   }

   public boolean canPlaceOn(Tile var1, Team var2, int var3) {
      if (var1 == null) {
         return false;
      } else if (Vars.state.isEditor()) {
         return true;
      } else {
         CoreBlock.CoreBuild var4 = var2.core();
         return var4 != null && (Vars.state.rules.infiniteResources || var4.items.has(this.requirements, Vars.state.rules.buildCostMultiplier));
      }
   }

   public class SeraphBuild extends CoreBlock.CoreBuild {
      public float openStart = -1.0E9F;

      public void draw() {
         float var1 = SeraphAnim.lids(Time.time - this.openStart) * 7.5F;
         Draw.rect(SeraphCore.this.seraphBase, this.x, this.y);
         Draw.rect(SeraphCore.this.seraphLidL, this.x - 3.5F - var1, this.y);
         Draw.color(this.team.color);
         Draw.rect(SeraphCore.this.seraphTeamL, this.x - 3.5F - var1, this.y);
         Draw.reset();
         Draw.rect(SeraphCore.this.seraphLidR, this.x + 3.0F + var1, this.y);
         Draw.color(this.team.color);
         Draw.rect(SeraphCore.this.seraphTeamR, this.x + 3.0F + var1, this.y);
         Draw.reset();
      }
   }
}
