package anomalies;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Items;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.StatusEffect;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.BuildVisibility;

/**
 * Demolisher: a 2x2 item turret, one shot every 1.5 seconds. Sprites: demolisher-base.png (the base), demolisher-barrel.png (the barrel,
 * drawn UNDER the head and kicking back when it fires), demolisher.png (the head), demolisher-bullet.png (the glowing shell),
 * both big ones share a 256x256 canvas (4 px = 1 world unit) whose centre is the turret's rotation centre.
 * Ammo: tratat 75 damage + pushes enemy units back half a block; titanium 50 damage + slows the hit unit by 25%; thorium 75 damage.
 */
public class DemolisherTurret extends ItemTurret {
   /** how far the barrel kicks back, world units */
   static final float KICK = 3.0F;
   /** how long the kick lasts, ticks */
   static final float KICK_TIME = 22.0F;
   static StatusEffect slow;
   TextureRegion barrelRegion;

   public DemolisherTurret(String name) {
      super(name);
      this.size = 2;
      this.health = 700;
      this.requirements(Category.turret, BuildVisibility.shown, ItemStack.with(new Object[]{AnomalyMod.tratat, 30, Items.silicon, 100, Items.titanium, 60}));
      this.reload = 90.0F;
      this.range = 190.0F;
      this.rotateSpeed = 6.0F;
      this.recoil = 0.0F; // the head stays in place, only the barrel kicks back
      this.shootY = 20.0F; // the tip of the barrel
      this.inaccuracy = 0.0F;
      this.targetAir = true;
      this.targetGround = true;
      this.shootSound = Sounds.shootBig;

      if (slow == null) {
         slow = new StatusEffect("demolisher-slow");
         slow.speedMultiplier = 0.75F;
         slow.color = Color.valueOf("8cc8ff");
      }

      WreckBullet tratat = new WreckBullet(6.0F, 75.0F, AnomalyMod.tratat.color);
      tratat.knockDist = 4.0F; // half a block
      WreckBullet titanium = new WreckBullet(6.0F, 50.0F, Items.titanium.color);
      titanium.status = slow;
      titanium.statusDuration = 150.0F;
      WreckBullet thorium = new WreckBullet(6.0F, 75.0F, Items.thorium.color);
      this.ammo(new Object[]{AnomalyMod.tratat, tratat, Items.titanium, titanium, Items.thorium, thorium});
      this.buildType = () -> new DemolisherBuild();
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

   public class DemolisherBuild extends ItemTurret.ItemTurretBuild {
      float lastHeat;
      float shotTime = -100000.0F;

      public DemolisherBuild() {
         super();
      }

      public void draw() {
         // a shot: the heat of the turret jumps up
         if (this.heat - this.lastHeat > 0.5F) {
            this.shotTime = Time.time;
         }
         this.lastHeat = this.heat;
         float k = (float)Math.pow((double)Mathf.clamp(1.0F - (Time.time - this.shotTime) / KICK_TIME), 1.6D) * KICK;

         // the barrel goes between the base and the head
         float z = Draw.z();
         if (DemolisherTurret.this.barrelRegion != null && DemolisherTurret.this.barrelRegion.found()) {
            Draw.z(Layer.turret - 0.1F);
            Draw.rect(DemolisherTurret.this.barrelRegion, this.x - Mathf.cosDeg(this.rotation) * k, this.y - Mathf.sinDeg(this.rotation) * k, this.rotation - 90.0F);
            Draw.z(z);
         }
         super.draw();
      }
   }
}
