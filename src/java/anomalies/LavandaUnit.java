package anomalies;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import mindustry.content.Fx;
import mindustry.entities.part.HoverPart;
import mindustry.gen.Unit;
import mindustry.gen.ElevationMoveUnit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

/**
 * Lavanda: a ground hover unit (levitates like the Elude). Sprites share a 128x128 canvas (4 px = 1 world unit), the unit centre is the canvas centre.
 * - lavanda.png the hull, lavanda-mid.png the middle piece drawn over the muzzle root, lavanda-cell.png the cell (team colour,
 *   works like a regular cell), lavanda-gun.png the turret with the muzzle (turns around its base, it recoils when firing; the sprite is padded so that its centre is the pivot);
 * - 3 hover rings per side (same effect as the Elude), at the red dots of the reference picture;
 * - fires violet glowing bullets (60 damage) that break into 3 glowing shards (20 damage each) on a hit.
 */
public class LavandaUnit extends UnitType {
   /** hover ring positions (local units: x to the side, y forward): front, middle, rear */
   static final float[][] HOVER = new float[][]{{3.1F, 7.8F}, {5.3F, 0.4F}, {3.4F, -7.45F}};
   TextureRegion midRegion, cellRegion;

   public LavandaUnit(String name) {
      super(name);
      this.constructor = ElevationMoveUnit::create; // the same kind of ground hover unit as the Elude
      this.alwaysUnlocked = true;
      this.flying = false;
      this.hovering = true; // levitates over the ground (and over deep water) without drowning
      this.canDrown = false;
      this.health = 700.0F;
      this.armor = 2.0F;
      this.speed = 1.0F;
      this.hitSize = 14.0F;
      this.rotateSpeed = 5.0F;
      this.faceTarget = false; // the turret turns by itself, the hull keeps facing where it moves
      this.targetAir = true;
      this.targetGround = true;
      this.outlines = false;
      this.drawCell = false;
      this.engineSize = 0.0F;

      // the turret: turns freely around its base (the sprite is padded so that its centre is the pivot), small recoil
      Weapon gun = new Weapon("anomalies-lavanda-gun");
      gun.x = 0.0F;
      gun.y = 2.75F;
      gun.shootY = 5.0F;
      gun.mirror = false;
      gun.rotate = true;
      gun.rotateSpeed = 8.0F;
      gun.reload = 40.0F;
      gun.recoil = 2.0F;
      gun.recoilTime = 16.0F;
      gun.shootCone = 8.0F;
      gun.ejectEffect = Fx.none;
      gun.shootSound = ModSounds.lavandaShot();
      gun.bullet = new LavandaShot();
      this.weapons.add(gun);

      // levitation: hover rings on both sides, like the Elude
      for (float[] h : HOVER) {
         final float hx = h[0], hy = h[1];
         this.parts.add(new HoverPart() {{
            x = hx;
            y = hy;
            mirror = true;
            radius = 4.5F;
            phase = 90.0F;
            stroke = 2.0F;
            layerOffset = -0.001F;
            color = Color.valueOf("bf92f9");
         }});
      }
   }

   public void load() {
      super.load();
      this.midRegion = Core.atlas.find(this.name + "-mid");
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

   public void draw(Unit unit) {
      float z = unit.elevation > 0.5F ? Layer.flyingUnitLow : this.groundLayer;
      float rot = unit.rotation - 90.0F;

      // hull, muzzle (with its recoil) and hover rings
      TurretPatch.shadowOff();
      try {
         super.draw(unit);
      } finally {
         TurretPatch.shadowOn();
      }

      // middle piece over the root of the muzzle, then the cell on top: dark when damaged, pulsing, otherwise the team colour
      Draw.z(z + 0.01F);
      Draw.rect(this.midRegion, unit.x, unit.y, rot);
      Draw.z(z + 0.011F);
      Draw.color(TeamTint.cell(unit));
      Draw.rect(this.cellRegion, unit.x, unit.y, rot);
      Draw.reset();
   }
}
