package anomalies;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import mindustry.game.EventType.Trigger;
import mindustry.graphics.Pal;

/**
 * Safety net against render state leaking from the mod's effects into the interface: before the UI is drawn the batch
 * colour, blending and shader are reset and the shared palette colours the mod touches are put back to their defaults.
 */
public class UiGuard {
   static boolean done;
   static final float SHADOW_A = Pal.shadow.a;

   public static void init() {
      if (done) return;
      done = true;
      Events.run(Trigger.uiDrawBegin, UiGuard::reset);
      Events.run(Trigger.preDraw, UiGuard::reset);
   }

   static void reset() {
      Draw.reset();
      Draw.blend();
      Draw.shader();
      Color.white.set(1.0F, 1.0F, 1.0F, 1.0F);
      Color.black.set(0.0F, 0.0F, 0.0F, 1.0F);
      Pal.shadow.a = SHADOW_A;
   }
}
