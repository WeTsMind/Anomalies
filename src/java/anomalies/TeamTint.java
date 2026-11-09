package anomalies;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.gen.Unit;

/**
 * The colour of the team-painted parts (cells, wings): the team colour while healthy, fading into dark grey as the health
 * drops (and pulsing when badly damaged). It stays fully opaque, so the part never turns transparent.
 * The returned colour is a shared scratch object: pass it straight to Draw.color.
 */
public class TeamTint {
   static final Color DARK = Color.valueOf("3a3a40");

   public static Color cell(Unit unit) {
      float f = Mathf.clamp(unit.healthf());
      float k = Mathf.clamp(0.0F + (f + Mathf.absin(Time.time, Math.max(f * 5.0F, 1.0F), 1.0F - f)));
      return Tmp.c4.set(DARK).lerp(unit.team.color, k).a(1.0F);
   }
}
