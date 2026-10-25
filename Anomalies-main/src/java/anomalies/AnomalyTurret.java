package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.meta.BuildVisibility;

public class AnomalyTurret extends PowerTurret {
   static final Color cGlow = Color.valueOf("ffd21f");
   static final Color cBody = Color.valueOf("ffe95e");
   static final Color cCore = Color.valueOf("fff8d0");
   static final float glowBack = 2.5F;
   static final float FLASH = 26.0F;
   static final float[] BUMP_F = new float[]{-9.0F, -9.0F, -9.25F, -7.5F, -7.5F, -4.25F, -4.25F};
   static final float[] BUMP_R = new float[]{-4.0F, 4.0F, 0.0F, -5.375F, 5.375F, -5.125F, 5.125F};
   static final BallLightningAnomaly fx = new BallLightningAnomaly();
   TextureRegion glowRegion;

   static float tx(float var0, float var1) {
      return (float)Math.cos(Math.toRadians((double)var0)) * var1;
   }

   static float ty(float var0, float var1) {
      return (float)Math.sin(Math.toRadians((double)var0)) * var1;
   }

   public AnomalyTurret(String var1) {
      super(var1);
      this.size = 2;
      this.health = 900;
      this.requirements(Category.turret, BuildVisibility.shown, ItemStack.with(new Object[]{Items.silicon, 120, Items.titanium, 80, Items.graphite, 60}));
      this.consumePower(1.0F);
      this.reload = 150.0F;
      this.range = 120.0F;
      this.minRange = 56.0F;
      this.rotateSpeed = 4.0F;
      this.recoil = 1.25F;
      this.shootY = 8.0F;
      this.targetAir = true;
      this.targetGround = true;
      this.shootType = new OrbShot();
      this.buildType = () -> new AnomalyBuild();
   }

   public void load() {
      super.load();
      this.glowRegion = Core.atlas.find(this.name + "-glow");
      TurretPatch.foreshadowSound(this);
   }

   static class OrbShot extends BulletType {
      OrbShot() {
         super(2.2F, 0.0F);
         this.lifetime = 1.0F;
         this.collides = false;
         this.collidesTiles = false;
         this.collidesAir = false;
         this.collidesGround = false;
         this.absorbable = false;
         this.hittable = false;
         this.reflectable = false;
         this.scaleLife = false;
         this.shootEffect = Fx.none;
         this.smokeEffect = Fx.none;
         this.hitEffect = Fx.none;
         this.despawnEffect = Fx.none;
      }

      public void draw(Bullet var1) {
      }

      public void despawned(Bullet var1) {
         super.despawned(var1);
         if (!Vars.net.client()) {
            TurretBall var2 = new TurretBall();
            var2.launch(var1.rotation(), var1.team);
            AnomalyManager.place(var2, var1.x, var1.y);
            var2.age = 0.0F;
            var2.life = 50.0F;
            var2.origin(var1.owner, var1.x, var1.y);
         }
      }
   }

   public class AnomalyBuild extends PowerTurret.PowerTurretBuild {
      float lastHeat;
      float glowFade;
      float shotTime = -100000.0F;

      public AnomalyBuild() {
         super();
      }

      public void draw() {
         this.drawNoShadow();
         if (this.heat - this.lastHeat > 0.5F) {
            this.shotTime = Time.time;
         }

         this.lastHeat = this.heat;
         float var1 = Time.time - this.shotTime;
         float var2 = (float)Math.pow((double)Mathf.clamp(1.0F - var1 / AnomalyTurret.this.reload), (double)1.8F) * AnomalyTurret.this.recoil;
         float var3 = this.x - AnomalyTurret.tx(this.rotation, 2.5F + var2);
         float var4 = this.y - AnomalyTurret.ty(this.rotation, 2.5F + var2);
         float var5 = 0.5F + 0.5F * (float)Math.sin((double)(Time.time / 18.0F));
         float var6 = Math.min(1.0F, 0.35F + 0.4F * Math.min(1.0F, this.efficiency) + 0.1F * var5 + 0.4F * this.heat);
         // the core glows only while the turret is supplied with power
         this.glowFade = Mathf.lerpDelta(this.glowFade, this.power == null ? 0.0F : Mathf.clamp(this.power.status), 0.03F);
         float powered = this.glowFade * this.glowFade * (3.0F - 2.0F * this.glowFade);
         var6 *= powered;
         Draw.z(51.0F);
         Draw.blend(Blending.additive);
         if (powered > 0.01F) {
            Draw.alpha(var6);
            float var7 = 28.0F * (1.0F + 0.06F * this.heat);
            Draw.rect(AnomalyTurret.this.glowRegion, var3, var4, var7, var7, Time.time * 0.5F);
         }
         float var8 = TurretPatch.begin(this, var1);
         if (var8 > 0.0F) {
            int var9 = (int)(Time.time / 3.0F);
            float var10 = Mathf.cosDeg(this.rotation);
            float var11 = Mathf.sinDeg(this.rotation);

            for(int var12 = 0; var12 < BUMP_F.length; ++var12) {
               float var13 = TurretPatch.arcBegin(var12) - var2;
               float var14 = AnomalyTurret.BUMP_R[var12];
               float var15 = this.x + var10 * var13 + var11 * var14;
               float var16 = this.y + var11 * var13 - var10 * var14;
               float var17 = this.rotation + (float)Math.toDegrees(Math.atan2((double)(-AnomalyTurret.BUMP_R[var12]), (double)AnomalyTurret.BUMP_F[var12]));
               Draw.color(BallLightningAnomaly.glow, TurretPatch.mulArc(0.55F));
               float var10002 = 1.0F + TurretPatch.mulArc(1.2F);
               Draw.color(BallLightningAnomaly.body, TurretPatch.mulArc(0.8F));
               TurretPatch.mulArc(1.0F);
               TurretPatch.colorArc(BallLightningAnomaly.core);
               TurretPatch.mulArc(0.5F);

               for(int var18 = 0; var18 < 3; ++var18) {
                  int var19 = var9 * 31 + var12 * 977 + var18 * 211;
                  if (!(TurretPatch.sparkHash(var19) < 0.2F)) {
                     float var20 = var17 + (BallLightningAnomaly.hash(var19 + 1) - 0.5F) * 150.0F;
                     float var21 = (8.0F + BallLightningAnomaly.hash(var19 + 2) * 10.0F) * (0.55F + TurretPatch.mulFade(0.45F));
                     TurretPatch.layeredFade(AnomalyTurret.fx, var15, var16, var15 + Mathf.cosDeg(var20) * var21, var16 + Mathf.sinDeg(var20) * var21, var19 + 5, 2.8F);
                  }
               }
            }
         }

         Draw.blend();
         Draw.reset();
      }

      public void drawNoShadow() {
         TurretPatch.shadowOff();
         super.draw();
         TurretPatch.shadowOn();
      }
   }
}
