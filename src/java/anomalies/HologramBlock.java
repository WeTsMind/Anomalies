package anomalies;

import arc.Core;
import arc.files.Fi;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.Slider;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.func.Cons;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

/**
 * Hologram Block: tap it and press "Draw" to open a white-on-blue canvas (default 64x64, size is editable, up to 1024).
 * The drawing is a packed bitmap (1 bit per pixel) shown above the block as a hologram.
 *
 * Multiplayer: the bitmap travels as a normal block config (deflate-compressed byte[] with a w/h header), so the game
 * itself sends it to the server and every client; it is saved with the world (write/read below).
 */
public class HologramBlock extends Block {
   public static final int DEFAULT = 64, MIN = 8, MAX = 1024;
   /** Copy buffer shared by every hologram block, null while nothing was copied. */
   public static Img clipboard;
   static final Color holo = Color.valueOf("bfe9ff");
   static final int ON = 0xffffffff, OFF = 0xffffff00; // transparent pixels stay white so filtering never darkens the edges
   /** Size of the longer side of the floating hologram, world units. */
   static final float SHOW = 40.0F;
   /** Blocks unlocked in this session: position -> password hash that was entered. */
   static final IntMap<String> unlocked = new IntMap<>();
   public TextureRegion bgRegion;
   /** True for the World Hologram: editable only in the map editor, no password. */
   public boolean world;
   public Color color = holo;

   public HologramBlock(String name) {
      super(name);
      this.size = 1;
      this.solid = true;
      this.update = false;
      this.destructible = true;
      this.configurable = true;
      this.alwaysUnlocked = true;
      this.health = 120;
      this.requirements(Category.effect, BuildVisibility.shown, ItemStack.with(new Object[]{Items.silicon, 25, Items.lead, 25}));
      // world holograms can only be edited in the map editor
      this.config(byte[].class, (HoloBuild b, byte[] d) -> {
         if (!((HologramBlock)b.block).world || canEditWorld()) b.setCompressed(d);
      });
      this.config(String.class, (HoloBuild b, String t) -> b.changePass(t));
      this.buildType = () -> new HoloBuild();
   }

   public void load() {
      super.load();
      this.bgRegion = Core.atlas.find(this.name + "-bg");
   }

   // ---- bitmap ---------------------------------------------------------------------------------------------------

   /** w x h bitmap, 1 bit per pixel, row 0 at the top. */
   public static class Img {
      public final int w, h;
      public final byte[] bits;

      public Img(int w, int h) {
         this.w = w;
         this.h = h;
         this.bits = new byte[(w * h + 7) >> 3];
      }

      boolean get(int x, int y) {
         int i = y * w + x;
         return (bits[i >> 3] & (1 << (i & 7))) != 0;
      }

      void put(int x, int y, boolean on) {
         if (x < 0 || y < 0 || x >= w || y >= h) return;
         int i = y * w + x;
         if (on) bits[i >> 3] |= (byte)(1 << (i & 7));
         else bits[i >> 3] &= (byte)~(1 << (i & 7));
      }

      Img copy() {
         Img c = new Img(w, h);
         System.arraycopy(bits, 0, c.bits, 0, bits.length);
         return c;
      }

      /** New image of another size, content kept from the top-left corner. */
      Img resized(int nw, int nh) {
         Img n = new Img(nw, nh);
         for(int y = 0; y < Math.min(h, nh); ++y) {
            for(int x = 0; x < Math.min(w, nw); ++x) {
               if (get(x, y)) n.put(x, y, true);
            }
         }
         return n;
      }

      boolean empty() {
         for(byte v : bits) {
            if (v != 0) return false;
         }
         return true;
      }
   }

   /** The World Hologram can be edited in the map editor and in the sandbox (infinite resources), never in normal play. */
   static boolean canEditWorld() {
      return Vars.state.isEditor() || Vars.state.rules.infiniteResources;
   }

   /** Hash of the system clipboard image seen at the last Copy, so a newer external image wins over the internal buffer. */
   static int seenImage = 0;

