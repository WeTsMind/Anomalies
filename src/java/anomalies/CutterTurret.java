package anomalies;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.consumers.ConsumeLiquid;
import mindustry.world.meta.BuildVisibility;

/**
 * Cutter: a 2x2 item turret fed with gold nuggets. Sprites: cutter-base.png (the base), cutter-barrel.png (the barrel, drawn UNDER the head and
 * kicking back half a block when it fires), cutter.png (the head), cutter-bullet.png (the main shell), cutter-shard.png (the shards);
 * the head and the barrel share a 160x160 canvas (4 px = 1 world unit) whose centre is the rotation centre of the turret.
 * The shell does 90 damage; at the shot, 4 shards fly apart behind it to both sides, 40 damage each. Shells and shards glow in the colour of the ammo.
 */
public class CutterTurret extends ItemTurret {
   /** how far the barrel kicks back, world units */
   static final float KICK = 4.0F; // half a block
   /** how long the kick lasts, ticks */
   static final float KICK_TIME = 20.0F;
   /** the rotation centre of the head is this much lower (towards the back), world units */
   static final float PIVOT = 3.0F;
   TextureRegion barrelRegion;

   public CutterTurret(String name) {
      super(name);
      this.size = 2;
      this.health = 700;
      this.requirements(Category.turret, BuildVisibility.shown, ItemStack.with(new Object[]{AnomalyMod.goldNugget, 150, Items.silicon, 50, Items.graphite, 40}));
      this.reload = 75.0F;
      this.range = 185.0F;
      this.rotateSpeed = 6.0F;
      this.recoil = 0.0F; // the head stays in place, only the barrel kicks back
      this.shootY = 9.0F; // the inner end of the slit between the barrel prongs
      this.inaccuracy = 0.0F;
      this.coolant = this.consume(new ConsumeLiquid(Liquids.cryofluid, 0.2F));
      this.targetAir = true;
      this.targetGround = true;
      this.shootSound = ModSounds.cutterShot();

      this.ammo(new Object[]{AnomalyMod.goldNugget, new CutterBullet(6.5F, 130.0F, 40.0F, AnomalyMod.goldNugget.color)});
      this.buildType = () -> new CutterBuild();
   }

   public void load() {
      super.load();
      this.barrelRegion = Core.atlas.find(this.name + "-barrel");
      if (Core.atlas.has(this.name + "-icon")) {
         TextureRegion icon = Core.atlas.find(this.name + "-icon");
         this.fullIcon = icon;
         this.uiIcon = icon;
      }
   }

   public class CutterBuild extends ItemTurret.ItemTurretBuild {
      float lastHeat;
      float shotTime = -100000.0F;

      public CutterBuild() {
         super();
      }

      public void draw() {
         // a shot: the heat of the turret jumps up
         if (this.heat - this.lastHeat > 0.5F) {
            this.shotTime = Time.time;
         }
         this.lastHeat = this.heat;
         float px = Mathf.cosDeg(this.rotation) * PIVOT, py = Mathf.sinDeg(this.rotation) * PIVOT - PIVOT;
         float k = (float)Math.pow((double)Mathf.clamp(1.0F - (Time.time - this.shotTime) / KICK_TIME), 1.6D) * KICK;

         // the barrel goes between the base and the head
         float z = Draw.z();
         if (CutterTurret.this.barrelRegion != null && CutterTurret.this.barrelRegion.found()) {
            Draw.z(Layer.turret - 0.1F);
            Draw.rect(CutterTurret.this.barrelRegion, this.x - Mathf.cosDeg(this.rotation) * k + px, this.y - Mathf.sinDeg(this.rotation) * k + py, this.rotation - 90.0F);
            Draw.z(z);
         }
         // the head turns around a point lower than the block centre; no shadow of the tower
         this.recoilOffset.add(px, py);
         TurretPatch.shadowOff();
         super.draw();
         TurretPatch.shadowOn();
         this.recoilOffset.sub(px, py);
      }
   }
}
