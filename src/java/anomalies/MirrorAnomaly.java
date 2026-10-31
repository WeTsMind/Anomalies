package anomalies;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Bullet;
import mindustry.gen.Entityc;
import mindustry.gen.Groups;
import mindustry.graphics.Drawf;

public class MirrorAnomaly extends Anomaly {
   static final int[][] SIZES = new int[][]{{1, 3}, {2, 3}, {2, 4}, {2, 5}};
   static final int N = 14;
   static final Color glow = Color.valueOf("58FFF9");
   static final Color body = Color.valueOf("58FFF9");
   static final Color core = Color.valueOf("58FFF9");
   static final int M = 6;
   static final int B = 2;
   public Team team;
   final float hw;
   final float hh;
   final float[] lw;
   final float[] rw;
   final float[] cj;
   final float[] mx;
   final float[] my;
   final float[][] bx;
   final float[][] by;
   boolean dropped;

   public static MirrorAnomaly alpha() {
      int[] var0 = SIZES[Mathf.random(SIZES.length - 1)];
      float var1 = AlphaSettings.scale();
      return new MirrorAnomaly((float)var0[0] * 4.0F * var1, (float)var0[1] * 4.0F * var1);
   }

   public MirrorAnomaly() {
      this(SIZES[Mathf.random(SIZES.length - 1)]);
   }

   private MirrorAnomaly(int[] var1) {
      this((float)var1[0] * 4.0F, (float)var1[1] * 4.0F);
   }

   public MirrorAnomaly(float var1, float var2) {
      this.lw = new float[15];
      this.rw = new float[15];
      this.cj = new float[15];
      this.mx = new float[6];
      this.my = new float[6];
      this.bx = new float[2][3];
      this.by = new float[2][3];
      this.hw = var1;
      this.hh = var2;
      this.rotation = Mathf.random(360.0F);
      this.tileRadius = Mathf.ceil(Mathf.sqrt(this.hw * this.hw + this.hh * this.hh) / 8.0F);

      for(int var3 = 0; var3 <= 14; ++var3) {
         float var4 = (float)var3 / 14.0F;
         float var5 = this.hw * (float)Math.pow(Math.sin((double)var4 * Math.PI), (double)0.55F);
         this.lw[var3] = var5 * Mathf.random(0.55F, 1.0F);
         this.rw[var3] = var5 * Mathf.random(0.55F, 1.0F);
         this.cj[var3] = 0.0F;
      }

      float var8 = Mathf.range(this.hw * 0.2F);

      for(int var10 = 0; var10 < 6; ++var10) {
         float var12 = (float)var10 / 5.0F;
         this.my[var10] = -this.hh * 0.95F + var12 * this.hh * 1.9F + (var10 > 0 && var10 < 5 ? Mathf.range(this.hh * 0.08F) : 0.0F);
         float var6 = this.hw * (float)Math.pow(Math.sin((double)(var12 * 0.9F + 0.05F) * Math.PI), (double)0.55F) * 0.6F;
         float var9 = var8 + Mathf.range(this.hw * 0.55F);
         var8 = Math.max(-var6, Math.min(var6, var9));
         this.mx[var10] = var8;
      }

      for(int var11 = 0; var11 < 2; ++var11) {
         int var13 = 1 + (int)(((float)var11 + Mathf.random(0.1F, 0.9F)) * 4.0F / 2.0F);
         float var14 = Mathf.random(1.0F) > 0.5F ? 1.0F : -1.0F;
         float var7 = this.hw * Mathf.random(0.8F, 1.4F);
         this.bx[var11][0] = this.mx[var13];
         this.by[var11][0] = this.my[var13];
         this.bx[var11][1] = this.mx[var13] + var14 * var7 * 0.55F;
         this.by[var11][1] = this.my[var13] + Mathf.range(this.hh * 0.12F);
         this.bx[var11][2] = this.mx[var13] + var14 * var7;
         this.by[var11][2] = this.by[var11][1] + Mathf.range(this.hh * 0.2F);
      }

   }

   public Color color() {
      return glow;
   }

   float clip(float var1, float var2, float var3, float var4) {
      float var5 = 0.0F;
      float var6 = 1.0F;
      float[] var7 = new float[]{var1, var2};
      float[] var8 = new float[]{var3 - var1, var4 - var2};
      float[] var9 = new float[]{AlloyBox.hw(this), AlloyBox.hh(this)};

      for(int var10 = 0; var10 < 2; ++var10) {
         if (Math.abs(var8[var10]) < 1.0E-6F) {
            if (Math.abs(var7[var10]) > var9[var10]) {
               return -1.0F;
            }
         } else {
            float var11 = (-var9[var10] - var7[var10]) / var8[var10];
            float var12 = (var9[var10] - var7[var10]) / var8[var10];
            if (var11 > var12) {
               float var13 = var11;
               var11 = var12;
               var12 = var13;
            }

            var5 = Math.max(var5, var11);
            var6 = Math.min(var6, var12);
            if (var5 > var6) {
               return -1.0F;
            }
         }
      }

      return var5;
   }

