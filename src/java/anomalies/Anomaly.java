package anomalies;

import arc.graphics.Color;
import arc.math.Mathf;

public abstract class Anomaly {
   public float x;
   public float y;
   public float rotation;
   public float age;
   public float life = 3600.0F;
   public int tileRadius = 2;
   public boolean dead;
   /** Permanent anomalies cannot be absorbed by the Anomaly Absorber. */
   public boolean permanent;

   public abstract Color color();

   public abstract void draw();

   public void update() {
   }

   public float fade() {
      return Mathf.clamp(Math.min(this.age, this.life - this.age) / 45.0F);
   }

   public float wx(float var1, float var2) {
      return this.x + var1 * Mathf.cosDeg(this.rotation) - var2 * Mathf.sinDeg(this.rotation);
   }

   public float wy(float var1, float var2) {
      return this.y + var1 * Mathf.sinDeg(this.rotation) + var2 * Mathf.cosDeg(this.rotation);
   }

   public void spawned() {
      AnomalyFx.spawn.at(this.x, this.y, (float)this.tileRadius * 8.0F, this.color());
   }

   public void removed() {
      AnomalyFx.pulse.at(this.x, this.y, (float)this.tileRadius * 8.0F + 8.0F, this.color());
   }
}
