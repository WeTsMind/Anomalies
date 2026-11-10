package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import java.util.Map;
import java.util.WeakHashMap;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.world.blocks.storage.CoreBlock;

/**
 * Winged flyer. Sprite layers (256x256 canvas, unit centre = canvas centre, 4 px = 1 world unit):
 * six wings (three pairs: outer, middle, inner; team colour, folded back when spawned from the core, then spread) and the dark body.
 * The end of every wing leaves a thin glowing ribbon trail in the team colour. Builder and miner (standard build / mining beams, no repair beam).
 */
public class OphanUnit extends UnitType {
   /** wing roots in sprite pixels (y down) and fold side (+1 left, -1 right); wing index = pair * 2 + (0 left, 1 right), pairs: outer, middle, inner */
   static final float[][] ROOTS = new float[][]{{112.0F, 115.0F, 1.0F}, {144.0F, 115.0F, -1.0F}, {106.0F, 139.0F, 1.0F}, {150.0F, 139.0F, -1.0F}, {120.0F, 143.0F, 1.0F}, {136.0F, 143.0F, -1.0F}};
   /** the end of every wing (sprite pixels, y down), same order as the roots: the outer lower corner, where its trail starts */
   static final float[][] TIPS = new float[][]{{57.5F, 158.5F}, {198.5F, 158.5F}, {76.5F, 170.5F}, {179.5F, 170.5F}, {98.5F, 177.5F}, {157.5F, 177.5F}};
   static final float FOLD = 30.0F;
   static final int TRAIL_LEN = 34;
   static final float TRAIL_HALF = 0.3F;
   /** the small trail of the engine flame */
   static final int ENGINE_TRAIL_LEN = 16;
   static final float ENGINE_TRAIL_HALF = 0.6F;
   static final Map<Unit, SeraphUnit.WingTrail[]> trails = new WeakHashMap();
   static final float[] tmp = new float[2];
   static final Color tail = new Color(), head = new Color(), core = new Color(), glow = new Color();
   final TextureRegion[] wingRegions = new TextureRegion[6];

   public OphanUnit(String name) {
      super(name);
      this.constructor = UnitEntity::create;
      this.alwaysUnlocked = true;
      this.flying = true;
      this.lowAltitude = true;
      this.health = 450.0F;
      this.speed = 2.8F;
      this.accel = 0.07F;
      this.drag = 0.06F;
      this.hitSize = 18.0F;
      this.rotateSpeed = 4.0F;
      this.omniMovement = false;
      this.faceTarget = false;
      this.outlines = false;
      this.drawCell = false;
      this.engineOffset = 5.75F; // flame comes out from behind the sprite
      this.engineSize = 1.8F;
      // builder / miner like the Emanate
      this.buildSpeed = 3.0F;
      this.mineTier = 3;
      this.mineSpeed = 3.0F;
      this.mineWalls = true;
   }

   /** The mining beam: yellow laser with a white core, see MineBeam. */
   @Override
   public void drawMiningBeam(Unit unit, float px, float py) {
      MineBeam.draw(unit, px, py);
   }

   public void load() {
      super.load();
      for (int i = 0; i < this.wingRegions.length; ++i) this.wingRegions[i] = Core.atlas.find(this.name + "-wing-" + i);
      if (Core.atlas.has(this.name + "-icon")) {
         TextureRegion icon = Core.atlas.find(this.name + "-icon");
         this.fullIcon = icon;
         this.uiIcon = icon;
      }
   }

   public void update(Unit unit) {
      super.update(unit);
      // while the lids open the unit sits inside the core (the core draws it); then it flies out
      float age = SeraphAnim.age(unit);
      if (age < SeraphAnim.OPEN_END) {
         CoreBlock.CoreBuild c = unit.closestCore();
         if (c != null) unit.set(c.x, c.y);
         unit.vel.setZero();
         unit.isShooting(false);
      } else if (age < 1.0E8F && SeraphAnim.released.add(unit)) {
         unit.impulse(0.0F, 3.0F);
      }
      if (Vars.headless) return;
      SeraphUnit.WingTrail[] t = trails.get(unit);
      if (t == null) {
         t = new SeraphUnit.WingTrail[TIPS.length + 1];
         for (int i = 0; i < t.length; ++i) t[i] = new SeraphUnit.WingTrail(i < TIPS.length ? TRAIL_LEN : ENGINE_TRAIL_LEN);
         trails.put(unit, t);
      }
      if (age < SeraphAnim.OPEN_END) {
         for (SeraphUnit.WingTrail w : t) w.clear();
      } else {
         float p = SeraphAnim.wings(age);
         for (int i = 0; i < TIPS.length; ++i) {
            wingPoint(unit.x, unit.y, unit.rotation - 90.0F, i, TIPS[i][0], TIPS[i][1], p, tmp);
            t[i].add(tmp[0], tmp[1]);
         }
         enginePoint(unit, tmp);
         t[TIPS.length].add(tmp[0], tmp[1]);
      }
   }

   /** no shadow under the unit */
   @Override
   public void drawShadow(Unit unit) {
   }

