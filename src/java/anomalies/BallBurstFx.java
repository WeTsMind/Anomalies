package anomalies;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import mindustry.entities.Effect;

public class BallBurstFx {
   static final Color glow = Color.valueOf("ffd21f");
   static final Color core = Color.valueOf("fff8d0");
   public static final Effect burst = new Effect(45.0F, (var0) -> {
      float var1 = var0.fout();
      float var2 = var0.finpow();
      Draw.color(core, glow, var0.fin());
      Fill.circle(var0.x, var0.y, var1 * var1 * 13.0F);
      Draw.color(glow, core, var1 * 0.6F);
      Lines.stroke(var1 * 3.2F);
      Lines.circle(var0.x, var0.y, 4.0F + var2 * 30.0F);
      Lines.stroke(var1 * 2.0F);
      Lines.circle(var0.x, var0.y, 2.0F + var2 * 15.0F);
      Angles.randLenVectors((long)var0.id, 12, 8.0F + var2 * 30.0F, (var1x, var2x) -> {
         Draw.color(core, glow, var0.fin());
         Lines.stroke(var0.fout() * 1.7F + 0.2F);
         Lines.lineAngle(var0.x + var1x, var0.y + var2x, Mathf.angle(var1x, var2x), var0.fout() * 7.0F + 1.0F);
      });
      Angles.randLenVectors((long)(var0.id + 1), 9, 5.0F + var2 * 22.0F, (var1x, var2x) -> {
         Draw.color(glow, core, var0.fout() * 0.5F);
         Fill.circle(var0.x + var1x, var0.y + var2x, var0.fout() * 2.0F);
      });
   });

   public static void play(float var0, float var1) {
      burst.at(var0, var1, 0.0F, glow);
   }
}
