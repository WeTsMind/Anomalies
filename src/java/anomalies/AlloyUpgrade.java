package anomalies;

import arc.scene.ui.Image;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.ctype.ContentType;
import mindustry.ctype.MappableContent;
import mindustry.gen.Building;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.ui.ReqImage;
import mindustry.world.Block;
import mindustry.world.blocks.payloads.UnitPayload;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.consumers.Consume;
import mindustry.world.consumers.ConsumeItems;
import java.lang.reflect.Field;

/**
 * Alloy is an upgrade of Golden Ratio in the regular (vanilla) Additive Reconstructor.
 * Upgrade cost: 20 surge alloy, 120 silicon, 50 titanium, 50 plastanium.
 * Arux is an upgrade of Alloy in the Multiplicative Reconstructor.
 * Upgrade cost: 100 surge alloy, 200 silicon, 150 titanium, 35 plastanium.
 * All other units keep the vanilla cost of their reconstructor.
 */
public class AlloyUpgrade {
   static final String GOLDEN = "anomalies-golden-ratio";
   static final String ALLOY = "anomalies-alloy";
   static final String ARUX = "anomalies-arux";

   public static ItemStack[] VANILLA_COST = ItemStack.with(new Object[]{Items.silicon, 40, Items.graphite, 40});
   public static ItemStack[] VANILLA_COST_MULTI = ItemStack.with(new Object[]{Items.silicon, 130, Items.titanium, 80, Items.metaglass, 40});
   public static final ItemStack[] ALLOY_COST = ItemStack.with(new Object[]{Items.surgeAlloy, 20, Items.silicon, 120, Items.titanium, 50, Items.plastanium, 50});
   public static final ItemStack[] ARUX_COST = ItemStack.with(new Object[]{Items.surgeAlloy, 100, Items.silicon, 200, Items.titanium, 150, Items.plastanium, 35});

   /** Runs BEFORE blocks are initialised (from loadContent): swaps the fixed item cost for one that depends on the unit inside. */
   public static void prepare() {
      swap(Blocks.additiveReconstructor, true);
      swap(Blocks.multiplicativeReconstructor, false);
   }

   @SuppressWarnings("unchecked")
   static void swap(Block block, boolean additive) {
      if (!(block instanceof Reconstructor)) {
         return;
      }
      try {
         Field field = Block.class.getDeclaredField("consumeBuilder");
         field.setAccessible(true);
         Seq<Consume> list = (Seq<Consume>)field.get(block);
         ItemStack[] vanilla = null;
         for (Consume c : list) {
            if (c instanceof ConsumeItems) {
               vanilla = ((ConsumeItems)c).items;
            }
         }
         if (vanilla != null) {
            if (additive) {
               VANILLA_COST = vanilla;
            } else {
               VANILLA_COST_MULTI = vanilla;
            }
         }
         list.removeAll(c -> c instanceof ConsumeItems);
         block.consume(new AdditiveConsume(additive ? VANILLA_COST : VANILLA_COST_MULTI));
      } catch (Throwable t) {
         Log.err(t);
      }
   }

   /** Runs AFTER the JSON content is loaded (from init): registers the upgrades and lets the reconstructors accept the extra items. */
   public static void register() {
      add(Blocks.additiveReconstructor, GOLDEN, ALLOY, ALLOY_COST);
      add(Blocks.multiplicativeReconstructor, ALLOY, ARUX, ARUX_COST);
   }

   static void add(Block block, String from, String to, ItemStack[] cost) {
      try {
         if (!(block instanceof Reconstructor)) {
            return;
         }
         MappableContent g = Vars.content.getByName(ContentType.unit, from);
         MappableContent a = Vars.content.getByName(ContentType.unit, to);
         if (!(g instanceof UnitType) || !(a instanceof UnitType)) {
            return;
         }
         Reconstructor rec = (Reconstructor)block;
         boolean has = false;
         for (UnitType[] pair : rec.upgrades) {
            if (pair[0] == g) {
               has = true;
            }
         }
         if (!has) {
            rec.upgrades.add(new UnitType[]{(UnitType)g, (UnitType)a});
         }
         for (ItemStack stack : cost) {
            int id = stack.item.id;
            if (rec.itemFilter != null && id < rec.itemFilter.length) {
               rec.itemFilter[id] = true;
            }
            if (rec.capacities != null && id < rec.capacities.length) {
               rec.capacities[id] = Math.max(rec.capacities[id], stack.amount * 2);
            }
            rec.itemCapacity = Math.max(rec.itemCapacity, stack.amount * 2);
         }
      } catch (Throwable t) {
         Log.err(t);
      }
   }

   /** What the reconstructor needs right now: depends on the unit that sits inside it. */
   static ItemStack[] cost(Building build) {
      boolean additive = build.block == Blocks.additiveReconstructor;
      try {
         if (build instanceof Reconstructor.ReconstructorBuild) {
            Reconstructor.ReconstructorBuild rb = (Reconstructor.ReconstructorBuild)build;
            if (rb.payload instanceof UnitPayload) {
               UnitType type = ((UnitPayload)rb.payload).unit.type;
               // the payload is already the upgraded unit at the moment the upgrade finishes and the items are taken
               if (type != null) {
                  if (additive && (GOLDEN.equals(type.name) || ALLOY.equals(type.name))) {
                     return ALLOY_COST;
                  }
                  if (build.block == Blocks.multiplicativeReconstructor && (ALLOY.equals(type.name) || ARUX.equals(type.name))) {
                     return ARUX_COST;
                  }
               }
            }
         }
      } catch (Throwable ignored) {
      }
      return additive ? VANILLA_COST : VANILLA_COST_MULTI;
   }

   static class AdditiveConsume extends ConsumeItems {
      AdditiveConsume(ItemStack[] vanilla) {
         super(vanilla);
      }

      @Override
      public void trigger(Building build) {
         for (ItemStack stack : cost(build)) {
            build.items.remove(stack);
         }
      }

      @Override
      public float efficiency(Building build) {
         return build.consumeTriggerValid() || build.items.has(cost(build)) ? 1.0F : 0.0F;
      }

      @Override
      public void build(Building build, Table table) {
         table.table(c -> {
            int i = 0;
            for (ItemStack stack : cost(build)) {
               c.add(new ReqImage(icon(stack), () -> build.items != null && build.items.has(stack.item, stack.amount))).padRight(8.0F).left();
               if (++i % 4 == 0) {
                  c.row();
               }
            }
         }).left();
      }
   }

   /** Item icon with the amount next to it (replacement for mindustry.ui.ItemImage). */
   static Table icon(ItemStack stack) {
      Table box = new Table();
      box.add(new Image(stack.item.uiIcon)).size(32.0F);
      box.add(String.valueOf(stack.amount)).padLeft(4.0F);
      return box;
   }
}
