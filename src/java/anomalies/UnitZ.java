package anomalies;

import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

/**
 * The layer a unit's body is drawn on (the same formula as UnitType.draw).
 *
 * Every piece of one unit (legs, tail, wings, turrets, cells...) must be drawn on exactly this layer, in the right order
 * (what is drawn first ends up below). The sprite batch keeps the drawing order inside one layer, so the whole unit stays
 * one block: another unit is either completely above it or completely below it. Pieces drawn with their own small offsets
 * (z + 0.01, z - 0.03...) get mixed with the pieces of other units instead: a turret floats above a neighbour's hull while its
 * own hull is below it, a wing slides under a neighbour's body, and so on.
 * Must be called at the very start of draw(Unit) (a unit carried as a payload uses the current layer).
 */
public class UnitZ {
   public static float of(UnitType type, Unit unit) {
      if (!unit.isAdded()) return Draw.z();
      if (unit.elevation > 0.5F || (type.flying && unit.dead)) return type.flyingLayer;
      return type.groundLayer + Mathf.clamp(type.hitSize / 4000.0F, 0.0F, 0.01F);
   }

   /**
    * Weapons that sit under the hull (negative layerOffset): the game would draw them below the hulls of ALL units, so they slid
    * under neighbouring units. Call this from drawBody() before the hull: they are drawn right now, on the hull layer, so the
    * hull covers them but they stay with their own unit. drawTopWeapons() then draws the rest (call it from drawWeapons()).
    */
   public static void drawUnderWeapons(UnitType type, Unit unit) {
      type.applyColor(unit);
      for (WeaponMount mount : unit.mounts) {
         Weapon w = mount.weapon;
         if (w.layerOffset >= 0.0F) continue;
         float off = w.layerOffset;
         w.layerOffset = 0.0F;
         try {
            w.draw(unit, mount);
         } finally {
            w.layerOffset = off;
         }
      }
      Draw.reset();
   }

   public static void drawTopWeapons(UnitType type, Unit unit) {
      type.applyColor(unit);
      for (WeaponMount mount : unit.mounts) {
         if (mount.weapon.layerOffset >= 0.0F) mount.weapon.draw(unit, mount);
      }
      Draw.reset();
   }
}
