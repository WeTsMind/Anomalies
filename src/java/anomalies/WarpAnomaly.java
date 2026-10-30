package anomalies;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.world.Block;
import mindustry.world.Tile;

public class WarpAnomaly extends Anomaly {
   static final Color violet = Color.valueOf("a35cff");
   static final Color light = Color.valueOf("e2c8ff");
   static final Color cyan = Color.valueOf("5ff0ff");
   static final Color abyss = Color.valueOf("05000d");
   static final Color deep = Color.valueOf("1b0838");
   final float s;
   final float HALF;
   final float blockRange;
   static final Map<Unit, Float> lastTp = new WeakHashMap();
   static final Effect fx = new Effect(40.0F, 200.0F, (var0) -> {
      Color var1 = Color.valueOf("a35cff");
      Color var2 = Color.valueOf("5ff0ff");
      Lines.stroke(var0.fout() * 3.0F + 0.3F);
      Draw.color(var2, var0.fout());
      Lines.circle(var0.x, var0.y, var0.finpow() * 22.0F);
      Angles.randLenVectors((long)var0.id, 14, var0.finpow() * 26.0F, (var2x, var3) -> {
         Draw.color(var1, var0.fout());
         Fill.circle(var0.x + var2x, var0.y + var3, var0.fout() * 2.2F + 0.3F);
      });
      Draw.reset();
   });
   float timer;
   float blockTimer;
   float flash;

   public WarpAnomaly() {
      this(1.0F);
   }

   public WarpAnomaly(float var1) {
      this.s = var1;
      this.HALF = 12.0F * var1;
      this.blockRange = 4.5F * var1;
      this.tileRadius = Math.max(1, Math.round(1.5F * var1));
   }

   public Color color() {
      return violet;
   }

   static boolean freeSpot(int var0, int var1, int var2) {
      for(int var3 = -var2; var3 <= var2; ++var3) {
         for(int var4 = -var2; var4 <= var2; ++var4) {
            Tile var5 = Vars.world.tile(var0 + var3, var1 + var4);
            if (var5 == null || var5.block() != Blocks.air || var5.solid()) {
               return false;
            }

            try {
               if (var5.floor().isDeep()) {
                  return false;
               }
            } catch (Throwable var7) {
            }
         }
      }

      return true;
   }

