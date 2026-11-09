package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import java.util.Map;
import java.util.WeakHashMap;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.entities.units.WeaponMount;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

/**
 * Nestor: winged flyer. Sprites share a 256x256 canvas (4 px = 1 world unit), the unit centre is the canvas centre.
 * - nestor.png body, nestor-wing.png wings, nestor-cut-big / nestor-cut-small side cuts (a glowing yellow dot sits in the middle of each),
 *   nestor-cell.png the "cell" in the rear slit (the engine flame comes out under it);
 * - centre slit: an alpha ball lightning charges there (reload 1.25 s) and is fired; the whole craft kicks back a little,
 * - 4 side guns (nestor-gun-side, nestor-gun-mid), rotate freely, yellow Stell-style bolts, reload 0.75 s, recoil,
 * - 2 small static front guns (nestor-gun-front), Cutter shell with 2 shards, reload 2 s, recoil.
 * The whole rear line leaves one wide, solid trail band.
 */
public class NestorUnit extends UnitType {
   static final float CENTER = 128.0F;
   /** reloads in ticks (twice as fast as before): centre 1.25 s, side guns 0.75 s, front guns 1 s */
   static final float RELOAD_CENTER = 75.0F, RELOAD_SIDE = 45.0F, RELOAD_FRONT = 60.0F;
   /** how far the hull kicks back at the ball shot, world units */
   static final float KICK = 2.0F;
   /** where the ball charges: the middle of the centre cut (notch bottom y=108, wing tips y=52), sprite pixels (y down) */
   static final float[] ORB = new float[]{128.0F, 80.0F};
   /** the engine flame: the rear slit under the cell, sprite pixels (y down) */
   static final float[] ENGINE = new float[]{128.0F, 151.0F};
   static final float ENGINE_RADIUS = 1.8F;
   /** glowing dots in the middle of the side cuts (big left/right, small left/right), sprite pixels (y down) */
   /** the whole rear line of the craft, left wingtip -> centre (the right half is mirrored), sprite pixels (y down) */
   static final float[][] REAR_HALF = new float[][]{{2.0F, 190.0F}, {11.0F, 183.0F}, {24.0F, 183.0F}, {38.0F, 183.0F}, {54.0F, 183.0F}, {61.0F, 175.0F}, {68.0F, 168.0F}, {77.0F, 168.0F}, {85.0F, 168.0F}, {95.0F, 157.0F}, {106.0F, 146.0F}, {117.0F, 135.0F}, {122.0F, 144.0F}, {128.0F, 151.0F}};
   /** the same line as one continuous polyline from the left wingtip to the right wingtip */
   static final float[][] REAR = buildRear();
   /** history length of the trail (ticks) */
   static final int TRAIL_LEN = 22;
   /** how far behind the rear line the always-visible glow strip reaches, world units */
   static final float GLOW_BACK = 2.6F;
   static final float TRAIL_ALPHA = 0.55F;
   static final Color yellow = Color.valueOf("ffd21f"), pale = Color.valueOf("fff8d0"), dotBody = Color.valueOf("ffe95e");
   static final Map<Unit, SeraphUnit.WingTrail[]> trails = new WeakHashMap();
   static final Color tail = new Color(), head = new Color(), core = new Color();
   static final float[] tmp = new float[2];
   TextureRegion wingRegion, cutBig, cutSmall, cellRegion;

