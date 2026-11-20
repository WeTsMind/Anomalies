package anomalies;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.files.Fi;
import arc.graphics.Pixmap;
import arc.graphics.PixmapIO;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.gl.FrameBuffer;
import arc.math.Mat;
import arc.util.Log;
import arc.util.ScreenUtils;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Legsc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;

/**
 * Icons that look exactly like the thing in the game: once the game has loaded, every unit and turret of the mod that has
 * its own "-icon" sprite is drawn by its own draw code (facing up, team colour of the player's team, full health, no
 * shadows) into an off-screen buffer, and that picture becomes its icon (build menu, database, unit lists, payloads).
 * The picture keeps the sprite scale (4 px = 1 world unit) and the rotation centre in the middle. When drawing fails, the
 * "-icon" sprite stays.
 */
public class IconRender {
   static final Team TEAM = Team.sharded;
   static final int MAX_PX = 1024;

   public static void init() {
      if (Vars.headless) return;
      Events.on(EventType.ClientLoadEvent.class, e -> Core.app.post(IconRender::renderAll));
   }

   /** content of this mod drawn by its own (Java) code */
   static boolean ours(UnlockableContent c) {
      return c.minfo != null && c.minfo.mod != null && "anomalies".equals(c.minfo.mod.name) && c.getClass().getName().startsWith("anomalies.");
   }

   /** the drawn icons are also saved here as PNG (<data folder>/anomalies-icons/<name>-icon.png) */
   static Fi dumpDir() {
      return Core.settings.getDataDirectory().child("anomalies-icons");
   }

   static void renderAll() {
      int done = 0;
      Fi dir = dumpDir();
      try {
         dir.mkdirs();
      } catch (Throwable ignored) {
      }
      for (UnitType type : Vars.content.units()) {
         if (!ours(type) || !Core.atlas.has(type.name + "-icon")) continue; // units without an own icon keep the game's one
         TextureRegion r = render(size(type.fullIcon, type.hitSize * 3.0F), () -> unit(type), dir.child(type.name.replace("anomalies-", "") + "-icon.png"));
         if (r != null) {
            type.fullIcon = r;
            type.uiIcon = r;
            done++;
         }
      }
      for (Block block : Vars.content.blocks()) {
         if (!ours(block)) continue;
         TextureRegion r = render(size(block.fullIcon, block.size * Vars.tilesize), () -> building(block), dir.child(block.name.replace("anomalies-", "") + "-icon.png"));
         if (r != null) {
            block.fullIcon = r;
            block.uiIcon = r;
            done++;
         }
      }
      Log.info("Anomalies: @ icons drawn from the game, saved to @", done, dir.absolutePath());
   }

   /** the drawing area, world units: the old icon or the given size, with room around it */
   static float size(TextureRegion old, float min) {
      float w = old == null ? 0.0F : Math.max(old.width, old.height) / 4.0F;
      return Math.max(w, min) * 1.6F + 8.0F;
   }

   static void unit(UnitType type) {
      Unit u = type.create(TEAM);
      u.set(0.0F, 0.0F);
      u.rotation = 90.0F;
      u.health = u.maxHealth;
      if (u instanceof Legsc) {
         ((Legsc)u).baseRotation(90.0F);
         ((Legsc)u).resetLegs();
      }
      boolean soft = type.drawSoftShadow;
      type.drawSoftShadow = false;
      try {
         type.draw(u);
      } finally {
         type.drawSoftShadow = soft;
      }
   }

   static void building(Block block) {
      Building b = block.newBuilding().create(block, TEAM);
      b.set(0.0F, 0.0F);
      Draw.z(Layer.block); // the game draws buildings on the block layer; parts above it set their own layer
      b.draw();
   }

   /** draws into an off-screen buffer and returns the picture cropped around the centre; null when the drawing failed or is empty */
   static TextureRegion render(float world, Runnable draw, Fi dump) {
      int px = Math.min(MAX_PX, (int)Math.ceil(world * 4.0F / 2.0F) * 2);
      float half = px / 8.0F;
      Mat proj = new Mat(Draw.proj());
      FrameBuffer fb = new FrameBuffer(px, px);
      Pixmap pix = null;
      boolean ok = false;
      Draw.flush();
      fb.begin(Color.clear);
      Draw.proj(-half, -half, half * 2.0F, half * 2.0F);
      Draw.sort(true);
      TurretPatch.shadowOff();
      try {
         draw.run();
         ok = true;
      } catch (Throwable t) {
         Log.err("Anomalies: icon drawing failed", t);
      } finally {
         Draw.reset();
         Draw.flush();
         Draw.sort(false);
         TurretPatch.shadowOn();
         if (ok) pix = ScreenUtils.getFrameBufferPixmap(0, 0, px, px, true);
         fb.end();
         Draw.proj(proj);
         fb.dispose();
      }
      if (pix == null) return null;
      try {
         return crop(pix, dump);
      } catch (Throwable t) {
         Log.err("Anomalies: icon cropping failed", t);
         return null;
      } finally {
         pix.dispose();
      }
   }

   /** crops to the drawn pixels, keeping the centre in the middle (rotation centre and world scale stay right) */
   static TextureRegion crop(Pixmap pix, Fi dump) {
      int w = pix.width, h = pix.height, c = w / 2;
      int ext = 0, eyt = 0;
      boolean any = false;
      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            if (pix.getA(x, y) == 0) continue;
            any = true;
            ext = Math.max(ext, Math.max(c - x, x + 1 - c));
            eyt = Math.max(eyt, Math.max(c - y, y + 1 - c));
         }
      }
      if (!any) return null;
      ext = Math.min(c, ext + 1);
      eyt = Math.min(c, eyt + 1);
      Pixmap out = pix.crop(c - ext, c - eyt, ext * 2, eyt * 2);
      try {
         PixmapIO.writePng(dump, out);
      } catch (Throwable t) {
         Log.err("Anomalies: icon saving failed", t);
      }
      Texture tex = new Texture(out);
      tex.setFilter(Texture.TextureFilter.nearest);
      out.dispose();
      return new TextureRegion(tex);
   }
}