   int[] find(int var1) {
      int var2 = Vars.world.width();
      int var3 = Vars.world.height();
      if (var2 > 2 * var1 + 3 && var3 > 2 * var1 + 3) {
         for(int var4 = 0; var4 < 150; ++var4) {
            int var5 = Mathf.random(var1 + 1, var2 - var1 - 2);
            int var6 = Mathf.random(var1 + 1, var3 - var1 - 2);
            if (!(Mathf.dst((float)var5 * 8.0F, (float)var6 * 8.0F, this.x, this.y) < 64.0F) && freeSpot(var5, var6, var1)) {
               return new int[]{var5, var6};
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public void update() {
      if (this.flash > 0.0F) {
         this.flash -= Time.delta;
      }

      float var1 = this.fade();
      if (!(var1 < 0.9F)) {
         this.timer += Time.delta;
         if (this.timer >= 3.0F) {
            this.timer = 0.0F;
            ArrayList<Unit> var2 = new ArrayList<>();
            Units.nearby(this.x - this.HALF, this.y - this.HALF, this.HALF * 2.0F, this.HALF * 2.0F, (var2x) -> {
               if (var2x.isValid() && !var2x.isFlying()) {
                  if (!(Math.abs(var2x.x - this.x) > this.HALF) && !(Math.abs(var2x.y - this.y) > this.HALF)) {
                     Float var3 = (Float)lastTp.get(var2x);
                     if (var3 == null || !(Time.time - var3 < 60.0F)) {
                        var2.add(var2x);
                     }
                  }
               }
            });

            for(Unit var4 : var2) {
               int[] var5 = this.find(1);
               if (var5 == null) {
                  break;
               }

               fx.at(var4.x, var4.y);
               var4.x = (float)var5[0] * 8.0F;
               var4.y = (float)var5[1] * 8.0F;

               try {
                  var4.vel.set(0.0F, 0.0F);
               } catch (Throwable var7) {
               }

               lastTp.put(var4, Time.time);
               fx.at(var4.x, var4.y);
               this.flash = 20.0F;
            }
         }

         this.blockTimer += Time.delta;
         if (this.blockTimer >= 45.0F) {
            this.blockTimer = 0.0F;
            this.moveBlock();
         }

      }
   }

   void moveBlock() {
      int var1 = Math.round(this.x / 8.0F);
      int var2 = Math.round(this.y / 8.0F);
      ArrayList var3 = new ArrayList();
      int var4 = (int)Math.ceil((double)this.blockRange) + 1;

      for(int var5 = -var4; var5 <= var4; ++var5) {
         for(int var6 = -var4; var6 <= var4; ++var6) {
            if (!(Math.sqrt((double)(var5 * var5 + var6 * var6)) > (double)this.blockRange)) {
               Tile var7 = Vars.world.tile(var1 + var5, var2 + var6);
               if (var7 != null && var7.build != null) {
                  Building var8 = var7.build;
                  if (!var3.contains(var8) && !var8.block.name.contains("core")) {
                     var3.add(var8);
                  }
               }
            }
         }
      }

      if (!var3.isEmpty()) {
         Building var17 = (Building)var3.get(Mathf.random(var3.size() - 1));
         Block var18 = var17.block;
         int var19 = Math.max(1, (var18.size + 1) / 2);
         int[] var20 = this.find(var19);
         if (var20 != null) {
            Tile var9 = Vars.world.tile(var20[0], var20[1]);
            Tile var10 = var17.tile;
            Object var11 = null;

            try {
               var11 = var17.config();
            } catch (Throwable var16) {
            }

            float var12 = var17.x;
            float var13 = var17.y;
            var9.setNet(var18, var17.team, var17.rotation);
            var10.setNet(Blocks.air);

            try {
               if (var11 != null && var9.build != null) {
                  var9.build.configureAny(var11);
               }
            } catch (Throwable var15) {
            }

            fx.at(var12, var13);
            fx.at((float)var20[0] * 8.0F, (float)var20[1] * 8.0F);
            this.flash = 20.0F;
         }
      }
   }

   static float hash(int var0) {
      double var1 = Math.sin((double)var0 * 12.9898) * 43758.5453;
      return (float)(var1 - Math.floor(var1));
   }

   public void draw() {
      AnomalyShaders.distort(this.x, this.y, 22.0F * this.s, this.fade(), AnomalyShaders.WARP, 88.0F);
      float var1 = this.fade();
      float var2 = Time.time;
      float var3 = 11.5F * this.s;
      float var4 = 0.85F + 0.15F * (float)Math.sin((double)(var2 * 0.1F)) + Math.max(0.0F, this.flash) * 0.02F;
      float var5 = (float)Math.PI;
      float var6 = 88.0F;
      Draw.z(var6);
      Draw.blend(Blending.additive);
      Draw.color(violet, 0.12F * var1 * var4);
      Fill.circle(this.x, this.y, var3 * 2.1F);
      Draw.color(violet, 0.18F * var1 * var4);
      Fill.circle(this.x, this.y, var3 * 1.45F);
      Draw.blend();
      Draw.z(var6 + 0.1F);
      Draw.color(abyss, var1);
      Fill.circle(this.x, this.y, var3);
      Draw.z(var6 + 0.2F);
      Draw.color(deep, var1 * 0.8F);
      Fill.circle(this.x, this.y, var3 * 0.8F);
      Draw.z(var6 + 0.3F);
      Draw.color(abyss, var1);
      Fill.circle(this.x, this.y, var3 * 0.55F);
      Draw.z(var6 + 0.4F);
      Draw.blend(Blending.additive);

      for(int var7 = 0; var7 < 3; ++var7) {
         float var8 = this.x;
         float var9 = this.y;

         for(int var10 = 1; var10 <= 14; ++var10) {
            float var11 = var3 * 0.92F * (float)var10 / 14.0F;
            float var12 = (float)var7 * 2.0F * var5 / 3.0F - var2 * 0.03F + var11 * 0.28F;
            float var13 = this.x + (float)Math.cos((double)var12) * var11;
            float var14 = this.y + (float)Math.sin((double)var12) * var11;
            Lines.stroke(1.4F * (1.0F - (float)var10 / 16.0F));
            Draw.color(violet, 0.5F * var1 * ((float)var10 / 14.0F));
            Lines.line(var8, var9, var13, var14);
            var8 = var13;
            var9 = var14;
         }
      }

      Draw.z(var6 + 0.5F);
      float[] var17 = new float[]{4.5F, 2.4F, 1.1F};
      float[] var18 = new float[]{0.22F, 0.6F, 1.0F};

      for(int var19 = 0; var19 < 3; ++var19) {
         Lines.stroke(var17[var19]);
         Draw.color(var19 == 2 ? light : (var19 == 1 ? cyan : violet), var18[var19] * var1 * var4);
         Lines.circle(this.x, this.y, var3);
      }

      Draw.z(var6 + 0.6F);

      for(int var20 = 0; var20 < 26; ++var20) {
         float var21 = var3 * (1.15F + hash(var20 * 3) * 0.8F);
         float var22 = (0.012F + hash(var20 * 5) * 0.03F) * (var20 % 2 == 0 ? 1.0F : -1.0F);
         float var23 = hash(var20) * 2.0F * var5 + var2 * var22;
         float var24 = this.x + (float)Math.cos((double)var23) * var21;
         float var25 = this.y + (float)Math.sin((double)var23) * var21;
         Draw.color(var20 % 3 == 0 ? cyan : (var20 % 3 == 1 ? violet : light), (0.5F + 0.5F * hash(var20 + 40)) * var1);
         int var15 = var20 % 3;
         if (var15 == 0) {
            Fill.circle(var24, var25, 0.7F + hash(var20 + 9) * 1.1F);
         } else if (var15 == 1) {
            Fill.square(var24, var25, 0.9F + hash(var20 + 9), var23 * 57.2958F + var2 * 4.0F);
         } else {
            Lines.stroke(1.0F);
            float var16 = var23 + (var22 > 0.0F ? 1.0F : -1.0F) * 1.5708F;
            Lines.line(var24, var25, var24 - (float)Math.cos((double)var16) * 3.5F, var25 - (float)Math.sin((double)var16) * 3.5F);
         }
      }

      Draw.blend();
      Drawf.light(this.x, this.y, 36.0F * this.s, violet, 0.5F * var1 * var4);
      Draw.reset();
   }
}
