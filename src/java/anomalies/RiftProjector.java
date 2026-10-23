package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.graphics.g2d.TextureRegion;
import arc.util.Eachable;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Items;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

public class RiftProjector extends Block {
   static final Color col = Color.valueOf("58FFF9");
   public TextureRegion glowRegion;
   public TextureRegion arrowRegion;
   public TextureRegion arrowGlowRegion;
   static final float chargeTime = 600.0F;
   static final float reach = 30.0F;

   public RiftProjector(String var1) {
      super(var1);
      this.size = 1;
      this.solid = true;
      this.rotate = true;
      this.update = true;
      this.hasPower = true;
      this.destructible = true;
      this.health = 250;
      this.requirements(Category.effect, BuildVisibility.shown, ItemStack.with(new Object[]{Items.silicon, 50, Items.titanium, 30}));
      this.consumePower(1.5F);
      this.buildType = () -> new RiftBuild();
   }

   public void load() {
      super.load();
      this.glowRegion = Core.atlas.find(this.name + "-glow");
      this.arrowRegion = Core.atlas.find(this.name + "-arrow");
      this.arrowGlowRegion = Core.atlas.find(this.name + "-arrow-glow");
   }

   // The sprite faces up, so the placement preview needs the same -90 offset as draw() below.
   @Override
   public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list) {
      Draw.rect(this.region, plan.drawx(), plan.drawy());
      Draw.rect(this.arrowRegion, plan.drawx(), plan.drawy(), (float)plan.rotation * 90.0F - 90.0F);
   }

   public class RiftBuild extends Building {
      float charge;
      MirrorAnomaly proj;

      float pa(int var1) {
         return (float)this.rotation * 90.0F + (var1 == 0 ? 45.0F : -45.0F);
      }

      float px(int var1) {
         return this.x + 30.0F * (float)Math.cos(Math.toRadians((double)this.pa(var1)));
      }

      float py(int var1) {
         return this.y + 30.0F * (float)Math.sin(Math.toRadians((double)this.pa(var1)));
      }

      boolean active() {
         return this.charge >= 600.0F;
      }

      public void updateTile() {
         float var1 = this.power.status;
         if (var1 > 0.01F) {
            this.charge = Math.min(600.0F, this.charge + Time.delta * var1);
         } else {
            this.charge = Math.max(0.0F, this.charge - Time.delta * 2.0F);
         }

         if (this.charge > 0.0F && this.active()) {
            if (this.proj == null || this.proj.age >= this.proj.life || this.proj.dead) {
               float var2 = this.px(0);
               float var3 = this.py(0);
               float var4 = this.px(1);
               float var5 = this.py(1);
               float var6 = var4 - var2;
               float var7 = var5 - var3;
               this.proj = new MirrorAnomaly(8.0F, (float)Math.sqrt((double)(var6 * var6 + var7 * var7)) / 2.0F);
               this.proj.team = this.team;
               this.proj.rotation = (float)Math.toDegrees(Math.atan2((double)(-var6), (double)var7));
               this.proj.x = (var2 + var4) / 2.0F;
               this.proj.y = (var3 + var5) / 2.0F;
               this.proj.age = 0.0F;
               this.proj.life = 120.0F;
               AnomalyManager.list.add(this.proj);
               this.proj.spawned();
            }

            this.proj.life = this.proj.age + 90.0F;
         }

      }

      public void draw() {
         // Sprite is drawn with its slit facing up; turn it so the slit points where the beams go.
         Draw.rect(this.block.region, this.x, this.y);
         Draw.rect(RiftProjector.this.arrowRegion, this.x, this.y, this.rotdeg() - 90.0F);
         float var1 = this.charge / 600.0F;
         if (!(var1 <= 0.001F)) {
            float var2 = Time.time;
            float var3 = 0.75F + 0.25F * (float)Math.sin((double)(var2 * 0.5F));
            Draw.z(Layer.effect);
            Draw.blend(Blending.additive);

            // glow over the bright parts of the sprite, same idea as the protector's star
            float powered = this.power == null ? 0.0F : Mathf.clamp(this.power.status);
            if (powered > 0.01F) {
               Draw.color(RiftProjector.col, var1 * var3 * powered);
               Draw.rect(RiftProjector.this.glowRegion, this.x, this.y);
               Draw.rect(RiftProjector.this.arrowGlowRegion, this.x, this.y, this.rotdeg() - 90.0F);
            }

            for(int var4 = 0; var4 < 2; ++var4) {
               float var5 = this.x + (this.px(var4) - this.x) * var1;
               float var6 = this.y + (this.py(var4) - this.y) * var1;

               for(int var7 = 0; var7 < 2; ++var7) {
                  Lines.stroke(var7 == 0 ? 3.2F : 1.2F);
                  Draw.color(RiftProjector.col, (var7 == 0 ? 0.3F : 0.95F) * var1 * var3);
                  Lines.line(this.x, this.y, var5, var6);
               }

               Draw.color(RiftProjector.col, 0.4F * var1 * var3);
               Fill.circle(var5, var6, 3.2F * var1);
               Draw.color(RiftProjector.col, var1);
               Fill.circle(var5, var6, 1.4F * var1);
            }

            Draw.color(RiftProjector.col, 0.8F * var1);
            Fill.circle(this.x, this.y, 2.2F);
            Draw.blend();
            Draw.reset();
         }
      }

      public void onRemoved() {
         super.onRemoved();
         if (this.proj != null) {
            this.proj.life = Math.min(this.proj.life, this.proj.age + 40.0F);
         }

      }

      public void write(Writes var1) {
         super.write(var1);
         var1.f(this.charge);
      }

      public void read(Reads var1, byte var2) {
         super.read(var1, var2);
         this.charge = var1.f();
      }
   }
}
