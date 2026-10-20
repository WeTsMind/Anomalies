package anomalies;

import arc.Core;
import arc.Events;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.ui.Styles;

public class RulesHook {
   static boolean done;

   public static void init() {
      AnomalyDefaults.init();
      AvoidAI.init();

      try {
         Events.on(EventType.ClientLoadEvent.class, (var0) -> install());
      } catch (Throwable var1) {
      }

   }

   static void install() {
      if (!done) {
         try {
            Object var0 = field(Vars.ui, "editor");
            if (var0 == null) {
               return;
            }

            IdentityHashMap var1 = new IdentityHashMap();
            ArrayDeque<Object[]> var2 = new ArrayDeque<>();
            var2.add(new Object[]{var0, 0});

            while(!var2.isEmpty() && var1.size() < 3000) {
               Object[] var3 = var2.poll();
               Object var4 = var3[0];
               int var5 = (Integer)var3[1];
               if (var4 != null && var1.put(var4, true) == null) {
                  if (var4.getClass().getSimpleName().equals("CustomRulesDialog")) {
                     hook(var4);
                     done = true;
                  } else if (var5 < 4) {
                     for(Class var6 = var4.getClass(); var6 != null && var6 != Object.class; var6 = var6.getSuperclass()) {
                        String var7 = var6.getName();
                        if (var7.startsWith("java.") || var7.startsWith("arc.")) {
                           break;
                        }

                        for(Field var11 : var6.getDeclaredFields()) {
                           if (!var11.getType().isPrimitive() && !Modifier.isStatic(var11.getModifiers())) {
                              String var12 = var11.getType().getName();
                              if (!var12.startsWith("java.") && !var12.startsWith("arc.")) {
                                 try {
                                    var11.setAccessible(true);
                                    Object var13 = var11.get(var4);
                                    if (var13 != null) {
                                       var2.add(new Object[]{var13, var5 + 1});
                                    }
                                 } catch (Throwable var14) {
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         } catch (Throwable var15) {
         }

      }
   }

   static Object field(Object var0, String var1) {
      try {
         for(Class var2 = var0.getClass(); var2 != null; var2 = var2.getSuperclass()) {
            try {
               Field var3 = var2.getDeclaredField(var1);
               var3.setAccessible(true);
               return var3.get(var0);
            } catch (Throwable var4) {
            }
         }
      } catch (Throwable var5) {
      }

      return null;
   }

   static void hook(Object var0) {
      try {
         Method var1 = null;

         for(Class var2 = var0.getClass(); var2 != null && var1 == null; var2 = var2.getSuperclass()) {
            try {
               var1 = var2.getDeclaredMethod("shown", Runnable.class);
            } catch (NoSuchMethodException var4) {
            }
         }

         if (var1 == null) {
            return;
         }

         var1.setAccessible(true);
         var1.invoke(var0, (Runnable)() -> append(var0));
      } catch (Throwable var5) {
      }

   }

   static void append(Object var0) {
      try {
         Object var1 = field(var0, "main");
         if (!(var1 instanceof Table)) {
            return;
         }

         Table var2 = (Table)var1;
         Table var3 = new Table();
         build(var3);
         var2.row();
         var2.add(var3).left();
         var2.row();
      } catch (Throwable var4) {
      }

   }

   static String tr(String var0, String var1) {
      return Core.bundle.get(var0, var1);
   }

   static void toggle(Table var0, String var1, String var2, BooleanSupplier var3, Runnable var4) {
      Supplier var5 = () -> {
         String var10000 = tr(var1, var2);
         return var10000 + ": " + UiHint.onOff(var3.getAsBoolean());
      };
      TextButton[] var6 = new TextButton[1];
      Cell<TextButton> var7 = var0.button((String)var5.get(), Styles.togglet, () -> {
         var4.run();
         if (var6[0] != null) {
            var6[0].setText((String)var5.get());
         }

      });
      var7.update((var1x) -> var1x.setText((String)var5.get())).checked((var1x) -> var3.getAsBoolean()).size(250.0F, 40.0F).colspan(3).row();
      var6[0] = (TextButton)var7.get();
   }

   static void build(Table var0) {
      var0.defaults().pad(3.0F);
      var0.add(tr("block.anomalies-command-block.name", "Command Block")).colspan(3).row();
      toggle(var0, "anomalies.generation", "Anomaly generation", AnomalySettings::enabled, () -> AnomalySettings.setEnabled(!AnomalySettings.enabled()));
      toggle(var0, "anomalies.worlddestroy", "World destruction", AnomalySettings::destroyWorld, () -> AnomalySettings.setDestroyWorld(!AnomalySettings.destroyWorld()));
      toggle(var0, "anomalies.alpha", "Alpha anomalies (2-3x)", AlphaSettings::enabled, () -> AlphaSettings.set(!AlphaSettings.enabled()));
      toggle(var0, "anomalies.timekill", "Time anomaly destroys units", TimeRule::kills, () -> TimeRule.set(!TimeRule.kills()));
      toggle(var0, "anomalies.avoid", "Units avoid anomalies", AvoidAI::enabled, () -> AvoidAI.set(!AvoidAI.enabled()));
      var0.add(tr("anomalies.chance", "Spawn chance per roll, %")).colspan(3).row();

      for(AnomalyType var4 : AnomalyType.all) {
         var0.add(var4.title()).width(110.0F).left();
         Label var5 = new Label(AnomalySettings.chance(var4) + "%");
         Slider var6 = new Slider(0.0F, 100.0F, 1.0F, false);
         var6.setValue((float)AnomalySettings.chance(var4));
         var6.changed(() -> {
            AnomalySettings.setChance(var4, (int)var6.getValue());
            var5.setText((int)var6.getValue() + "%");
         });
         var0.add(var6).width(140.0F);
         var0.add(var5).width(40.0F).row();
      }

      var0.table((var0x) -> {
         var0x.add(tr("anomalies.interval", "Roll every, sec"));
         var0x.field(String.valueOf(AnomalySettings.interval()), (sv) -> AnomalySettings.setInterval(Strings.parseInt(sv, AnomalySettings.interval()))).width(70.0F);
      }).colspan(3).row();
      Label var7 = new Label(tr("anomalies.help", ""));
      var7.setWrap(true);
      var0.add(var7).width(300.0F).colspan(3).left().row();
   }
}