   /** Image from the system clipboard (desktop "copy image"), as {w, h, argb[]} or null. Reflection keeps Android builds happy. */
   static Object[] systemImage() {
      try {
         Object tk = Class.forName("java.awt.Toolkit").getMethod("getDefaultToolkit").invoke(null);
         Object clip = tk.getClass().getMethod("getSystemClipboard").invoke(tk);
         Class<?> flavorCls = Class.forName("java.awt.datatransfer.DataFlavor");
         Object flavor = flavorCls.getField("imageFlavor").get(null);
         if (!(Boolean)clip.getClass().getMethod("isDataFlavorAvailable", flavorCls).invoke(clip, flavor)) return null;
         Object im = clip.getClass().getMethod("getData", flavorCls).invoke(clip, flavor);
         if (im == null) return null;
         int w = (Integer)im.getClass().getMethod("getWidth").invoke(im);
         int h = (Integer)im.getClass().getMethod("getHeight").invoke(im);
         int[] argb = new int[w * h];
         im.getClass().getMethod("getRGB", int.class, int.class, int.class, int.class, int[].class, int.class, int.class).invoke(im, 0, 0, w, h, argb, 0, w);
         return new Object[]{w, h, argb};
      } catch (Throwable t) {
         return null;
      }
   }

   /** Copy into the shared buffer. */
   static void doCopy(Img im) {
      clipboard = im.copy();
      Object[] sys = systemImage();
      seenImage = sys == null ? 0 : java.util.Arrays.hashCode((int[])sys[2]);
      Vars.ui.showInfoToast(Core.bundle.get("hologram.copied", "Copied"), 1.5F);
   }

   /** White pixels only, placed 1:1 (no margin). */
   static Img fromArgb(int[] argb, int w, int h) {
      Img n = new Img(w, h);
      for(int y = 0; y < h; ++y) {
         for(int x = 0; x < w; ++x) {
            int c = argb[y * w + x];
            if (((c >>> 24) & 255) >= 128 && ((c >>> 16) & 255) >= 240 && ((c >>> 8) & 255) >= 240 && (c & 255) >= 240) n.put(x, y, true);
         }
      }
      return n;
   }

   /** What a "paste" should insert: a newer image from the system clipboard, otherwise the shared buffer; null if none. */
   static Img pasteSource() {
      Object[] im = systemImage();
      if (im != null && java.util.Arrays.hashCode((int[])im[2]) != seenImage) return fromArgb((int[])im[2], (Integer)im[0], (Integer)im[1]);
      return clipboard == null ? null : clipboard.copy();
   }

   /** The picture cropped to the box around its white pixels (null if empty): position and canvas size no longer matter. */
   static Img tight(Img im) {
      int x0 = im.w, y0 = im.h, x1 = -1, y1 = -1;
      for(int y = 0; y < im.h; ++y) {
         for(int x = 0; x < im.w; ++x) {
            if (im.get(x, y)) {
               if (x < x0) x0 = x;
               if (x > x1) x1 = x;
               if (y < y0) y0 = y;
               if (y > y1) y1 = y;
            }
         }
      }
      if (x1 < 0) return null;
      Img n = new Img(x1 - x0 + 1, y1 - y0 + 1);
      for(int y = y0; y <= y1; ++y) {
         for(int x = x0; x <= x1; ++x) {
            if (im.get(x, y)) n.put(x - x0, y - y0, true);
         }
      }
      return n;
   }

   static String hash(String pw) {
      try {
         byte[] d = MessageDigest.getInstance("SHA-256").digest(("hologram:" + pw).getBytes("UTF-8"));
         StringBuilder sb = new StringBuilder();
         for(byte v : d) sb.append(String.format("%02x", v & 255));
         return sb.toString();
      } catch (Throwable t) {
         return "x" + pw.hashCode();
      }
   }

