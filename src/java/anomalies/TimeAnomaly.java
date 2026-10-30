package anomalies;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import java.util.Objects;
import mindustry.entities.Units;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;

public class TimeAnomaly extends Anomaly {
   static final float R = 9.0F;
   static final int CR = 9;
   static final int PTS = 5;
   static final int HOLE = 12;
   static final Color green = Color.valueOf("b4ffb0");
   static final Color glass = Color.valueOf("b9e2cf");
   final float[][] cx = new float[9][6];
   final float[][] cy = new float[9][6];
   final float[] hx = new float[12];
   final float[] hy = new float[12];
   public float cooldown;

   public TimeAnomaly() {
      this.tileRadius = 1;
      this.rotation = Mathf.random(360.0F);

      for(int var1 = 0; var1 < 9; ++var1) {
         float var2 = (float)var1 * 40.0F + Mathf.range(10.0F);

         for(int var3 = 1; var3 <= 5; ++var3) {
            var2 += Mathf.range(14.0F);
            float var4 = 2.5F + (float)var3 * 2.3F + Mathf.range(0.8F);
            this.cx[var1][var3] = Mathf.cosDeg(var2) * var4;
            this.cy[var1][var3] = Mathf.sinDeg(var2) * var4;
         }
      }

      for(int var5 = 0; var5 < 12; ++var5) {
         float var6 = (float)var5 * 30.0F;
         float var7 = Mathf.random(2.2F, 4.6F);
         this.hx[var5] = Mathf.cosDeg(var6) * var7;
         this.hy[var5] = Mathf.sinDeg(var6) * var7;
      }

   }

   public Color color() {
      return green;
   }

   public void update() {
      if (!(this.cooldown <= 0.0F)) {
         this.cooldown -= Time.delta;
      } else {
         AnomalyManager.tmp.clear();
         float var10000 = this.x - 9.0F - 20.0F;
         float var10001 = this.y - 9.0F - 20.0F;
         Seq var10004 = AnomalyManager.tmp;
         Objects.requireNonNull(var10004);
         Units.nearby(var10000, var10001, 58.0F, 58.0F, var10004::add);

         for(Unit var2 : AnomalyManager.tmp) {
            if (var2.isValid() && var2.within(this.x, this.y, 9.0F + var2.hitSize / 3.0F)) {
               AnomalyManager.rollback(var2);
               this.cooldown = 120.0F;
            }
         }

      }
   }

   public void draw() {
      AnomalyShaders.distort(this.x, this.y, 12.0F, this.fade(), AnomalyShaders.TIME, 110.0F);
      float var1 = this.fade();
      float var2 = 0.6F + Mathf.absin(Time.time, 16.0F, 0.4F);

      for(int var3 = 0; var3 < 9; ++var3) {
         int var4 = (var3 + 1) % 9;
         Draw.color(glass, (var3 % 2 == 0 ? 0.16F : 0.07F) * var1);
         Fill.tri(this.x, this.y, this.wx(this.cx[var3][5], this.cy[var3][5]), this.wy(this.cx[var3][5], this.cy[var3][5]), this.wx(this.cx[var4][5], this.cy[var4][5]), this.wy(this.cx[var4][5], this.cy[var4][5]));
      }

      Draw.blend(Blending.additive);

      for(int var10 = 0; var10 < 3; ++var10) {
         Lines.stroke(var10 == 0 ? 4.2F : (var10 == 1 ? 2.0F : 0.9F));
         Draw.color(var10 == 2 ? Color.white : green, var1 * var2 * (var10 == 0 ? 0.18F : (var10 == 1 ? 0.4F : 0.95F)));

         for(int var13 = 0; var13 < 9; ++var13) {
            float var5 = this.x;
            float var6 = this.y;

            for(int var7 = 1; var7 <= 5; ++var7) {
               float var8 = this.wx(this.cx[var13][var7], this.cy[var13][var7]);
               float var9 = this.wy(this.cx[var13][var7], this.cy[var13][var7]);
               Lines.line(var5, var6, var8, var9);
               var5 = var8;
               var6 = var9;
            }
         }
      }

      Draw.blend();
      Draw.color(Color.black, var1);

      for(int var11 = 0; var11 < 12; ++var11) {
         int var14 = (var11 + 1) % 12;
         Fill.tri(this.x, this.y, this.wx(this.hx[var11], this.hy[var11]), this.wy(this.hx[var11], this.hy[var11]), this.wx(this.hx[var14], this.hy[var14]), this.wy(this.hx[var14], this.hy[var14]));
      }

      Draw.blend(Blending.additive);
      Lines.stroke(1.1F);
      Draw.color(green, var1 * var2);

      for(int var12 = 0; var12 < 12; ++var12) {
         int var15 = (var12 + 1) % 12;
         Lines.line(this.wx(this.hx[var12], this.hy[var12]), this.wy(this.hx[var12], this.hy[var12]), this.wx(this.hx[var15], this.hy[var15]), this.wy(this.hx[var15], this.hy[var15]));
      }

      Draw.blend();
      Drawf.light(this.x, this.y, 36.0F, green, 0.4F * var1 * var2);
      Draw.reset();
   }
}
