package anomalies;

import arc.Core;
import arc.func.Prov;

public enum AnomalyType {
   mirror(MirrorAnomaly::new, MirrorAnomaly::alpha),
   distortion(DistortionAnomaly::new),
   time(TimeAnomaly::new),
   vortex(VortexAnomaly::new),
   ball(BallLightningAnomaly::new, () -> new BallLightningAnomaly(AlphaSettings.scale())),
   blackhole(BlackHoleAnomaly::new, () -> new BlackHoleAnomaly(AlphaSettings.scale())),
   warp(WarpAnomaly::new, () -> new WarpAnomaly(AlphaSettings.scale())),
   fire(FireBurstAnomaly::new),
   toxic(ToxicAnomaly::new);

   public static final AnomalyType[] all = values();
   public final Prov<Anomaly> prov;

   private AnomalyType(Prov<Anomaly> var3) {
      this(var3, (Prov)null);
   }

   private AnomalyType(Prov<Anomaly> var3, Prov<Anomaly> var4) {
      this.prov = () -> var4 != null && AlphaSettings.roll() ? (Anomaly)var4.get() : (Anomaly)var3.get();
   }

   public String title() {
      return Core.bundle.get("anomaly." + this.name(), this.name());
   }

   // $FF: synthetic method
   private static AnomalyType[] $values() {
      return new AnomalyType[]{mirror, distortion, time, vortex, ball, blackhole, warp, fire, toxic};
   }
}