   static byte[] deflate(byte[] raw) {
      Deflater d = new Deflater(9);
      d.setInput(raw);
      d.finish();
      ByteArrayOutputStream o = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      while(!d.finished()) {
         int n = d.deflate(buf);
         o.write(buf, 0, n);
      }
      d.end();
      return o.toByteArray();
   }

   static byte[] inflateRaw(byte[] c) {
      ByteArrayOutputStream o = new ByteArrayOutputStream();
      if (c == null) return o.toByteArray();
      try {
         Inflater inf = new Inflater();
         inf.setInput(c);
         byte[] buf = new byte[8192];
         int total = 0, limit = 4 + (MAX * MAX + 7) / 8;
         while(!inf.finished() && total < limit) {
            int n = inf.inflate(buf);
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
            o.write(buf, 0, n);
            total += n;
         }
         inf.end();
      } catch (Throwable t) {
         return new byte[0];
      }
      return o.toByteArray();
   }

   static byte[] pack(Img im) {
      byte[] raw = new byte[4 + im.bits.length];
      raw[0] = (byte)(im.w >> 8);
      raw[1] = (byte)im.w;
      raw[2] = (byte)(im.h >> 8);
      raw[3] = (byte)im.h;
      System.arraycopy(im.bits, 0, raw, 4, im.bits.length);
      return deflate(raw);
   }

   static Img unpack(byte[] c) {
      byte[] raw = inflateRaw(c);
      if (raw.length >= 4) {
         int w = ((raw[0] & 255) << 8) | (raw[1] & 255), h = ((raw[2] & 255) << 8) | (raw[3] & 255);
         if (w >= 1 && h >= 1 && w <= MAX && h <= MAX) {
            Img im = new Img(w, h);
            System.arraycopy(raw, 4, im.bits, 0, Math.min(im.bits.length, raw.length - 4));
            return im;
         }
      }
      return new Img(DEFAULT, DEFAULT);
   }

   /** Worlds saved before the canvas became resizable: fixed 500x500 without header. */
   static Img legacy(byte[] c) {
      byte[] raw = inflateRaw(c);
      Img im = new Img(500, 500);
      System.arraycopy(raw, 0, im.bits, 0, Math.min(im.bits.length, raw.length));
      return im;
   }

   // ---- the building ---------------------------------------------------------------------------------------------

   public class HoloBuild extends Building {
      public Img img = new Img(DEFAULT, DEFAULT);
      /** Password hash, empty = no password. Changing it needs the old hash, which the server checks (see changePass). */
      public String pass = "";
      Texture tex;
      boolean texDirty = true;

      /** Config payload "oldHash:newHash". */
      void changePass(String t) {
         int i = t.indexOf(':');
         if (i < 0) return;
         String old = t.substring(0, i), nw = t.substring(i + 1);
         if (this.pass.isEmpty() || this.pass.equals(old)) this.pass = nw;
      }

      boolean locked() {
         return !this.pass.isEmpty() && !this.pass.equals(unlocked.get(this.pos()));
      }

      void askPassword(String title, Cons<String> ok) {
         BaseDialog d = new BaseDialog(title);
         TextField f = new TextField("");
         f.setPasswordMode(true);
         f.setMessageText(Core.bundle.get("hologram.password", "Password"));
         d.cont.add(f).size(260.0F, 44.0F).row();
         d.buttons.button(Core.bundle.get("hologram.ok", "OK"), () -> {
            d.hide();
            ok.get(f.getText());
         }).size(130.0F, 50.0F);
         d.addCloseButton();
         d.show();
      }

      HoloDialog dialog(boolean view) {
         return new HoloDialog(this.img, HologramBlock.this.bgRegion, view, this::isValid, d -> this.configure(d));
      }

      void open(boolean view) {
         if (!this.locked()) {
            this.dialog(view).show();
            return;
         }
         this.askPassword(Core.bundle.get("hologram.enter", "Enter password"), pw -> {
            if (hash(pw).equals(this.pass)) {
               unlocked.put(this.pos(), this.pass);
               this.dialog(view).show();
            } else {
               Vars.ui.showInfoToast(Core.bundle.get("hologram.wrong", "Wrong password"), 1.5F);
            }
         });
      }

