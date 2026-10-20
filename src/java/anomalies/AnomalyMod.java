package anomalies;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.TextureAtlas;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.ctype.ContentType;
import mindustry.ctype.MappableContent;
import mindustry.mod.Mod;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.OreBlock;
import mindustry.world.blocks.units.UnitFactory;

public class AnomalyMod extends Mod {
   public static Block tzinch;
   public static Block projector;
   public static Block command;
   public static Block wallBreaker;
   public static UnitType hunter;
   public static Item polcim;
   public static Block polcimOre;
   public static Block denseGrass;
   public static Block dryGrass;
   public static Block desertBush;
   public static Block cabbage;

   public void loadContent() {
      tzinch = new TzinchBlock("tzinch-block");
      projector = new RiftProjector("rift-projector");
      command = new CommandBlock2("command-block");
      wallBreaker = new WallBreaker("wall-breaker");
      hunter = new AnomalyHunter("hunter");
      polcim = new Item("polcim", Color.valueOf("5aa8ff")) {
         {
            this.hardness = 3;
         }

         public void load() {
            super.load();

            try {
               TextureAtlas.AtlasRegion var1 = Core.atlas.find(this.name);
               this.fullIcon = var1;
               this.uiIcon = var1;
            } catch (Throwable var2) {
            }

         }
      };
      polcimOre = new OreBlock("polcim-ore", polcim);
      ((Floor)polcimOre).variants = 1;
      denseGrass = new Floor("dense-grass", 3);
      denseGrass.mapColor = Color.valueOf("65b582");
      dryGrass = new Floor("dry-grass", 3);
      dryGrass.mapColor = Color.valueOf("f9cb7d");
      desertBush = new BushProp("desert-bush", 10, 0.4F, 32.0F, 6.0F, true);
      cabbage = new BushProp("cabbage", 4, 1.2F, 16.5F, 14.0F, false);
   }

   public void init() {
      AnomalyManager.init();
      RiftSpawner.init();
      PolcimOre.init();
      RulesHook.init();
      if (Blocks.airFactory instanceof UnitFactory) {
         ((UnitFactory)Blocks.airFactory).plans.add(new UnitFactory.UnitPlan(hunter, 1200.0F, ItemStack.with(new Object[]{Items.silicon, 40, Items.lead, 20})));
      }

      addGoldenRatio();
   }

   static void addGoldenRatio() {
      try {
         MappableContent var0 = Vars.content.getByName(ContentType.unit, "anomalies-golden-ratio");
         if (var0 instanceof UnitType && Blocks.groundFactory instanceof UnitFactory) {
            ((UnitFactory)Blocks.groundFactory).plans.add(new UnitFactory.UnitPlan((UnitType)var0, 1200.0F, ItemStack.with(new Object[]{Items.silicon, 20, Items.lead, 15})));
         }

      } catch (Throwable var1) {
      }
   }
}
