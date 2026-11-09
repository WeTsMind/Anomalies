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
   /** the sprites share one 256x256 canvas (4 px = 1 world unit); the unit centre is the canvas centre */
   static final float CENTER = 128.0F;
   // wing root in sprite pixels (256x256, y down) and fold side (+1 left, -1 right): front (big) wings left / right, rear (small) wings left / right
   static final float[][] WINGS = new float[][]{{115.0F, 117.0F, 1.0F}, {141.0F, 117.0F, -1.0F}, {119.0F, 140.0F, 1.0F}, {137.0F, 140.0F, -1.0F}};
   static final float FOLD = 36.0F;
   // wing tips (sprite px): the outer lower corners, where the trails start
   static final float[][] TIPS = new float[][]{{73.5F, 153.5F}, {182.5F, 153.5F}, {88.0F, 162.5F}, {168.0F, 162.5F}};
   static final Color ENGINE = Color.valueOf("ffd633");
   /** the wings and their trails are painted in the team colour */
   static final Color tail = new Color(), head = new Color(), core = new Color(), glow = new Color();
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
      this.hitSize = 14.0F;
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
      this.engineOffset = 6.25F; // flame starts right under the cell (5.5 units below the centre), not in the air behind the body
      this.engineSize = 1.4F;
      // sharp engine trail: the game's own tapered trail behind the rear flame
      this.engineColor = ENGINE;
      this.trailColor = ENGINE;
      this.trailLength = 14;
      this.trailScl = 0.5F;
      this.outlines = false;

      // two small guns on the shoulders of the hull: they fire in turn (1, 2, 1, 2) every 0.5 s, each one every 1 s.
      // The shell throws shards like the Cutter, but the shards fly straight.
      Weapon gun = new Weapon("anomalies-seraph-gun");
      gun.x = 3.5F;
      gun.y = 3.125F;
      gun.shootY = 1.9F;
      gun.mirror = true;
      gun.alternate = true;
      gun.rotate = false;
      gun.reload = 60.0F;
      gun.recoil = 1.0F;
      gun.recoilTime = 10.0F;
      gun.shootCone = 20.0F;
      gun.ejectEffect = Fx.none;
      gun.shootSound = ModSounds.cutterShot();
      CutterBullet shell = new CutterBullet(6.5F, 10.0F, 4.0F, Color.valueOf("ffd27f"), CutterBullet.SHARD_ANGLES, true);
      shell.lifetime = 26.0F;
      gun.bullet = shell;
      this.weapons.add(gun);
   }

   /** The cell colour: the plain team colour, same as the team details of the Ophan. */
   static Color vivid(mindustry.game.Team t) {
      return Tmp.c2.set(t.color);
   }

   /** The cell next to the Seraph is tinted with the plain team colour (dimmer and pulsing when damaged). */
   @Override
   public Color cellColor(Unit unit) {
      return TeamTint.cell(unit);
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

   /** no shadow and no dark soft blob under the unit */
   public void drawShadow(Unit unit) {
   }

   public void drawSoftShadow(Unit unit) {
   }

   public void drawSoftShadow(float x, float y, float rotation, float alpha) {
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
         float var8 = (WINGS[var7][0] - CENTER) / 4.0F;
         float var9 = (CENTER - WINGS[var7][1]) / 4.0F;
         float var10 = WINGS[var7][2] * 36.0F * (1.0F - var2);
         float var11 = Mathf.cosDeg(var10);
         float var12 = Mathf.sinDeg(var10);
         float var13 = -var8;
         float var14 = -var9;
         float var15 = var8 + var13 * var11 - var14 * var12;
         float var16 = var9 + var13 * var12 + var14 * var11;
         Draw.blend(Blending.additive);
         Draw.color(TeamTint.cell(var1), 0.55F * var17);
         Draw.rect(this.wingRegions[var7], var1.x + var15 * var5 - var16 * var6, var1.y + var15 * var6 + var16 * var5, var4 + var10);
      }

      Draw.blend(Blending.additive);
      float var18 = 1.0F; // constant wing light, no pulsing
      glow.set(TeamTint.cell(var1)).lerp(Color.white, 0.25F); // like the cell: grey and pulsing when damaged

      for(int var19 = 0; var19 < 4; ++var19) {
         float var20 = WINGS[var19][2] * 36.0F * (1.0F - var2);
         wingPoint(var1, var19, CENTER, CENTER, var2, tmp);
         Draw.color(glow, 0.8F * var18 * var17);
         Draw.rect(this.wingRegions[var19], tmp[0], tmp[1], var4 + var20);
         Draw.color(glow, 0.32F * var18 * var17);
         Draw.rect(this.wingRegions[var19], tmp[0], tmp[1], 72.0F, 72.0F, var4 + var20);
      }

      Draw.blend();
      Draw.reset();
   }

   /** World position of a wing sprite pixel (128x128, y down) with the wing folded by (1 - p). */
   static void wingPoint(Unit var0, int var1, float var2, float var3, float var4, float[] var5) {
      float var6 = var0.rotation - 90.0F;
      float var7 = Mathf.cosDeg(var6);
      float var8 = Mathf.sinDeg(var6);
      float var9 = (WINGS[var1][0] - CENTER) / 4.0F;
      float var10 = (CENTER - WINGS[var1][1]) / 4.0F;
      float var11 = WINGS[var1][2] * 36.0F * (1.0F - var4);
      float var12 = Mathf.cosDeg(var11);
      float var13 = Mathf.sinDeg(var11);
      float var14 = (var2 - CENTER) / 4.0F - var9;
      float var15 = (CENTER - var3) / 4.0F - var10;
      float var16 = var9 + var14 * var12 - var15 * var13;
      float var17 = var10 + var14 * var13 + var15 * var12;
      var5[0] = var0.x + var16 * var7 - var17 * var8;
      var5[1] = var0.y + var16 * var8 + var17 * var7;
   }

   /** The mining beam: yellow laser with a white core, see MineBeam. */
   @Override
   public void drawMiningBeam(Unit unit, float px, float py) {
      MineBeam.draw(unit, px, py);
   }

   void drawTrails(Unit var1, float var2) {
      WingTrail[] var3 = trails.get(var1);
      if (var3 != null) {
         float var4 = var1.elevation > 0.5F ? (this.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : this.groundLayer;
         Draw.z(var4 - 0.035F);
         Draw.blend(Blending.additive);
         float var8 = SeraphAnim.wings(SeraphAnim.age(var1));

         for(int var7 = 0; var7 < 4; ++var7) {
            wingPoint(var1, var7, TIPS[var7][0], TIPS[var7][1], var8, tmp);
            var3[var7].live(tmp[0], tmp[1]);
            // team colour ribbon with a brighter thin core
            tail.set(var1.team.color);
            head.set(var1.team.color).lerp(Color.white, 0.45F);
            core.set(head).lerp(Color.white, 0.6F);
            var3[var7].draw(tail, head, TRAIL_HALF, var2);
            var3[var7].draw(head, core, TRAIL_HALF * 0.5F, var2);
         }

         Draw.blend();
         Draw.reset();
      }
   }

   /** Fixed-length point history drawn as ONE ribbon: neighbouring quads share the same mitred edge, so there are no gaps or double-bright overlaps. */
   static class WingTrail {
      final float[] pts;
      int n;
      /** current (this frame) position of the wing end: the ribbon always starts exactly on the wing */
      float liveX, liveY;
      boolean live;
      /** frames spent standing still since the last new point */
      int idle;

      WingTrail(int len) {
         pts = new float[len * 2];
      }

      void clear() {
         n = 0;
         idle = 0;
         live = false;
      }

      void live(float x, float y) {
         liveX = x;
         liveY = y;
         live = true;
      }

      float gx(int i) {
         return i < n ? pts[i * 2] : liveX;
      }

      float gy(int i) {
         return i < n ? pts[i * 2 + 1] : liveY;
      }

      int size() {
         return n;
      }

      void add(float x, float y) {
         int max = pts.length / 2;
         if (n > 0) {
            float dx = x - pts[(n - 1) * 2], dy = y - pts[(n - 1) * 2 + 1];
            if (dx * dx + dy * dy < 0.0004F) {
               // standing still (mining, building, hovering): do not pile up points, and let the old ones fade away,
               // otherwise the trail would hang there forever
               if (++idle >= 2) {
                  idle = 0;
                  System.arraycopy(pts, 2, pts, 0, pts.length - 2);
                  n--;
               }
               return;
            }
            idle = 0;
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
         int m = n + (live && n > 0 ? 1 : 0);
         if (m < 2) return;
         float px = 0.0F, py = 1.0F; // last valid normal
         float lx = 0.0F, ly = 0.0F, lc = 0.0F; // previous left/right/colour
         float rx = 0.0F, ry = 0.0F;
         for(int i = 0; i < m; ++i) {
            int a = Math.max(i - 1, 0), b = Math.min(i + 1, m - 1);
            float dx = gx(b) - gx(a), dy = gy(b) - gy(a);
            float len = (float)Math.sqrt(dx * dx + dy * dy);
            if (len > 1.0E-4F) {
               px = -dy / len;
               py = dx / len;
            }
            float t = i / (float)(m - 1); // 0 = tail, 1 = wing tip
            float w = half * t;
            float c = Tmp.c1.set(tail).lerp(head, t).a(alpha * t * t).toFloatBits();
            float x = gx(i), y = gy(i);
            float ax = x + px * w, ay = y + py * w, bx = x - px * w, by = y - py * w;
            if (i > 0) {
               Fill.quad(lx, ly, lc, rx, ry, lc, bx, by, c, ax, ay, c);
            }
            lx = ax; ly = ay; rx = bx; ry = by; lc = c;
         }
      }
   }
}