      void openPasswordMenu() {
         if (this.locked()) {
            this.askPassword(Core.bundle.get("hologram.enter", "Enter password"), pw -> {
               if (hash(pw).equals(this.pass)) {
                  unlocked.put(this.pos(), this.pass);
                  this.openPasswordMenu();
               } else {
                  Vars.ui.showInfoToast(Core.bundle.get("hologram.wrong", "Wrong password"), 1.5F);
               }
            });
            return;
         }
         BaseDialog d = new BaseDialog(Core.bundle.get("hologram.passtitle", "Block password"));
         TextField f = new TextField("");
         f.setPasswordMode(true);
         f.setMessageText(Core.bundle.get("hologram.newpass", "New password"));
         d.cont.add(f).size(260.0F, 44.0F).row();
         d.cont.button(Core.bundle.get("hologram.set", "Set password"), () -> {
            if (f.getText().isEmpty()) return;
            String nw = hash(f.getText());
            this.configure(this.pass + ":" + nw);
            unlocked.put(this.pos(), nw);
            d.hide();
            Vars.ui.showInfoToast(Core.bundle.get("hologram.passset", "Password set"), 1.5F);
         }).size(260.0F, 50.0F).row();
         if (!this.pass.isEmpty()) {
            d.cont.button(Core.bundle.get("hologram.remove", "Remove password"), () -> {
               this.configure(this.pass + ":");
               unlocked.remove(this.pos());
               d.hide();
               Vars.ui.showInfoToast(Core.bundle.get("hologram.passremoved", "Password removed"), 1.5F);
            }).size(260.0F, 50.0F).row();
         }
         d.addCloseButton();
         d.show();
      }

      void setCompressed(byte[] d) {
         this.img = unpack(d);
         this.texDirty = true;
      }

      public byte[] config() {
         return pack(this.img);
      }

      void rebuildTex() {
         this.texDirty = false;
         if (this.tex != null) {
            this.tex.dispose();
            this.tex = null;
         }
         if (this.img.empty()) return;
         Pixmap pm = new Pixmap(this.img.w, this.img.h);
         pm.fill(OFF);
         for(int y = 0; y < this.img.h; ++y) {
            for(int x = 0; x < this.img.w; ++x) {
               if (this.img.get(x, y)) pm.set(x, y, ON);
            }
         }
         this.tex = new Texture(pm, true);
         // crisp pixels when zoomed in, mipmaps when the hologram is small on screen
         this.tex.setFilter(Texture.TextureFilter.mipMapLinearLinear, Texture.TextureFilter.nearest);
         pm.dispose();
      }

      public void draw() {
         Draw.rect(this.block.region, this.x, this.y);
         if (this.texDirty) this.rebuildTex();
         Color holo = HologramBlock.this.color;
         float flick = 0.85F + 0.1F * Mathf.sin(Time.time / 11.0F) + (Mathf.chance(0.01) ? -0.2F : 0.0F);
         float big = Math.max(this.img.w, this.img.h);
         float sw = SHOW * this.img.w / big, sh = SHOW * this.img.h / big;
         float cy = this.y + 5.0F + sh / 2.0F;
         Draw.z(Layer.effect);
         Draw.blend(Blending.additive);
         // frame of the projection and the emitter glow
         Lines.stroke(0.6F);
         Draw.color(holo, 0.3F * flick);
         Lines.rect(this.x - sw / 2.0F, cy - sh / 2.0F, sw, sh);
         Draw.color(holo, 0.25F * flick);
         Lines.line(this.x, this.y + 3.0F, this.x, cy - sh / 2.0F);
         if (this.tex != null) {
            Tmp.tr1.set(this.tex);
            Draw.color(holo, flick);
            Draw.rect(Tmp.tr1, this.x, cy, sw, sh);
         }
         Draw.blend();
         Draw.reset();
         Drawf.light(this.x, this.y, 30.0F, holo, 0.3F * flick);
      }

