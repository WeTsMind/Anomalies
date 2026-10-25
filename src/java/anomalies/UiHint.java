package anomalies;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;

/** Small UI helpers: localized ON/OFF and a grey caption under a button. */
public class UiHint {
   public static String onOff(boolean on) {
      return on ? Core.bundle.get("anomalies.on", "ON") : Core.bundle.get("anomalies.off", "OFF");
   }

   /** Adds a small grey caption line (text from the bundle) and ends the row. */
   public static void add(Table table, String key, String fallback, int colspan) {
      try {
         Label label = new Label(Core.bundle.get(key, fallback));
         label.setWrap(true);
         label.setFontScale(0.8F);
         label.setColor(Color.lightGray);
         table.add(label).width(250.0F).colspan(colspan).left().row();
      } catch (Throwable ignored) {
      }
   }
}
