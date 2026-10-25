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
 * All other units keep the vanilla cost of the reconstructor (40 silicon + 40 graphite).
 */
public class AlloyUpgrade {
   static final String GOLDEN = "anomalies-golden-ratio";
   static final String ALLOY = "anomalies-alloy";

   public static final ItemStack[] VANILLA_COST = ItemStack.with(new Object[]{Items.silicon, 40, Items.graphite, 40});
   public static final ItemStack[] ALLOY_COST = ItemStack.with(new Object[]{Items.surgeAlloy, 20, Items.silicon, 120, Items.titanium, 50, Items.plastanium, 50});

   /** Runs BEFORE blocks are initialised (from loadContent): swaps the fixed item cost for one that depends on the unit inside. */
   @SuppressWarnings("unchecked")
   public static void prepare() {
      if (!(Blocks.additiveReconstructor instanceof Reconstructor)) {
         return;
      }
      try {
         Block block = Blocks.additiveReconstructor;
         Field field = Block.class.getDeclaredField("consumeBuilder");
         field.setAccessible(true);
         Seq<Consume> list = (Seq<Consume>)field.get(block);
         list.removeAll(c -> c instanceof ConsumeItems);
         block.consume(new AdditiveConsume());
      } catch (Throwable t) {
         Log.err(t);
      }
   }

   /** Runs AFTER the JSON content is loaded (from init): registers the upgrade and lets the reconstructor accept the extra items. */
   public static void register() {
      try {
         if (!(Blocks.additiveReconstructor instanceof Reconstructor)) {
            return;
         }
         MappableContent g = Vars.content.getByName(ContentType.unit, GOLDEN);
         MappableContent a = Vars.content.getByName(ContentType.unit, ALLOY);
         if (!(g instanceof UnitType) || !(a instanceof UnitType)) {
            return;
         }
         Reconstructor rec = (Reconstructor)Blocks.additiveReconstructor;
         boolean has = false;
         for (UnitType[] pair : rec.upgrades) {
            if (pair[0] == g) {
               has = true;
            }
         }
         if (!has) {
            rec.upgrades.add(new UnitType[]{(UnitType)g, (UnitType)a});
         }
         for (ItemStack stack : ALLOY_COST) {
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
      try {
         if (build instanceof Reconstructor.ReconstructorBuild) {
            Reconstructor.ReconstructorBuild rb = (Reconstructor.ReconstructorBuild)build;
            if (rb.payload instanceof UnitPayload) {
               UnitType type = ((UnitPayload)rb.payload).unit.type;
               // the payload is already Alloy at the moment the upgrade finishes and the items are taken
               if (type != null && (GOLDEN.equals(type.name) || ALLOY.equals(type.name))) {
                  return ALLOY_COST;
               }
            }
         }
      } catch (Throwable ignored) {
      }
      return VANILLA_COST;
   }

   static class AdditiveConsume extends ConsumeItems {
      AdditiveConsume() {
         super(VANILLA_COST);
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
