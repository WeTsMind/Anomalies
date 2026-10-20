package anomalies;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.IntMap;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Bullet;
import mindustry.gen.Entityc;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Posc;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.StaticWall;

public class AnomalyManager {
   public static final float minSpawn = 1.0E10F;
   public static final float maxSpawn = 1.0E10F;
   public static final float minLife = 54000.0F;
   public static final float maxLife = 108000.0F;
   public static final int RING = 61;
   public static final Seq<Anomaly> list = new Seq();
   public static IntSet reflected = new IntSet();
   static final IntMap<Track> tracks = new IntMap();
   static final IntSeq removeIds = new IntSeq();
   public static final Seq<Unit> tmp = new Seq();
   static float spawnTimer;
   static float nextSpawn;
   static float sampleTimer;
   static float cleanTimer;
   static int stamp;
   static boolean needSeed;

   public static void init() {
      Events.on(EventType.WorldLoadEvent.class, (var0) -> reset());
      Events.run(Trigger.update, AnomalyManager::update);
      Events.run(Trigger.draw, AnomalyManager::draw);
   }

   static void reset() {
      list.clear();
      reflected.clear();
      tracks.clear();
      cleanTimer = 0.0F;
      sampleTimer = 0.0F;
      spawnTimer = 0.0F;
      nextSpawn = Mathf.random(1.0E10F, 1.0E10F);
      needSeed = true;
   }

   static void update() {
      if (Vars.state.isPlaying() && !Vars.net.client()) {
         float var0 = Time.delta;
         if (needSeed) {
            needSeed = false;
            Groups.unit.each((var0x) -> {
               Track var1 = new Track();

               for(int var2 = 0; var2 < 61; ++var2) {
                  var1.xs[var2] = var0x.x;
                  var1.ys[var2] = var0x.y;
               }

               var1.count = 61;
               var1.stamp = stamp;
               tracks.put(var0x.id, var1);
            });
         }

         if ((spawnTimer += var0) >= (float)AnomalySettings.interval() * 60.0F) {
            spawnTimer = 0.0F;
            AnomalySettings.roll();
         }

         if ((sampleTimer += var0) >= 60.0F) {
            sampleTimer -= 60.0F;
            sample();
         }

         if ((cleanTimer += var0) >= 120.0F) {
            cleanTimer = 0.0F;
            IntSet var1 = new IntSet();
            Groups.bullet.each((var1x) -> {
               if (reflected.contains(var1x.id)) {
                  var1.add(var1x.id);
               }

            });
            reflected = var1;
         }

         for(int var3 = list.size - 1; var3 >= 0; --var3) {
            Anomaly var2 = (Anomaly)list.get(var3);
            var2.age += var0;
            if (!(var2.age >= var2.life) && !var2.dead) {
               var2.update();
            } else {
               var2.removed();
               list.remove(var3);
            }
         }

      }
   }

   static void draw() {
      if (Vars.state.isGame()) {
         GoldenGlow.draw();

         for(Anomaly var1 : list) {
            Draw.z(110.0F);
            var1.draw();
         }

         Draw.reset();
      }
   }

   static void sample() {
      ++stamp;
      Groups.unit.each((var0) -> {
         Track var1 = (Track)tracks.get(var0.id);
         if (var1 == null) {
            var1 = new Track();
            tracks.put(var0.id, var1);
         }

         var1.stamp = stamp;
         var1.xs[var1.idx] = var0.x;
         var1.ys[var1.idx] = var0.y;
         var1.idx = (var1.idx + 1) % 61;
         if (var1.count < 61) {
            ++var1.count;
         }

         if (var1.cooldown > 0) {
            --var1.cooldown;
         }

      });
      removeIds.clear();

      for(IntMap.Entry var1 : tracks) {
         if (((Track)var1.value).stamp != stamp) {
            removeIds.add(var1.key);
         }
      }

      for(int var2 = 0; var2 < removeIds.size; ++var2) {
         tracks.remove(removeIds.get(var2));
      }

   }