      public void onRemoved() {
         super.onRemoved();
         if (this.tex != null) {
            this.tex.dispose();
            this.tex = null;
         }
      }

      public void buildConfiguration(Table table) {
         table.defaults().size(130.0F, 46.0F);
         if (!HologramBlock.this.world || canEditWorld()) {
            table.button(Core.bundle.get("hologram.edit", "Draw"), () -> {
               Vars.control.input.config.hideConfig();
               this.open(false);
            }).row();
         }
         table.button(Core.bundle.get("hologram.view", "View"), () -> {
            Vars.control.input.config.hideConfig();
            this.open(true);
         }).row();
         if (HologramBlock.this.world) {
            table.button(Core.bundle.get("hologram.copy", "Copy"), () -> doCopy(this.img));
         } else {
            table.button(Core.bundle.get("hologram.password", "Password"), () -> {
               Vars.control.input.config.hideConfig();
               this.openPasswordMenu();
            });
         }
      }

      public byte version() {
         return 2;
      }

      public void write(Writes w) {
         super.write(w);
         byte[] c = pack(this.img);
         w.i(c.length);
         w.b(c);
         w.str(this.pass);
      }

      public void read(Reads r, byte revision) {
         super.read(r, revision);
         byte[] c = r.b(r.i());
         this.img = revision < 1 ? legacy(c) : unpack(c);
         if (revision >= 2) {
            String pw = r.str();
            this.pass = pw == null ? "" : pw;
         }
         this.texDirty = true;
      }
   }

   // ---- the drawing dialog ---------------------------------------------------------------------------------------

   public static class HoloDialog extends BaseDialog {
      final TextureRegion bg;
      final arc.func.Boolp valid;
      final Cons<byte[]> sink;
      final boolean view;
      float zoom = 1.0F;
      Img img;
      Pixmap pm;
      Texture tex;
      boolean dirty = true;
      boolean eraser;
      int brush = 3, eraserSize = 8;
      TextField fw, fh;
      final Seq<Img> undo = new Seq<>(), redo = new Seq<>();

