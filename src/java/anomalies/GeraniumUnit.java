package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import java.util.Map;
import java.util.WeakHashMap;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.blocks.defense.turrets.Turret;

/**
 * Geranium: tier 2 flyer, the upgrade of the Crocus (additive reconstructor). Sprites share one 256x256 canvas (4 px = 1 world unit),
 * the unit centre is the canvas centre.
 * - geranium.png: the hull, a U-shaped slit with a round hole at its bottom (the ball lightning charges in the hole and leaves through the slit),
 *   three cut-outs at the bottom for the three engine flames,
 * - geranium-wing-l.png / geranium-wing-r.png: the wings (a big front one and a small rear one each), in their own colours and glowing, each pair turned 10 degrees inwards.
 * The rear outer corners of the front and of the rear wings leave a team-colour trail each (two pairs).
 * It shoots the ball lightning of the Golden Ratio, but straight. The whole craft kicks back a little when it fires.
 */
public class GeraniumUnit extends UnitType {
   static final float CENTER = 128.0F;
   /** side of the canvas in world units */
   static final float CANVAS = 64.0F;
   /** the three engine flames: the cut-outs at the bottom of the hull, sprite pixels (y down) */
   static final float[][] ENGINES = new float[][]{{112.0F, 133.0F}, {128.0F, 142.0F}, {143.0F, 133.0F}};
   static final float ENGINE_RADIUS = 1.6F;
   /** where each wing is attached to the hull (left, right), sprite pixels (y down) */
   static final float[][] WING_PIVOT = new float[][]{{114.0F, 100.0F}, {142.0F, 100.0F}};
   /** how far the wings are turned towards the hull, degrees */
   static final float WING_TURN = 10.0F;
   /** the round hole of the hull, where the ball charges, sprite pixels (y down) */
   static final float[] ORB = new float[]{127.5F, 111.5F};
   /** the slit is 4 world units ahead of the centre: the muzzle is a bit further, in the slit */
   static final float CANNON_Y = 4.1F;
   static final float RELOAD = 120.0F;
   /** how far the hull kicks back at the moment of the shot, world units */
   static final float KICK = 1.4F;
   /** rear outer corners of the wings (left, right), sprite pixels (y down), before the wings are turned */
   static final float[][] WING_TIPS = new float[][]{{91.5F, 136.5F}, {164.5F, 136.5F}, {103.5F, 143.5F}, {152.5F, 143.5F}};
   /** trails: 0, 1 = the rear outer corners of the front wings (left, right), 2, 3 = the same of the rear (small) wings */
   static final int TRAILS = 4;
   static final int WING_TRAIL_LEN = 34;
   static final float WING_TRAIL_HALF = 0.35F;
   static final Map<Unit, SeraphUnit.WingTrail[]> trails = new WeakHashMap();
   static final Color tail = new Color(), head = new Color(), core = new Color();
   static final Color orbGlow = Color.valueOf("ffd21f"), orbBody = Color.valueOf("ffe95e"), orbCore = Color.valueOf("fff8d0");
   static final float[] tmp = new float[2];
   final TextureRegion[] wingRegions = new TextureRegion[2];

   public GeraniumUnit(String name) {
      super(name);
      this.constructor = UnitEntity::create;
      this.alwaysUnlocked = true;
      this.flying = true;
      this.lowAltitude = true;
      this.health = 900.0F;
      this.armor = 2.0F;
      this.speed = 2.4F;
      this.accel = 0.08F;
      this.drag = 0.04F;
      this.hitSize = 14.0F;
      this.rotateSpeed = 5.0F;
      this.faceTarget = true;
      this.targetAir = true;
      this.targetGround = true;
      this.outlines = false;
      this.drawCell = false;
      // the default single engine circle is not drawn: the standard flames are drawn by this class, one in each cut-out
      this.engineSize = 0.0F;

      // invisible weapon: the ball charges in the hole of the hull, which is drawn by this class
      Weapon cannon = new Weapon();
      cannon.x = 0.0F;
      cannon.y = CANNON_Y;
      cannon.shootY = 4.0F;
      cannon.mirror = false;
      cannon.rotate = false;
      cannon.reload = RELOAD;
      cannon.recoil = 1.0F;
      cannon.recoilTime = 16.0F;
      cannon.shootCone = 12.0F;
      cannon.ejectEffect = Fx.none;
      cannon.shootSound = shootSound();
      cannon.bullet = new StraightShot();
      this.weapons.add(cannon);
   }

   static arc.audio.Sound shootSound() {
      try {
         if (Blocks.foreshadow instanceof Turret) {
            arc.audio.Sound s = ((Turret)Blocks.foreshadow).shootSound;
            if (s != null) return s;
         }
      } catch (Throwable ignored) {
      }
      return Sounds.shoot;
   }

