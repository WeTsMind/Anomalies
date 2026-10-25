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
 * Winged flyer with a separate front piece. Sprite layers (256x256 canvas, 4 px = 1 world unit):
 * wing + wing tip (team colour), rear body, front piece (slides forward while attacking), team-colour details.
 */
public class OphanUnit extends UnitType {
   /** how far the front piece slides forward while attacking, sprite pixels */
   static final float FRONT_SLIDE = 8.0F;
   static final float FRONT_SPEED = 0.12F;
   static final Map<Unit, float[]> opens = new WeakHashMap();
   static final Map<Unit, SeraphUnit.WingTrail[]> trails = new WeakHashMap();
   static final float[] tmp = new float[2];
   static final Color tail = new Color(), head = new Color(), core = new Color();
   TextureRegion wingRegion, tipRegion, teamRegion, frontRegion, frontTeamRegion;

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
   }

   public void load() {
      super.load();
      this.wingRegion = Core.atlas.find(this.name + "-wing");
      this.tipRegion = Core.atlas.find(this.name + "-tip");
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
      if (Vars.headless) return;
      float[] o = opens.get(unit);
      if (o == null) {
         o = new float[1];
         opens.put(unit, o);
      }
      o[0] = Mathf.approachDelta(o[0], unit.isShooting() ? 1.0F : 0.0F, FRONT_SPEED);
      SeraphUnit.WingTrail[] t = trails.get(unit);
      if (t == null) {
         t = new SeraphUnit.WingTrail[WingedUnit.TIPS.length];
         for (int i = 0; i < t.length; ++i) t[i] = new SeraphUnit.WingTrail(WingedUnit.TRAIL_LEN);
         trails.put(unit, t);
      }
      for (int i = 0; i < t.length; ++i) {
         WingedUnit.tipPoint(unit, i, tmp);
         t[i].add(tmp[0], tmp[1]);
      }
   }

   public void draw(Unit unit) {
      float z = unit.elevation > 0.5F ? (this.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : this.groundLayer;
      float rot = unit.rotation - 90.0F;
      this.drawTrails(unit, z);

      Draw.z(z - 0.03F);
      Draw.color(unit.team.color);
      Draw.rect(this.wingRegion, unit.x, unit.y, rot);
      Draw.rect(this.tipRegion, unit.x, unit.y, rot);
      Draw.reset();

      super.draw(unit);

      // front piece: slides forward (along the unit's facing) while attacking
      float[] o = opens.get(unit);
      float slide = FRONT_SLIDE / 4.0F * (o == null ? 0.0F : o[0]);
      float fx = unit.x - Mathf.sinDeg(rot) * slide, fy = unit.y + Mathf.cosDeg(rot) * slide;
      Draw.z(z + 0.005F);
      Draw.rect(this.frontRegion, fx, fy, rot);
      Draw.z(z + 0.01F);
      Draw.color(unit.team.color);
      Draw.rect(this.teamRegion, unit.x, unit.y, rot);
      Draw.rect(this.frontTeamRegion, fx, fy, rot);
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
         w.draw(tail, head, WingedUnit.TRAIL_HALF, 1.0F);
         w.draw(head, core, WingedUnit.TRAIL_HALF * 0.5F, 1.0F);
      }
      Draw.blend();
      Draw.reset();
   }
}
