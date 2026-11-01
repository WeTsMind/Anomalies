package anomalies;

import arc.Core;
import arc.audio.Sound;
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
import mindustry.content.Items;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Layer;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.blocks.units.UnitFactory;

/**
 * Crocus: tier 1 flyer of the air factory. Sprites share one 96x96 canvas (4 px = 1 world unit), the unit centre is the canvas centre.
 * - crocus.png: hull, with two cut-outs at the bottom: the standard engine flames of a flyer sit in them,
 * - crocus-cannon.png: the cannon in the slit of the hull (drawn under the hull and a bit wider than the slit, so no hairline gaps show), it kicks back when it fires,
 * - crocus-wing.png: the wings, painted in the team colour; the upper outer corners of the wings leave a team-colour trail.
 */
public class CrocusUnit extends UnitType {
   static final float CENTER = 48.0F;
   /** upper outer corners of the wings, sprite pixels (y down) */
   static final float[][] WING_TIPS = new float[][]{{25.5F, 21.5F}, {70.5F, 21.5F}};
   /** the engine flames: the middle of the two cut-outs at the bottom of the hull, sprite pixels (y down) */
   static final float[][] ENGINES = new float[][]{{38.0F, 52.0F}, {58.0F, 52.0F}};
   /** radius of one engine flame (the standard flyer engine circle, a bit smaller because there are two of them) */
   static final float ENGINE_RADIUS = 1.2F;
   static final int WING_TRAIL_LEN = 34;
   static final float WING_TRAIL_HALF = 0.35F;
   /** cannon centre: 18 sprite px above the unit centre */
   static final float CANNON_Y = 4.5F;
   static final Color tail = new Color(), head = new Color(), core = new Color();
   /** 0, 1: the wing corners */
   static final Map<Unit, SeraphUnit.WingTrail[]> trails = new WeakHashMap();
   static final float[] tmp = new float[2];
   TextureRegion wingRegion;

   public CrocusUnit(String name) {
      super(name);
      this.constructor = UnitEntity::create;
      this.alwaysUnlocked = true;
      this.flying = true;
      this.lowAltitude = true;
      this.health = 340.0F;
      this.armor = 0.0F;
      this.speed = 2.6F;
      this.accel = 0.08F;
      this.drag = 0.04F;
      this.hitSize = 11.0F;
      this.rotateSpeed = 6.0F;
      this.faceTarget = true;
      this.targetAir = true;
      this.targetGround = true;
      this.outlines = false;
      this.drawCell = false;
      // the default single engine circle (centre of the hull) is not drawn: the same standard flames are drawn
      // by this class, one in each cut-out at the bottom of the hull
      this.engineSize = 0.0F;

      Weapon cannon = new Weapon("anomalies-crocus-cannon");
      cannon.x = 0.0F;
      cannon.y = CANNON_Y;
      cannon.shootY = 3.75F; // half of the sprite length: the muzzle
      cannon.mirror = false;
      cannon.rotate = false;
      cannon.reload = 42.0F;
      cannon.recoil = 2.0F; // the cannon kicks back and slides forward again
      cannon.recoilTime = 16.0F;
      cannon.shootCone = 15.0F;
      cannon.layerOffset = -0.001F; // under the hull: the wider edges of the cannon hide under the hull outline, no gaps
      cannon.ejectEffect = Fx.none;
      cannon.shootSound = soundOf("shootStell", Sounds.shoot);
      cannon.bullet = new CrocusShot();
      this.weapons.add(cannon);
   }

   static Sound soundOf(String field, Sound fallback) {
      try {
         Object v = Sounds.class.getField(field).get(null);
         if (v instanceof Sound) return (Sound)v;
      } catch (Throwable ignored) {
      }
      return fallback;
   }

   /** Adds Crocus to the air factory (runs after the content is initialised, so the item capacity is raised by hand). */
   public static void addPlan(UnitType crocus) {
      if (crocus == null || !(Blocks.airFactory instanceof UnitFactory)) return;
      UnitFactory f = (UnitFactory)Blocks.airFactory;
      ItemStack[] cost = ItemStack.with(new Object[]{Items.silicon, 30, Items.titanium, 30, AnomalyMod.polcim, 10});
      f.plans.add(new UnitFactory.UnitPlan(crocus, 1200.0F, cost));
      for (ItemStack s : cost) {
         int id = s.item.id;
         if (f.itemFilter != null && id < f.itemFilter.length) f.itemFilter[id] = true;
         if (f.capacities != null && id < f.capacities.length) f.capacities[id] = Math.max(f.capacities[id], s.amount * 2);
         f.itemCapacity = Math.max(f.itemCapacity, s.amount * 2);
      }
   }

   public void load() {
      super.load();
      this.wingRegion = Core.atlas.find(this.name + "-wing");
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

   public void update(Unit unit) {
      super.update(unit);
      if (Vars.headless) return;
      SeraphUnit.WingTrail[] t = trails.get(unit);
      if (t == null) {
         t = new SeraphUnit.WingTrail[2];
         for (int i = 0; i < 2; ++i) t[i] = new SeraphUnit.WingTrail(WING_TRAIL_LEN);
         trails.put(unit, t);
      }
      for (int i = 0; i < 2; ++i) {
         trailPoint(unit, i, tmp);
         t[i].add(tmp[0], tmp[1]);
      }
   }

   public void draw(Unit unit) {
      float z = unit.elevation > 0.5F ? (this.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : this.groundLayer;
      float rot = unit.rotation - 90.0F;
      this.drawTrails(unit, z);
      this.drawEngineFlames(unit, z);

      // wings: painted in the team colour, under the hull
      Draw.z(z - 0.03F);
      Draw.color(unit.team.color);
      Draw.rect(this.wingRegion, unit.x, unit.y, rot);
      Draw.reset();

      // hull and the cannon (drawn by the weapon, with its recoil)
      super.draw(unit);
   }

   /** The standard flyer engine flame (the same circle the game draws for other flyers), once in each cut-out at the bottom of the hull. */
   void drawEngineFlames(Unit unit, float z) {
      float scale = unit.elevation;
      if (scale <= 0.0F) return;
      float pulse = Mathf.absin(Time.time, 2.0F, ENGINE_RADIUS / 4.0F);
      Draw.z(z - 0.025F);
      for (int i = 0; i < ENGINES.length; ++i) {
         point(unit, ENGINES[i][0], ENGINES[i][1], tmp);
         Draw.color(this.engineColor == null ? unit.team.color : this.engineColor);
         Fill.circle(tmp[0], tmp[1], (ENGINE_RADIUS + pulse) * scale);
         Draw.color(Color.white);
         Fill.circle(tmp[0], tmp[1], (ENGINE_RADIUS + pulse) / 2.0F * scale);
      }
      Draw.reset();
   }

   /** A team-colour ribbon behind each wing corner. */
   void drawTrails(Unit unit, float z) {
      SeraphUnit.WingTrail[] t = trails.get(unit);
      if (t == null) return;
      Draw.z(z - 0.035F);
      Draw.blend(Blending.additive);
      for (int i = 0; i < 2; ++i) {
         trailPoint(unit, i, tmp);
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

   /** The wing corners. */
   static void trailPoint(Unit unit, int i, float[] out) {
      point(unit, WING_TIPS[i][0], WING_TIPS[i][1], out);
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