   /** no dark soft blob under the unit either */
   public void drawSoftShadow(Unit unit) {
   }

   public void drawSoftShadow(float x, float y, float rotation, float alpha) {
   }

   public void draw(Unit unit) {
      float age = SeraphAnim.age(unit);
      if (age < SeraphAnim.OPEN_END) return; // still inside the core, the core draws it
      float p = SeraphAnim.wings(age);
      float z = unit.elevation > 0.5F ? (this.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : this.groundLayer;
      float rot = unit.rotation - 90.0F;
      this.drawTrails(unit, z);
      Draw.z(z - 0.03F);
      this.drawWings(unit.x, unit.y, rot, p, TeamTint.cell(unit), 1.0F);

      super.draw(unit);
   }

   /** All six wings, team coloured; p = 0 folded back, 1 spread. */
   void drawWings(float x, float y, float rot, float p, Team team, float alpha) {
      this.drawWings(x, y, rot, p, team.color, alpha);
   }

   void drawWings(float x, float y, float rot, float p, Color tint, float alpha) {
      // the wings are semi-transparent but solid-looking in the team colour (normal blending), and glow on top of that: an additive pass of the
      // same sprites plus a slightly larger, fainter one as a halo
      for (int pass = 0; pass < 3; ++pass) {
         if (pass == 0) {
            Draw.color(tint, 0.62F * alpha);
         } else {
            Draw.blend(Blending.additive);
            Draw.color(tint, (pass == 1 ? 0.4F : 0.16F) * alpha);
         }
         for (int i = 0; i < ROOTS.length; ++i) {
            float a = ROOTS[i][2] * FOLD * (1.0F - p);
            wingPoint(x, y, rot, i, 128.0F, 128.0F, p, tmp);
            if (pass == 2) {
               Draw.rect(this.wingRegions[i], tmp[0], tmp[1], 72.0F, 72.0F, rot + a);
            } else {
               Draw.rect(this.wingRegions[i], tmp[0], tmp[1], rot + a);
            }
         }
      }
      Draw.blend();
      Draw.reset();
   }

   /** The whole unit with folded wings resting inside the core (drawn by the core, facing up). */
   void drawInCore(float x, float y, Team team, float alpha) {
      this.drawWings(x, y, 0.0F, 0.0F, team, alpha);
      Draw.color(Color.white, alpha);
      Draw.rect(this.region, x, y);
      Draw.reset();
   }

   void drawTrails(Unit unit, float z) {
      SeraphUnit.WingTrail[] t = trails.get(unit);
      if (t == null) return;
      float p = SeraphAnim.wings(SeraphAnim.age(unit));
      for (int i = 0; i < TIPS.length; ++i) {
         wingPoint(unit.x, unit.y, unit.rotation - 90.0F, i, TIPS[i][0], TIPS[i][1], p, tmp);
         t[i].live(tmp[0], tmp[1]);
      }
      enginePoint(unit, tmp);
      t[TIPS.length].live(tmp[0], tmp[1]);
      tail.set(unit.team.color);
      head.set(unit.team.color).lerp(Color.white, 0.45F);
      core.set(head).lerp(Color.white, 0.6F);
      Draw.z(z - 0.035F);
      Draw.blend(Blending.additive);
      for (int i = 0; i < TIPS.length; ++i) {
         t[i].draw(tail, head, TRAIL_HALF, 1.0F);
         t[i].draw(head, core, TRAIL_HALF * 0.5F, 1.0F);
      }
      // engine flame trail
      tail.set(Pal.engine);
      head.set(Pal.engine).lerp(Color.white, 0.4F);
      t[TIPS.length].draw(tail, head, ENGINE_TRAIL_HALF, 1.0F);
      Draw.blend();
      Draw.reset();
   }

   /** World position of the engine flame centre. */
   void enginePoint(Unit unit, float[] out) {
      float rot = unit.rotation - 90.0F;
      out[0] = unit.x + Mathf.sinDeg(rot) * this.engineOffset;
      out[1] = unit.y - Mathf.cosDeg(rot) * this.engineOffset;
   }

   /** World position of a sprite pixel (256x256, y down) of a wing that is folded by (1 - p) around its root. */
   static void wingPoint(float ux, float uy, float rot, int i, float px, float py, float p, float[] out) {
      float c = Mathf.cosDeg(rot), s = Mathf.sinDeg(rot);
      float rx = (ROOTS[i][0] - 128.0F) / 4.0F, ry = (128.0F - ROOTS[i][1]) / 4.0F;
      float a = ROOTS[i][2] * FOLD * (1.0F - p);
      float ca = Mathf.cosDeg(a), sa = Mathf.sinDeg(a);
      float dx = (px - 128.0F) / 4.0F - rx, dy = (128.0F - py) / 4.0F - ry;
      float lx = rx + dx * ca - dy * sa, ly = ry + dx * sa + dy * ca;
      out[0] = ux + lx * c - ly * s;
      out[1] = uy + lx * s + ly * c;
   }
}
