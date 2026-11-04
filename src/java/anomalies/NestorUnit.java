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
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.gen.WeaponMount;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

/**
 * Nestor: winged flyer. Sprites share a 256x256 canvas (4 px = 1 world unit), the unit centre is the canvas centre.
 * - nestor.png body, nestor-wing.png wings, nestor-cut-big / nestor-cut-small side cuts (a glowing yellow dot sits in the middle of each),
 *   nestor-cell.png the "cell" in the rear slit (the engine flame comes out under it);
 * - centre slit: an alpha ball lightning charges there (reload 2.5 s) and is fired; the whole craft kicks back a little,
 * - 4 side guns (nestor-gun-side, nestor-gun-mid), rotate freely, yellow Stell-style bolts, reload 1.5 s, recoil,
 * - 2 small static front guns (nestor-gun-front), Cutter shell with 2 shards, reload 2 s, recoil.
 * The wings leave a trail along the whole rear edge.
 */
public class NestorUnit extends UnitType {
   static final float CENTER = 128.0F;
   /** reloads in ticks: centre 2.5 s, side guns 1.5 s, front guns 2 s */
   static final float RELOAD_CENTER = 150.0F, RELOAD_SIDE = 90.0F, RELOAD_FRONT = 120.0F;
   /** how far the hull kicks back at the ball shot, world units */
   static final float KICK = 2.0F;
   /** where the ball charges in the centre slit, sprite pixels (y down) */
   static final float[] ORB = new float[]{128.0F, 98.0F};
   /** the engine flame: the rear slit under the cell, sprite pixels (y down) */
   static final float[] ENGINE = new float[]{128.0F, 151.0F};
   static final float ENGINE_RADIUS = 1.8F;
   /** glowing dots in the middle of the side cuts (big left/right, small left/right), sprite pixels (y down) */
   static final float[][] DOTS = new float[][]{{51.0F, 162.0F}, {204.0F, 162.0F}, {84.0F, 157.5F}, {171.0F, 157.5F}};
   /** points along the rear edge of the wings (left wing; the right wing is mirrored), sprite pixels (y down) */
   static final float[][] EDGE = new float[][]{{3.0F, 189.0F}, {14.0F, 180.0F}, {26.0F, 168.0F}, {38.0F, 156.0F}, {50.0F, 144.0F}, {62.0F, 132.0F}};
   static final int TRAIL_LEN = 26;
   static final float TRAIL_HALF = 0.3F;
   static final Color yellow = Color.valueOf("ffd21f"), pale = Color.valueOf("fff8d0"), dotBody = Color.valueOf("ffe95e");
   static final Map<Unit, SeraphUnit.WingTrail[]> trails = new WeakHashMap();
   static final Color tail = new Color(), head = new Color(), core = new Color();
   static final float[] tmp = new float[2];
   TextureRegion wingRegion, cutBig, cutSmall, cellRegion;

   public NestorUnit(String name) {
      super(name);
      this.constructor = UnitEntity::create;
      this.alwaysUnlocked = true;
      this.flying = true;
      this.lowAltitude = true;
      this.health = 1500.0F;
      this.armor = 3.0F;
      this.speed = 2.2F;
      this.accel = 0.08F;
      this.drag = 0.04F;
      this.hitSize = 24.0F;
      this.rotateSpeed = 4.5F;
      this.faceTarget = true; // the centre slit and the front guns do not turn: the craft aims
      this.targetAir = true;
      this.targetGround = true;
      this.outlines = false;
      this.drawCell = false;
      this.engineSize = 0.0F; // the flame is drawn by this class

      // 0: centre slit (invisible weapon: the ball is drawn by this class). Must stay first: mounts[0] drives the kick and the charge
      Weapon ball = new Weapon();
      ball.x = 0.0F;
      ball.y = (CENTER - ORB[1]) / 4.0F;
      ball.shootY = 0.0F;
      ball.mirror = false;
      ball.rotate = false;
      ball.reload = RELOAD_CENTER;
      ball.recoil = 1.0F;
      ball.recoilTime = 16.0F;
      ball.shootCone = 12.0F;
      ball.ejectEffect = Fx.none;
      ball.shootSound = GeraniumUnit.shootSound();
      ball.bullet = new NestorBallShot();
      this.weapons.add(ball);

      // side guns: free rotation, yellow bolts, recoil
      this.weapons.add(sideGun("anomalies-nestor-gun-side", 17.75F, 1.25F));
      this.weapons.add(sideGun("anomalies-nestor-gun-mid", 9.5F, 4.5F));

      // front guns: static, Cutter shell with 2 shards
      Weapon front = new Weapon("anomalies-nestor-gun-front");
      front.x = 10.875F;
      front.y = 12.0F;
      front.shootY = 2.75F;
      front.mirror = true;
      front.alternate = false;
      front.rotate = false;
      front.reload = RELOAD_FRONT;
      front.recoil = 2.0F;
      front.recoilTime = 14.0F;
      front.shootCone = 15.0F;
      front.ejectEffect = Fx.none;
      front.shootSound = ModSounds.cutterShot();
      front.bullet = new CutterBullet(6.5F, 100.0F, 40.0F, Color.valueOf("ffd27f"), new float[]{45.0F});
      this.weapons.add(front);
   }

