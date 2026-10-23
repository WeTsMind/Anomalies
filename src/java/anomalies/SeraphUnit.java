package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import java.util.Map;
import java.util.WeakHashMap;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

public class SeraphUnit extends UnitType {
   // wing root in sprite pixels (128x128, y down) and fold side (+1 left, -1 right)
   static final float[][] WINGS = new float[][]{{49.0F, 38.0F, 1.0F}, {78.5F, 38.0F, -1.0F}, {54.5F, 53.0F, 1.0F}, {72.5F, 53.0F, -1.0F}};
   static final float FOLD = 36.0F;
   // wing tips (sprite px), glow tint and trail colour: front (big) wings red, rear (small) wings gold
   static final float[][] TIPS = new float[][]{{29.0F, 75.0F}, {98.0F, 75.0F}, {39.0F, 82.0F}, {88.0F, 82.0F}};
   static final Color[] GLOW = new Color[]{Color.valueOf("ff8a74"), Color.valueOf("ff8a74"), Color.valueOf("ffd27a"), Color.valueOf("ffd27a")};
   static final Color[] TRAIL = new Color[]{Color.valueOf("ff6e5a"), Color.valueOf("ff6e5a"), Color.valueOf("ffbe3c"), Color.valueOf("ffbe3c")};
   static final int TRAIL_LEN = 20;
   static final Map<Unit, float[]> trails = new WeakHashMap();
   static final float[] tmp = new float[2];
   final TextureRegion[] wingRegions = new TextureRegion[4];

   public SeraphUnit(String var1) {
      super(var1);
      this.constructor = UnitEntity::create;
      this.alwaysUnlocked = true;
      this.isEnemy = false;
      this.flying = true;
      this.lowAltitude = true;
      this.health = 350.0F;
      this.speed = 3.0F;
      this.accel = 0.07F;
      this.drag = 0.06F;
      this.hitSize = 12.0F;
      this.rotateSpeed = 4.0F;
      this.omniMovement = false;
      this.faceTarget = false;
      this.buildSpeed = 0.8F;
      this.mineTier = 1;
      this.mineSpeed = 0.5F;
      this.itemCapacity = 30;
      this.engineOffset = 6.0F;
      this.engineSize = 2.4F;
      this.outlines = false;
      this.weapons.add(new Weapon() {
         {
            this.reload = 18.0F;
            this.x = 3.0F;
            this.y = 0.0F;
            this.top = false;
            this.rotate = true;
            this.ejectEffect = Fx.casing1;
            this.bullet = new BasicBulletType(2.8F, 12.0F) {
               {
                  this.width = 7.0F;
                  this.height = 9.0F;
                  this.lifetime = 55.0F;
                  this.shootEffect = Fx.shootSmall;
                  this.smokeEffect = Fx.shootSmallSmoke;
                  this.buildingDamageMultiplier = 0.01F;
               }
            };
         }
      });
   }

   public void load() {
      super.load();

      for(int var1 = 0; var1 < 4; ++var1) {
         this.wingRegions[var1] = Core.atlas.find(this.name + "-wing-" + var1);
      }

      if (Core.atlas.has(this.name + "-icon")) {
         TextureRegion var2 = Core.atlas.find(this.name + "-icon");
         this.fullIcon = var2;
         this.uiIcon = var2;
      }

   }

   public void update(Unit var1) {
      super.update(var1);
      if (!Vars.headless) {
         float var2 = SeraphAnim.age(var1);
         float[] var3 = (float[])trails.get(var1);
         if (var3 == null) {
            var3 = new float[2 + 8 * TRAIL_LEN];
            trails.put(var1, var3);
         }

         if (var2 < 36.0F) {
            var3[1] = 0.0F;
         } else {
            float var4 = SeraphAnim.wings(var2);
            int var5 = ((int)var3[0] + 1) % TRAIL_LEN;
            var3[0] = (float)var5;
            var3[1] = Math.min(var3[1] + 1.0F, (float)TRAIL_LEN);

            for(int var6 = 0; var6 < 4; ++var6) {
               wingPoint(var1, var6, TIPS[var6][0], TIPS[var6][1], var4, tmp);
               var3[2 + (var6 * TRAIL_LEN + var5) * 2] = tmp[0];
               var3[3 + (var6 * TRAIL_LEN + var5) * 2] = tmp[1];
            }

         }
      }
   }

   public void draw(Unit var1) {
      float var2 = SeraphAnim.age(var1);
      if (var2 >= 36.0F) {
         float var3 = SeraphAnim.wingAlpha(var2);
         this.drawTrails(var1, var3);
         this.drawWings(var1, SeraphAnim.wings(var2), var3);
      }

      super.draw(var1);
   }

