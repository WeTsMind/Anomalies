package anomalies;

import arc.Events;
import arc.struct.Seq;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Call;
import mindustry.gen.Player;

public class AnomalySync {
   static final String TAG = "anomalies-sync";
   static final IdentityHashMap<Anomaly, Integer> sent = new IdentityHashMap();
   static final HashMap<Integer, Anomaly> remote = new HashMap();
   static Class<?>[] classes;
   static int nextId = 1;

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
               if (var8.age >= var8.life + 300.0F) {
                  drop(var8, var6, var5);
               }
            }

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

         }
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

      return -1;
   }

   static void sendAdd(Anomaly var0, int var1, Player var2) {
      int var3 = typeOf(var0);
      if (var3 >= 0) {
         String var4 = "a|" + var1 + "|" + var3 + "|" + var0.x + "|" + var0.y + "|" + var0.life + "|" + var0.age;
         var4 = AlphaSync.append(var4, var0);
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

            Anomaly var3 = (Anomaly)AnomalyType.all[Integer.parseInt(var1[2])].prov.get();
            var3 = AlphaSync.fix(var1, var3);
            var3.x = Float.parseFloat(var1[3]);
            var3.y = Float.parseFloat(var1[4]);
            var3.life = Float.parseFloat(var1[5]);
            var3.age = Float.parseFloat(var1[6]);
            remote.put(var2, var3);
            AnomalyManager.list.add(var3);
            if (var3.age < 60.0F) {
               var3.spawned();
            }
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
