package anomalies;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.core.Renderer;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

/** The yellow laser with a white core and round ends (same look for the Ophan and the Seraph mining beam). */
public class MineBeam {
   public static final Color glow = Color.valueOf("ffc93a");
   public static final Color core = Color.white;

   public static void draw(Unit unit, float px, float py) {
      if (!unit.mining()) return;
      float ex = unit.mineTile.worldx() + Mathf.sin(Time.time + 48.0F, 12.0F, 1.0F);
      float ey = unit.mineTile.worldy() + Mathf.sin(Time.time + 48.0F, 14.0F, 1.0F);
      float a = Renderer.unitLaserOpacity;

      Draw.z(Layer.flyingUnit + 0.1F);
      // yellow glow
      Draw.color(glow, 0.9F * a);
      Lines.stroke(4.0F);
      Lines.line(px, py, ex, ey, false);
      Fill.circle(px, py, 2.6F);
      Fill.circle(ex, ey, 2.6F);
      // white core
      Draw.color(core, a);
      Lines.stroke(1.6F);
      Lines.line(px, py, ex, ey, false);
      Fill.circle(px, py, 1.5F);
      Fill.circle(ex, ey, 1.5F);
      Drawf.light(px, py, ex, ey, 20.0F, glow, 0.4F * a);
      Draw.reset();
   }
}
