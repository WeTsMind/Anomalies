package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

/**
 * Data Block: holds the data of one building that can be unlocked (chosen in its settings).
 * Standing next to it (within {@link #range}) downloads the data into the player's unit (green line block -> unit);
 * the data is then carried to a {@link DecryptorBlock}.
 * Download is local to the player whose unit is near; the block's chosen building is a normal block config (synced + saved).
 */
public class DataBlock extends Block {
   /** Download radius, world units (4 tiles). */
   public static final float range = 32.0F;
   /** Ticks needed to download. */
   public static final float dlTime = 120.0F;
   public static final Color col = Color.valueOf("5DFF7A");

   public DataBlock(String name) {
      super(name);
      this.size = 1;
      this.solid = true;
      this.update = true;
      this.destructible = true;
      this.configurable = true;
      this.saveConfig = true;
      this.alwaysUnlocked = true;
      this.health = 200;
      this.requirements(Category.effect, BuildVisibility.shown, ItemStack.with(new Object[]{Items.silicon, 20, Items.lead, 30}));
      this.config(Block.class, (DataBuild b, Block t) -> {
         if (DataBlock.inGameEdit()) {
            b.target = t; // only the map editor may set it
            b.used = false;
            b.held = false;
         }
      });
      // 0 = data returned to the block (carrier lost it), 1 = a unit carries the data now, 2 = delivered to a Decryptor: used up for good
      this.config(Integer.class, (DataBuild b, Integer v) -> {
         if (v == 1) {
            b.held = true;
         } else if (v == 2) {
            b.held = false;
            b.used = true;
         } else {
            b.held = false;
         }
      });
      this.buildType = () -> new DataBuild();
   }

   /** True while editing a map in game ("Edit in game"): rules.editor is on and the standalone map editor is closed. */
   static boolean inGameEdit() {
      return Vars.state.rules.editor && !(Vars.ui != null && Vars.ui.editor != null && Vars.ui.editor.isShown());
   }

   /** Buildings that are still locked and can be chosen as the content of a Data Block. */
   static Seq<Block> unlockables() {
      return Vars.content.blocks().select((b) -> b.requirements.length > 0 && b.buildVisibility != BuildVisibility.hidden && (DataBlock.inGameEdit() || !b.unlocked()));
   }

   public void drawPlace(int x, int y, int rotation, boolean valid) {
      super.drawPlace(x, y, rotation, valid);
      Drawf.dashCircle((float)x * 8.0F + this.offset, (float)y * 8.0F + this.offset, range, col);
   }

   public class DataBuild extends Building {
      /** The building this data unlocks. */
      public Block target;
      /** 0..1 download progress of the local player. */
      float dl;
      /** The data was delivered to a Decryptor: the block is deactivated for good (saved). */
      public boolean used;
      /** A unit carries the data right now: nobody else can take it until the carrier loses it (not saved). */
      public boolean held;

      /** On a campaign sector the data cannot be downloaded until the sector is captured. */
      boolean sectorLocked() {
         return Vars.state.isCampaign() && Vars.state.rules.sector != null && !Vars.state.rules.sector.isCaptured();
      }

      public Block config() {
         return this.target;
      }

      public boolean shouldShowConfigure(mindustry.gen.Player player) {
         return DataBlock.inGameEdit();
      }

      public void buildConfiguration(Table table) {
         Table cont = new Table().top();
         cont.defaults().size(40.0F);
         int i = 0;
         for (Block b : unlockables()) {
            ImageButton btn = cont.button(Tex.whiteui, Styles.clearTogglei, 24.0F, () -> this.configure(b)).tooltip(b.localizedName).get();
            btn.getStyle().imageUp = new TextureRegionDrawable(b.uiIcon);
            btn.update(() -> btn.setChecked(this.target == b));
            if (++i % 6 == 0) cont.row();
         }
         ScrollPane pane = new ScrollPane(cont, Styles.smallPane);
         pane.setScrollingDisabled(true, false);
         table.add(pane).maxHeight(Scl.scl(200.0F));
      }

      public void updateTile() {
         Unit u = Vars.headless || Vars.player == null ? null : Vars.player.unit();
         boolean near = u != null && u.isValid() && u.team == this.team && this.target != null && !this.used && !this.held && !sectorLocked() && u.within(this, range)
            && !(DataSystem.carried == this.target && DataSystem.carrier == u);
         this.dl = near ? Math.min(1.0F, this.dl + Time.delta / dlTime) : Math.max(0.0F, this.dl - Time.delta / (dlTime / 2.0F));
         if (near && this.dl >= 1.0F) {
            DataSystem.give(this.target, u, this);
            this.dl = 0.0F;
            this.configure(1);
            if (Vars.ui != null) Vars.ui.showInfoToast(Core.bundle.format("anomalies.data.got", this.target.localizedName), 2.5F);
         }
      }

      public void draw() {
         super.draw();
         Unit u = Vars.player == null ? null : Vars.player.unit();
         if (this.dl > 0.001F && u != null && u.isValid()) {
            Draw.z(Layer.effect);
            Draw.blend(Blending.additive);
            Draw.color(col, 0.35F + 0.65F * this.dl);
            Lines.stroke(0.8F + 1.4F * this.dl);
            Lines.line(this.x, this.y, u.x, u.y);
            for (int i = 0; i < 4; ++i) {
               float t = (Time.time / 40.0F + (float)i / 4.0F) % 1.0F;
               Fill.circle(Mathf.lerp(this.x, u.x, t), Mathf.lerp(this.y, u.y, t), 1.2F);
            }
            Draw.blend();
            Draw.color(Color.black, 0.6F);
            Fill.rect(this.x, this.y - 6.0F, 10.0F, 1.8F);
            Draw.color(col);
            Fill.rect(this.x - 4.6F + 4.6F * this.dl, this.y - 6.0F, 9.2F * this.dl, 1.2F);
            Draw.reset();
         }
      }

      public void drawSelect() {
         Drawf.dashCircle(this.x, this.y, range, col);
      }

      public void write(Writes write) {
         super.write(write);
         write.str(this.target == null ? "" : this.target.name);
         write.bool(this.used);
      }

      public byte version() {
         return 1;
      }

      public void read(Reads read, byte revision) {
         super.read(read, revision);
         String n = read.str();
         this.target = n.isEmpty() ? null : Vars.content.block(n);
         if (revision >= 1) this.used = read.bool();
      }
   }
}
