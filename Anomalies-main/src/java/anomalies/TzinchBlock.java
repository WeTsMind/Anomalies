package anomalies;

import arc.Core;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

public class TzinchBlock extends Block {
   public TzinchBlock(String var1) {
      super(var1);
      this.size = 2;
      this.solid = true;
      this.update = false;
      this.destructible = true;
      this.configurable = true;
      this.health = 300;
      this.requirements(Category.effect, BuildVisibility.shown, ItemStack.with(new Object[0]));
      this.buildType = () -> new TzinchBuild();
   }

   public class TzinchBuild extends Building {
      AnomalyType sel;
      int tx;
      int ty;
      boolean inited;
      boolean alpha;
      int lifeSec;
      Anomaly mine;

      public TzinchBuild() {
         this.sel = AnomalyType.mirror;
      }

      void initCoords() {
         if (!this.inited) {
            this.tx = this.tile.x;
            this.ty = this.tile.y;
            this.inited = true;
         }

      }

      int cx() {
         return Math.max(0, Math.min(Vars.world.width() - 1, this.tx));
      }

      int cy() {
         return Math.max(0, Math.min(Vars.world.height() - 1, this.ty));
      }

      void spawnIt() {
         int var10000 = this.sel.ordinal();
         AnomalyNet.send("spawn|" + var10000 + "|" + this.cx() + "|" + this.cy() + "|" + this.lifeSec + "|" + (this.alpha ? 1 : 0));
         if (!Vars.net.client()) {
            this.mine = AnomalyNet.last;
         }

      }

      void deleteIt() {
         if (this.mine != null) {
            this.mine.dead = true;
            this.mine.life = Math.min(this.mine.life, this.mine.age + 1.0F);
         }

         int var10000 = this.cx();
         AnomalyNet.send("del|" + var10000 + "|" + this.cy());
         this.mine = null;
      }

      public void buildConfiguration(Table var1) {
         this.initCoords();
         var1.table(Styles.black6, (var1x) -> {
            var1x.defaults().pad(3.0F);
            int var2 = 0;

            for(AnomalyType var6 : AnomalyType.all) {
               Cell var7 = var1x.button(var6.title(), Styles.togglet, () -> this.sel = var6).checked((var2x) -> this.sel == var6).size(150.0F, 38.0F);
               ++var2;
               if (var2 % 2 == 0) {
                  var7.row();
               }
            }

            if (var2 % 2 == 1) {
               var1x.row();
            }

            var1x.button(Core.bundle.get("anomalies.tz.alpha", "Alpha"), Styles.togglet, () -> this.alpha = !this.alpha).checked((ab) -> this.alpha).size(310.0F, 38.0F).colspan(2).row();
            var1x.table((tt) -> {
               tt.add("X");
               tt.field(String.valueOf(this.tx), (sv) -> this.tx = Strings.parseInt(sv, this.tx)).width(70.0F);
               tt.add("  Y");
               tt.field(String.valueOf(this.ty), (sv) -> this.ty = Strings.parseInt(sv, this.ty)).width(70.0F);
            }).colspan(2).row();
            var1x.table((tt) -> {
               tt.add(Core.bundle.get("anomalies.tz.life", "Life, sec (0 = default)"));
               tt.field(String.valueOf(this.lifeSec), (sv) -> this.lifeSec = Math.max(0, Strings.parseInt(sv, this.lifeSec))).width(70.0F);
            }).colspan(2).row();
            var1x.button(Core.bundle.get("anomalies.tz.spawn", "Spawn"), Styles.defaultt, this::spawnIt).size(150.0F, 44.0F);
            var1x.button(Core.bundle.get("anomalies.tz.delete", "Delete anomaly"), Styles.defaultt, this::deleteIt).size(150.0F, 44.0F);
         });
      }

      public void write(Writes var1) {
         super.write(var1);
         var1.i(this.sel.ordinal());
         var1.i(this.tx);
         var1.i(this.ty);
         var1.bool(this.inited);
         var1.i(this.lifeSec);
      }

      public void read(Reads var1, byte var2) {
         super.read(var1, var2);
         this.sel = AnomalyType.all[Math.max(0, Math.min(AnomalyType.all.length - 1, var1.i()))];
         this.tx = var1.i();
         this.ty = var1.i();
         this.inited = var1.bool();

         try {
            this.lifeSec = Math.max(0, var1.i());
         } catch (Throwable var4) {
            this.lifeSec = 0;
         }

      }
   }
}
