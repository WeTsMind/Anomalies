package anomalies;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.world.Block;

/** The data the local player's unit carries from a Data Block to a Decryptor. Lost when the unit dies: the data then returns to its Data Block. */
public class DataSystem {
   public static Block carried;
   public static Unit carrier;
   /** the Data Block the carried data came from */
   static DataBlock.DataBuild source;
   static TextureRegion icon;

   public static void init() {
      Events.on(EventType.WorldLoadEvent.class, (e) -> clear());
      Events.run(EventType.Trigger.draw, DataSystem::draw);
   }

   public static void clear() {
      carried = null;
      carrier = null;
      source = null;
   }

   public static void give(Block b, Unit u, DataBlock.DataBuild src) {
      carried = b;
      carrier = u;
      source = src;
   }

   /** The carrier lost the data (died, changed unit): the data goes back to its Data Block so it can be taken again. */
   static void release() {
      DataBlock.DataBuild src = source;
      clear();
      if (src != null && src.isValid() && src.held) src.configure(0);
   }

   public static boolean has() {
      validate();
      return carried != null;
   }

   public static Block take() {
      validate();
      Block b = carried;
      DataBlock.DataBuild src = source;
      clear();
      if (src != null && src.isValid()) src.configure(2); // delivered: the Data Block is used up for good
      return b;
   }

   static void validate() {
      if (carried != null && (Vars.player == null || carrier == null || carrier.dead() || carrier != Vars.player.unit())) release();
   }

   static void draw() {
      validate();
      if (carried == null) return;
      if (icon == null) icon = Core.atlas.find("anomalies-data-block");
      Draw.z(Layer.flyingUnit + 1.0F);
      Draw.rect(icon, carrier.x, carrier.y + carrier.hitSize / 2.0F + 6.0F + Mathf.sin(Time.time, 20.0F, 1.5F), 6.0F, 6.0F);
      Draw.reset();
   }
}
