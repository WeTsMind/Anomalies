package anomalies;

import arc.graphics.Color;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.meta.BuildVisibility;

/**
 * World Hologram: a Hologram Block that can only be placed and edited in the map editor or the sandbox. Map makers draw the secret picture there;
 * in a normal game players can only View it and Copy the picture (to paste it into a Decryptor).
 * The editing restriction itself lives in {@link HologramBlock} (config handler + buttons, flag {@code world}).
 */
public class WorldHologramBlock extends HologramBlock {
   public WorldHologramBlock(String name) {
      super(name);
      this.world = true;
      this.color = Color.valueOf("ffb26b");
      this.requirements(Category.effect, new BuildVisibility(() -> canEditWorld()), ItemStack.with(new Object[0]));
   }
}
