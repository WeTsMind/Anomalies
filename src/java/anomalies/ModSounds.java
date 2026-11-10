package anomalies;

import arc.audio.Sound;
import mindustry.Vars;
import mindustry.gen.Sounds;

/** Own sounds of the mod (src/resources/sounds/*.ogg); every one falls back to a regular game sound if it cannot be loaded. */
public class ModSounds {
   private ModSounds() {
   }

   /** Loads sounds/&lt;name&gt;.ogg of the mod, or returns the fallback. */
   public static Sound get(String name, Sound fallback) {
      try {
         Sound snd = Vars.tree.loadSound(name);
         if (snd != null) return snd;
      } catch (Throwable ignored) {
      }
      return fallback;
   }

   public static Sound cutterShot() {
      return get("cutter-shot", Sounds.shoot);
   }

   public static Sound demolisherShot() {
      return get("demolisher-shot", Sounds.shoot);
   }

   public static Sound lavandaShot() {
      return get("lavanda-shot", Sounds.shoot);
   }

   public static Sound empPulse() {
      return get("emp-pulse", Sounds.none);
   }
}
