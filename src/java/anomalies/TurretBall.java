package anomalies;

import arc.graphics.Blending;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import java.util.ArrayList;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Entityc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;

public class TurretBall extends BallLightningAnomaly {
   static final float SPEED = 2.2F;
   static final float LIFE = 150.0F;
   static final float STRIKE_DAMAGE = 150.0F;
   static final float STRIKE_INTERVAL = 15.0F;
   static final float UNIT_RADIUS = 24.0F;
   static final int BUILD_RADIUS = 2;
   static final int MAX_TARGETS = 3;
   public Team team;
   float vx;
   float vy;
   float ox;
   float oy;
   float orad;

   public TurretBall() {
      this(1.0F);
   }

   public TurretBall(float var1) {
      super(var1);
      this.cd = 0.0F;
   }

   public void launch(float var1, Team var2) {
      this.team = var2;
      this.rotation = var1;
      this.vx = Mathf.cosDeg(var1) * 2.2F;
      this.vy = Mathf.sinDeg(var1) * 2.2F;
   }

   public float fade() {
      return Mathf.clamp(Math.min(this.age, this.life - this.age) / 8.0F);
   }

   public void update() {
      for(int var4 = 0; var4 < 9; ++var4) {
         if (this.st[var4] > 0.0F) {
            this.st[var4] -= Time.delta;
         }
      }

      this.act = Math.max(0.0F, this.act - Time.delta / 60.0F);
      this.x += this.vx * Time.delta;
      this.y += this.vy * Time.delta;
      if (this.orad > 0.0F && Mathf.dst(this.x, this.y, this.ox, this.oy) > this.orad) {
         this.dead = true;
      } else {
         float var14 = 160.0F;
         if (!(this.x < -var14) && !(this.y < -var14) && !(this.x > (float)Vars.world.width() * 8.0F + var14) && !(this.y > (float)Vars.world.height() * 8.0F + var14)) {
            this.cd -= Time.delta;
            if (!(this.cd > 0.0F)) {
               Team var5 = this.team;
               ArrayList var6 = new ArrayList();
               Units.nearby(this.x - 24.0F, this.y - 24.0F, 48.0F, 48.0F, (var2x) -> {
                  if (var2x.isValid() && var2x.team != var5) {
                     var6.add(var2x);
                  }

               });
               int var7 = Math.round(this.x / 8.0F);
               int var8 = Math.round(this.y / 8.0F);

               for(int var3 = -2; var3 <= 2; ++var3) {
                  for(int var2 = -2; var2 <= 2; ++var2) {
                     Building var9 = Vars.world.build(var7 + var3, var8 + var2);
                     if (var9 != null && var9.team != var5 && var9.team != Team.derelict && !var6.contains(var9)) {
                        var6.add(var9);
                     }
                  }
               }

               if (var6.isEmpty()) {
                  this.cd = 3.0F;
               } else {
                  this.cd = 15.0F;
                  this.act = 1.0F;
                  int var13 = Math.min(var6.size(), 3);

                  for(int var12 = 0; var12 < var13; ++var12) {
                     Object var1 = var6.remove(Mathf.random(var6.size() - 1));
                     float var10;
                     float var15;
                     if (var1 instanceof Building) {
                        Building var11 = (Building)var1;
                        var10 = var11.x;
                        var15 = var11.y;
                        AlloyDmg.hit(var11, 150.0F);
                     } else {
                        Unit var16 = (Unit)var1;
                        var10 = var16.x;
                        var15 = var16.y;
                        AlloyDmg.hit(var16, 150.0F);
                     }

                     this.sx[var12] = var10;
                     this.sy[var12] = var15;
                     this.st[var12] = 14.0F;
                     this.sd[var12] = this.salt += 977;
                  }

               }
            }
         } else {
            this.dead = true;
         }
      }
   }

   public void draw() {
      float var2 = this.fade();
      float var3 = Time.time;
      float var4 = 0.8F + 0.2F * (float)Math.sin((double)(var3 * 0.3F));
      float var5 = this.x;
      float var6 = this.y;
      float var7 = 1.0F - this.act;
      int var8 = (int)(var3 / (this.act > 0.5F ? 3.0F : 9.0F));
      float var9 = 0.7F + 0.3F * var7 + (0.3F - 0.3F * var7) * hash(var8 * 7 + 1);
      float var10 = 0.45F + 0.55F * this.act;
      Draw.blend(Blending.additive);
      Lines.stroke(1.0F);
      float var11 = 0.9F + 0.1F * (float)Math.sin((double)(var3 * 0.08F));
      Draw.color(glow, (0.15F + 0.15F * this.act) * var2 * var4);
      Fill.circle(var5, var6, 6.5F * var11 * this.s);
      Draw.color(body, (0.4F + 0.3F * this.act) * var2);
      Fill.circle(var5, var6, 4.0F * var11 * this.s);
      Draw.color(core, (0.75F + 0.25F * this.act) * var2);
      Fill.circle(var5, var6, 2.2F * var11 * this.s);

      for(int var1 = 0; var1 < 6; ++var1) {
         int var13 = var8 * 31 + var1 * 211;
         if (!(hash(var13) < 0.25F)) {
            float var12 = var1 < 2 ? 1.0F : this.act;
            if (!(var12 < 0.05F)) {
               float var15 = hash(var13 + 1) * ((float)Math.PI * 2F);
               float var16 = (9.0F + hash(var13 + 2) * 11.0F) * (0.6F + 0.4F * this.act) * this.s;
               this.layered(var5, var6, var5 + (float)Math.cos((double)var15) * var16, var6 + (float)Math.sin((double)var15) * var16, var13 + 5, 3.5F, var2 * var9 * var10 * var12);
            }
         }
      }

      for(int var17 = 0; var17 < 9; ++var17) {
         if (!(this.st[var17] <= 0.0F)) {
            float var18 = this.st[var17] / 14.0F;
            int var19 = this.sd[var17] + var8 * 13;
            this.layered(var5, var6, this.sx[var17], this.sy[var17], var19, 6.0F * this.s, var2 * var9 * Math.min(1.0F, var18 * 1.5F));
            Draw.color(glow, 0.5F * var18 * var2);
            Fill.circle(this.sx[var17], this.sy[var17], 6.0F * var18);
            Draw.color(core, var18 * var2);
            Fill.circle(this.sx[var17], this.sy[var17], 2.5F * var18);
         }
      }

      Draw.blend();
      Drawf.light(var5, var6, 30.0F * this.s, glow, (0.35F + 0.25F * this.act) * var2);
      Draw.reset();
   }

   public void removed() {
      super.removed();
      BallBurstFx.play(this.x, this.y);
   }

   void origin(Entityc var1, float var2, float var3) {
      this.ox = var2;
      this.oy = var3;
      this.orad = 120.0F;
      if (var1 instanceof Building) {
         Building var4 = (Building)var1;
         this.ox = var4.x;
         this.oy = var4.y;
         if (var4.block instanceof AnomalyTurret) {
            this.orad = ((AnomalyTurret)var4.block).range;
         }
      }

   }
}
