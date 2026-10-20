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
import java.util.Arrays;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;

public class BlackHoleAnomaly extends Anomaly {
   static final float grow = 10800.0F;
   static final float morph = 180.0F;
   static final float eatTime = 3600.0F;
   static final float holeLife = 18000.0F;
   static final float R0 = 9.0F;
   final float s;
   final int RT;
   final int W;
   final float RPX;
   static final Color warm = Color.valueOf("ffd78a");
   static final Color soft = Color.valueOf("fff3d6");
   static final Color white = Color.valueOf("ffffff");
   static final Color black = Color.valueOf("000000");
   final float[] bx;
   final float[] by;
   final float[] bs;
   final Color[] bc;
   int bn;
   final ArrayList<Unit> doomed;
   final ArrayList<float[]> dd;
   int[] order;
   int eaten;
   boolean adj;
   boolean boom;
   float unitTimer;
   static final Effect blast = new Effect(100.0F, 700.0F, (var0) -> {
      Color var1 = Color.valueOf("2f7bff");
      Color var2 = Color.valueOf("8fd0ff");
      Color var3 = Color.valueOf("e8f6ff");
      Draw.color(var2, var0.fout() * 0.5F);
      Fill.circle(var0.x, var0.y, var0.fout() * 70.0F);
      Draw.color(var3, var0.fout());
      Fill.circle(var0.x, var0.y, var0.fout() * 32.0F);
      Lines.stroke(var0.fout() * 9.0F + 0.5F);
      Draw.color(var1, var0.fout());
      Lines.circle(var0.x, var0.y, var0.finpow() * 150.0F);
      Angles.randLenVectors((long)var0.id, 60, var0.finpow() * 150.0F, (var2x, var3x) -> {
         Draw.color(var2, var0.fout());
         Fill.circle(var0.x + var2x, var0.y + var3x, var0.fout() * 6.0F + 0.6F);
      });
      Angles.randLenVectors((long)(var0.id + 7), 34, 20.0F + var0.finpow() * 190.0F, (var2x, var3x) -> {
         Lines.stroke(var0.fout() * 1.8F + 0.2F);
         Draw.color(var3, var0.fout());
         Lines.lineAngle(var0.x + var2x, var0.y + var3x, Mathf.angle(var2x, var3x), var0.fout() * 14.0F + 2.0F);
      });
      Draw.reset();
   });

   public BlackHoleAnomaly() {
      this(1.0F);
   }

   public BlackHoleAnomaly(float var1) {
      this.bx = new float[256];
      this.by = new float[256];
      this.bs = new float[256];
      this.bc = new Color[256];
      this.doomed = new ArrayList();
      this.dd = new ArrayList();
      this.s = var1;
      this.RT = Math.round(10.0F * var1);
      this.W = this.RT * 2 + 1;
      this.RPX = (float)this.RT * 8.0F;
      this.tileRadius = 1;
   }

   public Color color() {
      return white;
   }

   void buildOrder() {
      int var1 = 0;
      int[] var2 = new int[this.W * this.W];

      for(int var3 = -this.RT; var3 <= this.RT; ++var3) {
         for(int var4 = -this.RT; var4 <= this.RT; ++var4) {
            if (Math.sqrt((double)(var3 * var3 + var4 * var4)) <= (double)((float)this.RT + 0.2F)) {
               var2[var1++] = (var3 + this.RT) * this.W + var4 + this.RT;
            }
         }
      }

      this.order = Arrays.copyOf(var2, var1);

      for(int var6 = var1 - 1; var6 > 0; --var6) {
         int var7 = Mathf.random(var6);
         int var5 = this.order[var6];
         this.order[var6] = this.order[var7];
         this.order[var7] = var5;
      }

   }

