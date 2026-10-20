package anomalies;

import arc.Events;
import arc.struct.Seq;
import arc.util.Time;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;

public class AnomalySync {
   static final String TAG = "anomalies-sync";
   static final IdentityHashMap<Anomaly, Integer> sent = new IdentityHashMap();
   static final HashMap<Integer, Anomaly> remote = new HashMap();
   static Class<?>[] classes;
   static int nextId = 1;
   static final int EXTRA = 100;
   static float updAcc;
   static java.util.HashSet<Integer> beamSent = new java.util.HashSet();
   static final HashMap<Integer, Float> beamStamp = new HashMap();

   public static void init() {
      Vars.netClient.addPacketHandler("anomalies-sync", AnomalySync::receive);
      Events.on(EventType.PlayerJoin.class, (var0) -> {
         if (Vars.net.server()) {
            for(Map.Entry var2 : sent.entrySet()) {
               sendAdd((Anomaly)var2.getKey(), (Integer)var2.getValue(), var0.player);
            }

         }
      });
      Events.on(EventType.WorldLoadEvent.class, (var0) -> {
         remote.clear();
         sent.clear();
         beamSent.clear();
         beamStamp.clear();
      });
      Events.run(Trigger.update, AnomalySync::update);
   }

   static void update() {
      if (Vars.state.isPlaying()) {
         if (Vars.net.client()) {
            Seq var5 = AnomalyManager.list;

            for(int var6 = var5.size - 1; var6 >= 0; --var6) {
               Anomaly var8 = (Anomaly)var5.get(var6);
               var8.age += AlphaSync.dt();
               clientTick(var8, AlphaSync.dt());
               if (var8.age >= var8.life + 300.0F) {
                  drop(var8, var6, var5);
               }
            }

            expireBeams();
         } else if (!Vars.net.server()) {
            sent.clear();
         } else {
            Seq var0 = AnomalyManager.list;
            IdentityHashMap var1 = new IdentityHashMap();

            for(int var2 = 0; var2 < var0.size; ++var2) {
               Anomaly var3 = (Anomaly)var0.get(var2);
               var1.put(var3, true);
               if (!sent.containsKey(var3)) {
                  int var4 = nextId++;
                  sent.put(var3, var4);
                  sendAdd(var3, var4, (Player)null);
               }
            }

            Iterator var7 = sent.entrySet().iterator();

            while(var7.hasNext()) {
               Map.Entry var9 = (Map.Entry)var7.next();
               if (!var1.containsKey(var9.getKey())) {
                  Call.clientPacketReliable("anomalies-sync", "r|" + String.valueOf(var9.getValue()));
                  var7.remove();
               }
            }

            serverUpdates();
         }
      }
   }

   static void clientTick(Anomaly var0, float var1) {
      if (var0 instanceof TurretBall) {
         TurretBall var2 = (TurretBall)var0;
         var2.x += var2.vx * var1;
         var2.y += var2.vy * var1;

         for(int var3 = 0; var3 < var2.st.length; ++var3) {
            if (var2.st[var3] > 0.0F) {
               var2.st[var3] -= var1;
            }
         }

         var2.act = Math.max(0.0F, var2.act - var1 / 60.0F);
      }

   }

   static boolean mobile(Anomaly var0) {
      return var0 instanceof TurretBall || var0 instanceof AlloyArcMirror;
   }