   public static void rollback(Unit var0) {
      Track var1 = (Track)tracks.get(var0.id);
      if (var1 == null || var1.cooldown <= 0) {
         AnomalyFx.pulse.at(var0.x, var0.y, var0.hitSize + 10.0F, Pal.heal);
         if (var1 != null && var1.count >= 61) {
            float var2 = var1.xs[var1.idx];
            float var3 = var1.ys[var1.idx];
            var0.set(var2, var3);
            var0.vel.setZero();
            var1.cooldown = 2;
            AnomalyFx.pulse.at(var2, var3, var0.hitSize + 10.0F, Pal.heal);
         } else {
            TimeRule.despawnOrRestore(var0);
         }
      }
   }

   public static void reflect(Bullet var0) {
      Entityc var2 = var0.owner;
      float var3 = var0.rotation() + 180.0F;
      if (var2 instanceof Posc) {
         Posc var1 = (Posc)var2;
         boolean var4 = !(var2 instanceof Healthc) || !((Healthc)var2).dead();
         if (var4) {
            var3 = Angles.angle(var0.x, var0.y, var1.getX(), var1.getY());
            var0.aimX = var1.getX();
            var0.aimY = var1.getY();
         }
      }

      Team var6 = var0.team == Vars.state.rules.waveTeam ? Vars.state.rules.defaultTeam : Vars.state.rules.waveTeam;
      if (var6 == var0.team) {
         var6 = Team.derelict;
      }

      var0.team = var6;
      var0.rotation(var3);
      var0.vel.setAngle(var3);
      var0.time = 0.0F;
      var0.x += Mathf.cosDeg(var3) * 3.0F;
      var0.y += Mathf.sinDeg(var3) * 3.0F;
      reflected.add(var0.id);
      AnomalyFx.pulse.at(var0.x, var0.y, 8.0F, Color.valueOf("5fd8ff"));
   }

   public static boolean spawnRandom() {
      return spawnRandom(AnomalyType.all[Mathf.random(AnomalyType.all.length - 1)]);
   }

   public static boolean spawnRandom(AnomalyType var0) {
      if (Vars.state.isGame() && Vars.world.width() > 0) {
         Anomaly var1 = (Anomaly)var0.prov.get();
         int var2 = var1.tileRadius + 2;
         if (Vars.world.width() > var2 * 2 && Vars.world.height() > var2 * 2) {
            for(int var3 = 0; var3 < 300; ++var3) {
               int var5 = Mathf.random(var2, Vars.world.width() - 1 - var2);
               int var4;
               if (free(var5, var4 = Mathf.random(var2, Vars.world.height() - 1 - var2), var1.tileRadius)) {
                  place(var1, (float)(var5 * 8), (float)(var4 * 8));
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static Anomaly spawn(AnomalyType var0, float var1, float var2) {
      Anomaly var3 = (Anomaly)var0.prov.get();
      place(var3, var1, var2);
      return var3;
   }

   static void place(Anomaly var0, float var1, float var2) {
      var0.x = var1;
      var0.y = var2;
      var0.age = 0.0F;
      var0.life = Mathf.random(54000.0F, 108000.0F);
      list.add(var0);
      var0.spawned();
   }

   static boolean free(int var0, int var1, int var2) {
      if (!SpawnGuard.ok(var0, var1, var2)) {
         return false;
      } else {
         for(int var3 = -var2; var3 <= var2; ++var3) {
            for(int var4 = -var2; var4 <= var2; ++var4) {
               Tile var5;
               if (var3 * var3 + var4 * var4 <= var2 * var2 + 1 && ((var5 = Vars.world.tile(var0 + var3, var1 + var4)) == null || var5.block() instanceof StaticWall)) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   static class Track {
      float[] xs = new float[61];
      float[] ys = new float[61];
      int idx;
      int count;
      int stamp;
      int cooldown;
   }
}
