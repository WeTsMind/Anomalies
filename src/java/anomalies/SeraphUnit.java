package anomalies;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import mindustry.content.Fx;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

public class SeraphUnit extends UnitType {
   // wing root in sprite pixels (128x128, y down) and fold side (+1 left, -1 right)
   static final float[][] WINGS = new float[][]{{49.0F, 38.0F, 1.0F}, {78.5F, 38.0F, -1.0F}, {54.5F, 66.0F, 1.0F}, {72.5F, 66.0F, -1.0F}};
   static final float FOLD = 36.0F;
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
      this.accel = 0.08F;
      this.drag = 0.04F;
      this.hitSize = 12.0F;
      this.rotateSpeed = 15.0F;
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

   public void draw(Unit var1) {
      float var2 = SeraphAnim.age(var1);
      if (!(var2 < 24.0F)) {
         this.drawWings(var1, SeraphAnim.wings(var2));
         super.draw(var1);
      }
   }

   void drawWings(Unit var1, float var2) {
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
         Draw.rect(this.wingRegions[var7], var1.x + var15 * var5 - var16 * var6, var1.y + var15 * var6 + var16 * var5, var4 + var10);
      }

      Draw.reset();
   }
}
