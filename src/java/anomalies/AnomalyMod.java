package anomalies;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureAtlas;
import arc.math.Angles;
import mindustry.content.Liquids;
import mindustry.entities.Effect;
import mindustry.world.draw.DrawDefault;
import mindustry.world.draw.DrawLiquidTile;
import mindustry.world.draw.DrawMulti;
import mindustry.world.draw.DrawRegion;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.ctype.ContentType;
import mindustry.ctype.MappableContent;
import mindustry.mod.Mod;
import mindustry.type.Category;
import mindustry.type.LiquidStack;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.BuildVisibility;
import mindustry.type.Item;
import mindustry.type.Liquid;
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
   public static Item tratat;
   public static Block tratatOre;
   public static Item gold;
   public static Block goldOre;
   public static Liquid liquidGold;
   public static Item goldNugget;
   public static Item stone;
   public static Block goldCrucible;
   public static Block cryoMold;

   /** White steam puffs rising from the cryogenic mold while it works. */
   static final Effect moldSteam = new Effect(70.0F, e -> {
      Draw.color(Color.valueOf("e8f1f5"), 0.55F * e.fout());
      Angles.randLenVectors(e.id, 3, 2.0F + e.finpow() * 9.0F, (x, y) -> Fill.circle(e.x + x, e.y + y + e.fin() * 6.0F, 0.8F + e.fin() * 2.2F));
      Draw.reset();
   });

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
      tratat = new Item("tratat", Color.valueOf("5ee58b")) {
         {
            this.hardness = 4;
         }
      };
      tratatOre = new OreBlock("tratat-ore", tratat);
      ((Floor)tratatOre).variants = 2;
      // gold: hardness of titanium (3), so a pneumatic drill or better mines it
      gold = new Item("gold", Color.valueOf("f5b73c")) {
         {
            this.hardness = 3;
         }
      };
      goldOre = new OreBlock("gold-ore", gold);
      ((Floor)goldOre).variants = 3;
      ((OreBlock)goldOre).oreDefault = true;
      ((OreBlock)goldOre).oreThreshold = 0.86F;
      ((OreBlock)goldOre).oreScale = 24.0F;
      // liquid gold: for now just a liquid (colour taken from its sprite)
      liquidGold = new Liquid("liquid-gold", Color.valueOf("ffd27f")) {
         {
            this.viscosity = 0.7F;
            this.temperature = 0.8F;
            this.heatCapacity = 0.6F;
         }
      };
      // two plain items for now (no recipes yet)
      goldNugget = new Item("gold-nugget", Color.valueOf("ffd27f"));
      stone = new Item("stone", Color.valueOf("646469"));
      // gold crucible: 5 gold -> 3 liquid gold + 10 stone per second, 150 power units/second (2.5 per tick), item capacity 20
      goldCrucible = new GenericCrafter("gold-crucible") {
         {
            this.requirements(Category.crafting, BuildVisibility.shown, ItemStack.with(new Object[]{Items.graphite, 50, Items.titanium, 50, Items.silicon, 40}));
            this.size = 2;
            this.health = 300;
            this.craftTime = 60.0F;
            this.itemCapacity = 20;
            this.liquidCapacity = 20.0F;
            this.consumeItems(ItemStack.with(new Object[]{AnomalyMod.gold, 5}));
            this.consumePower(2.5F);
            this.outputItem = new ItemStack(AnomalyMod.stone, 10);
            this.outputLiquids = LiquidStack.with(new Object[]{AnomalyMod.liquidGold, 3.0F / 60.0F});
         }
      };

      // cryogenic mold: 4 liquid gold + cryofluid + 60 power units/second (1 per tick) -> 1 gold nugget every 5 seconds, steam while it works.
      // Drawn in layers: bottom, the liquid gold (visible through the hole of the top part), the top part.
      cryoMold = new GenericCrafter("cryogenic-mold") {
         {
            this.requirements(Category.crafting, BuildVisibility.shown, ItemStack.with(new Object[]{Items.copper, 50, Items.graphite, 20, Items.silicon, 10}));
            this.size = 1;
            this.health = 120;
            this.craftTime = 300.0F;
            this.itemCapacity = 5;
            this.liquidCapacity = 10.0F;
            this.consumeLiquid(AnomalyMod.liquidGold, 4.0F / 300.0F);
            this.consumeLiquid(Liquids.cryofluid, 0.1F);
            this.consumePower(1.0F);
            this.outputItem = new ItemStack(AnomalyMod.goldNugget, 1);
            this.updateEffect = AnomalyMod.moldSteam;
            this.updateEffectChance = 0.12F;
            this.drawer = new DrawMulti(new DrawRegion("-bottom"), new DrawLiquidTile(AnomalyMod.liquidGold, 0.0F), new DrawDefault());
         }
      };
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
