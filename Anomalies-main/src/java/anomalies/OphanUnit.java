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
import mindustry.content.Fx;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.game.Team;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.blocks.storage.CoreBlock;

/**
 * Winged flyer. Sprite layers (256x256 canvas, unit centre = canvas centre, 4 px = 1 world unit):
 * two wings (team colour, folded back when spawned from the core, then spread), dark body, front piece, team-colour details.
 * Each wing end leaves a thin glowing ribbon trail. Shoots the healing laser of the Emanate.
 */
public class OphanUnit extends UnitType {
   /** wing roots in sprite pixels (y down) and fold side (+1 left, -1 right) */
   static final float[][] ROOTS = new float[][]{{108.0F, 90.0F, 1.0F}, {148.0F, 90.0F, -1.0F}};
   /** wing ends in sprite pixels (y down): lowest point of each wing */
   static final float[][] TIPS = new float[][]{{95.0F, 162.0F}, {161.0F, 162.0F}};
   static final float FOLD = 30.0F;
   static final int TRAIL_LEN = 34;
   static final float TRAIL_HALF = 0.3F;
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
      this.engineOffset = 5.5F;
      this.engineSize = 1.6F;
      // the Emanate laser: healing beam with short side beams
      this.weapons.add(new Weapon() {
         {
            this.x = 5.0F;
            this.y = 8.0F;
            this.reload = 50.0F;
            this.mirror = true;
            this.alternate = true;
            this.top = false;
            this.shootSound = Sounds.shoot;
            this.ejectEffect = Fx.none;
            this.bullet = new LaserBulletType() {
               {
                  this.damage = 60.0F;
                  this.length = 170.0F;
                  this.sideAngle = 45.0F;
                  this.sideWidth = 1.0F;
                  this.sideLength = 70.0F;
                  this.healPercent = 12.0F;
                  this.collidesTeam = true;
                  this.colors = new Color[]{Pal.heal.cpy().a(0.4F), Pal.heal, Color.white};
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
         t = new SeraphUnit.WingTrail[TIPS.length];
         for (int i = 0; i < t.length; ++i) t[i] = new SeraphUnit.WingTrail(TRAIL_LEN);
         trails.put(unit, t);
      }
      if (age < SeraphAnim.OPEN_END) {
         for (SeraphUnit.WingTrail w : t) w.clear();
      } else {
         float p = SeraphAnim.wings(age);
         for (int i = 0; i < t.length; ++i) {
            wingPoint(unit.x, unit.y, unit.rotation - 90.0F, i, TIPS[i][0], TIPS[i][1], p, tmp);
            t[i].add(tmp[0], tmp[1]);
         }
      }
   }

   /** no shadow under the unit */
   @Override
   public void drawShadow(Unit unit) {
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
      tail.set(unit.team.color);
      head.set(unit.team.color).lerp(Color.white, 0.45F);
      core.set(head).lerp(Color.white, 0.6F);
      Draw.z(z - 0.035F);
      Draw.blend(Blending.additive);
      for (SeraphUnit.WingTrail w : t) {
         w.draw(tail, head, TRAIL_HALF, 1.0F);
         w.draw(head, core, TRAIL_HALF * 0.5F, 1.0F);
      }
      Draw.blend();
      Draw.reset();
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
