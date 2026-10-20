package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.entities.Leg;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.LegsUnit;
import mindustry.gen.Legsc;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.blocks.defense.turrets.Turret;

public class AlloyUnit extends UnitType {
   static final Color glow = Color.valueOf("ffd21f");
   static final Color body = Color.valueOf("ffe95e");
   static final Color core = Color.valueOf("fff8d0");
   static final float CY = 3.0F;
   static final float PY = -4.5F;
   static final float RELOAD = 180.0F;
   static final float SPEED = 1.0666667F;
   static final float DIST = -1.5344418F;
   static final float HALF = 12.0F;
   static final Color cyan = Color.valueOf("58FFF9");
   static final float RANGE = 80.0F;
   static final Map<Unit, MirrorAnomaly[]> mirrors = new WeakHashMap();
   static final Set<Anomaly> alloyMirrors = Collections.newSetFromMap(new WeakHashMap());
   static final float ARM_X = 12.0F;
   static final float ARM_Y = 11.0F;
   TextureRegion proj;

   public AlloyUnit(String var1) {
      super(var1);
      this.constructor = LegsUnit::create;
      this.alwaysUnlocked = true;
      this.health = 1800.0F;
      this.armor = 4.0F;
      this.speed = 0.5F;
      this.hitSize = 18.0F;
      this.rotateSpeed = 4.0F;
      this.drag = 0.11F;
      this.accel = 0.2F;
      this.hovering = true;
      this.allowLegStep = true;
      this.lockLegBase = true;
      this.legCount = 6;
      this.legGroupSize = 3;
      this.legLength = 17.0F;
      this.legBaseOffset = 5.0F;
      this.legMoveSpace = 1.0F;
      this.legForwardScl = 0.7F;
      this.legContinuousMove = true;
      this.legStraightness = 0.25F;
      this.legMaxLength = 1.1F;
      this.legMinLength = 0.2F;
      this.legLengthScl = 0.96F;
      this.outlines = false;
      this.drawCell = false;
      this.targetAir = true;
      this.range = 80.0F;
      this.maxRange = 80.0F;
      Weapon var2 = new Weapon("anomalies-alloy-launcher");
      var2.x = 0.0F;
      var2.y = 3.0F;
      var2.mirror = false;
      var2.rotate = false;
      var2.shootCone = 8.0F;
      var2.reload = 180.0F;
      var2.recoil = 0.0F;
      var2.shootY = 0.0F;
      var2.bullet = new AlloyShot();

      try {
         if (Blocks.foreshadow instanceof Turret) {
            var2.shootSound = ((Turret)Blocks.foreshadow).shootSound;
         }
      } catch (Throwable var4) {
      }

      this.weapons.add(var2);
   }

   public void load() {
      super.load();
      this.proj = Core.atlas.find(this.name + "-projector");
   }

   public void update(Unit var1) {
      super.update(var1);
      if (!Vars.net.client()) {
         float var2 = var1.rotation;
         float var3 = Mathf.cosDeg(var2);
         float var4 = Mathf.sinDeg(var2);
         float var5 = -var4;
         float var6 = var3;
         float var7 = var1.x + var3 * -1.5344418F;
         float var8 = var1.y + var4 * -1.5344418F;
         var7 += -var3 * recoil(var1);
         var8 += -var4 * recoil(var1);
         MirrorAnomaly[] var9 = (MirrorAnomaly[])mirrors.get(var1);
         if (var9 == null) {
            var9 = new MirrorAnomaly[3];
            mirrors.put(var1, var9);
         }

         float[][] var10 = new float[][]{{var7, var8, var5 * 26.0F, var3 * 26.0F}, null, null};

         for(int var11 = 0; var11 < 2; ++var11) {
            float var12 = var11 == 0 ? 1.0F : -1.0F;
            float var13 = var7 + var5 * 12.0F * var12;
            float var14 = var8 + var6 * 12.0F * var12;
            float var15 = var5 * 12.0F * var12 - var3 * 11.0F;
            float var16 = var6 * 12.0F * var12 - var4 * 11.0F;
            var10[var11 + 1] = new float[]{var13 + var15 / 2.0F, var14 + var16 / 2.0F, var15 / 2.0F, var16 / 2.0F};
         }

         for(int var19 = 0; var19 < 1; ++var19) {
            float var20 = Mathf.len(var10[var19][2], var10[var19][3]);
            MirrorAnomaly var21 = var9[var19];
            if (var21 == null || ((MirrorAnomaly)var21).dead || ((MirrorAnomaly)var21).age >= ((MirrorAnomaly)var21).life) {
               var21 = new AlloyArcMirror(5.0F, var20);
               ((MirrorAnomaly)var21).team = var1.team;
               ((MirrorAnomaly)var21).dropped = true;
               smooth((MirrorAnomaly)var21);
               ((MirrorAnomaly)var21).age = 0.0F;
               ((MirrorAnomaly)var21).life = 120.0F;
               AnomalyManager.list.add(var21);
               ((MirrorAnomaly)var21).spawned();
               alloyMirrors.add(var21);
               var9[var19] = (MirrorAnomaly)var21;
            }

            ((MirrorAnomaly)var21).x = var10[var19][0];
            ((MirrorAnomaly)var21).y = var10[var19][1];
            ((MirrorAnomaly)var21).rotation = (float)Math.toDegrees(Math.atan2((double)(-var10[var19][2]), (double)var10[var19][3]));
            ((MirrorAnomaly)var21).life = ((MirrorAnomaly)var21).age + 90.0F;
         }

      }
   }