   static void serverUpdates() {
      updAcc += Time.delta;
      if (!(updAcc < 3.0F)) {
         updAcc = 0.0F;

         for(Map.Entry var1 : sent.entrySet()) {
            Anomaly var2 = (Anomaly)var1.getKey();
            if (mobile(var2) && !var2.dead) {
               StringBuilder var3 = new StringBuilder("u|").append(var1.getValue()).append('|').append(var2.x).append('|').append(var2.y).append('|').append(var2.rotation);
               if (var2 instanceof TurretBall) {
                  TurretBall var4 = (TurretBall)var2;
                  var3.append('|').append(var4.vx).append('|').append(var4.vy);
                  var3.append('|').append(var2.life).append('|').append(var2.age).append('|');

                  for(int var5 = 0; var5 < var4.st.length; ++var5) {
                     if (var4.st[var5] > 0.0F) {
                        var3.append(var5).append(',').append(var4.sx[var5]).append(',').append(var4.sy[var5]).append(',').append(var4.st[var5]).append(',').append(var4.sd[var5]).append(';');
                     }
                  }
               } else {
                  var3.append("|0|0|").append(var2.life).append('|').append(var2.age).append('|');
               }

               Call.clientPacketUnreliable("anomalies-sync", var3.toString());
            }
         }

         java.util.HashSet var6 = new java.util.HashSet();

         try {
            for(Map.Entry var8 : AnomalyHunter.beams.entrySet()) {
               Unit var9 = (Unit)var8.getKey();
               float[] var10 = (float[])var8.getValue();
               if (var9 != null && var10 != null && var9.isValid()) {
                  var6.add(var9.id);
                  Call.clientPacketUnreliable("anomalies-sync", "b|" + var9.id + "|" + var10[0] + "|" + var10[1] + "|" + var10[2]);
               }
            }
         } catch (Throwable var11) {
         }

         for(Integer var12 : beamSent) {
            if (!var6.contains(var12)) {
               Call.clientPacketReliable("anomalies-sync", "bx|" + var12);
            }
         }

         beamSent = var6;
      }
   }

   static void expireBeams() {
      java.util.Iterator var0 = beamStamp.entrySet().iterator();

      while(var0.hasNext()) {
         Map.Entry var1 = (Map.Entry)var0.next();
         if (Time.time - (Float)var1.getValue() > 20.0F) {
            Unit var2 = (Unit)Groups.unit.getByID((Integer)var1.getKey());
            if (var2 != null) {
               AnomalyHunter.beams.remove(var2);
            }

            var0.remove();
         }
      }

   }

   static Anomaly makeExtra(int var0, String var1) {
      try {
         String[] var2 = var1.split(";", -1);
         String[] var3 = var2[1].split(",");
         float var4 = Float.parseFloat(var2[0]);
         Team var5 = Team.get(Integer.parseInt(var3[0]));
         Object var6;
         if (var0 == 103) {
            AlloyArcMirror var7 = new AlloyArcMirror(Float.parseFloat(var3[1]), Float.parseFloat(var3[2]));
            var7.team = var5;
            var7.dropped = true;
            AlloyUnit.smooth(var7);
            AlloyUnit.alloyMirrors.add(var7);
            var6 = var7;
         } else {
            float var10 = Float.parseFloat(var3[3]);
            TurretBall var8;
            if (var0 == 101) {
               var8 = new AlloyBall(var10);
            } else if (var0 == 102) {
               var8 = new ZigzagBall();
            } else {
               var8 = new TurretBall(var10);
            }

            var8.team = var5;
            var8.vx = Float.parseFloat(var3[1]);
            var8.vy = Float.parseFloat(var3[2]);
            var6 = var8;
         }

         ((Anomaly)var6).rotation = var4;
         return (Anomaly)var6;
      } catch (Throwable var9) {
         return null;
      }
   }

   static String extraEnc(Anomaly var0, int var1) {
      if (var0 instanceof AlloyArcMirror) {
         AlloyArcMirror var2 = (AlloyArcMirror)var0;
         return var0.rotation + ";" + (var2.team == null ? -1 : var2.team.id) + "," + var2.hw + "," + var2.hh;
      } else {
         TurretBall var3 = (TurretBall)var0;
         return var0.rotation + ";" + (var3.team == null ? -1 : var3.team.id) + "," + var3.vx + "," + var3.vy + "," + var3.s;
      }
   }

   static void drop(Anomaly var0, int var1, Seq<Anomaly> var2) {
      var2.remove(var0);
      var0.dead = true;
      var0.removed();
   }

   static int typeOf(Anomaly var0) {
      if (classes == null) {
         AnomalyType[] var1 = AnomalyType.all;
         classes = new Class[var1.length];

         for(int var2 = 0; var2 < var1.length; ++var2) {
            classes[var2] = ((Anomaly)var1[var2].prov.get()).getClass();
         }
      }

      for(int var3 = 0; var3 < classes.length; ++var3) {
         if (classes[var3] == var0.getClass()) {
            return var3;
         }
      }

      Class var4 = var0.getClass();
      if (var4 == TurretBall.class) {
         return 100;
      } else if (var4 == AlloyBall.class) {
         return 101;
      } else if (var4 == ZigzagBall.class) {
         return 102;
      } else {
         return var4 == AlloyArcMirror.class ? 103 : -1;
      }

   }

