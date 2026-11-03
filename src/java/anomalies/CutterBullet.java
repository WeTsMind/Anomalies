package anomalies;

import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
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
   /** the shards appear only after the shell has flown this far, world units (4 blocks) */
   static final float SHARD_DIST = 32.0F;
   /** a shard flies apart for this long, then turns towards the shell for HOME_TIME more, ticks */
   static final float APART_TIME = 60.0F, HOME_TIME = 120.0F;
   public final WreckBullet shard;

   /** A shard: slows down while flying apart (1 s), then flies towards the last known position of the main shell (2 s). */
   static class Shard extends WreckBullet {
      Shard(float speed, float damage, Color color) {
         super(speed, damage, color);
      }

      public void update(Bullet b) {
         super.update(b);
         if (b.time < APART_TIME) {
            b.vel.scl(Math.max(1.0F - 0.07F * Time.delta, 0.01F));
            return;
         }
         if (!(b.data instanceof Vec2)) return;
         Vec2 t = (Vec2)b.data;
         float sp = Mathf.approachDelta(b.vel.len(), this.speed, 0.3F);
         if (!b.within(t.x, t.y, 4.0F)) {
            b.vel.setAngle(Angles.moveToward(b.rotation(), Angles.angle(b.x, b.y, t.x, t.y), 8.0F * Time.delta));
         }
         b.vel.setLength(sp);
      }
   }

   public CutterBullet(float speed, float damage, float shardDamage, Color color) {
      super(speed, damage, color);
      this.spriteName = "anomalies-cutter-bullet";
      this.spin = false;
      this.spriteScale = 1.25F;
      this.lifetime = 30.0F;
      this.shard = new Shard(4.6F, shardDamage, color);
      this.shard.spriteName = "anomalies-cutter-shard";
      this.shard.spin = false;
      this.shard.spriteScale = 1.1F;
      this.shard.lifetime = APART_TIME + HOME_TIME;
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

   public void update(Bullet b) {
      super.update(b);
      // the shards fly to where the shell is (or where it ended)
      Vec2 last = b.data instanceof Vec2 ? (Vec2)b.data : null;
      if (last == null) {
         last = new Vec2(b.x, b.y);
         b.data = last;
      } else {
         last.set(b.x, b.y);
      }
      if (b.fdata != 0.0F || b.time * this.speed < SHARD_DIST) return;
      b.fdata = 1.0F;
      float c = Mathf.cosDeg(b.rotation()), s = Mathf.sinDeg(b.rotation());
      float bx = b.x - c * BEHIND, by = b.y - s * BEHIND;
      for (int i = 0; i < SHARD_ANGLES.length; ++i) {
         for (int side = -1; side <= 1; side += 2) {
            Bullet sb = this.shard.create(b.owner, b.team, bx, by, b.rotation() + side * SHARD_ANGLES[i]);
            if (sb != null) sb.data = last;
         }
      }
   }
}