   static void smooth(MirrorAnomaly var0) {
      for(int var1 = 0; var1 <= 14; ++var1) {
         float var2 = (float)var1 / 14.0F;
         float var3 = var0.hw * Mathf.sqrt(Math.max(0.0F, 1.0F - (2.0F * var2 - 1.0F) * (2.0F * var2 - 1.0F)));
         var0.lw[var1] = var3;
         var0.rw[var1] = var3;
         var0.cj[var1] = 0.0F;
      }

      for(int var4 = 0; var4 < 6; ++var4) {
         var0.mx[var4] = 0.0F;
      }

      for(int var5 = 0; var5 < 2; ++var5) {
         var0.bx[var5][1] = var0.bx[var5][2] = var0.bx[var5][0] = 0.0F;
         var0.by[var5][1] = var0.by[var5][2] = var0.by[var5][0];
      }

   }

   public void draw(Unit var1) {
      float var2 = 0.0F;
      if (var1.mounts.length > 0) {
         float var3 = 180.0F - var1.mounts[0].reload;
         if (var3 < 1.5F) {
            var2 = 4.5F * var3 / 1.5F;
         } else {
            float var4 = Math.max(0.0F, 1.0F - (var3 - 1.5F) / 50.0F);
            var2 = 4.5F * var4 * var4 * (3.0F - 2.0F * var4);
         }
      }

      if (var2 <= 0.01F) {
         this.drawAll(var1);
      } else {
         float var18 = -Mathf.cosDeg(var1.rotation) * var2;
         float var19 = -Mathf.sinDeg(var1.rotation) * var2;
         Leg[] var5 = var1 instanceof Legsc ? ((Legsc)var1).legs() : null;
         var1.x += var18;
         var1.y += var19;
         if (var5 != null) {
            for(Leg var9 : var5) {
               var9.joint.add(var18 / 2.0F, var19 / 2.0F);
            }
         }

         boolean var16 = false;

         try {
            var16 = true;
            this.drawAll(var1);
            var16 = false;
         } finally {
            if (var16) {
               var1.x -= var18;
               var1.y -= var19;
               if (var5 != null) {
                  for(Leg var14 : var5) {
                     var14.joint.sub(var18 / 2.0F, var19 / 2.0F);
                  }
               }

            }
         }

         var1.x -= var18;
         var1.y -= var19;
         if (var5 != null) {
            for(Leg var23 : var5) {
               var23.joint.sub(var18 / 2.0F, var19 / 2.0F);
            }
         }

      }
   }