   void handle(Bullet var1, float var2, float var3) {
      if (!AnomalyManager.reflected.contains(var1.id)) {
         if (var1.type.speed > 0.01F) {
            float var19 = var1.x - var1.vel.x * Time.delta;
            float var20 = var1.y - var1.vel.y * Time.delta;
            float var21 = var19 - this.x;
            float var22 = var20 - this.y;
            float var23 = var1.x - this.x;
            float var24 = var1.y - this.y;
            float var25 = this.clip(var21 * var2 + var22 * var3, -var21 * var3 + var22 * var2, var23 * var2 + var24 * var3, -var23 * var3 + var24 * var2);
            if (var25 >= 0.0F) {
               AnomalyManager.reflect(var1);
            }

         } else {
            if (Vars.net.client()) {
               return; // hitscan reflection spawns new bullets - server-authoritative only
            }

            float var4;
            try {
               var4 = rangeOf(var1.type);
            } catch (Throwable var18) {
               var4 = 300.0F;
            }

            if (var4 < 8.0F || var4 > 2000.0F) {
               var4 = 300.0F;
            }

            float var5 = var1.rotation();
            float var6 = var1.x + Mathf.cosDeg(var5) * var4;
            float var7 = var1.y + Mathf.sinDeg(var5) * var4;
            float var8 = var1.x - this.x;
            float var9 = var1.y - this.y;
            float var10 = var6 - this.x;
            float var11 = var7 - this.y;
            float var12 = this.clip(var8 * var2 + var9 * var3, -var8 * var3 + var9 * var2, var10 * var2 + var11 * var3, -var10 * var3 + var11 * var2);
            if (!(var12 < 0.0F)) {
               float var13 = var1.x + (var6 - var1.x) * var12;
               float var14 = var1.y + (var7 - var1.y) * var12;
               float var15 = 2.0F * (this.rotation + 90.0F) - var5;
               Team var16 = var1.team == Vars.state.rules.waveTeam ? Vars.state.rules.defaultTeam : Vars.state.rules.waveTeam;
               if (var16 == var1.team) {
                  var16 = Team.derelict;
               }

               Bullet var17 = var1.type.create((Entityc)null, var16, var13, var14, var15);
               if (var17 != null) {
                  AnomalyManager.reflected.add(var17.id);
               }

               AnomalyManager.reflected.add(var1.id);
               var1.remove();
               AnomalyFx.pulse.at(var13, var14, 8.0F, glow);
            }
         }
      }
   }

   public void removed() {
      super.removed();
      if (!this.dropped && this.team == null && this.age >= this.life) {
         this.dropped = true;

         try {
            PolcimOre.drop(this.x, this.y);
         } catch (Throwable var2) {
         }
      }

   }

   public void update() {
      float var1 = Mathf.cosDeg(this.rotation);
      float var2 = Mathf.sinDeg(this.rotation);
      Groups.bullet.each((var3) -> {
         if (this.team == null || var3.team != this.team) {
            try {
               this.handle(var3, var1, var2);
            } catch (Throwable var5) {
            }

         }
      });
   }

   static float hash(int var0) {
      double var1 = Math.sin((double)var0 * 12.9898) * 43758.5453;
      return (float)(var1 - Math.floor(var1));
   }

   void seg(float var1, float var2, float var3, float var4) {
      Lines.line(this.wx(var1, var2), this.wy(var1, var2), this.wx(var3, var4), this.wy(var3, var4));
   }

   void bolt(float var1, float var2, float var3, float var4, int var5, int var6) {
      float var7 = var1;
      float var8 = var2;
      float var9 = var3;

      for(int var10 = 0; var10 < var6; ++var10) {
         var9 += (hash(var5 + var10 * 7) - 0.5F) * 1.1F;
         float var11 = var4 / (float)var6 * (0.6F + hash(var5 + var10 * 13) * 0.8F);
         float var12 = var7 + (float)Math.cos((double)var9) * var11;
         float var13 = var8 + (float)Math.sin((double)var9) * var11;
         this.seg(var7, var8, var12, var13);
         if (var10 > 0 && hash(var5 + var10 * 31) > 2.0F) {
            float var14 = var9 + (hash(var5 + var10 * 5) > 0.5F ? 0.9F : -0.9F);
            this.seg(var12, var13, var12 + (float)Math.cos((double)var14) * var11 * 0.8F, var13 + (float)Math.sin((double)var14) * var11 * 0.8F);
         }

         var7 = var12;
         var8 = var13;
      }

   }

