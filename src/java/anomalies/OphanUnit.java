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
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.weapons.RepairBeamWeapon;
import mindustry.world.blocks.storage.CoreBlock;

/**
 * Winged flyer. Sprite layers (256x256 canvas, unit centre = canvas centre, 4 px = 1 world unit):
 * two wings (team colour, folded back when spawned from the core, then spread), dark body, front piece, team-colour details.
 * Each wing end leaves a thin glowing ribbon trail. Builder and miner with the Emanate repair beam.
 */
public class OphanUnit extends UnitType {
   /** wing roots in sprite pixels (y down) and fold side (+1 left, -1 right) */
   static final float[][] ROOTS = new float[][]{{108.0F, 90.0F, 1.0F}, {148.0F, 90.0F, -1.0F}};
   /** wing ends in sprite pixels (y down): rear end of left/right wing, then front end of left/right wing (wing index = i & 1) */
   static final float[][] TIPS = new float[][]{{95.0F, 162.0F}, {161.0F, 162.0F}, {108.0F, 90.0F}, {148.0F, 90.0F}};
   static final float FOLD = 30.0F;
   static final int TRAIL_LEN = 34;
   static final float TRAIL_HALF = 0.3F;
   /** the small trail of the engine flame */
   static final int ENGINE_TRAIL_LEN = 16;
   static final float ENGINE_TRAIL_HALF = 0.6F;
   static final Map<Unit, SeraphUnit.WingTrail[]> trails = new WeakHashMap();
   static final float[] tmp = new float[2];
   static final Color tail = new Color(), head = new Color(), core = new Color();
   final TextureRegion[] wingRegions = new TextureRegion[2];
   TextureRegion teamRegion, frontRegion, frontTeamRegion;

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
      this.hitSize = 16.0F;
      this.rotateSpeed = 4.0F;
      this.omniMovement = false;
      this.faceTarget = false;
      this.outlines = false;
      this.drawCell = false;
      this.engineOffset = 3.0F; // flame comes out from behind the sprite
      this.engineSize = 1.8F;
      // builder / miner like the Emanate
      this.buildSpeed = 3.0F;
      this.mineTier = 3;
      this.mineSpeed = 3.0F;
      this.mineWalls = true;
      // the Emanate repair beam: heals blocks and units
      this.weapons.add(new RepairBeamWeapon("") {
         {
            this.x = 5.0F;
            this.y = 4.0F;
            this.shootY = 6.0F;
            this.beamWidth = 0.8F;
            this.repairSpeed = 0.5F;
            this.fractionRepairSpeed = 0.045F;
            this.aiControllable = true;
            this.controllable = false;
            this.autoTarget = true;
            this.bullet = new BulletType() {
               {
                  this.maxRange = 120.0F;
               }
            };
         }
      });
   }

   public void load() {
      super.load();
      this.wingRegions[0] = Core.atlas.find(this.name + "-wing-l");
      this.wingRegions[1] = Core.atlas.find(this.name + "-wing-r");
      this.teamRegion = Core.atlas.find(this.name + "-team");
      this.frontRegion = Core.atlas.find(this.name + "-front");
      this.frontTeamRegion = Core.atlas.find(this.name + "-front-team");
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
            wingPoint(unit.x, unit.y, unit.rotation - 90.0F, i & 1, TIPS[i][0], TIPS[i][1], p, tmp);
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
      this.drawWings(unit.x, unit.y, rot, p, unit.team, 1.0F);

      super.draw(unit);

      Draw.z(z + 0.005F);
      Draw.rect(this.frontRegion, unit.x, unit.y, rot);
      Draw.z(z + 0.01F);
      Draw.color(unit.team.color);
      Draw.rect(this.teamRegion, unit.x, unit.y, rot);
      Draw.rect(this.frontTeamRegion, unit.x, unit.y, rot);
      Draw.reset();
   }

   /** Both wings, team coloured; p = 0 folded back, 1 spread. */
   void drawWings(float x, float y, float rot, float p, Team team, float alpha) {
      Draw.color(team.color, alpha);
      for (int i = 0; i < 2; ++i) {
         float a = ROOTS[i][2] * FOLD * (1.0F - p);
         wingPoint(x, y, rot, i, 128.0F, 128.0F, p, tmp);
         Draw.rect(this.wingRegions[i], tmp[0], tmp[1], rot + a);
      }
      Draw.reset();
   }

   /** The whole unit with folded wings resting inside the core (drawn by the core, facing up). */
   void drawInCore(float x, float y, Team team, float alpha) {
      this.drawWings(x, y, 0.0F, 0.0F, team, alpha);
      Draw.color(Color.white, alpha);
      Draw.rect(this.region, x, y);
      Draw.rect(this.frontRegion, x, y);
      Draw.color(team.color, alpha);
      Draw.rect(this.teamRegion, x, y);
      Draw.rect(this.frontTeamRegion, x, y);
      Draw.reset();
   }

   void drawTrails(Unit unit, float z) {
      SeraphUnit.WingTrail[] t = trails.get(unit);
      if (t == null) return;
      float p = SeraphAnim.wings(SeraphAnim.age(unit));
      for (int i = 0; i < TIPS.length; ++i) {
         wingPoint(unit.x, unit.y, unit.rotation - 90.0F, i & 1, TIPS[i][0], TIPS[i][1], p, tmp);
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