   static float[][] buildRear() {
      int h = REAR_HALF.length;
      float[][] r = new float[h * 2 - 1][];
      for (int i = 0; i < h; ++i) {
         r[i] = REAR_HALF[i];
         if (i < h - 1) r[r.length - 1 - i] = new float[]{255.0F - REAR_HALF[i][0], REAR_HALF[i][1]};
      }
      return r;
   }

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
      front.layerOffset = -0.002F; // drawn under the hull, like in the reference picture
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
         t = new SeraphUnit.WingTrail[REAR.length];
         for (int i = 0; i < t.length; ++i) t[i] = new SeraphUnit.WingTrail(TRAIL_LEN);
         trails.put(unit, t);
      }
      for (int i = 0; i < t.length; ++i) {
         point(unit, REAR[i][0], REAR[i][1], tmp);
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

         // the wings glow: the same sprite once more, additive, in the Nestor yellow, with a soft pulse
         float pulse = 0.85F + 0.15F * Mathf.sin(Time.time * 0.1F + unit.id);
         Draw.z(z - 0.029F);
         Draw.blend(Blending.additive);
         Draw.color(yellow, 0.55F * pulse);
         Draw.rect(this.wingRegion, unit.x, unit.y, rot);
         Draw.color(pale, 0.2F * pulse);
         Draw.rect(this.wingRegion, unit.x, unit.y, rot);
         Draw.blend();
         Draw.reset();

         // hull and the guns (drawn by the weapons, with their recoil)
         TurretPatch.shadowOff();
         try {
            super.draw(unit);
         } finally {
            TurretPatch.shadowOn();
         }

         Draw.z(z + 0.01F);
         // the cell works like the standard one: dark when damaged, pulsing, otherwise the team colour
         Draw.color(TeamTint.cell(unit));
         Draw.rect(this.cellRegion, unit.x, unit.y, rot);
         Draw.reset();
         this.drawOrb(unit, z);
      } finally {
         unit.x = px;
         unit.y = py;
      }
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

   /**
    * One wide, solid team-colour band along the whole rear line of the craft (wingtip to wingtip). Every point of the rear
    * line leaves a history; neighbouring histories are joined into quads, so the band is as wide as the craft itself and
    * fades out towards its end. A short glow strip right behind the line keeps it visible when the craft hovers.
    */
   void drawTrails(Unit unit, float z) {
      SeraphUnit.WingTrail[] t = trails.get(unit);
      if (t == null) return;
      tail.set(unit.team.color);
      head.set(unit.team.color).lerp(Color.white, 0.45F);
      core.set(head).lerp(Color.white, 0.6F);
      int cnt = t.length;
      int steps = Integer.MAX_VALUE; // common length of the histories (they are aligned from the newest end)
      for (int i = 0; i < cnt; ++i) {
         point(unit, REAR[i][0], REAR[i][1], tmp);
         t[i].live(tmp[0], tmp[1]);
         steps = Math.min(steps, t[i].size() + 1);
      }
      Draw.z(z - 0.035F);
      Draw.blend(Blending.additive);

      // the glow strip right behind the rear line
      float bx = Mathf.cosDeg(unit.rotation + 180.0F) * GLOW_BACK, by = Mathf.sinDeg(unit.rotation + 180.0F) * GLOW_BACK;
      float cs = Tmp.c1.set(head).a(TRAIL_ALPHA).toFloatBits(), ce = Tmp.c2.set(tail).a(0.0F).toFloatBits();
      for (int i = 0; i + 1 < cnt; ++i) {
         float x0 = t[i].liveX, y0 = t[i].liveY, x1 = t[i + 1].liveX, y1 = t[i + 1].liveY;
         Fill.quad(x0, y0, cs, x1, y1, cs, x1 + bx, y1 + by, ce, x0 + bx, y0 + by, ce);
      }

      // the swept band: pass 0 = whole length (tail -> head), pass 1 = bright core near the craft
      if (steps >= 2) {
         for (int pass = 0; pass < 2; ++pass) {
            int last = pass == 0 ? steps - 1 : Math.max(1, (int)((steps - 1) * 0.45F));
            for (int k = 0; k < last; ++k) {
               float f0 = 1.0F - k / (float)(steps - 1), f1 = 1.0F - (k + 1) / (float)(steps - 1); // 1 = at the craft, 0 = end
               float a0 = TRAIL_ALPHA * f0 * f0 * (pass == 0 ? 1.0F : 0.5F), a1 = TRAIL_ALPHA * f1 * f1 * (pass == 0 ? 1.0F : 0.5F);
               Color from = pass == 0 ? tail : head, to = pass == 0 ? head : core;
               float c0 = Tmp.c1.set(from).lerp(to, f0).a(a0).toFloatBits(), c1 = Tmp.c2.set(from).lerp(to, f1).a(a1).toFloatBits();
               for (int i = 0; i + 1 < cnt; ++i) {
                  int ia = t[i].size() - k, ib = t[i + 1].size() - k; // index of age k (the live position is index size())
                  Fill.quad(t[i].gx(ia), t[i].gy(ia), c0, t[i + 1].gx(ib), t[i + 1].gy(ib), c0,
                     t[i + 1].gx(ib - 1), t[i + 1].gy(ib - 1), c1, t[i].gx(ia - 1), t[i].gy(ia - 1), c1);
               }
            }
         }
      }
      Draw.blend();
      Draw.reset();
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