      public HoloDialog(Img start, TextureRegion bg, boolean view, arc.func.Boolp valid, Cons<byte[]> sink) {
         super(Core.bundle.get("hologram.title", "Hologram"));
         this.bg = bg;
         this.valid = valid;
         this.sink = sink;
         this.view = view;
         this.img = start.copy();
         this.rebuildGfx();
         this.hidden(() -> {
            this.tex.dispose();
            this.pm.dispose();
         });
         this.addCloseButton();

         float sw = Core.scene.getWidth(), sh = Core.scene.getHeight();
         boolean portrait = sh > sw;
         float ch = view ? 150.0F : 250.0F;
         float cs = portrait ? Math.min(sw - 20.0F, sh - ch - 150.0F) : Math.min(sh - 110.0F, sw - 360.0F);
         cs = Math.max(160.0F, cs);
         final float fcs = cs;

         Table c = new Table();
         c.defaults().pad(4.0F).size(150.0F, 44.0F);
         Canvas canvas = new Canvas();
         Table holder = new Table();
         ScrollPane pane = new ScrollPane(holder, Styles.defaultPane);
         // while drawing a drag must paint, so panning uses the scroll bars / wheel; in view mode a drag pans
         pane.setFlickScroll(view);
         Runnable layout = () -> {
            holder.clear();
            float z = fcs * this.zoom;
            holder.add(canvas).size(z).pad(Math.max(0.0F, (fcs - z) / 2.0F));
         };
         layout.run();

         Label zl = new Label(Core.bundle.get("hologram.zoom", "Zoom") + ": 100%");
         Slider zs = new Slider(0.25F, 8.0F, 0.05F, false);
         zs.setValue(1.0F);
         zs.changed(() -> {
            this.zoom = zs.getValue();
            zl.setText(Core.bundle.get("hologram.zoom", "Zoom") + ": " + Math.round(this.zoom * 100.0F) + "%");
            layout.run();
         });
         c.add(zl).colspan(2).size(300.0F, 24.0F).row();
         c.add(zs).colspan(2).size(300.0F, 30.0F).row();

         if (view) {
            c.button(Core.bundle.get("hologram.copy", "Copy"), () -> doCopy(this.img)).colspan(2).size(300.0F, 44.0F).row();
         }
         if (!view) {
            c.button(Core.bundle.get("hologram.undo", "Undo"), this::undo);
            c.button(Core.bundle.get("hologram.redo", "Redo"), this::redo).row();
            c.button(Core.bundle.get("hologram.eraser", "Eraser"), Styles.togglet, () -> this.eraser = !this.eraser).checked(t -> this.eraser);
            c.button(Core.bundle.get("hologram.import", "Import PNG"), this::chooseFile).row();
            c.button(Core.bundle.get("hologram.copy", "Copy"), this::copy);
            c.button(Core.bundle.get("hologram.paste", "Paste"), this::paste).row();
            this.fw = new TextField(String.valueOf(this.img.w));
            this.fh = new TextField(String.valueOf(this.img.h));
            this.fw.setFilter(TextField.TextFieldFilter.digitsOnly);
            this.fh.setFilter(TextField.TextFieldFilter.digitsOnly);
            c.add(Core.bundle.get("hologram.size", "Canvas size")).colspan(2).size(300.0F, 24.0F).row();
            c.add(this.fw);
            c.add(this.fh).row();
            c.button(Core.bundle.get("hologram.apply", "Apply size"), this::applySize).colspan(2).size(300.0F, 44.0F).row();
            addSlider(c, "hologram.brush", "Brush size", 1, 40, this.brush, v -> this.brush = v);
            addSlider(c, "hologram.erasersize", "Eraser size", 1, 80, this.eraserSize, v -> this.eraserSize = v);
         }

         if (portrait) {
            this.cont.add(pane).size(fcs).row();
            this.cont.add(new ScrollPane(c, Styles.defaultPane)).size(Math.min(sw - 10.0F, 330.0F), ch).row();
         } else {
            this.cont.add(pane).size(fcs);
            this.cont.add(c).padLeft(12.0F).top();
         }
      }

      void addSlider(Table t, String key, String def, int min, int max, int val, arc.func.Intc set) {
         Label l = new Label(Core.bundle.get(key, def) + ": " + val);
         Slider s = new Slider((float)min, (float)max, 1.0F, false);
         s.setValue((float)val);
         s.changed(() -> {
            set.get((int)s.getValue());
            l.setText(Core.bundle.get(key, def) + ": " + (int)s.getValue());
         });
         t.add(l).colspan(2).size(300.0F, 24.0F).row();
         t.add(s).colspan(2).size(300.0F, 30.0F).row();
      }

      /** (Re)creates the pixmap/texture for the current image, also when the canvas size changed. */
      void rebuildGfx() {
         if (this.tex != null) this.tex.dispose();
         if (this.pm != null) this.pm.dispose();
         this.pm = new Pixmap(this.img.w, this.img.h);
         this.pm.fill(OFF);
         for(int y = 0; y < this.img.h; ++y) {
            for(int x = 0; x < this.img.w; ++x) {
               if (this.img.get(x, y)) this.pm.set(x, y, ON);
            }
         }
         this.tex = new Texture(this.pm);
         this.tex.setFilter(Texture.TextureFilter.linear, Texture.TextureFilter.nearest);
         this.dirty = false;
         if (this.fw != null) {
            this.fw.setText(String.valueOf(this.img.w));
            this.fh.setText(String.valueOf(this.img.h));
         }
      }

      void pushUndo() {
         this.undo.add(this.img.copy());
         if (this.undo.size > 40) this.undo.remove(0);
         this.redo.clear();
      }

      void commit() {
         if (this.valid.get()) this.sink.get(pack(this.img));
      }

