package anomalies;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Camera;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.Effect;
import mindustry.game.EventType.Trigger;
import mindustry.world.Tile;

final class PolcimGlow {
   static final int MAX_TILES = 600;
   static final float PER_SEC = 0.9F;
   static boolean installed;
   static int timer;
   static int[] xs = new int[600];
   static int[] ys = new int[600];
   static int count;
   static final Effect mote = new Effect(75.0F, 24.0F, (var0) -> {
      float var1 = var0.fin();
      float var2 = (float)var0.id * 0.37F;
      float var3 = var0.x + (float)Math.sin((double)(var2 + var1 * 5.0F)) * 1.6F;
      float var4 = var0.y + var1 * 7.0F;
      float var5 = (float)Math.sin(Math.PI * (double)var1);
      float var6 = 0.75F + 0.25F * (float)Math.sin((double)(var2 * 3.0F + var1 * 22.0F));
      Draw.blend(Blending.additive);
      Draw.color(Color.white, 0.22F * var5 * var6);
      Fill.circle(var3, var4, 2.2F);
      Draw.color(Color.white, 0.9F * var5 * var6);
      Fill.circle(var3, var4, 0.8F);
      Draw.blend();
      Draw.reset();
   });

   static float rnd(int var0) {
      double var1 = Math.sin((double)var0 * 12.9898 + 78.233) * 43758.5453;
      return (float)(var1 - Math.floor(var1));
   }

   static void install() {
      if (!installed) {
         Events.run(Trigger.update, PolcimGlow::tick);
         installed = true;
      }
   }

   static void tick() {
      try {
         if (Vars.state == null || !Vars.state.isGame() || Vars.world == null || Core.camera == null) {
            return;
         }

         if (AnomalyMod.polcimOre == null) {
            return;
         }

         if (timer-- <= 0) {
            timer = 20;
            rescan();
         }

         float var0 = 0.015F;

         for(int var1 = 0; var1 < count; ++var1) {
            if (Math.random() < (double)var0 && Vars.world.tile(xs[var1], ys[var1]).block() == Blocks.air) {
               mote.at(((float)xs[var1] + (float)Math.random() - 0.5F) * 8.0F, ((float)ys[var1] + (float)Math.random() - 0.5F) * 8.0F, 0.0F);
            }
         }
      } catch (Throwable var2) {
      }

   }

   static void rescan() {
      Camera var0 = Core.camera;
      float var1 = var0.width / 2.0F + 16.0F;
      float var2 = var0.height / 2.0F + 16.0F;
      int var3 = Math.max(0, (int)((var0.position.x - var1) / 8.0F));
      int var4 = Math.min(Vars.world.width() - 1, (int)((var0.position.x + var1) / 8.0F));
      int var5 = Math.max(0, (int)((var0.position.y - var2) / 8.0F));
      int var6 = Math.min(Vars.world.height() - 1, (int)((var0.position.y + var2) / 8.0F));
      int var7 = 0;

      for(int var8 = var3; var8 <= var4 && var7 < 600; ++var8) {
         for(int var9 = var5; var9 <= var6 && var7 < 600; ++var9) {
            Tile var10 = Vars.world.tile(var8, var9);
            if (var10 != null && var10.overlay() == AnomalyMod.polcimOre && var10.block() == Blocks.air) {
               xs[var7] = var8;
               ys[var7] = var9;
               ++var7;
            }
         }
      }

      count = var7;
   }
}
