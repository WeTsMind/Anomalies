package anomalies;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

/**
 * Decryptor: take the data from a {@link DataBlock} to this block, press "Load data", wait {@link #decryptTime}
 * (progress bar over the block), then take the blueprints of the building.
 * Loaded block + progress are saved with the world (so joining players get them); every client advances the progress itself.
 */
public class DecryptorBlock extends Block {
   /** Decryption time in ticks (30 s). */
   public static final float decryptTime = 1800.0F;
   /** How close the unit with the data must be to load it, world units. */
   public static final float loadRange = 40.0F;
   public static final Color col = Color.valueOf("5DFF7A");

   public DecryptorBlock(String name) {
      super(name);
      this.size = 2;
      this.solid = true;
      this.update = true;
      this.destructible = true;
      this.configurable = true;
      this.alwaysUnlocked = true;
      this.health = 400;
      this.requirements(Category.effect, BuildVisibility.shown, ItemStack.with(new Object[]{Items.silicon, 60, Items.titanium, 40, Items.lead, 50}));
      // Block = load that data, Integer = reset (blueprints taken)
      this.config(Block.class, (DecryptorBuild b, Block t) -> {
         if (b.loaded == null) {
            b.loaded = t;
            b.progress = 0.0F;
         }
      });
      this.config(Integer.class, (DecryptorBuild b, Integer i) -> {
         b.loaded = null;
         b.progress = 0.0F;
      });
      this.buildType = () -> new DecryptorBuild();
   }

   public void setBars() {
      super.setBars();
      this.addBar("anomalies-decrypt", (DecryptorBuild b) -> new Bar(() -> b.loaded == null ? Core.bundle.get("anomalies.decrypt.idle") : Core.bundle.format("anomalies.decrypt.working", (int)(b.progress * 100.0F)), () -> col, () -> b.progress));
   }

   public class DecryptorBuild extends Building {
      public Block loaded;
      public float progress;

      public void buildConfiguration(Table table) {
         table.defaults().pad(3.0F);
         table.label(() -> this.loaded == null ? Core.bundle.get("anomalies.decrypt.idle")
            : (this.progress < 1.0F ? Core.bundle.format("anomalies.decrypt.working", (int)(this.progress * 100.0F)) : Core.bundle.format("anomalies.decrypt.done", this.loaded.localizedName))).row();
         table.add(new Bar(() -> this.loaded == null ? "" : (int)(this.progress * 100.0F) + "%", () -> col, () -> this.progress)).width(180.0F).height(18.0F).row();
         table.button(Core.bundle.get("anomalies.decrypt.load"), Styles.defaultt, this::tryLoad).size(180.0F, 40.0F).update((b) -> b.setDisabled(this.loaded != null)).row();
         table.button(Core.bundle.get("anomalies.decrypt.take"), Styles.defaultt, this::tryTake).size(180.0F, 40.0F).update((b) -> b.setDisabled(this.loaded == null || this.progress < 1.0F));
      }

      void tryLoad() {
         if (this.loaded != null) return;
         Unit u = Vars.player == null ? null : Vars.player.unit();
         if (!DataSystem.has()) {
            Vars.ui.showInfoToast(Core.bundle.get("anomalies.decrypt.nodata"), 2.0F);
         } else if (u == null || u.isNull() || !u.within(this, loadRange)) {
            Vars.ui.showInfoToast(Core.bundle.get("anomalies.decrypt.far"), 2.0F);
         } else {
            this.configure(DataSystem.take());
         }
      }

      void tryTake() {
         if (this.loaded == null || this.progress < 1.0F) return;
         Block b = this.loaded;
         AnomalyNet.send("unlock|" + b.name);
         b.unlock();
         Vars.ui.showInfoToast(Core.bundle.format("anomalies.data.unlocked", b.localizedName), 3.0F);
         this.configure(0);
      }

      public void updateTile() {
         if (this.loaded != null && this.progress < 1.0F) this.progress = Math.min(1.0F, this.progress + Time.delta / decryptTime);
      }

      public void draw() {
         super.draw();
         if (this.loaded == null) return;
         Draw.z(Layer.blockOver);
         Draw.color(Color.black, 0.7F);
         Fill.rect(this.x, this.y - 11.0F, 16.0F, 3.0F);
         Draw.color(col);
         Fill.rect(this.x - 7.5F + 7.5F * this.progress, this.y - 11.0F, 15.0F * this.progress, 2.0F);
         if (this.progress >= 1.0F) {
            Draw.color(col, 0.5F + 0.5F * Mathf.absin(Time.time, 6.0F, 1.0F));
            Lines.stroke(1.0F);
            Lines.square(this.x, this.y, 8.5F);
         }
         Draw.reset();
      }

      public void write(Writes write) {
         super.write(write);
         write.str(this.loaded == null ? "" : this.loaded.name);
         write.f(this.progress);
      }

      public void read(Reads read, byte revision) {
         super.read(read, revision);
         String n = read.str();
         this.loaded = n.isEmpty() ? null : Vars.content.block(n);
         this.progress = read.f();
      }
   }
}
