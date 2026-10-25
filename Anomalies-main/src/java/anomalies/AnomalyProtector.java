package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

/** Blocks automatic anomaly spawning in a radius around itself while powered. Polcim widens the radius. */
public class AnomalyProtector extends Block {
   static final Color glow = Color.valueOf("7fd4ff");
   /** Base protection radius, tiles. */
   public static final float RADIUS = 10.0F;
   /** Radius while Polcim is being consumed, tiles. */
   public static final float BOOST_RADIUS = 18.0F;
   /** How long one Polcim keeps the boost on, ticks (20 s). */
   public static final float BOOST_TIME = 1200.0F;

   static final Seq<ProtectorBuild> cache = new Seq<>();
   static float cacheStamp = -1.0F;

   public TextureRegion starRegion;
   public TextureRegion radarRegion;
   public TextureRegion radarGlowRegion;

   public AnomalyProtector(String var1) {
      super(var1);
      this.size = 2;
      this.solid = true;
      this.update = true;
      this.hasPower = true;
      this.hasItems = true;
      this.itemCapacity = 10;
      this.destructible = true;
      this.health = 400;
      this.requirements(Category.effect, BuildVisibility.shown, ItemStack.with(new Object[]{AnomalyMod.polcim, 50, Items.silicon, 50, Items.titanium, 20, Items.plastanium, 20}));
      this.consumePower(1.5F);
      this.buildType = () -> new ProtectorBuild();
   }

   public void load() {
      super.load();
      this.starRegion = Core.atlas.find(this.name + "-star");
      this.radarRegion = Core.atlas.find(this.name + "-radar");
      this.radarGlowRegion = Core.atlas.find(this.name + "-radar-glow");
   }

   public void drawPlace(int var1, int var2, int var3, boolean var4) {
      super.drawPlace(var1, var2, var3, var4);
      Drawf.dashCircle((float)var1 * 8.0F + this.offset, (float)var2 * 8.0F + this.offset, 80.0F, glow);
   }

   /** True if a protector forbids an anomaly of the given tile radius from spawning at tile (tx, ty). */
   public static boolean blocks(int tx, int ty, int tileRadius) {
      try {
         if (Time.time != cacheStamp) {
            cacheStamp = Time.time;
            cache.clear();
            Groups.build.each((b) -> {
               if (b instanceof ProtectorBuild && ((ProtectorBuild)b).active()) {
                  cache.add((ProtectorBuild)b);
               }
            });
         }

         for(int i = 0; i < cache.size; ++i) {
            ProtectorBuild p = cache.get(i);
            float dx = (float)tx - p.x / 8.0F;
            float dy = (float)ty - p.y / 8.0F;
            float r = p.radius() + (float)tileRadius;
            if (dx * dx + dy * dy < r * r) {
               return true;
            }
         }
      } catch (Throwable ignored) {
      }

      return false;
   }

   public class ProtectorBuild extends Building {
      float boostLeft;
      float heat;
      float radarRot;
      float shown = RADIUS;

      boolean active() {
         return this.power != null && this.power.status > 0.01F;
      }

      boolean boosted() {
         return this.boostLeft > 0.0F && this.active();
      }

      float radius() {
         return this.boosted() ? BOOST_RADIUS : RADIUS;
      }

      public boolean acceptItem(Building var1, Item var2) {
         return var2 == AnomalyMod.polcim && this.items.get(var2) < this.getMaximumAccepted(var2);
      }

      public void updateTile() {
         boolean on = this.active();
         this.heat = Mathf.lerpDelta(this.heat, on ? 1.0F : 0.0F, 0.03F);
         if (this.power != null && this.power.status >= 0.999F) {
            this.radarRot += Time.delta;
         }

         if (on) {
            if (this.boostLeft > 0.0F) {
               this.boostLeft -= Time.delta;
            }

            if (this.boostLeft <= 0.0F && this.items.get(AnomalyMod.polcim) > 0) {
               this.items.remove(AnomalyMod.polcim, 1);
               this.boostLeft = BOOST_TIME;
            }
         }

         this.shown = Mathf.lerpDelta(this.shown, this.radius(), 0.05F);
      }

      public void draw() {
         Draw.rect(this.block.region, this.x, this.y);
         float pulse = 0.75F + 0.25F * (float)Math.sin((double)(Time.time / 18.0F));
         float half = (float)AnomalyProtector.this.size * 4.0F;
         float fade = this.heat * this.heat * (3.0F - 2.0F * this.heat);
         if (this.heat >= 0.01F) {
            Draw.blend(Blending.additive);
            Draw.color(AnomalyProtector.glow, fade * pulse);
            Draw.rect(AnomalyProtector.this.starRegion, this.x, this.y);
            Draw.blend();
            Draw.reset();
         }

         Draw.rect(AnomalyProtector.this.radarRegion, this.x, this.y, this.radarRot);
         if (this.heat < 0.01F) {
            return;
         }

         Draw.blend(Blending.additive);
         Draw.color(AnomalyProtector.glow, fade * pulse);
         Draw.rect(AnomalyProtector.this.radarGlowRegion, this.x, this.y, this.radarRot);
         Draw.z(Layer.effect);

         // Large Mender-style pulse: a square ring that expands from the block outwards.
         int rings = this.boosted() ? 2 : 1;
         for(int i = 0; i < rings; ++i) {
            float f = 1.0F - ((Time.time + (float)i * 50.0F) / 100.0F) % 1.0F;
            float reach = half + (1.0F - f) * 14.0F;
            Draw.color(AnomalyProtector.glow);
            Lines.stroke((2.0F * f + 0.2F) * this.heat);
            Draw.alpha(this.heat * Math.min(1.0F, f * 1.5F));
            Lines.square(this.x, this.y, reach);
         }

         Draw.blend();
         Draw.reset();
         Drawf.light(this.x, this.y, 30.0F + 12.0F * this.heat, AnomalyProtector.glow, 0.5F * this.heat * pulse);
      }

      public void drawSelect() {
         super.drawSelect();
         Drawf.dashCircle(this.x, this.y, this.shown * 8.0F, AnomalyProtector.glow);
      }

      public void write(Writes var1) {
         super.write(var1);
         var1.f(this.boostLeft);
      }

      public void read(Reads var1, byte var2) {
         super.read(var1, var2);
         this.boostLeft = var1.f();
      }
   }
}