   public void update() {
      if (!this.adj) {
         this.adj = true;
         this.life = 28980.0F;
      }

      float var1 = 10980.0F;
      if (!(this.age < var1)) {
         if (this.order == null) {
            this.buildOrder();
         }

         int var2 = Math.round(this.x / 8.0F);
         int var3 = Math.round(this.y / 8.0F);
         int var4 = (int)(Math.min(1.0F, (this.age - var1) / 3600.0F) * (float)this.order.length);

         while(this.eaten < var4) {
            int var5 = this.order[this.eaten++];
            this.consume(var2 + var5 / this.W - this.RT, var3 + var5 % this.W - this.RT);
         }

         for(int var15 = this.doomed.size() - 1; var15 >= 0; --var15) {
            Unit var6 = (Unit)this.doomed.get(var15);
            float[] var7 = (float[])this.dd.get(var15);
            float var8 = (this.age - var7[2]) / 90.0F;
            if (var6.isValid() && !(var8 >= 1.0F)) {
               float var9 = var7[1] * (1.0F - var8 * var8);
               float var10 = var7[0] + var8 * var8 * 14.0F;
               var6.x = this.x + (float)Math.cos((double)var10) * var9;
               var6.y = this.y + (float)Math.sin((double)var10) * var9;
               var6.rotation += 25.0F;

               try {
                  var6.vel.set(0.0F, 0.0F);
               } catch (Throwable var13) {
               }
            } else {
               try {
                  if (var6.isValid()) {
                     var6.kill();
                  }
               } catch (Throwable var14) {
               }

               this.doomed.remove(var15);
               this.dd.remove(var15);
            }
         }

         this.unitTimer += Time.delta;
         if (this.unitTimer >= 25.0F) {
            this.unitTimer = 0.0F;
            ArrayList var16 = new ArrayList();
            Units.nearby(this.x - this.RPX, this.y - this.RPX, this.RPX * 2.0F, this.RPX * 2.0F, (var2x) -> {
               if (var2x.isValid() && !this.doomed.contains(var2x) && Mathf.dst(var2x.x, var2x.y, this.x, this.y) <= this.RPX) {
                  var16.add(var2x);
               }

            });
            if (!var16.isEmpty()) {
               Unit var17 = (Unit)var16.get(Mathf.random(var16.size() - 1));
               this.doomed.add(var17);
               this.dd.add(new float[]{(float)Math.atan2((double)(var17.y - this.y), (double)(var17.x - this.x)), Mathf.dst(var17.x, var17.y, this.x, this.y), this.age});
            }
         }

         if (!this.boom && this.life - this.age <= 20.0F) {
            this.boom = true;

            try {
               blast.at(this.x, this.y);
               Effect.shake(8.0F, 30.0F, this.x, this.y);
               Damage.damage(this.x, this.y, 152.0F * this.s, 5000.0F);
            } catch (Throwable var12) {
            }
         }

      }
   }

   void consume(int var1, int var2) {
      Tile var3 = Vars.world.tile(var1, var2);
      if (var3 != null) {
         boolean var4 = AnomalySettings.destroyWorld();
         if (var4 || var3.build != null) {
            int var5 = this.bn++ & 255;
            this.bx[var5] = (float)var1 * 8.0F;
            this.by[var5] = (float)var2 * 8.0F;
            this.bs[var5] = this.age;

            try {
               this.bc[var5] = var3.block() != Blocks.air ? var3.block().mapColor : var3.floor().mapColor;
            } catch (Throwable var11) {
               this.bc[var5] = white;
            }

            try {
               if (var3.block() != Blocks.air && (var4 || var3.build != null)) {
                  var3.setNet(Blocks.air);
               }
            } catch (Throwable var12) {
               try {
                  var3.setBlock(Blocks.air);
               } catch (Throwable var10) {
               }
            }

            if (var4) {
               try {
                  var3.setFloorNet(Blocks.space);
               } catch (Throwable var9) {
                  try {
                     var3.setFloor((Floor)Blocks.space);
                  } catch (Throwable var8) {
                  }
               }

            }
         }
      }
   }

   static float hash(int var0) {
      double var1 = Math.sin((double)var0 * 12.9898) * 43758.5453;
      return (float)(var1 - Math.floor(var1));
   }

   void disc(float var1, float var2, float var3, float var4, float var5) {
      float[] var6 = new float[]{3.2F, 1.8F, 0.8F};
      float[] var7 = new float[]{0.18F, 0.5F, 1.0F};

      for(int var8 = 0; var8 < 3; ++var8) {
         Lines.stroke(var6[var8] * var5);
         Draw.color(var8 == 2 ? white : (var8 == 1 ? soft : warm), var7[var8] * var4);
         byte var9 = 24;
         float var10 = 0.0F;
         float var11 = 0.0F;

         for(int var12 = 0; var12 <= var9; ++var12) {
            float var13 = var2 + (var3 - var2) * (float)var12 / (float)var9;
            float var14 = this.x + (float)Math.cos((double)var13) * var1 * 2.4F;
            float var15 = this.y + (float)Math.sin((double)var13) * var1 * 0.3F;
            if (var12 > 0) {
               Lines.line(var10, var11, var14, var15);
            }

            var10 = var14;
            var11 = var15;
         }
      }

   }

