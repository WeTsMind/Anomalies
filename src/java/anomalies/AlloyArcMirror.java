package anomalies;

import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

public class AlloyArcMirror extends MirrorAnomaly {
   static final float UNIT_BACK = 13.0F;
   static final float EXTRA_ARC = 6.0F;
   static final float EXTRA_OUT = 12.0F;
   private float baseRot;
   /** Unit this shield is attached to (null on clients until found). */
   Unit owner;
   private final float[] anchorTmp = new float[3];

   /** Pins the shield to the owner's current position/rotation so it never lags behind the unit. */
   private void sync() {
      if (this.owner != null && (!this.owner.isValid() || !(this.owner.type instanceof AlloyUnit))) {
         this.owner = null;
      }

      if (this.owner == null && this.team != null) {
         Unit best = null;
         float bd = 48.0F * 48.0F;

         for(int i = 0; i < Groups.unit.size(); ++i) {
            Unit u = Groups.unit.index(i);
            if (u.team == this.team && u.type instanceof AlloyUnit) {
               float dx = u.x - this.x;
               float dy = u.y - this.y;
               float d = dx * dx + dy * dy;
               if (d < bd) {
                  bd = d;
                  best = u;
               }
            }
         }

         this.owner = best;
      }

      if (this.owner != null) {
         AlloyUnit.anchor(this.owner, this.anchorTmp);
         this.x = this.anchorTmp[0];
         this.y = this.anchorTmp[1];
         this.rotation = this.anchorTmp[2];
      }
   }

   public void update() {
      this.sync();
      super.update();
   }

   public void draw() {
      this.sync();
      super.draw();
   }

   public AlloyArcMirror(float var1, float var2) {
      super(var1, var2);
   }

   private float radius() {
      return (float)Math.sqrt((double)(169.0F + this.hh * this.hh));
   }

   private float halfAngle() {
      return (float)Math.atan2((double)this.hh, 23.82461052445301);
   }

   private float warpX(float var1, float var2) {
      float var3 = var2 / this.hh * this.halfAngle();
      return -13.0F + (this.radius() + var1) * (float)Math.cos((double)var3);
   }

   private float warpY(float var1, float var2) {
      float var3 = var2 / this.hh * this.halfAngle();
      return (this.radius() + var1) * (float)Math.sin((double)var3);
   }

   public float wx(float var1, float var2) {
      double var3 = Math.toRadians((double)this.rotation);
      return this.x + this.warpX(var1, var2) * (float)Math.cos(var3) - this.warpY(var1, var2) * (float)Math.sin(var3);
   }

   public float wy(float var1, float var2) {
      double var3 = Math.toRadians((double)this.rotation);
      return this.y + this.warpX(var1, var2) * (float)Math.sin(var3) + this.warpY(var1, var2) * (float)Math.cos(var3);
   }

   void handle(Bullet var1, float var2, float var3) {
      this.baseRot = this.rotation;

      try {
         super.handle(var1, var2, var3);
      } finally {
         this.rotation = this.baseRot;
      }

   }

   private boolean inside(float var1, float var2, float var3, float var4) {
      float var5 = var1 * var1 + var2 * var2;
      if (var5 > var3 * var3 + 0.001F) {
         return false;
      } else if (var5 < 1.0E-6F) {
         return true;
      } else {
         return Math.abs((float)Math.atan2((double)var2, (double)var1)) <= var4 + 1.0E-4F;
      }
   }

   float clip(float var1, float var2, float var3, float var4) {
      float var5 = this.radius() + this.hw + 12.0F;
      float var6 = this.halfAngle() + 6.0F / this.radius();
      float var7 = var1 + 13.0F;
      float var8 = var2;
      float var9 = var3 - var1;
      float var10 = var4 - var2;
      float[] var11 = new float[8];
      int var12 = 0;
      var11[var12++] = 0.0F;
      var11[var12++] = 1.0F;
      float var13 = var9 * var9 + var10 * var10;
      float var14 = 2.0F * (var7 * var9 + var2 * var10);
      float var15 = var7 * var7 + var2 * var2 - var5 * var5;
      if (var13 > 1.0E-9F) {
         float var16 = var14 * var14 - 4.0F * var13 * var15;
         if (var16 >= 0.0F) {
            float var17 = (float)Math.sqrt((double)var16);
            var11[var12++] = (-var14 - var17) / (2.0F * var13);
            var11[var12++] = (-var14 + var17) / (2.0F * var13);
         }
      }

      for(int var26 = 0; var26 < 2; ++var26) {
         float var28 = (float)Math.cos(var26 == 0 ? (double)var6 : (double)(-var6));
         float var18 = (float)Math.sin(var26 == 0 ? (double)var6 : (double)(-var6));
         float var19 = var9 * var18 - var10 * var28;
         if (Math.abs(var19) > 1.0E-9F) {
            var11[var12++] = (var28 * var8 - var18 * var7) / var19;
         }
      }

      float var27 = -1.0F;
      float var29 = 0.0F;
      float var30 = 0.0F;

      for(int var31 = 0; var31 < var12; ++var31) {
         float var20 = var11[var31];
         if (!(var20 < 0.0F) && !(var20 > 1.0F) && (!(var27 >= 0.0F) || !(var20 >= var27))) {
            float var21 = var7 + var9 * var20;
            float var22 = var8 + var10 * var20;
            if (this.inside(var21, var22, var5, var6)) {
               var27 = var20;
               var29 = var21;
               var30 = var22;
            }
         }
      }

      if (var27 >= 0.0F) {
         float var32 = (float)Math.atan2((double)var30, (double)var29);
         if (var32 > var6) {
            var32 = var6;
         }

         if (var32 < -var6) {
            var32 = -var6;
         }

         this.rotation = this.baseRot + (float)Math.toDegrees((double)var32);
      }

      return var27;
   }
}
