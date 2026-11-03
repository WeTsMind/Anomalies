package anomalies;

import arc.graphics.Color;
import arc.math.Mathf;
import mindustry.gen.Bullet;

/**
 * The main shell of the Cutter turret. At the moment of the shot, behind it, shards fly apart to both sides
 * (each shard is a {@link WreckBullet} of its own, in the same colour).
 */
public class CutterBullet extends WreckBullet {
   /** angles of the shards from the flight direction, degrees (they are mirrored to the other side) */
   static final float[] SHARD_ANGLES = new float[]{38.0F, 78.0F};
   /** how far behind the shell the shards start, world units */
   static final float BEHIND = 3.0F;
   public final WreckBullet shard;

   public CutterBullet(float speed, float damage, float shardDamage, Color color) {
      super(speed, damage, color);
      this.spriteName = "anomalies-cutter-bullet";
      this.spin = false;
      this.spriteScale = 1.25F;
      this.lifetime = 30.0F;
      this.shard = new WreckBullet(4.6F, shardDamage, color);
      this.shard.spriteName = "anomalies-cutter-shard";
      this.shard.spin = false;
      this.shard.spriteScale = 1.1F;
      this.shard.lifetime = 28.0F;
      this.shard.drag = 0.07F;
      this.shard.hitSize = 4.0F;
      this.shard.trailLength = 6;
      this.shard.trailWidth = 1.1F;
   }

   public void load() {
      super.load();
      this.shard.load();
   }

   public void init() {
      super.init();
      this.shard.init();
   }

   public void init(Bullet b) {
      super.init(b);
      float c = Mathf.cosDeg(b.rotation()), s = Mathf.sinDeg(b.rotation());
      float bx = b.x - c * BEHIND, by = b.y - s * BEHIND;
      for (int i = 0; i < SHARD_ANGLES.length; ++i) {
         for (int side = -1; side <= 1; side += 2) {
            this.shard.create(b.owner, b.team, bx, by, b.rotation() + side * SHARD_ANGLES[i]);
         }
      }
   }
}