   public void draw() {
      if (!(this instanceof AlloyArcMirror)) {
         AnomalyShaders.distortMirror(this.x, this.y, this.hw, this.hh, this.rotation, this.lw, this.rw, this.fade(), 110.0F);
      }

      float var1 = this.fade();
      float var2 = 0.75F + Mathf.absin(Time.time, 14.0F, 0.25F);
      Draw.color(body, 0.16F * var1);

      for(int var3 = 0; var3 < 14; ++var3) {
         float var4 = -this.hh + (float)var3 / 14.0F * this.hh * 2.0F;
         float var5 = -this.hh + (float)(var3 + 1) / 14.0F * this.hh * 2.0F;
         Fill.quad(this.wx(-this.lw[var3], var4), this.wy(-this.lw[var3], var4), this.wx(this.rw[var3], var4), this.wy(this.rw[var3], var4), this.wx(this.rw[var3 + 1], var5), this.wy(this.rw[var3 + 1], var5), this.wx(-this.lw[var3 + 1], var5), this.wy(-this.lw[var3 + 1], var5));
      }

      Draw.blend(Blending.additive);

      for(int var17 = 0; var17 < 2; ++var17) {
         Lines.stroke(var17 == 0 ? 3.5F : 1.2F);
         Draw.color(var17 == 0 ? glow : core, (var17 == 0 ? 0.1F : 0.3F) * var1 * var2);

         for(int var19 = 0; var19 < 14; ++var19) {
            float var21 = -this.hh + (float)var19 / 14.0F * this.hh * 2.0F;
            float var6 = -this.hh + (float)(var19 + 1) / 14.0F * this.hh * 2.0F;
            this.seg(-this.lw[var19], var21, -this.lw[var19 + 1], var6);
            this.seg(this.rw[var19], var21, this.rw[var19 + 1], var6);
         }
      }

      int var18 = (int)(Time.time / 6.0F);
      float var20 = 0.85F + 0.15F * hash(var18 * 7 + 1);
      float[] var22 = new float[6];
      float[] var23 = new float[6];

      for(int var7 = 0; var7 < 6; ++var7) {
         float var8 = var7 != 0 && var7 != 5 ? 1.0F : 0.0F;
         var22[var7] = this.mx[var7] + (hash(var18 * 17 + var7 * 3) - 0.5F) * this.hw * 0.08F * var8;
         var23[var7] = this.my[var7] + (hash(var18 * 19 + var7 * 5) - 0.5F) * this.hh * 0.02F * var8;
      }

      float[] var24 = new float[]{6.0F, 2.6F};
      float[] var25 = new float[]{0.4F, 1.0F};

      for(int var9 = 0; var9 < 2; ++var9) {
         Draw.color(var9 == 1 ? core : glow, var25[var9] * var1 * var2 * var20);

         for(int var10 = 0; var10 < 5; ++var10) {
            float var11 = (float)Math.sin(Math.PI * (double)((float)var10 + 0.5F) / (double)5.0F);
            Lines.stroke(var24[var9] * (0.35F + 0.65F * var11));
            this.seg(var22[var10], var23[var10], var22[var10 + 1], var23[var10 + 1]);
         }

         for(int var27 = 0; var27 < 2; ++var27) {
            float var29 = this.bx[var27][0] + (var22[0] - this.mx[0]) * 0.0F;
            float var12 = this.by[var27][0];
            float var13 = this.bx[var27][1] + (hash(var18 * 23 + var27) - 0.5F) * this.hw * 0.3F;
            float var14 = this.by[var27][1] + (hash(var18 * 29 + var27) - 0.5F) * this.hh * 0.1F;
            float var15 = this.bx[var27][2] + (hash(var18 * 31 + var27) - 0.5F) * this.hw * 0.3F;
            float var16 = this.by[var27][2] + (hash(var18 * 37 + var27) - 0.5F) * this.hh * 0.1F;
            Lines.stroke(var24[var9] * 0.5F);
            this.seg(var29, var12, var13, var14);
            Lines.stroke(var24[var9] * 0.2F);
            this.seg(var13, var14, var15, var16);
         }
      }

      int var26 = (int)(Time.time / 4.0F);

      for(int var28 = 0; var28 < 4; ++var28) {
         int var30 = var26 * 31 + var28 * 977 + (int)(this.hw * 13.0F);
         if (!(hash(var30) < 0.35F)) {
            int var31 = (int)(hash(var30 + 2) * 6.0F) % 6;
            float var32 = hash(var30 + 3) * ((float)Math.PI * 2F);
            float var33 = this.hh * (0.2F + hash(var30 + 4) * 0.25F);

            for(int var34 = 0; var34 < 2; ++var34) {
               Lines.stroke(var34 == 0 ? 2.4F : 0.8F);
               Draw.color(var34 == 0 ? glow : core, (var34 == 0 ? 0.35F : 1.0F) * var1);
               this.bolt(var22[var31], var23[var31], var32, var33, var30 + 100, 3);
            }
         }
      }

      Draw.blend();
      Drawf.light(this.x, this.y, this.hh * 2.2F, glow, 0.5F * var1);
      Draw.reset();
   }

   private static java.lang.reflect.Method rangeMethod;

   /** BulletType.calculateRange() is protected, so it is called through reflection. */
   static float rangeOf(mindustry.entities.bullet.BulletType type) throws Exception {
      if (rangeMethod == null) {
         rangeMethod = mindustry.entities.bullet.BulletType.class.getDeclaredMethod("calculateRange");
         rangeMethod.setAccessible(true);
      }
      return (Float)rangeMethod.invoke(type);
   }
}