   static void sendAdd(Anomaly var0, int var1, Player var2) {
      int var3 = typeOf(var0);
      if (var3 >= 0) {
         String var4 = "a|" + var1 + "|" + var3 + "|" + var0.x + "|" + var0.y + "|" + var0.life + "|" + var0.age;
         var4 = var3 >= 100 ? var4 + "|" + extraEnc(var0, var3) : AlphaSync.append(var4, var0);
         if (var2 == null) {
            Call.clientPacketReliable("anomalies-sync", var4);
         } else {
            Call.clientPacketReliable(var2.con, "anomalies-sync", var4);
         }

      }
   }

   static void receive(String var0) {
      try {
         String[] var1 = var0.split("\\|");
         if (var1[0].equals("a")) {
            int var2 = Integer.parseInt(var1[1]);
            if (remote.containsKey(var2)) {
               return;
            }

            int var9 = Integer.parseInt(var1[2]);
            Anomaly var3;
            if (var9 >= 100) {
               var3 = makeExtra(var9, var1[7]);
               if (var3 == null) {
                  return;
               }
            } else {
               var3 = (Anomaly)AnomalyType.all[var9].prov.get();
               var3 = AlphaSync.fix(var1, var3);
            }

            var3.x = Float.parseFloat(var1[3]);
            var3.y = Float.parseFloat(var1[4]);
            var3.life = Float.parseFloat(var1[5]);
            var3.age = Float.parseFloat(var1[6]);
            remote.put(var2, var3);
            AnomalyManager.list.add(var3);
            if (var3.age < 60.0F) {
               var3.spawned();
            }
         } else if (var1[0].equals("u")) {
            Anomaly var6 = (Anomaly)remote.get(Integer.parseInt(var1[1]));
            if (var6 == null) {
               return;
            }

            var6.x = Float.parseFloat(var1[2]);
            var6.y = Float.parseFloat(var1[3]);
            var6.rotation = Float.parseFloat(var1[4]);
            var6.life = Float.parseFloat(var1[7]);
            var6.age = Float.parseFloat(var1[8]);
            if (var6 instanceof TurretBall) {
               TurretBall var7 = (TurretBall)var6;
               var7.vx = Float.parseFloat(var1[5]);
               var7.vy = Float.parseFloat(var1[6]);
               if (var1.length > 9) {
                  for(String var11 : var1[9].split(";")) {
                     String[] var12 = var11.split(",");
                     if (var12.length == 5) {
                        int var13 = Integer.parseInt(var12[0]);
                        var7.sx[var13] = Float.parseFloat(var12[1]);
                        var7.sy[var13] = Float.parseFloat(var12[2]);
                        var7.st[var13] = Float.parseFloat(var12[3]);
                        var7.sd[var13] = Integer.parseInt(var12[4]);
                        var7.act = 1.0F;
                     }
                  }
               }
            }
         } else if (var1[0].equals("b")) {
            Unit var14 = (Unit)Groups.unit.getByID(Integer.parseInt(var1[1]));
            if (var14 != null) {
               float[] var15 = (float[])AnomalyHunter.beams.get(var14);
               if (var15 == null) {
                  var15 = new float[3];
                  AnomalyHunter.beams.put(var14, var15);
               }

               var15[0] = Float.parseFloat(var1[2]);
               var15[1] = Float.parseFloat(var1[3]);
               var15[2] = Float.parseFloat(var1[4]);
               beamStamp.put(Integer.parseInt(var1[1]), Time.time);
            }
         } else if (var1[0].equals("bx")) {
            int var16 = Integer.parseInt(var1[1]);
            Unit var17 = (Unit)Groups.unit.getByID(var16);
            if (var17 != null) {
               AnomalyHunter.beams.remove(var17);
            }

            beamStamp.remove(var16);
         } else if (var1[0].equals("r")) {
            Anomaly var5 = (Anomaly)remote.remove(Integer.parseInt(var1[1]));
            if (var5 != null && AnomalyManager.list.remove(var5)) {
               var5.dead = true;
               var5.removed();
            }
         }
      } catch (Throwable var4) {
      }

   }
}
