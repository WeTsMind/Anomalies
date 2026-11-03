package anomalies;

import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.meta.BuildVisibility;

/** Gold wall (1x1, sprite gold-wall.png): a plain wall made of gold nuggets. */
public class GoldWall extends Wall {
   public GoldWall(String name) {
      super(name);
      this.requirements(Category.defense, BuildVisibility.shown, ItemStack.with(new Object[]{AnomalyMod.goldNugget, 6}));
      this.health = 500;
   }
}
