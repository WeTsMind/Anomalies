package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

/**
 * Decryptor: paste a picture copied from a World Hologram (or any picture that is identical once cropped to its white
 * pixels). If it matches a {@link Secrets} entry the block works for N seconds and then unlocks the secret's content,
 * but only while that secret's condition (sector captured / enemy defeated) is met, otherwise it waits.
 *
 * Multiplayer: the pasted picture is a normal block config (byte[]) so every client has it; each client runs the
 * timer and unlocks for itself, because unlocks are stored per player.
 */
public class DecryptorBlock extends Block {
   static final Color green = Color.valueOf("84f491");
   public TextureRegion bgRegion;

   public DecryptorBlock(String name) {
      super(name);
      this.size = 2;
      this.solid = true;
      this.update = true;
      this.destructible = true;
      this.configurable = true;
      this.alwaysUnlocked = true;
      this.health = 300;
      this.requirements(Category.effect, BuildVisibility.shown, ItemStack.with(new Object[]{Items.silicon, 60, Items.titanium, 30, Items.lead, 50}));
      this.config(byte[].class, (DecryptBuild b, byte[] d) -> b.insert(d));
      this.buildType = () -> new DecryptBuild();
   }

   public void load() {
      super.load();
      this.bgRegion = Core.atlas.find(this.name + "-bg");
   }

   public class DecryptBuild extends Building {
      HologramBlock.Img img;
      String secretId = "";
      float progress;
      boolean done;

      /** Empty array = clear the slot. */
      void insert(byte[] d) {
         this.progress = 0.0F;
         this.done = false;
         if (d == null || d.length == 0) {
            this.img = null;
            this.secretId = "";
            return;
         }
         this.img = HologramBlock.unpack(d);
         Secrets.Secret s = Secrets.match(this.img);
         this.secretId = s == null ? "" : s.id;
      }

      Secrets.Secret secret() {
         return this.secretId.isEmpty() ? null : Secrets.get(this.secretId);
      }

      boolean waiting() {
         Secrets.Secret s = this.secret();
         return s != null && !this.done && !Secrets.met(s);
      }

      boolean working() {
         return this.secret() != null && !this.done && !this.waiting();
      }

      public void updateTile() {
         Secrets.Secret s = this.secret();
         if (s == null || this.done) return;
         UnlockableContent reward = s.reward();
         if (reward == null) return;
         if (reward.unlocked()) {
            this.done = true;
            return;
         }
         if (!Secrets.met(s)) return;
         this.progress += this.delta();
         if (this.progress >= s.ticks) {
            this.done = true;
            reward.unlock();
            if (!Vars.headless) {
               try {
                  Vars.ui.hudfrag.showUnlock(reward);
               } catch (Throwable t) {
                  Vars.ui.showInfoToast(Core.bundle.format("decryptor.unlocked", reward.localizedName), 3.0F);
               }
            }
         }
      }

      String statusText() {
         if (this.img == null) return Core.bundle.get("decryptor.empty", "Empty. Paste a picture.");
         Secrets.Secret s = this.secret();
         if (s == null) return Core.bundle.get("decryptor.unknown", "Cannot decrypt this picture.");
         UnlockableContent r = s.reward();
         String name = r == null ? "?" : r.localizedName;
         if (this.done) return Core.bundle.format("decryptor.done", name);
         if (this.waiting()) return Core.bundle.format("decryptor.waiting", Secrets.conditionText(s));
         return Core.bundle.format("decryptor.working", (int)(100.0F * Mathf.clamp(this.progress / s.ticks)));
      }

      public void draw() {
         Draw.rect(this.block.region, this.x, this.y);
         float p = 0.0F;
         Secrets.Secret s = this.secret();
         if (s != null) p = this.done ? 1.0F : Mathf.clamp(this.progress / s.ticks);
         if (this.working() || this.done) {
            Draw.blend(Blending.additive);
            Draw.color(green, this.done ? 0.2F : 0.15F + 0.15F * Mathf.absin(Time.time, 20.0F, 1.0F));
            Draw.rect(this.block.region, this.x, this.y);
            Draw.blend();
         }
         if (s != null) {
            float w = 24.0F, y = this.y - 19.0F;
            Draw.color(Pal.darkerGray);
            Fill.rect(this.x, y, w + 2.0F, 4.0F);
            Draw.color(this.waiting() ? Color.valueOf("ffb26b") : green);
            Fill.rect(this.x - w / 2.0F + w * p / 2.0F, y, w * p, 2.0F);
         }
         Draw.reset();
         if (this.working()) Drawf.light(this.x, this.y, 40.0F, green, 0.3F);
      }

      public void buildConfiguration(Table table) {
         table.table(Styles.black6, t -> {
            t.defaults().pad(3.0F);
            t.label(() -> this.statusText()).width(230.0F).wrap().row();
            t.button(Core.bundle.get("decryptor.paste", "Paste"), () -> {
               HologramBlock.Img im = HologramBlock.pasteSource();
               if (im == null || HologramBlock.tight(im) == null) {
                  Vars.ui.showInfoToast(Core.bundle.get("decryptor.nothing", "No picture to paste"), 1.5F);
               } else {
                  this.configure(HologramBlock.pack(im));
               }
            }).size(230.0F, 44.0F).row();
            t.button(Core.bundle.get("hologram.view", "View"), () -> {
               if (this.img == null) return;
               Vars.control.input.config.hideConfig();
               new HologramBlock.HoloDialog(this.img, DecryptorBlock.this.bgRegion, true, this::isValid, d -> {}).show();
            }).size(230.0F, 44.0F).row();
            t.button(Core.bundle.get("decryptor.clear", "Clear"), () -> this.configure(new byte[0])).size(230.0F, 44.0F);
         });
      }

      public byte[] config() {
         return this.img == null ? null : HologramBlock.pack(this.img);
      }

      public void write(Writes w) {
         super.write(w);
         w.bool(this.img != null);
         if (this.img != null) {
            byte[] c = HologramBlock.pack(this.img);
            w.i(c.length);
            w.b(c);
         }
         w.str(this.secretId);
         w.f(this.progress);
         w.bool(this.done);
      }

      public void read(Reads r, byte revision) {
         super.read(r, revision);
         if (r.bool()) {
            this.img = HologramBlock.unpack(r.b(r.i()));
         } else {
            this.img = null;
         }
         String id = r.str();
         this.secretId = id == null ? "" : id;
         this.progress = r.f();
         this.done = r.bool();
      }
   }
}
