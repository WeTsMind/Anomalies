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
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;

/**
 * Winged flyer. Sprite layers (256x256 canvas, unit centre = canvas centre, 4 px = 1 world unit):
 * wing + wing tip (team colour), dark body, team-colour details. The wing tips leave the same thin glowing ribbon trail as the Seraph.
 */
public class WingedUnit extends UnitType {
   /** wing tips in sprite pixels (y down): lowest point of each wing-tip piece */
   static final float[][] TIPS = new float[][]{{115.5F, 167.0F}, {140.5F, 167.0F}};
   static final int TRAIL_LEN = 34;
   /** same ribbon half width as the Seraph trails */
   static final float TRAIL_HALF = 0.3F;
   static final Map<Unit, SeraphUnit.WingTrail[]> trails = new WeakHashMap();
   static final float[] tmp = new float[2];
   static final Color tail = new Color(), head = new Color(), core = new Color();
   /** front blades: closed (as in the reference sprite) they sit this many sprite pixels closer to the centre than in their own sprites; open = their sprite position */
   static final float[] BLADE_CLOSED = new float[]{8.0F, -9.0F};
   /** how fast the blades open (per tick) */
   static final float BLADE_SPEED = 0.12F;
   static final Map<Unit, float[]> opens = new WeakHashMap();
   TextureRegion wingRegion, tipRegion, teamRegion;
   final TextureRegion[] bladeRegions = new TextureRegion[2];

   public WingedUnit(String name) {
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
   }

   public void load() {
      super.load();
      this.wingRegion = Core.atlas.find(this.name + "-wing");
      this.tipRegion = Core.atlas.find(this.name + "-tip");
      this.teamRegion = Core.atlas.find(this.name + "-team");
      this.bladeRegions[0] = Core.atlas.find(this.name + "-blade-l");
      this.bladeRegions[1] = Core.atlas.find(this.name + "-blade-r");
      if (Core.atlas.has(this.name + "-icon")) {
         TextureRegion icon = Core.atlas.find(this.name + "-icon");
         this.fullIcon = icon;
         this.uiIcon = icon;
      }
   }

   public void update(Unit unit) {
      super.update(unit);
      if (Vars.headless) return;
      // front blades: closed at rest, open while the unit attacks
      float[] o = opens.get(unit);
      if (o == null) {
         o = new float[1];
         opens.put(unit, o);
      }
      o[0] = Mathf.approachDelta(o[0], unit.isShooting() ? 1.0F : 0.0F, BLADE_SPEED);
      SeraphUnit.WingTrail[] t = trails.get(unit);
      if (t == null) {
         t = new SeraphUnit.WingTrail[TIPS.length];
         for (int i = 0; i < t.length; ++i) t[i] = new SeraphUnit.WingTrail(TRAIL_LEN);
         trails.put(unit, t);
      }
      for (int i = 0; i < t.length; ++i) {
         tipPoint(unit, i, tmp);
         t[i].add(tmp[0], tmp[1]);
      }
   }

   public void draw(Unit unit) {
      float z = unit.elevation > 0.5F ? (this.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : this.groundLayer;
      float rot = unit.rotation - 90.0F;
      this.drawTrails(unit, z);

      // wings, white -> team colour
      Draw.z(z - 0.03F);
      Draw.color(unit.team.color);
      Draw.rect(this.wingRegion, unit.x, unit.y, rot);
      Draw.rect(this.tipRegion, unit.x, unit.y, rot);
      Draw.reset();

      super.draw(unit);

      this.drawBlades(unit, z);

      // team-colour details on the body
      Draw.z(z + 0.01F);
      Draw.color(unit.team.color);
      Draw.rect(this.teamRegion, unit.x, unit.y, rot);
      Draw.reset();
   }

   /** The two front blades rest in the reference position and slide outward to their own sprite position while attacking. */
   void drawBlades(Unit unit, float z) {
      float[] o = opens.get(unit);
      float open = o == null ? 0.0F : o[0];
      float base = unit.rotation - 90.0F;
      float c = Mathf.cosDeg(base), s = Mathf.sinDeg(base);
      Draw.reset();
      Draw.z(z + 0.005F);
      for (int i = 0; i < 2; ++i) {
         float lx = BLADE_CLOSED[i] / 4.0F * (1.0F - open);
         Draw.rect(this.bladeRegions[i], unit.x + lx * c, unit.y + lx * s, base);
      }
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
         w.draw(head, core, TRAIL_HALF * 0.5F, 1.0F); // bright thin core
      }
      Draw.blend();
      Draw.reset();
   }

   /** World position of a wing-tip sprite pixel. */
   static void tipPoint(Unit unit, int i, float[] out) {
      float a = unit.rotation - 90.0F;
      float c = Mathf.cosDeg(a), s = Mathf.sinDeg(a);
      float lx = (TIPS[i][0] - 128.0F) / 4.0F;
      float ly = (128.0F - TIPS[i][1]) / 4.0F;
      out[0] = unit.x + lx * c - ly * s;
      out[1] = unit.y + lx * s + ly * c;
   }
}
