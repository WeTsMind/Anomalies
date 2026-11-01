package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.world.Block;
import mindustry.world.draw.DrawBlock;

/**
 * Heat glow of the gold crucible: the yellow "snowflake" (sprite name + "-glow", only the star pixels)
 * is drawn once more on top with additive blending. It fades in while the crucible warms up, fades out
 * when it stops, and flickers a little while it works.
 */
public class CrucibleGlow extends DrawBlock {
   static final Color glow = Color.valueOf("ff9a2e");
   TextureRegion region;

   public void load(Block block) {
      this.region = Core.atlas.find(block.name + "-glow");
   }

   public void draw(Building build) {
      float w = build.warmup();
      if (w <= 0.01F) return;
      float pulse = 0.82F + Mathf.absin(Time.time + build.id * 3.0F, 9.0F, 0.18F) + Mathf.range(0.02F);
      if (this.region != null && this.region.found()) {
         Draw.z(Layer.blockAdditive);
         Draw.blend(Blending.additive);
         Draw.color(glow, w * pulse);
         Draw.rect(this.region, build.x, build.y);
         // a second, fainter pass makes the star look hotter in the middle of its colour
         Draw.color(Color.white, w * pulse * 0.25F);
         Draw.rect(this.region, build.x, build.y);
         Draw.blend();
         Draw.reset();
      }
      Drawf.light(build.x, build.y, 40.0F * w, glow, 0.5F * w * pulse);
   }
}
