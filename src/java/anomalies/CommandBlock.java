package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.Time;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

public class CommandBlock extends Block {
   static final Color glow = Color.valueOf("ffd24a");
   public TextureRegion starRegion;

   public CommandBlock(String var1) {
      super(var1);
      this.size = 1;
      this.solid = true;
      this.update = false;
      this.destructible = true;
      this.configurable = true;
      this.health = 200;
      this.requirements(Category.effect, BuildVisibility.shown, ItemStack.with(new Object[0]));
      this.buildType = () -> new CommandBuild();
   }

   public void load() {
      super.load();
      this.starRegion = Core.atlas.find(this.name + "-star");
   }

   public class CommandBuild extends Building {
      public void draw() {
         Draw.rect(this.block.region, this.x, this.y);
         float var1 = 0.75F + 0.25F * (float)Math.sin((double)(Time.time / 18.0F));
         Draw.z(35.0F);
         Draw.blend(Blending.additive);
         Draw.color(CommandBlock.glow, var1);
         Draw.rect(CommandBlock.this.starRegion, this.x, this.y);
         Draw.blend();
         Draw.reset();
         Drawf.light(this.x, this.y, 28.0F, CommandBlock.glow, 0.5F * var1);
      }

      public void buildConfiguration(Table var1) {
         var1.table(Styles.black6, (var0) -> {
            var0.defaults().pad(3.0F);
            var0.button("", Styles.togglet, () -> AnomalyNet.set("anom.enabled", AnomalySettings.enabled() ? "0" : "1")).update((var0x) -> {
               String var10001 = Core.bundle.get("anomalies.generation", "Anomaly generation");
               var0x.setText(var10001 + ": " + UiHint.onOff(AnomalySettings.enabled()));
            }).checked((var0x) -> AnomalySettings.enabled()).size(250.0F, 40.0F).colspan(3).row();
            UiHint.add(var0, "anomalies.generation.hint", "Anomalies appear on their own while this is on.", 3);
            var0.add(Core.bundle.get("anomalies.chance", "Spawn chance per roll, %")).colspan(3).row();

            for(AnomalyType var4 : AnomalyType.all) {
               var0.add(var4.title()).width(110.0F).left();
               Label var5 = new Label(AnomalySettings.chance(var4) + "%");
               Slider var6 = new Slider(0.0F, 100.0F, 1.0F, false);
               var6.setValue((float)AnomalySettings.chance(var4));
               var6.changed(() -> {
                  AnomalyNet.set("anom.c." + var4.name(), String.valueOf((int)var6.getValue()));
                  var5.setText((int)var6.getValue() + "%");
               });
               var0.add(var6).width(140.0F);
               var0.add(var5).width(40.0F).row();
            }

            var0.table((var0x) -> {
               var0x.add(Core.bundle.get("anomalies.interval", "Roll every, sec"));
               var0x.field(String.valueOf(AnomalySettings.interval()), (sv) -> AnomalyNet.set("anom.interval", String.valueOf(Math.max(1, Strings.parseInt(sv, AnomalySettings.interval()))))).width(70.0F);
            }).colspan(3).row();
            var0.table((var0x) -> {
               var0x.add(Core.bundle.get("anomalies.count", "Rolls per interval (amount)"));
               var0x.field(String.valueOf(AnomalySettings.count()), (sv) -> AnomalyNet.set("anom.count", String.valueOf(Math.max(1, Math.min(50, Strings.parseInt(sv, AnomalySettings.count())))))).width(70.0F);
            }).colspan(3).row();
            var0.button("", Styles.togglet, () -> AnomalyNet.set("anom.destroy", AnomalySettings.destroyWorld() ? "0" : "1")).update((var0x) -> {
               String var10001 = Core.bundle.get("anomalies.worlddestroy", "World destruction");
               var0x.setText(var10001 + ": " + UiHint.onOff(AnomalySettings.destroyWorld()));
            }).checked((var0x) -> AnomalySettings.destroyWorld()).size(250.0F, 40.0F).colspan(3).row();
            UiHint.add(var0, "anomalies.worlddestroy.hint", "When off, the Black Hole only eats buildings and leaves walls and floor.", 3);
         });
      }
   }
}