   void drawWings(Unit var1, float var2, float var17) {
      float var3 = var1.elevation > 0.5F ? (this.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : this.groundLayer;
      Draw.z(var3 - 0.03F);
      float var4 = var1.rotation - 90.0F;
      float var5 = Mathf.cosDeg(var4);
      float var6 = Mathf.sinDeg(var4);

      for(int var7 = 0; var7 < 4; ++var7) {
         float var8 = (WINGS[var7][0] - 64.0F) / 4.0F;
         float var9 = (64.0F - WINGS[var7][1]) / 4.0F;
         float var10 = WINGS[var7][2] * 36.0F * (1.0F - var2);
         float var11 = Mathf.cosDeg(var10);
         float var12 = Mathf.sinDeg(var10);
         float var13 = -var8;
         float var14 = -var9;
         float var15 = var8 + var13 * var11 - var14 * var12;
         float var16 = var9 + var13 * var12 + var14 * var11;
         Draw.alpha(var17);
         Draw.rect(this.wingRegions[var7], var1.x + var15 * var5 - var16 * var6, var1.y + var15 * var6 + var16 * var5, var4 + var10);
      }

      Draw.blend(Blending.additive);
      float var18 = 0.7F + 0.3F * (float)Math.sin((double)(Time.time / 14.0F + (float)var1.id));

      for(int var19 = 0; var19 < 4; ++var19) {
         float var20 = WINGS[var19][2] * 36.0F * (1.0F - var2);
         wingPoint(var1, var19, 64.0F, 64.0F, var2, tmp);
         Draw.color(GLOW[var19], 0.5F * var18 * var17);
         Draw.rect(this.wingRegions[var19], tmp[0], tmp[1], var4 + var20);
         Draw.color(GLOW[var19], 0.14F * var18 * var17);
         Draw.rect(this.wingRegions[var19], tmp[0], tmp[1], 36.0F, 36.0F, var4 + var20);
      }

      Draw.blend();
      Draw.reset();
   }

   /** World position of a wing sprite pixel (128x128, y down) with the wing folded by (1 - p). */
   static void wingPoint(Unit var0, int var1, float var2, float var3, float var4, float[] var5) {
      float var6 = var0.rotation - 90.0F;
      float var7 = Mathf.cosDeg(var6);
      float var8 = Mathf.sinDeg(var6);
      float var9 = (WINGS[var1][0] - 64.0F) / 4.0F;
      float var10 = (64.0F - WINGS[var1][1]) / 4.0F;
      float var11 = WINGS[var1][2] * 36.0F * (1.0F - var4);
      float var12 = Mathf.cosDeg(var11);
      float var13 = Mathf.sinDeg(var11);
      float var14 = (var2 - 64.0F) / 4.0F - var9;
      float var15 = (64.0F - var3) / 4.0F - var10;
      float var16 = var9 + var14 * var12 - var15 * var13;
      float var17 = var10 + var14 * var13 + var15 * var12;
      var5[0] = var0.x + var16 * var7 - var17 * var8;
      var5[1] = var0.y + var16 * var8 + var17 * var7;
   }

   void drawTrails(Unit var1, float var2) {
      float[] var3 = (float[])trails.get(var1);
      if (var3 != null && !(var3[1] < 2.0F)) {
         float var4 = var1.elevation > 0.5F ? (this.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : this.groundLayer;
         Draw.z(var4 - 0.035F);
         Draw.blend(Blending.additive);
         int var5 = (int)var3[0];
         int var6 = (int)var3[1];

         for(int var7 = 0; var7 < 4; ++var7) {
            for(int var8 = 0; var8 < var6 - 1; ++var8) {
               int var9 = ((var5 - var8) % TRAIL_LEN + TRAIL_LEN) % TRAIL_LEN;
               int var10 = ((var5 - var8 - 1) % TRAIL_LEN + TRAIL_LEN) % TRAIL_LEN;
               float var11 = 1.0F - ((float)var8 + 0.5F) / (float)TRAIL_LEN;
               Draw.color(TRAIL[var7], var11 * var11 * 0.9F * var2);
               Lines.stroke(0.3F + 0.9F * var11);
               Lines.line(var3[2 + (var7 * TRAIL_LEN + var9) * 2], var3[3 + (var7 * TRAIL_LEN + var9) * 2], var3[2 + (var7 * TRAIL_LEN + var10) * 2], var3[3 + (var7 * TRAIL_LEN + var10) * 2]);
            }
         }

         Draw.blend();
         Draw.reset();
      }
   }
}
