package anomalies;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.serialization.Base64Coder;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType;
import mindustry.type.SectorPreset;

/**
 * Table of the pictures the Decryptor understands. To add a secret: draw the picture in a World Hologram (map editor),
 * then add a line to the static block below with its packed form (HologramBlock.pack, base64) or load it from your own source,
 * the content it unlocks, the condition and the decrypting time.
 *
 * Conditions: SECTOR = the sector preset with that name must be captured, ENEMY = an enemy unit of that type must
 * have been destroyed (remembered in the settings), NONE = always.
 */
public class Secrets {
   public static final Seq<Secret> all = new Seq<>();

   /** Example secret #1 (the picture from the 128x128 sprite, cropped to its white pixels). */
   static final String KEY1 = "eNpNVM1qI0cQrqZhK4cwc3VIx/MKm1NaRPEc8xqGHHIKtGKIDTGaMTroplwdkuBnCLk4l6SFFs8ljF9gQ1oIVpewtNnDzpJZVfpvJI0YUM9XVV/VV9UF38C3EB6LcHhq4kenjo4hHB9DkB1BH9fJbcdqKrkZ3N3z9VSkyERc/VVgci8AFjebCHFA0J9+J4wHJWgG8mHBdwzOHKQAGj0xFdhC5lKAuS2zTADN38wRQdjVeozWTF5/71jxRLQNQ9VadIYwW30izfXrk2sXX57Le4btneM1YPOaf7bRv79xaQvzJ47NKyG/mu7oh1VliItbvm0fiZ4IuhLwuenfMV8b9EzfcHn3OaAOBVuHKrRMhjp6rmcNu0xalGArqYQaStwhEkbRFOhSr8mwcBLu56ymydCZutxFMnSREU6jaJmXCbsmuGnmfCw9TgIkfTt6UtENTchGBDcD8WN0kzp6T86CYUxc9QUYNH/ExAUhEO+r2MS3hlmqKWagUUE+tilx+5ApyDBWCO0vfNGsH2KF+mliukl7GyNKsburWpYMm4Yu2lGaH/PyXdWOto8x4v2vvC23/SgaotJPG5nk1DNU4z1ZT5Dp5Sxl9b6o8dSmhMUZGmJ9gOySGegKOIsTKJxOPcSAHTHAILGHLgsIwoSk5IJ73eOXerWOvQlSKTeArukQXAHdAFLuJfaV/PsKrB+LIPfPf4vQhkBW//YTmucikSlly+wZJrLTjGhpEplerXv6sUhkncxM6Uc0kFVvwZyrgWyXg/gwG8jcPZ2vB7IVxp5HMvde+X4VkMrbFenOe8baqQD+jQW7ytQlpPJ8tK2AxOhwFQZKJHy+8v7+lqoP3AT5OVRe1DmH7X/eqCUCo0AV1+h75C5E9iWQv0bQcciNES4N37TChRELXkLtLycDo5cjy9ywObIpSCnQLxL34anUi9mFXyvOTd7lL0fyhYdyqDf65gIx3iFQmZJTZdNOatmuwUURD+YEKmv6tLiESyTr0wKsZxXoZZkgdSXqUcfSaTHH8TxPB7eNZpvzYfnJZ90XYr8KG+hn+xWqP4Kx3ENyGBtICpr9Qb8nOuzTe6J/2P40NHnYI/oAmWMIrsAcIJjWxfFapyNIU360yXV5+P8/vJF3Vg==";

   static {
      // id, picture, reward type (block / unit / item), reward name, condition type, condition argument, ticks
      add("key1", KEY1, "block", "anomalies-rift-projector", "SECTOR", "frozenForest", 30 * 60);
   }

   public static class Secret {
      public String id, rewardType, rewardName, condType, condArg;
      public HologramBlock.Img key;
      public float ticks;

      public UnlockableContent reward() {
         ContentType t = rewardType.equals("unit") ? ContentType.unit : rewardType.equals("item") ? ContentType.item : ContentType.block;
         Object c = Vars.content.getByName(t, rewardName);
         return c instanceof UnlockableContent ? (UnlockableContent)c : null;
      }
   }

   static void add(String id, String packedKey, String rewardType, String rewardName, String condType, String condArg, float ticks) {
      Secret s = new Secret();
      s.id = id;
      s.rewardType = rewardType;
      s.rewardName = rewardName;
      s.condType = condType;
      s.condArg = condArg;
      s.ticks = ticks;
      try {
         s.key = HologramBlock.unpack(Base64Coder.decode(packedKey));
      } catch (Throwable t) {
         return;
      }
      all.add(s);
   }

   public static Secret match(HologramBlock.Img img) {
      HologramBlock.Img t = HologramBlock.tight(img);
      if (t == null) return null;
      for(Secret s : all) {
         HologramBlock.Img k = HologramBlock.tight(s.key);
         if (k != null && k.w == t.w && k.h == t.h && java.util.Arrays.equals(k.bits, t.bits)) return s;
      }
      return null;
   }

   public static Secret get(String id) {
      for(Secret s : all) {
         if (s.id.equals(id)) return s;
      }
      return null;
   }

   public static boolean met(Secret s) {
      try {
         if (s.condType.equals("SECTOR")) {
            Object p = Vars.content.getByName(ContentType.sector, s.condArg);
            if (!(p instanceof SectorPreset)) return false;
            mindustry.type.Sector sec = ((SectorPreset)p).sector;
            return sec != null && (sec.info.wasCaptured || sec.isCaptured());
         }
         if (s.condType.equals("ENEMY")) return Core.settings.getBool("anom.kill." + s.condArg, false);
      } catch (Throwable t) {
         return false;
      }
      return s.condType.equals("NONE");
   }

   public static String conditionText(Secret s) {
      try {
         if (s.condType.equals("SECTOR")) {
            Object p = Vars.content.getByName(ContentType.sector, s.condArg);
            String n = p instanceof SectorPreset ? ((SectorPreset)p).localizedName : s.condArg;
            return Core.bundle.format("decryptor.cond.sector", n);
         }
         if (s.condType.equals("ENEMY")) {
            Object u = Vars.content.getByName(ContentType.unit, s.condArg);
            String n = u instanceof UnlockableContent ? ((UnlockableContent)u).localizedName : s.condArg;
            return Core.bundle.format("decryptor.cond.enemy", n);
         }
      } catch (Throwable t) {
      }
      return "";
   }

   /** Remember which enemy unit types the player has destroyed (for ENEMY conditions). */
   public static void init() {
      Events.on(EventType.UnitDestroyEvent.class, e -> {
         try {
            if (e.unit != null && e.unit.team == Vars.state.rules.waveTeam) Core.settings.put("anom.kill." + e.unit.type.name, true);
         } catch (Throwable t) {
         }
      });
   }
}
