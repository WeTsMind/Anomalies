package anomalies;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.part.HoverPart;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.ElevationMoveUnit;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

/**
 * Gvozdika: tier 2 ground hover unit, the upgrade of the Lavanda (additive reconstructor). Sprites share an 80x80 canvas
 * (4 px = 1 world unit), the unit centre is the canvas centre:
 * - gvozdika.png the hull; gvozdika-gun.png the main turret (body + barrel), turns around the unit centre and recoils;
 *   gvozdika-cell.png the white piece at the back of the main turret: the cell (team colour), turns and recoils with the turret;
 *   gvozdika-cell-outline.png its dark outline, drawn on top in its own colour (not tinted);
 * - gvozdika-side-l.png / gvozdika-side-r.png the 4 side turrets (16x22, the centre of the sprite is the pivot), each turns on its own;
 * - main turret: the Demolisher shell in glowing violet, 60 damage, once a second;
 * - side turrets: regular bullets in glowing violet, 10 damage, each turret reloads in 0.5 s, they fire in turn 1, 2, 3, 4
 *   (top left, top right, bottom right, bottom left) and again;
 * - big hover rings (the Elude effect) under each side turret, so that the glow shows from under the hull.
 */
public class GvozdikaUnit extends UnitType {
   /** side turret pivots (local units: x to the side, y forward) in firing order: top left, top right, bottom right, bottom left */
   static final float[][] SIDE = new float[][]{{-4.5F, 4.0F}, {4.5F, 4.0F}, {4.5F, -5.25F}, {-4.5F, -5.25F}};
   /** reload of every side turret, ticks (0.5 s) */
   static final float SIDE_RELOAD = 30.0F;
   /** the main turret: reload, ticks (1 s) */
   static final float MAIN_RELOAD = 60.0F;
   TextureRegion cellRegion, cellOutlineRegion;
   Weapon main;

   public GvozdikaUnit(String name) {
      super(name);
      this.constructor = ElevationMoveUnit::create; // the same kind of ground hover unit as the Elude / Lavanda
      this.alwaysUnlocked = true;
      this.flying = false;
      this.hovering = true;
      this.canDrown = false;
      this.health = 550.0F;
      this.armor = 4.0F;
      this.speed = 0.85F;
      this.hitSize = 17.0F;
      this.rotateSpeed = 4.0F;
      this.faceTarget = false; // the turrets turn by themselves, the hull keeps facing where it moves
      this.targetAir = true;
      this.targetGround = true;
      this.outlines = false;
      this.drawCell = false;
      this.engineSize = 0.0F;

      // the main turret: turns around the unit centre, small recoil, the Demolisher shell in violet
      Weapon gun = new Weapon("anomalies-gvozdika-gun");
      gun.x = 0.0F;
      gun.y = 0.0F;
      gun.shootY = 8.0F;
      gun.mirror = false;
      gun.rotate = true;
      gun.rotateSpeed = 6.0F;
      gun.reload = MAIN_RELOAD;
      gun.recoil = 1.5F;
      gun.recoilTime = 20.0F;
      gun.shootCone = 8.0F;
      gun.ejectEffect = Fx.none;
      gun.shootSound = ModSounds.demolisherShot();
      gun.bullet = GvozdikaShot.main();
      this.weapons.add(gun);
      this.main = gun;

      // the side turrets: each turns on its own, small recoil, they fire one after another
      GvozdikaShot.Side sideShot = new GvozdikaShot.Side();
      for (int i = 0; i < SIDE.length; i++) {
         SideWeapon w = new SideWeapon(SIDE[i][0] < 0.0F ? "anomalies-gvozdika-side-l" : "anomalies-gvozdika-side-r", i);
         w.x = SIDE[i][0];
         w.y = SIDE[i][1];
         w.shootY = 2.75F;
         w.mirror = false;
         w.rotate = true;
         w.rotateSpeed = 9.0F;
         w.reload = SIDE_RELOAD;
         w.recoil = 1.0F;
         w.recoilTime = 10.0F;
         w.shootCone = 10.0F;
         w.ejectEffect = Fx.casing1;
         w.shootSound = Sounds.shoot;
         w.bullet = sideShot;
         this.weapons.add(w);
      }

      // levitation: big hover rings under each side turret (the Elude effect), they glow out from under the hull
      for (float[] h : new float[][]{{4.5F, 4.0F}, {4.5F, -5.25F}}) {
         final float hx = h[0], hy = h[1];
         this.parts.add(new HoverPart() {{
            x = hx;
            y = hy;
            mirror = true;
            radius = 9.0F;
            phase = 90.0F;
            stroke = 2.5F;
            layerOffset = -0.001F;
            color = Color.valueOf("bf92f9");
         }});
      }
   }