   public void load() {
      super.load();
      this.wingRegions[0] = Core.atlas.find(this.name + "-wing-l");
      this.wingRegions[1] = Core.atlas.find(this.name + "-wing-r");
      if (Core.atlas.has(this.name + "-icon")) {
         TextureRegion icon = Core.atlas.find(this.name + "-icon");
         this.fullIcon = icon;
         this.uiIcon = icon;
      }
   }

   /** no shadow and no dark soft blob under the unit */
   @Override
   public void drawShadow(Unit unit) {
   }

   public void drawSoftShadow(Unit unit) {
   }

   public void drawSoftShadow(float x, float y, float rotation, float alpha) {
   }

   /** 0..1: how fresh the last shot is (1 at the shot, fades to 0). */
   static float kick(Unit unit) {
      WeaponMount[] m = unit.mounts;
      return m != null && m.length > 0 && m[0] != null ? Mathf.clamp(m[0].recoil) : 0.0F;
   }

   /** 0..1: how far the ball is charged (1 = ready to fire). */
   static float charge(Unit unit) {
      WeaponMount[] m = unit.mounts;
      return m != null && m.length > 0 && m[0] != null ? Mathf.clamp(1.0F - m[0].reload / RELOAD) : 1.0F;
   }

   public void update(Unit unit) {
      super.update(unit);
      if (Vars.headless) return;
      SeraphUnit.WingTrail[] t = trails.get(unit);
      if (t == null) {
         t = new SeraphUnit.WingTrail[TRAILS];
         for (int i = 0; i < TRAILS; ++i) t[i] = new SeraphUnit.WingTrail(WING_TRAIL_LEN);
         trails.put(unit, t);
      }
      for (int i = 0; i < TRAILS; ++i) {
         tipPoint(unit, i, tmp);
         t[i].add(tmp[0], tmp[1]);
      }
   }

