package anomalies;

import arc.Core;
import arc.graphics.Color;
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

public class SeraphCore extends CoreBlock {
   TextureRegion seraphBase;
   TextureRegion seraphLidL;
   TextureRegion seraphLidR;
   /** the light lines of the lids, drawn in the team colour */
   TextureRegion seraphTeamL;
   TextureRegion seraphTeamR;
   /** the Seraph waiting inside: body, team cell and the four folded wings */
   TextureRegion seraphBody;
   TextureRegion seraphCell;
   final TextureRegion[] seraphWings = new TextureRegion[4];

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
      this.seraphBody = Core.atlas.find("anomalies-seraph");
      this.seraphCell = Core.atlas.find("anomalies-seraph-cell");
      for(int var1 = 0; var1 < 4; ++var1) {
         this.seraphWings[var1] = Core.atlas.find("anomalies-seraph-wing-" + var1);
      }
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
         float var0 = Time.time - this.openStart;
         float var1 = SeraphAnim.lids(var0) * 7.5F;
         Draw.rect(SeraphCore.this.seraphBase, this.x, this.y);
         if (var0 >= 0.0F && var0 < SeraphAnim.OPEN_END) {
            this.drawSeraph(Mathf.clamp(var1 / 7.5F * 5.0F));
         }
         Draw.rect(SeraphCore.this.seraphLidL, this.x - 3.5F - var1, this.y);
         Draw.color(this.team.color);
         Draw.rect(SeraphCore.this.seraphTeamL, this.x - 3.5F - var1, this.y);
         Draw.reset();
         Draw.rect(SeraphCore.this.seraphLidR, this.x + 3.0F + var1, this.y);
         Draw.color(this.team.color);
         Draw.rect(SeraphCore.this.seraphTeamR, this.x + 3.0F + var1, this.y);
         Draw.reset();
      }

      /** The Seraph with its wings folded back, resting inside the open core until it flies out. */
      void drawSeraph(float var1) {
         Draw.color(Color.white, var1);

         for(int var2 = 0; var2 < 4; ++var2) {
            float var3 = (SeraphUnit.WINGS[var2][0] - 64.0F) / 4.0F;
            float var4 = (64.0F - SeraphUnit.WINGS[var2][1]) / 4.0F;
            float var5 = SeraphUnit.WINGS[var2][2] * 36.0F;
            float var6 = Mathf.cosDeg(var5);
            float var7 = Mathf.sinDeg(var5);
            float var8 = -var3;
            float var9 = -var4;
            Draw.rect(SeraphCore.this.seraphWings[var2], this.x + var3 + var8 * var6 - var9 * var7, this.y + var4 + var8 * var7 + var9 * var6, var5);
         }

         Draw.rect(SeraphCore.this.seraphBody, this.x, this.y);
         Draw.color(SeraphUnit.vivid(this.team), var1);
         Draw.rect(SeraphCore.this.seraphCell, this.x, this.y);
         Draw.reset();
      }
   }
}
