package anomalies;

import arc.audio.Sound;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.ctype.ContentType;
import mindustry.ctype.MappableContent;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.units.UnitFactory;

public class AnomalyModGolden extends AnomalyModTurret {
   static final String UNIT = "anomalies-golden-ratio";
   public static UnitType alloy;

   public void loadContent() {
      super.loadContent();

      try {
         alloy = new AlloyUnit("alloy");
      } catch (Throwable var3) {
         report(var3);
      }

      try {
         AlloyUpgrade.prepare();
      } catch (Throwable var4) {
         report(var4);
      }

      try {
         if (polcimOre instanceof Floor) {
            ((Floor)polcimOre).variants = 3;
         }
      } catch (Throwable var2) {
         report(var2);
      }

   }

   public void init() {
      super.init();

      try {
         PolcimGlow.install();
      } catch (Throwable var5) {
         report(var5);
      }

      try {
         AlloyUpgrade.register();
      } catch (Throwable var4) {
         report(var4);
      }

      try {
         patchPlan();
      } catch (Throwable var3) {
         report(var3);
      }

      try {
         patchWeapon();
      } catch (Throwable var2) {
         report(var2);
      }

   }

   static void report(Throwable var0) {
      try {
         Log.err(var0);
      } catch (Throwable var2) {
      }

   }

   static void patchPlan() {
      if (Blocks.groundFactory instanceof UnitFactory && polcim != null) {
         UnitFactory var0 = (UnitFactory)Blocks.groundFactory;

         for(int var1 = 0; var1 < var0.plans.size; ++var1) {
            UnitFactory.UnitPlan var2 = (UnitFactory.UnitPlan)var0.plans.get(var1);
            if (var2.unit != null && "anomalies-golden-ratio".equals(var2.unit.name)) {
               var2.requirements = ItemStack.with(new Object[]{Items.silicon, 100, Items.titanium, 120, polcim, 50});
               if (var0.capacities != null) {
                  for(ItemStack var6 : var2.requirements) {
                     short var7 = var6.item.id;
                     if (var7 >= 0 && var7 < var0.capacities.length) {
                        var0.capacities[var7] = Math.max(var0.capacities[var7], var6.amount * 2);
                     }
                  }
               }
            }
         }

      }
   }

   static void patchWeapon() {
      MappableContent var1 = Vars.content.getByName(ContentType.unit, "anomalies-golden-ratio");
      if (var1 instanceof UnitType) {
         UnitType var2 = (UnitType)var1;
         ZigzagShot var3 = new ZigzagShot();
         var3.init();
         var2.health = 1500.0F;
         Sound var4 = null;

         try {
            if (Blocks.foreshadow instanceof Turret) {
               var4 = ((Turret)Blocks.foreshadow).shootSound;
            }
         } catch (Throwable var9) {
         }

         boolean var5 = false;

         try {
            GoldenAim.install();
            var5 = true;
         } catch (Throwable var8) {
            report(var8);
         }

         for(int var0 = 0; var0 < var2.weapons.size; ++var0) {
            Weapon var6 = (Weapon)var2.weapons.get(var0);
            var6.rotateSpeed = var5 ? 0.0F : 3.0F;
            var6.bullet = var3;
            var6.rotationLimit = 90.0F;
            var6.reload = 150.0F;
            var6.recoil = 2.5F;
            if (var4 != null) {
               var6.shootSound = var4;
            }
         }

         for(int var10 = 0; var10 < var2.weapons.size; ++var10) {
            try {
               ((Weapon)var2.weapons.get(var10)).recoilTime = 24.0F;
            } catch (Throwable var7) {
            }
         }

         var2.range = 160.0F;
         var2.maxRange = 160.0F;
      }
   }
}