   public void draw(Unit unit) {
      float z = unit.elevation > 0.5F ? (this.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : this.groundLayer;
      float rot = unit.rotation - 90.0F;
      // the whole craft kicks back when it fires
      float k = kick(unit) * KICK;
      float ox = Mathf.cosDeg(unit.rotation + 180.0F) * k, oy = Mathf.sinDeg(unit.rotation + 180.0F) * k;

      this.drawTrails(unit, z);
      this.drawEngineFlames(unit, z, ox, oy);
      this.drawWings(unit, z, rot, ox, oy);

      // the hull
      float px = unit.x, py = unit.y;
      unit.x = px + ox;
      unit.y = py + oy;
      try {
         super.draw(unit);
      } finally {
         unit.x = px;
         unit.y = py;
      }

      this.drawOrb(unit, z, ox, oy);
   }

   /** Team-colour ribbons behind the rear outer corners of the front wings and of the rear wings (two pairs). */
   void drawTrails(Unit unit, float z) {
      SeraphUnit.WingTrail[] t = trails.get(unit);
      if (t == null) return;
      Draw.z(z - 0.035F);
      Draw.blend(Blending.additive);
      for (int i = 0; i < TRAILS; ++i) {
         tipPoint(unit, i, tmp);
         t[i].live(tmp[0], tmp[1]);
         tail.set(unit.team.color);
         head.set(unit.team.color).lerp(Color.white, 0.45F);
         core.set(head).lerp(Color.white, 0.6F);
         t[i].draw(tail, head, WING_TRAIL_HALF, 1.0F);
         t[i].draw(head, core, WING_TRAIL_HALF * 0.5F, 1.0F);
      }
      Draw.blend();
      Draw.reset();
   }

   /** World position of the corner of a wing, after the wing has been turned inwards around its pivot. */
   static void tipPoint(Unit unit, int i, float[] out) {
      int side = i & 1; // the rear wings sit on the same pivots as the front ones
      float pxl = (WING_PIVOT[side][0] - CENTER) / 4.0F, pyl = (CENTER - WING_PIVOT[side][1]) / 4.0F;
      float dx = (WING_TIPS[i][0] - WING_PIVOT[i][0]) / 4.0F, dy = (WING_PIVOT[i][1] - WING_TIPS[i][1]) / 4.0F;
      float turn = side == 0 ? -WING_TURN : WING_TURN;
      float tc = Mathf.cosDeg(turn), ts = Mathf.sinDeg(turn);
      float lx = pxl + dx * tc - dy * ts;
      float ly = pyl + dx * ts + dy * tc;
      float a = unit.rotation - 90.0F;
      float c = Mathf.cosDeg(a), s = Mathf.sinDeg(a);
      out[0] = unit.x + lx * c - ly * s;
      out[1] = unit.y + lx * s + ly * c;
   }

   /** The two wings: team colour, under the hull, each one turned inwards around the point where it meets the hull. */
   void drawWings(Unit unit, float z, float rot, float ox, float oy) {
      float c = Mathf.cosDeg(rot), s = Mathf.sinDeg(rot);
      float pulse = 0.85F + 0.15F * Mathf.sin(Time.time * 0.1F + unit.id);
      Draw.z(z - 0.03F);
      for (int pass = 0; pass < 2; ++pass) {
      // pass 0: the wings in their own colours (no team tint); pass 1: the same sprites once more, additive, so they glow
      if (pass == 0) {
         Draw.color(Color.white);
      } else {
         Draw.z(z - 0.029F);
         Draw.blend(Blending.additive);
         Draw.color(Color.white, 0.5F * pulse);
      }
      for (int i = 0; i < 2; ++i) {
         if (this.wingRegions[i] == null) continue;
         float lx = (WING_PIVOT[i][0] - CENTER) / 4.0F;
         float ly = (CENTER - WING_PIVOT[i][1]) / 4.0F;
         // where the pivot is in the world
         float wx = unit.x + ox + lx * c - ly * s;
         float wy = unit.y + oy + lx * s + ly * c;
         // the pivot inside the canvas, from its bottom left corner
         float originX = lx + CANVAS / 2.0F, originY = ly + CANVAS / 2.0F;
         float turn = i == 0 ? -WING_TURN : WING_TURN;
         // this Draw.rect takes the CENTRE of the canvas (not its corner): the pivot has to end up at (wx, wy)
         Draw.rect(this.wingRegions[i], wx - lx, wy - ly, CANVAS, CANVAS, originX, originY, rot + turn);
      }
      if (pass == 1) Draw.blend();
      }
      Draw.reset();
   }

   /** The standard flyer engine flame (the circle the game draws for other flyers), once in each of the three cut-outs. */
   void drawEngineFlames(Unit unit, float z, float ox, float oy) {
      float scale = unit.elevation;
      if (scale <= 0.0F) return;
      float pulse = Mathf.absin(Time.time, 2.0F, ENGINE_RADIUS / 4.0F);
      Draw.z(z - 0.025F);
      for (int i = 0; i < ENGINES.length; ++i) {
         point(unit, ENGINES[i][0], ENGINES[i][1], tmp);
         Draw.color(this.engineColor == null ? unit.team.color : this.engineColor);
         Fill.circle(tmp[0] + ox, tmp[1] + oy, (ENGINE_RADIUS + pulse) * scale);
         Draw.color(Color.white);
         Fill.circle(tmp[0] + ox, tmp[1] + oy, (ENGINE_RADIUS + pulse) / 2.0F * scale);
      }
      Draw.reset();
   }

   /** The ball lightning charging in the round hole of the hull: it grows until the next shot, and flashes at the shot. */
   void drawOrb(Unit unit, float z, float ox, float oy) {
      if (Vars.headless) return;
      float ch = charge(unit);
      float flash = kick(unit);
      float r = 0.35F + 0.6F * ch;
      float flick = 0.9F + 0.1F * Mathf.sin(Time.time * 0.3F + unit.id);
      point(unit, ORB[0], ORB[1], tmp);
      float bx = tmp[0] + ox, by = tmp[1] + oy;
      Draw.z(z + 0.01F);
      Draw.blend(Blending.additive);
      Draw.color(orbGlow, (0.18F + 0.2F * ch + 0.3F * flash) * flick);
      Fill.circle(bx, by, r * 1.6F);
      Draw.color(orbBody, (0.45F + 0.3F * ch) * flick);
      Fill.circle(bx, by, r);
      Draw.color(orbCore, 0.8F + 0.2F * ch);
      Fill.circle(bx, by, r * 0.5F);
      Draw.blend();
      Draw.reset();
      Drawf.light(bx, by, 14.0F + 8.0F * ch, orbGlow, 0.3F + 0.25F * ch);
   }

   /** World position of a sprite pixel (y down, canvas centre = unit centre). */
   static void point(Unit unit, float px, float py, float[] out) {
      float a = unit.rotation - 90.0F;
      float c = Mathf.cosDeg(a), s = Mathf.sinDeg(a);
      float lx = (px - CENTER) / 4.0F;
      float ly = (CENTER - py) / 4.0F;
      out[0] = unit.x + lx * c - ly * s;
      out[1] = unit.y + lx * s + ly * c;
   }
}
