package anomalies;

import arc.Events;
import arc.math.Mathf;
import arc.math.Rand;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.world.Tile;

/**
 * Ruby deposits are hexagons: when a new map starts (game tick 0, not in the editor, not on a client), a few solid flat-top
 * hexagons of ruby ore (radius 3, 4 or 5 tiles) are laid on free ground. Every map gets them once: the rules tag "anom.ruby" marks
 * a map that already has them, so saves and maps re-loaded later stay as they are.
 */
public class RubyOre {
   static final String TAG = "anom.ruby";
   /** one hexagon per this many tiles of map area, at least MIN */
   static final int AREA_PER_HEX = 3000, MIN = 2, MAX = 24;
   /** no deposit this close to a core, tiles */
   static final float CORE_GAP = 12.0F;

   public static void init() {
      Events.on(EventType.WorldLoadEvent.class, e -> {
         try {
            generate();
         } catch (Throwable t) {
            arc.util.Log.err("Anomalies: ruby generation failed", t);
         }
      });
   }

   /** flat-top hexagon with circumradius r around (0, 0), in tiles */
   static boolean inHex(float x, float y, float r) {
      float ax = Math.abs(x), ay = Math.abs(y);
      float s3 = 1.7320508F;
      // ax <= r - 0.5: no lone tile sticking out at the side corners
      return ax <= r - 0.5F && ay <= r * s3 / 2.0F && s3 * ax + ay <= s3 * r;
   }

   /** a tile the ore can lie on: open ground without ore, building or spawn */
   static boolean free(Tile t) {
      return t != null && !t.solid() && t.build == null && t.overlay() == Blocks.air && !t.floor().isDeep() && !t.floor().isLiquid
         && t.floor() != Blocks.space && t.floor() != Blocks.empty;
   }

   static boolean nearCore(int x, int y) {
      for (Team team : Team.all) {
         if (team.data().cores.isEmpty()) continue;
         for (Building c : team.data().cores) {
            if (Mathf.dst(c.tileX(), c.tileY(), x, y) < CORE_GAP + 4.0F) return true;
         }
      }
      return false;
   }

   static void generate() {
      if (Vars.net.client() || Vars.state.isEditor() || Vars.world.isGenerating() || AnomalyMod.rubyOre == null) return;
      if (Vars.state.rules.tags.containsKey(TAG)) return;
      Vars.state.rules.tags.put(TAG, "1");
      if (Vars.state.tick > 1.0D) return; // an old save: leave its map alone

      int w = Vars.world.width(), h = Vars.world.height();
      if (w < 20 || h < 20) return;
      Rand rand = new Rand();
      int want = Math.min(MAX, Math.max(MIN, w * h / AREA_PER_HEX));
      int placed = 0;
      int[][] centres = new int[want][];
      for (int attempt = 0; attempt < want * 40 && placed < want; attempt++) {
         float r = rand.random(3, 5); // whole radii only: they give clean hexagons on the tile grid
         int cx = rand.random(6, w - 7), cy = rand.random(6, h - 7);
         if (nearCore(cx, cy)) continue;
         boolean close = false;
         for (int i = 0; i < placed; i++) {
            if (Mathf.dst(centres[i][0], centres[i][1], cx, cy) < 14.0F) close = true;
         }
         if (close) continue;
         int ir = (int)Math.ceil(r);
         int total = 0, ok = 0;
         for (int dx = -ir; dx <= ir; dx++) {
            for (int dy = -ir; dy <= ir; dy++) {
               if (!inHex(dx, dy, r)) continue;
               total++;
               if (free(Vars.world.tile(cx + dx, cy + dy))) ok++;
            }
         }
         if (total == 0 || ok < total) continue; // only whole hexagons, so the shape is always clear
         for (int dx = -ir; dx <= ir; dx++) {
            for (int dy = -ir; dy <= ir; dy++) {
               if (inHex(dx, dy, r)) Vars.world.tile(cx + dx, cy + dy).setOverlay(AnomalyMod.rubyOre);
            }
         }
         centres[placed++] = new int[]{cx, cy};
      }
   }
}
