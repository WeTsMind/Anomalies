package anomalies;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;

import java.util.WeakHashMap;

/**
 * TEST01: a flying builder made of a chain of segments (like the Renale). Sprites share a 256 canvas (4 px = 1 world unit).
 * - the head (layers 1 + 7) turns together with the unit;
 * - every other layer is its own segment: it hangs from the previous one and follows its angle with a lag,
 *   so the whole body bends when the unit turns.
 * The sprites are cropped, each one centred on its own centre; DIST is the distance (world units) from the centre of the previous segment.
 */
public class Test01Unit extends UnitType {
   static final String[] SEG = {"2", "3", "4", "4c", "5", "5c", "6", "6c", "8"};
   /** distance from the previous segment centre to this one (the first one is measured from the head centre) */
   static final float[] DIST = {4.625F, 2.875F, 3.125F, 4.25F, 4.125F, 4.25F, 3.0F, 3.0F, 6.25F};
   /** how fast a segment follows the previous one (bigger = stiffer) */
   static final float[] FOLLOW = {0.14F, 0.12F, 0.11F, 0.10F, 0.09F, 0.08F, 0.07F, 0.06F, 0.05F};
   TextureRegion[] segs = new TextureRegion[SEG.length];
   TextureRegion head, headTop;
   static final WeakHashMap<Unit, float[]> ANGLES = new WeakHashMap<>();

   public Test01Unit(String name) {
      super(name);
      this.alwaysUnlocked = true;
      this.flying = true;
      this.health = 450.0F;
      this.armor = 1.0F;
      this.speed = 2.4F;
      this.accel = 0.08F;
      this.drag = 0.04F;
      this.rotateSpeed = 6.0F;
      this.hitSize = 14.0F;
      this.buildSpeed = 0.8F; // can build
      this.itemCapacity = 30;
      this.faceTarget = false;
      this.outlines = false;
      this.drawCell = false;
      this.engineSize = 0.0F;
      this.lowAltitude = true;
   }

   public void load() {
      super.load();
      for (int i = 0; i < SEG.length; ++i) {
         this.segs[i] = Core.atlas.find(this.name + "-seg-" + SEG[i]);
      }
      this.head = Core.atlas.find(this.name + "-seg-head");
      this.headTop = Core.atlas.find(this.name + "-seg-head-top");
      if (Core.atlas.has(this.name + "-icon")) {
         TextureRegion icon = Core.atlas.find(this.name + "-icon");
         this.fullIcon = icon;
         this.uiIcon = icon;
      }
   }

   @Override
   public void drawShadow(Unit unit) {
   }

   public void drawSoftShadow(Unit unit) {
   }

   public void drawSoftShadow(float x, float y, float rotation, float alpha) {
   }

   static void part(TextureRegion r, float x, float y, float ang) {
      Draw.rect(r, x, y, r.width / 4.0F, r.height / 4.0F, ang - 90.0F);
   }

   public void draw(Unit unit) {
      float[] a = ANGLES.get(unit);
      if (a == null) {
         a = new float[SEG.length];
         for (int i = 0; i < a.length; ++i) a[i] = unit.rotation;
         ANGLES.put(unit, a);
      }
      // the angles lag one behind another (only advanced while the game runs)
      float prev = unit.rotation;
      for (int i = 0; i < a.length; ++i) {
         if (!mindustry.Vars.state.isPaused()) {
            a[i] = Mathf.slerpDelta(a[i], prev, FOLLOW[i]);
         }
         prev = a[i];
      }

      // positions: every segment hangs behind the previous one along the previous angle
      float[] px = new float[SEG.length], py = new float[SEG.length];
      float x = unit.x, y = unit.y, ang = unit.rotation;
      for (int i = 0; i < SEG.length; ++i) {
         x -= Mathf.cosDeg(ang) * DIST[i];
         y -= Mathf.sinDeg(ang) * DIST[i];
         px[i] = x;
         py[i] = y;
         ang = a[i];
      }

      float z = Layer.flyingUnit;
      // from the tail up to the head
      for (int i = SEG.length - 1; i >= 0; --i) {
         Draw.z(z + 0.0001F * (SEG.length - i));
         part(this.segs[i], px[i], py[i], a[i]);
      }
      Draw.z(z + 0.002F);
      part(this.headTop, unit.x, unit.y, unit.rotation);
      Draw.z(z + 0.0021F);
      part(this.head, unit.x, unit.y, unit.rotation);
      Draw.reset();
   }
}