      void undo() {
         if (this.undo.isEmpty()) return;
         this.redo.add(this.img);
         this.img = this.undo.pop();
         this.rebuildGfx();
         this.commit();
      }

      void redo() {
         if (this.redo.isEmpty()) return;
         this.undo.add(this.img);
         this.img = this.redo.pop();
         this.rebuildGfx();
         this.commit();
      }

      void applySize() {
         int nw = Mathf.clamp(Strings.parseInt(this.fw.getText(), this.img.w), MIN, MAX);
         int nh = Mathf.clamp(Strings.parseInt(this.fh.getText(), this.img.h), MIN, MAX);
         if (nw != this.img.w || nh != this.img.h) {
            this.pushUndo();
            this.img = this.img.resized(nw, nh);
            this.commit();
         }
         this.rebuildGfx(); // also writes the clamped numbers back into the fields
      }

      void copy() {
         doCopy(this.img);
      }

      void paste() {
         Object[] im = systemImage();
         if (im != null && java.util.Arrays.hashCode((int[])im[2]) != seenImage) {
            this.applyImage((int[])im[2], (Integer)im[0], (Integer)im[1]);
            return;
         }
         if (clipboard == null) {
            Vars.ui.showInfoToast(Core.bundle.get("hologram.nothing", "Nothing copied yet"), 1.5F);
            return;
         }
         this.pushUndo();
         this.img = clipboard.copy();
         this.rebuildGfx();
         this.commit();
      }

      /**
       * Keeps only the white pixels (all channels >= 240, opaque), everything else is removed. The picture is placed 1:1.
       * If it is bigger than 64x64 (or does not fit the current canvas with a 2 px margin) the canvas becomes
       * picture size + 2 px on every side; otherwise the picture is centred on the current canvas.
       */
      void applyImage(int[] argb, int w, int h) {
         int cw = this.img.w, ch = this.img.h;
         int ox, oy;
         if (w > DEFAULT || h > DEFAULT || w + 4 > cw || h + 4 > ch) {
            cw = Math.min(MAX, w + 4);
            ch = Math.min(MAX, h + 4);
            ox = 2;
            oy = 2;
         } else {
            ox = (cw - w) / 2;
            oy = (ch - h) / 2;
         }
         Img n = new Img(cw, ch);
         for(int y = 0; y < h; ++y) {
            for(int x = 0; x < w; ++x) {
               int c = argb[y * w + x];
               int a = (c >>> 24) & 255, r = (c >>> 16) & 255, g = (c >>> 8) & 255, b = c & 255;
               if (a >= 128 && r >= 240 && g >= 240 && b >= 240) n.put(ox + x, oy + y, true);
            }
         }
         this.pushUndo();
         this.img = n;
         this.rebuildGfx();
         this.commit();
      }

      void chooseFile() {
         Vars.platform.showFileChooser(true, "png", f -> Core.app.post(() -> this.importFile(f)));
      }

      void importFile(Fi f) {
         try {
            Pixmap src = new Pixmap(f);
            int w = src.getWidth(), h = src.getHeight();
            int[] argb = new int[w * h];
            for(int y = 0; y < h; ++y) {
               for(int x = 0; x < w; ++x) {
                  int c = src.get(x, y); // rgba8888 -> argb
                  argb[y * w + x] = (c << 24) | (c >>> 8);
               }
            }
            src.dispose();
            this.applyImage(argb, w, h);
         } catch (Throwable t) {
            Vars.ui.showException(t);
         }
      }

      void set(int x, int y, boolean on) {
         if (x < 0 || y < 0 || x >= this.img.w || y >= this.img.h) return;
         this.img.put(x, y, on);
         this.pm.set(x, y, on ? ON : OFF);
         this.dirty = true;
      }

      void disc(int cx, int cy, int diameter, boolean on) {
         float r = diameter / 2.0F;
         int ir = (int)Math.ceil(r);
         for(int dy = -ir; dy <= ir; ++dy) {
            for(int dx = -ir; dx <= ir; ++dx) {
               if (dx * dx + dy * dy <= r * r + 0.25F) this.set(cx + dx, cy + dy, on);
            }
         }
      }