   static Weapon sideGun(String sprite, float x, float y) {
      Weapon w = new Weapon(sprite);
      w.x = x;
      w.y = y;
      w.shootY = 5.5F;
      w.mirror = true;
      w.alternate = false; // both sides fire together
      w.rotate = true;
      w.rotateSpeed = 20.0F;
      w.reload = RELOAD_SIDE;
      w.recoil = 2.0F;
      w.recoilTime = 14.0F;
      w.ejectEffect = Fx.none;
      w.shootSound = CrocusUnit.soundOf("shootStell", Sounds.shoot);
      w.bullet = new NestorShot();
      return w;
   }

   public void load() {
      super.load();
      this.wingRegion = Core.atlas.find(this.name + "-wing");
      this.cutBig = Core.atlas.find(this.name + "-cut-big");
      this.cutSmall = Core.atlas.find(this.name + "-cut-small");
      this.cellRegion = Core.atlas.find(this.name + "-cell");
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

   /** 0..1: how fresh the last ball shot is. */
   static float kick(Unit unit) {
      WeaponMount[] m = unit.mounts;
      return m != null && m.length > 0 && m[0] != null ? Mathf.clamp(m[0].recoil) : 0.0F;
   }

   /** 0..1: how far the ball is charged (1 = ready). */
   static float charge(Unit unit) {
      WeaponMount[] m = unit.mounts;
      return m != null && m.length > 0 && m[0] != null ? Mathf.clamp(1.0F - m[0].reload / RELOAD_CENTER) : 1.0F;
   }

   public void update(Unit unit) {
      super.update(unit);
      if (Vars.headless) return;
      SeraphUnit.WingTrail[] t = trails.get(unit);
      if (t == null) {
         t = new SeraphUnit.WingTrail[EDGE.length * 2];
         for (int i = 0; i < t.length; ++i) t[i] = new SeraphUnit.WingTrail(TRAIL_LEN);
         trails.put(unit, t);
      }
      for (int i = 0; i < t.length; ++i) {
         edgePoint(unit, i, tmp);
         t[i].add(tmp[0], tmp[1]);
      }
   }

   public void draw(Unit unit) {
      float z = unit.elevation > 0.5F ? (this.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : this.groundLayer;
      float rot = unit.rotation - 90.0F;
      this.drawTrails(unit, z);

      // the whole craft kicks back when the ball is fired
      float k = kick(unit) * KICK;
      float ox = Mathf.cosDeg(unit.rotation + 180.0F) * k, oy = Mathf.sinDeg(unit.rotation + 180.0F) * k;
      float px = unit.x, py = unit.y;
      unit.x = px + ox;
      unit.y = py + oy;
      try {
         this.drawEngineFlame(unit, z);

         // side cuts and wings, under the hull
         Draw.z(z - 0.04F);
         Draw.rect(this.cutBig, unit.x, unit.y, rot);
         Draw.rect(this.cutSmall, unit.x, unit.y, rot);
         Draw.z(z - 0.03F);
         Draw.rect(this.wingRegion, unit.x, unit.y, rot);
         Draw.reset();

         // hull and the guns (drawn by the weapons, with their recoil)
         super.draw(unit);

         Draw.z(z + 0.01F);
         Draw.rect(this.cellRegion, unit.x, unit.y, rot);
         Draw.reset();
         this.drawDots(unit, z);
         this.drawOrb(unit, z);
      } finally {
         unit.x = px;
         unit.y = py;
      }
   }

   /** Yellow glowing dots in the middle of the side cuts. */
   void drawDots(Unit unit, float z) {
      Draw.z(z + 0.012F);
      Draw.blend(Blending.additive);
      for (int i = 0; i < DOTS.length; ++i) {
         point(unit, DOTS[i][0], DOTS[i][1], tmp);
         float pulse = 0.85F + 0.15F * Mathf.sin(Time.time * 0.12F + i * 1.7F + unit.id);
         Draw.color(yellow, 0.35F * pulse);
         Fill.circle(tmp[0], tmp[1], 2.2F * pulse);
         Draw.color(dotBody, 0.8F);
         Fill.circle(tmp[0], tmp[1], 1.2F * pulse);
         Draw.color(pale, 0.95F);
         Fill.circle(tmp[0], tmp[1], 0.55F);
         Drawf.light(tmp[0], tmp[1], 8.0F, yellow, 0.25F);
      }
      Draw.blend();
      Draw.reset();
   }

   /** The alpha ball lightning charging in the centre slit: grows until the next shot, flashes at the shot. */
   void drawOrb(Unit unit, float z) {
      float ch = charge(unit);
      float flash = kick(unit);
      float r = 0.5F + 1.6F * ch;
      float flick = 0.9F + 0.1F * Mathf.sin(Time.time * 0.3F + unit.id);
      point(unit, ORB[0], ORB[1], tmp);
      float bx = tmp[0], by = tmp[1];
      Draw.z(z + 0.011F);
      Draw.blend(Blending.additive);
      Draw.color(yellow, (0.18F + 0.2F * ch + 0.3F * flash) * flick);
      Fill.circle(bx, by, r * 1.6F);
      Draw.color(dotBody, (0.45F + 0.3F * ch) * flick);
      Fill.circle(bx, by, r);
      Draw.color(pale, 0.8F + 0.2F * ch);
      Fill.circle(bx, by, r * 0.5F);
      Draw.blend();
      Draw.reset();
      Drawf.light(bx, by, 14.0F + 8.0F * ch, yellow, 0.3F + 0.25F * ch);
   }

   /** The standard flyer engine flame, in the rear slit under the cell. */
   void drawEngineFlame(Unit unit, float z) {
      float scale = unit.elevation;
      if (scale <= 0.0F) return;
      float pulse = Mathf.absin(Time.time, 2.0F, ENGINE_RADIUS / 4.0F);
      point(unit, ENGINE[0], ENGINE[1], tmp);
      Draw.z(z - 0.025F);
      Draw.color(this.engineColor == null ? unit.team.color : this.engineColor);
      Fill.circle(tmp[0], tmp[1], (ENGINE_RADIUS + pulse) * scale);
      Draw.color(Color.white);
      Fill.circle(tmp[0], tmp[1], (ENGINE_RADIUS + pulse) / 2.0F * scale);
      Draw.reset();
   }

   /** Team-colour ribbons along the whole rear edge of both wings. */
   void drawTrails(Unit unit, float z) {
      SeraphUnit.WingTrail[] t = trails.get(unit);
      if (t == null) return;
      tail.set(unit.team.color);
      head.set(unit.team.color).lerp(Color.white, 0.45F);
      core.set(head).lerp(Color.white, 0.6F);
      Draw.z(z - 0.035F);
      Draw.blend(Blending.additive);
      for (int i = 0; i < t.length; ++i) {
         edgePoint(unit, i, tmp);
         t[i].live(tmp[0], tmp[1]);
         t[i].draw(tail, head, TRAIL_HALF, 1.0F);
         t[i].draw(head, core, TRAIL_HALF * 0.5F, 1.0F);
      }
      Draw.blend();
      Draw.reset();
   }

   /** Point i of the rear edge: the first half belongs to the left wing, the second to the right (mirrored). */
   static void edgePoint(Unit unit, int i, float[] out) {
      int n = EDGE.length;
      float px = EDGE[i % n][0], py = EDGE[i % n][1];
      if (i >= n) px = 255.0F - px;
      point(unit, px, py, out);
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