   void drawAll(Unit var1) {
      super.draw(var1);
      float var2 = var1.rotation;
      float var3 = var1.x + Mathf.cosDeg(var2) * -4.5F;
      float var4 = var1.y + Mathf.sinDeg(var2) * -4.5F;
      float var5 = var1.x + Mathf.cosDeg(var2) * 5.1042113F;
      float var6 = var1.y + Mathf.sinDeg(var2) * 5.1042113F;
      float var7 = 0.75F + 0.25F * Mathf.sin(Time.time * 0.5F);
      Draw.z(74.5F);
      Draw.blend(Blending.additive);

      for(int var8 = 0; var8 < 2; ++var8) {
         float var9 = var8 == 0 ? 1.0F : -1.0F;
         float var10 = var5 - Mathf.sinDeg(var2) * 21.43183F * var9;
         float var11 = var6 + Mathf.cosDeg(var2) * 21.43183F * var9;

         for(int var12 = 0; var12 < 2; ++var12) {
            Lines.stroke(var12 == 0 ? 3.2F : 1.2F);
            Draw.color(cyan, (var12 == 0 ? 0.3F : 0.95F) * var7);
            Lines.line(var3, var4, var10, var11);
         }

         Draw.color(cyan, 0.4F * var7);
         Fill.circle(var10, var11, 3.2F);
         Draw.color(cyan, 1.0F);
         Fill.circle(var10, var11, 1.4F);
      }

      Draw.blend();
      Draw.z(74.0F);
      Draw.reset();
      if (this.proj != null && this.proj.found()) {
         Draw.rect(this.proj, var1.x + Mathf.cosDeg(var2) * -4.5F, var1.y + Mathf.sinDeg(var2) * -4.5F, var2 - 90.0F);
      }

      float var13 = 1.0F;
      if (var1.mounts.length > 0) {
         var13 = Mathf.clamp(1.0F - var1.mounts[0].reload / 180.0F);
      }

      if (var13 > 0.02F) {
         float var14 = var1.x + Mathf.cosDeg(var2) * 3.0F;
         float var15 = var1.y + Mathf.sinDeg(var2) * 3.0F;
         float var16 = 0.35F * AlphaSettings.scale() * var13;
         float var17 = 0.9F + 0.1F * Mathf.sin(Time.time * 0.3F);
         Draw.z(77.0F);
         Draw.blend(Blending.additive);
         Draw.color(body, 0.55F * var13);
         Fill.circle(var14, var15, 4.0F * var17 * var16);
         Draw.color(core, 0.9F * var13);
         Fill.circle(var14, var15, 2.2F * var17 * var16);
         Draw.blend();
      }

      Draw.reset();
   }

   static float recoil(Unit var0) {
      if (var0.mounts.length <= 0) {
         return 0.0F;
      } else {
         float var1 = 180.0F - var0.mounts[0].reload;
         if (var1 < 1.5F) {
            return 4.5F * var1 / 1.5F;
         } else {
            float var2 = Math.max(0.0F, 1.0F - (var1 - 1.5F) / 50.0F);
            return 4.5F * var2 * var2 * (3.0F - 2.0F * var2);
         }
      }
   }

   static class AlloyShot extends BulletType {
      AlloyShot() {
         super(0.01F, 0.0F);
         this.lifetime = 1.0F;
         this.collides = false;
         this.collidesTiles = false;
         this.collidesAir = false;
         this.collidesGround = false;
         this.hittable = false;
         this.absorbable = false;
         this.keepVelocity = false;
         this.rangeOverride = 80.0F;
         this.shootEffect = Fx.none;
         this.smokeEffect = Fx.none;
         this.hitEffect = Fx.none;
         this.despawnEffect = Fx.none;
      }

      public void init(Bullet var1) {
         super.init(var1);
         AnomalyManager.reflected.add(var1.id);
      }

      public void draw(Bullet var1) {
      }

      public void despawned(Bullet var1) {
         super.despawned(var1);
         if (!Vars.net.client()) {
            float var2 = var1.rotation();
            AlloyBall var3 = new AlloyBall(AlphaSettings.scale());
            ((TurretBall)var3).launch(var2, var1.team);
            var3.vx = Mathf.cosDeg(var2) * 2.0F;
            var3.vy = Mathf.sinDeg(var2) * 2.0F;
            AnomalyManager.place(var3, var1.x, var1.y);
            var3.age = 0.0F;
            var3.life = 1500.0F;
            var3.ox = var1.x;
            var3.oy = var1.y;
            var3.orad = 160.0F;
         }
      }
   }
}
