package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import java.util.Map;
import java.util.WeakHashMap;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.core.Renderer;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.blocks.storage.CoreBlock;

public class SeraphUnit extends UnitType {
   // wing root in sprite pixels (128x128, y down) and fold side (+1 left, -1 right)
   static final float[][] WINGS = new float[][]{{49.0F, 38.0F, 1.0F}, {78.5F, 38.0F, -1.0F}, {54.5F, 53.0F, 1.0F}, {72.5F, 53.0F, -1.0F}};
   static final float FOLD = 36.0F;
   // wing tips (sprite px), glow tint and trail colour: front (big) wings red, rear (small) wings gold
   static final float[][] TIPS = new float[][]{{29.0F, 75.0F}, {98.0F, 75.0F}, {39.0F, 82.0F}, {88.0F, 82.0F}};
   // bright, glowing coral / gold (same hues as the wing sprites, just lighter)
   static final Color NEON_RED = Color.valueOf("ff9a84");
   static final Color NEON_GOLD = Color.valueOf("ffe08a");
   // trail colours sampled from the wing sprites: front wings ff4f41 / ff9967, rear wings ff9400 / ffb857
   static final Color TRAIL_RED = Color.valueOf("ff4f41");
   static final Color TRAIL_GOLD = Color.valueOf("ff9400");
   static final Color HEAD_RED = Color.valueOf("ff9967");
   static final Color HEAD_GOLD = Color.valueOf("ffb857");
   static final Color ENGINE = Color.valueOf("ff5a2a");
   static final Color[] GLOW = new Color[]{NEON_RED, NEON_RED, NEON_GOLD, NEON_GOLD};
   static final Color[] TRAIL = new Color[]{TRAIL_RED, TRAIL_RED, TRAIL_GOLD, TRAIL_GOLD};
   static final Color[] HEAD = new Color[]{HEAD_RED, HEAD_RED, HEAD_GOLD, HEAD_GOLD};
   /** half width of the ribbon at the wing tip, world units (thin) */
   static final float TRAIL_HALF = 0.3F;
   static final int TRAIL_LEN = 34;
   static final Map<Unit, WingTrail[]> trails = new WeakHashMap();
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
      // copper (hardness 1), lead (1) and scrap (0) are all within tier 1
      this.mineItems = Seq.with(Items.copper, Items.lead, Items.scrap);
      this.mineFloor = true;
      this.mineWalls = false;
      this.itemCapacity = 30;
      this.engineOffset = 1.5F; // flame starts right under the cell, not in the air behind the body
      this.engineSize = 1.4F;
      // sharp engine trail: the game's own tapered trail behind the rear flame
      this.engineColor = ENGINE;
      this.trailColor = ENGINE;
      this.trailLength = 14;
      this.trailScl = 0.5F;
      this.outlines = false;
      // the purple laser: one pulse every 6 ticks while fire is held, see SeraphBeam
      this.weapons.add(new Weapon() {
         {
            this.reload = 6.0F;
            this.x = 0.0F;
            this.y = 8.0F;
            this.mirror = false;
            this.top = false;
            this.rotate = true;
            this.shootSound = Sounds.none;
            this.ejectEffect = Fx.none;
            this.bullet = new SeraphBeam();
         }
      });
   }

   /** Brighter, more saturated version of a team colour (the cell colour). */
   static Color vivid(mindustry.game.Team t) {
      Color v = Tmp.c2.set(t.color);
      v.saturation(Math.min(1.0F, v.saturation() * 1.4F + 0.25F));
      v.value(1.0F);
      return v;
   }

   /** The cell next to the Seraph glows in a more saturated, brighter version of the team colour. */
   @Override
   public Color cellColor(Unit unit) {
      float f = Mathf.clamp(unit.healthf());
      Color vivid = Tmp.c2.set(unit.team.color);
      vivid.saturation(Math.min(1.0F, vivid.saturation() * 1.4F + 0.25F));
      vivid.value(1.0F);
      float k = Mathf.clamp(0.3F + 0.7F * (f + Mathf.absin(Time.time, Math.max(f * 5.0F, 1.0F), 1.0F - f)));
      return Tmp.c1.set(Color.black).lerp(vivid, k);
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
      // while the lids open the Seraph sits inside the core (the core draws it); then it flies out
      float var0 = SeraphAnim.age(var1);
      if (var0 < SeraphAnim.OPEN_END) {
         CoreBlock.CoreBuild var9 = var1.closestCore();
         if (var9 != null) var1.set(var9.x, var9.y);
         var1.vel.setZero();
         var1.isShooting(false);
      } else if (var0 < 1.0E8F && SeraphAnim.released.add(var1)) {
         var1.impulse(0.0F, 3.0F);
      }

      if (!Vars.headless) {
         float var2 = SeraphAnim.age(var1);
         WingTrail[] var3 = trails.get(var1);
         if (var3 == null) {
            var3 = new WingTrail[4];
            for(int var6 = 0; var6 < 4; ++var6) {
               var3[var6] = new WingTrail(TRAIL_LEN);
            }

            trails.put(var1, var3);
         }

         if (var2 < 36.0F) {
            for(int var6 = 0; var6 < 4; ++var6) {
               var3[var6].clear();
            }
         } else {
            float var4 = SeraphAnim.wings(var2);

            // one thin ribbon per wing (mitred joints, see WingTrail), so there are no visible seams between segments
            for(int var6 = 0; var6 < 4; ++var6) {
               wingPoint(var1, var6, TIPS[var6][0], TIPS[var6][1], var4, tmp);
               var3[var6].add(tmp[0], tmp[1]);
            }

         }
      }
   }

   public void draw(Unit var1) {
      float var2 = SeraphAnim.age(var1);
      if (var2 < SeraphAnim.OPEN_END) return;
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
         Draw.blend(Blending.additive);
         Draw.color(Color.white, 0.55F * var17);
         Draw.rect(this.wingRegions[var7], var1.x + var15 * var5 - var16 * var6, var1.y + var15 * var6 + var16 * var5, var4 + var10);
      }

      Draw.blend(Blending.additive);
      float var18 = 1.0F; // constant wing light, no pulsing

      for(int var19 = 0; var19 < 4; ++var19) {
         float var20 = WINGS[var19][2] * 36.0F * (1.0F - var2);
         wingPoint(var1, var19, 64.0F, 64.0F, var2, tmp);
         Draw.color(GLOW[var19], 0.8F * var18 * var17);
         Draw.rect(this.wingRegions[var19], tmp[0], tmp[1], var4 + var20);
         Draw.color(GLOW[var19], 0.32F * var18 * var17);
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

   /** The mining beam is a glowing purple laser (same palette as the Laser Block) instead of the default grey one. */
   @Override
   public void drawMiningBeam(Unit unit, float px, float py) {
      if (!unit.mining()) return;
      float ex = unit.mineTile.worldx() + Mathf.sin(Time.time + 48.0F, 12.0F, 1.0F);
      float ey = unit.mineTile.worldy() + Mathf.sin(Time.time + 48.0F, 14.0F, 1.0F);
      float a = Renderer.unitLaserOpacity;
      float pulse = 0.85F + 0.15F * Mathf.sin(Time.time / 4.0F);

      Draw.z(Layer.flyingUnit + 0.1F);
      Draw.blend(Blending.additive);
      Lines.stroke(6.0F * pulse);
      Draw.color(LaserBlock.col, 0.14F * a);
      Lines.line(px, py, ex, ey);
      Lines.stroke(3.4F * pulse);
      Draw.color(LaserBlock.col, 0.35F * a);
      Lines.line(px, py, ex, ey);
      Lines.stroke(1.4F);
      Draw.color(LaserBlock.hot, 0.95F * a);
      Lines.line(px, py, ex, ey);

      // glow at both ends and sparks where the beam burns the ore
      Fill.light(ex, ey, 18, 8.0F * pulse, Tmp.c1.set(LaserBlock.col).a(0.6F * a), Tmp.c2.set(LaserBlock.col).a(0.0F));
      Fill.light(px, py, 14, 5.0F * pulse, Tmp.c1.set(LaserBlock.col).a(0.5F * a), Tmp.c2.set(LaserBlock.col).a(0.0F));
      Mathf.randomSeed((long)unit.id * 7L + (long)(Time.time / 3.0F));
      Lines.stroke(1.0F);
      Draw.color(LaserBlock.hot, 0.9F * a);
      for(int i = 0; i < 4; ++i) {
         Lines.lineAngle(ex, ey, Mathf.random(360.0F), Mathf.random(1.5F, 5.5F));
      }

      Draw.blend();
      Drawf.light(px, py, ex, ey, 26.0F, LaserBlock.col, 0.5F * a);

      if (unit.isLocal()) {
         Lines.stroke(1.0F, Pal.accent);
         Lines.poly(unit.mineTile.worldx(), unit.mineTile.worldy(), 4, 4.0F * Mathf.sqrt2, Time.time);
      }

      Draw.reset();
   }

   void drawTrails(Unit var1, float var2) {
      WingTrail[] var3 = trails.get(var1);
      if (var3 != null) {
         float var4 = var1.elevation > 0.5F ? (this.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : this.groundLayer;
         Draw.z(var4 - 0.035F);
         Draw.blend(Blending.additive);

         for(int var7 = 0; var7 < 4; ++var7) {
            var3[var7].draw(TRAIL[var7], HEAD[var7], TRAIL_HALF, var2);
            var3[var7].draw(HEAD[var7], NEON_GOLD, TRAIL_HALF * 0.5F, var2); // bright thin core
         }

         Draw.blend();
         Draw.reset();
      }
   }

   /** Fixed-length point history drawn as ONE ribbon: neighbouring quads share the same mitred edge, so there are no gaps or double-bright overlaps. */
   static class WingTrail {
      final float[] pts;
      int n;

      WingTrail(int len) {
         pts = new float[len * 2];
      }

      void clear() {
         n = 0;
      }

      int size() {
         return n;
      }

      void add(float x, float y) {
         int max = pts.length / 2;
         if (n > 0) {
            float dx = x - pts[(n - 1) * 2], dy = y - pts[(n - 1) * 2 + 1];
            if (dx * dx + dy * dy < 0.0004F) return; // standing still: do not pile up points
         }
         if (n == max) {
            System.arraycopy(pts, 2, pts, 0, pts.length - 2);
            n--;
         }
         pts[n * 2] = x;
         pts[n * 2 + 1] = y;
         n++;
      }

      void draw(Color tail, Color head, float half, float alpha) {
         if (n < 2) return;
         float px = 0.0F, py = 1.0F; // last valid normal
         float lx = 0.0F, ly = 0.0F, lc = 0.0F; // previous left/right/colour
         float rx = 0.0F, ry = 0.0F;
         for(int i = 0; i < n; ++i) {
            int a = Math.max(i - 1, 0), b = Math.min(i + 1, n - 1);
            float dx = pts[b * 2] - pts[a * 2], dy = pts[b * 2 + 1] - pts[a * 2 + 1];
            float len = (float)Math.sqrt(dx * dx + dy * dy);
            if (len > 1.0E-4F) {
               px = -dy / len;
               py = dx / len;
            }
            float t = i / (float)(n - 1); // 0 = tail, 1 = wing tip
            float w = half * t;
            float c = Tmp.c1.set(tail).lerp(head, t).a(alpha * t * t).toFloatBits();
            float x = pts[i * 2], y = pts[i * 2 + 1];
            float ax = x + px * w, ay = y + py * w, bx = x - px * w, by = y - py * w;
            if (i > 0) {
               Fill.quad(lx, ly, lc, rx, ry, lc, bx, by, c, ax, ay, c);
            }
            lx = ax; ly = ay; rx = bx; ry = by; lc = c;
         }
      }
   }
}