   public void draw() {
      float var1 = this.fade();
      float var2 = Time.time;
      float var3 = this.age >= 10800.0F ? Math.min(1.0F, (this.age - 10800.0F) / 180.0F) : 0.0F;
      float var4 = Math.max(0.0F, Math.min(1.0F, (this.age - 9000.0F) / 1800.0F));
      float var5 = (float)Math.PI;
      Draw.z(89.0F);
      Draw.blend(Blending.additive);
      if (var3 < 1.0F) {
         float var6 = 1.0F - var3;
         float var7 = 1.0F + 0.12F * (1.0F + var4 * 2.0F) * (float)Math.sin((double)(var2 * (0.12F + var4 * 0.3F)));
         float var8 = (3.0F + 1.5F * var4) * var7 * (0.4F + 0.6F * var6);
         Draw.color(white, 0.1F * var1 * var6);
         Fill.circle(this.x, this.y, var8 * 3.6F);
         Draw.color(white, 0.22F * var1 * var6);
         Fill.circle(this.x, this.y, var8 * 2.2F);
         Draw.color(soft, 0.6F * var1 * var6);
         Fill.circle(this.x, this.y, var8 * 1.4F);
         Draw.color(white, var1 * var6);
         Fill.circle(this.x, this.y, var8);
         if (var4 > 0.3F) {
            for(int var9 = 0; var9 < 4; ++var9) {
               int var10 = (int)(var2 / 4.0F) * 17 + var9 * 53;
               float var11 = hash(var10) * 2.0F * var5;
               float var12 = var8 * (1.6F + hash(var10 + 1) * 2.4F) * var4;
               Lines.stroke(1.0F);
               Draw.color(white, 0.7F * var1 * var6);
               Lines.line(this.x, this.y, this.x + (float)Math.cos((double)var11) * var12, this.y + (float)Math.sin((double)var11) * var12);
            }
         }
      }

      if (var3 > 0.0F) {
         float var16 = 1.0F + (this.order == null ? 0.0F : 1.2F * (float)this.eaten / (float)this.order.length);
         float var17 = 9.0F * this.s * var3 * var16;
         Draw.color(warm, 0.08F * var1 * var3);
         Fill.circle(this.x, this.y, var17 * 3.0F);
         Draw.color(soft, 0.12F * var1 * var3);
         Fill.circle(this.x, this.y, var17 * 1.9F);
         this.disc(var17, 0.0F, var5, var1, var3);
         Draw.blend();
         Draw.color(black, var1);
         Fill.circle(this.x, this.y, var17);

         for(int var18 = 0; var18 < 256; ++var18) {
            if (this.bc[var18] != null && !(this.age - this.bs[var18] >= 45.0F) && !(this.age < this.bs[var18])) {
               float var20 = (this.age - this.bs[var18]) / 45.0F;
               float var22 = this.bx[var18] - this.x;
               float var25 = this.by[var18] - this.y;
               float var27 = (float)Math.sqrt((double)(var22 * var22 + var25 * var25)) * (1.0F - var20 * var20);
               float var13 = (float)Math.atan2((double)var25, (double)var22) + var20 * 3.0F;
               float var14 = this.x + (float)Math.cos((double)var13) * var27;
               float var15 = this.y + (float)Math.sin((double)var13) * var27;
               Draw.color(this.bc[var18], var1 * (1.0F - 0.3F * var20));
               Fill.square(var14, var15, 4.2F * (1.0F - 0.85F * var20), var13 * 57.2958F + var20 * 360.0F);
            }
         }

         Draw.blend(Blending.additive);
         float[] var19 = new float[]{4.0F, 2.2F, 1.0F};
         float[] var21 = new float[]{0.2F, 0.55F, 1.0F};

         for(int var23 = 0; var23 < 3; ++var23) {
            Lines.stroke(var19[var23] * var3);
            Draw.color(var23 == 2 ? white : (var23 == 1 ? soft : warm), var21[var23] * var1);
            Lines.circle(this.x, this.y, var17 * 1.06F);
         }

         this.disc(var17, var5, 2.0F * var5, var1, var3);

         for(int var24 = 0; var24 < 14; ++var24) {
            float var26 = 0.004F + hash(var24 * 7) * 0.006F;
            float var28 = (var2 * var26 + hash(var24)) % 1.0F;
            float var29 = hash(var24 + 100) * 2.0F * var5 + var28 * 5.0F;
            float var30 = var17 * (1.3F + 3.7F * (1.0F - var28));
            Draw.color(var24 % 3 == 0 ? white : warm, 0.8F * var1 * var3 * var28);
            Fill.circle(this.x + (float)Math.cos((double)var29) * var30, this.y + (float)Math.sin((double)var29) * var30 * 0.8F, 0.9F);
         }
      }

      Draw.blend();
      Drawf.light(this.x, this.y, (40.0F + 30.0F * var3) * this.s, white, 0.5F * var1);
      Draw.reset();
   }
}
