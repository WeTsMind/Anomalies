package anomalies;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import mindustry.entities.Effect;

public class AnomalyFx {
   public static final Effect pulse = new Effect(35.0F, (var0) -> {
      Draw.color(var0.color, Color.white, var0.fout() * 0.5F);
      Lines.stroke(var0.fout() * 2.5F);
      Lines.circle(var0.x, var0.y, var0.finpow() * var0.rotation + 2.0F);
   });
   public static final Effect spawn = new Effect(45.0F, (var0) -> {
      Draw.color(var0.color, Color.white, var0.fout() * 0.6F);
      Lines.stroke(var0.fout() * 2.0F);
      Lines.circle(var0.x, var0.y, var0.finpow() * var0.rotation * 1.4F);
      Angles.randLenVectors((long)var0.id, 12, var0.finpow() * var0.rotation * 1.2F, (var1, var2) -> Fill.circle(var0.x + var1, var0.y + var2, var0.fout() * 2.2F));
   });
}