      class Canvas extends Element {
         float lastX, lastY;

         Canvas() {
            this.touchable = Touchable.enabled;
            this.addListener(new InputListener() {
               public boolean touchDown(InputEvent e, float x, float y, int pointer, KeyCode button) {
                  if (HoloDialog.this.view) return false;
                  HoloDialog.this.pushUndo();
                  lastX = px(x);
                  lastY = py(y);
                  stroke(lastX, lastY, lastX, lastY);
                  return true;
               }

               public void touchDragged(InputEvent e, float x, float y, int pointer) {
                  float nx = px(x), ny = py(y);
                  stroke(lastX, lastY, nx, ny);
                  lastX = nx;
                  lastY = ny;
               }

               public void touchUp(InputEvent e, float x, float y, int pointer, KeyCode button) {
                  HoloDialog.this.commit();
               }
            });
         }

         /** Screen pixels per canvas pixel: the canvas is fitted into this element keeping its aspect ratio. */
         float sc() {
            return Math.min(this.width / HoloDialog.this.img.w, this.height / HoloDialog.this.img.h);
         }

         float px(float lx) {
            float sc = this.sc();
            return (lx - (this.width - HoloDialog.this.img.w * sc) / 2.0F) / sc;
         }

         float py(float ly) {
            float sc = this.sc();
            return HoloDialog.this.img.h - (ly - (this.height - HoloDialog.this.img.h * sc) / 2.0F) / sc;
         }

         void stroke(float x1, float y1, float x2, float y2) {
            int d = HoloDialog.this.eraser ? HoloDialog.this.eraserSize : HoloDialog.this.brush;
            float dist = Mathf.dst(x1, y1, x2, y2);
            int steps = Math.max(1, (int)Math.ceil(dist / Math.max(1.0F, d / 3.0F)));
            for(int i = 0; i <= steps; ++i) {
               float t = i / (float)steps;
               HoloDialog.this.disc((int)Math.floor(Mathf.lerp(x1, x2, t)), (int)Math.floor(Mathf.lerp(y1, y2, t)), d, !HoloDialog.this.eraser);
            }
         }

         public void draw() {
            if (HoloDialog.this.dirty) {
               HoloDialog.this.tex.draw(HoloDialog.this.pm);
               HoloDialog.this.dirty = false;
            }
            float sc = this.sc();
            float dw = HoloDialog.this.img.w * sc, dh = HoloDialog.this.img.h * sc;
            float x0 = this.x + (this.width - dw) / 2.0F, y0 = this.y + (this.height - dh) / 2.0F;
            Draw.color(Color.white);
            // blue striped background, one background pixel = one canvas pixel, cropped at the right / top edge
            TextureRegion bg = HoloDialog.this.bg;
            float ts = 64.0F * sc;
            for(float ty = 0.0F; ty < dh - 0.01F; ty += ts) {
               for(float tx = 0.0F; tx < dw - 0.01F; tx += ts) {
                  float fw = Math.min(ts, dw - tx) / ts, fh = Math.min(ts, dh - ty) / ts;
                  Tmp.tr1.set(bg);
                  Tmp.tr1.u2 = bg.u + (bg.u2 - bg.u) * fw;
                  Tmp.tr1.v = bg.v2 - (bg.v2 - bg.v) * fh;
                  Draw.rect(Tmp.tr1, x0 + tx + ts * fw / 2.0F, y0 + ty + ts * fh / 2.0F, ts * fw, ts * fh);
               }
            }
            Tmp.tr2.set(HoloDialog.this.tex);
            Draw.rect(Tmp.tr2, x0 + dw / 2.0F, y0 + dh / 2.0F, dw, dh);
            Lines.stroke(2.0F);
            Draw.color(Pal.accent);
            Lines.rect(x0, y0, dw, dh);
            Draw.reset();
         }
      }
   }
}