   public void load() {
      super.load();
      this.cellRegion = Core.atlas.find(this.name + "-cell");
      this.cellOutlineRegion = Core.atlas.find(this.name + "-cell-outline");
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
      float z = UnitZ.of(this, unit); // the body layer: the cell goes on it, after the turrets (see UnitZ)

      // hull, turrets (with their recoil) and hover rings
      TurretPatch.shadowOff();
      try {
         super.draw(unit);
      } finally {
         TurretPatch.shadowOn();
      }

      // the cell sits on the back of the main turret: turns and recoils with it; dark and pulsing when damaged, otherwise the team colour
      WeaponMount m = null;
      for (WeaponMount mount : unit.mounts) {
         if (mount.weapon == this.main) m = mount;
      }
      float rot = unit.rotation - 90.0F;
      float tr = m == null ? rot : rot + m.rotation;
      float kick = m == null ? 0.0F : (float)Math.pow((double)m.recoil, 1.8D) * this.main.recoil;
      float tx = unit.x + Angles.trnsx(tr, 0.0F, -kick);
      float ty = unit.y + Angles.trnsy(tr, 0.0F, -kick);
      Draw.z(z);
      Draw.color(TeamTint.cell(unit));
      Draw.rect(this.cellRegion, tx, ty, tr);
      // the dark outline of the cell keeps its own colour
      Draw.color();
      Draw.rect(this.cellOutlineRegion, tx, ty, tr);
      Draw.reset();
   }

   /**
    * A side turret that keeps the firing order: it may only fire when it is its turn (the next turret with a target after the
    * one that fired last) and a quarter of the reload has passed since that shot.
    */
   public static class SideWeapon extends Weapon {
      final int order;

      public SideWeapon(String name, int order) {
         super(name);
         this.order = order;
         this.mountType = SideMount::new;
      }

      @Override
      public void update(Unit unit, WeaponMount mount) {
         SideMount[] side = new SideMount[SIDE.length];
         for (WeaponMount wm : unit.mounts) {
            if (wm instanceof SideMount && wm.weapon instanceof SideWeapon) {
               int o = ((SideWeapon)wm.weapon).order;
               if (o >= 0 && o < side.length) side[o] = (SideMount)wm;
            }
         }
         // the turret that fired last
         int last = -1;
         for (int i = 0; i < side.length; i++) {
            if (side[i] != null && side[i].lastShot > -1.0E8F && (last < 0 || side[i].lastShot > side[last].lastShot)) last = i;
         }
         // its turn: the next turret after it (1, 2, 3, 4, 1...) that has a target; a turret without a target is skipped,
         // so the others never wait for it
         int turn = -1;
         for (int k = 1; k <= side.length; k++) {
            int i = (last + k + side.length) % side.length;
            if (side[i] != null && side[i].shoot) {
               turn = i;
               break;
            }
         }
         float gap = this.reload / side.length / Math.max(unit.reloadMultiplier, 0.01F);
         boolean allowed = turn == this.order && (last < 0 || Time.time - side[last].lastShot >= gap);
         if (!allowed) {
            // hold the shot (it still turns towards the target)
            mount.reload = Math.max(mount.reload, Time.delta * unit.reloadMultiplier + 0.01F);
         }
         float before = mount.reload;
         super.update(unit, mount);
         if (mount.reload > before + 0.001F && mount instanceof SideMount) {
            ((SideMount)mount).lastShot = Time.time;
         }
      }
   }

   public static class SideMount extends WeaponMount {
      /** when this turret fired last (Time.time), never = -1e9 */
      public float lastShot = -1.0E9F;

      public SideMount(Weapon weapon) {
         super(weapon);
      }
   }
}
