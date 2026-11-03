package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.StatusEffect;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

/**
 * EMP tower (2x2). When an enemy flying unit is within 20 blocks, it sends out a yellow radiation wave. Every enemy flyer the wave reaches
 * gets an electric shock: it is stopped and takes 50 damage per second. An enemy flyer within 5 blocks of a shocked one is shocked too
 * (the current jumps through the air along a chain of lightning). The shock and the lightning last 10 seconds after the emission,
 * the next emission is possible 20 seconds after the previous one.
 * Sprites: emp-tower.png (the base), emp-tower-top.png (the spinning emitter on top), emp-tower-icon.png (menu icon), emp-shock.png (status icon).
 */
public class EmpTower extends Block {
   /** 20 blocks */
   static final float RANGE = 160.0F;
   /** 5 blocks */
   static final float CHAIN = 40.0F;
   /** 20 seconds between emissions */
   static final float COOLDOWN = 1200.0F;
   /** 10 seconds of shock and lightning */
   static final float DURATION = 600.0F;
   /** the radiation wave needs this long to reach the full range, ticks */
   static final float WAVE_TIME = 24.0F;
   static final Color glow = Color.valueOf("ffe04a"), core = Color.valueOf("fff9c8");
   static StatusEffect shock;
   static arc.audio.Sound pulseSound;
   TextureRegion topRegion;

   public EmpTower(String name) {
      super(name);
      this.size = 2;
      this.health = 600;
      this.update = true;
      this.solid = true;
      this.hasPower = true;
      this.consumePower(2.0F);
      this.requirements(Category.turret, BuildVisibility.shown, ItemStack.with(new Object[]{AnomalyMod.goldNugget, 100, Items.silicon, 100, Items.copper, 200, Items.lead, 50}));
      if (shock == null) {
         shock = new StatusEffect("emp-shock");
         shock.speedMultiplier = 0.0F; // stopped
         shock.damage = 50.0F / 60.0F; // 50 damage per second (damage of a status is per tick)
         shock.color = glow;
      }
      this.buildType = () -> new EmpTowerBuild();
   }

   public void setStats() {
      super.setStats();
      this.stats.add(Stat.range, RANGE / 8.0F, StatUnit.blocks);
   }

   public void load() {
      super.load();
      this.topRegion = Core.atlas.find(this.name + "-top");
      if (Core.atlas.has(this.name + "-icon")) {
         TextureRegion icon = Core.atlas.find(this.name + "-icon");
         this.fullIcon = icon;
         this.uiIcon = icon;
      }
   }

   public TextureRegion[] icons() {
      if (Core.atlas.has(this.name + "-icon")) {
         return new TextureRegion[]{Core.atlas.find(this.name + "-icon")};
      }
      return super.icons();
   }

   public void drawPlace(int x, int y, int rotation, boolean valid) {
      super.drawPlace(x, y, rotation, valid);
      Drawf.dashCircle(x * 8 + this.offset, y * 8 + this.offset, RANGE, Pal.accent);
   }

   /** A jagged bolt between two points; it changes its shape a few times per second. */
   static void bolt(float x1, float y1, float x2, float y2, long seed, float width) {
      float dst = Mathf.dst(x1, y1, x2, y2);
      int n = Math.max(2, (int)(dst / 7.0F));
      float nx = -(y2 - y1) / Math.max(dst, 0.001F), ny = (x2 - x1) / Math.max(dst, 0.001F);
      Mathf.randomSeed(seed);
      float px = x1, py = y1;
      for (int i = 1; i <= n; ++i) {
         float t = (float)i / n;
         float off = i == n ? 0.0F : Mathf.range(3.2F);
         float qx = Mathf.lerp(x1, x2, t) + nx * off, qy = Mathf.lerp(y1, y2, t) + ny * off;
         Lines.stroke(width);
         Lines.line(px, py, qx, qy);
         px = qx;
         py = qy;
      }
   }

   public class EmpTowerBuild extends Building {
      /** ticks until the next emission is possible */
      float cooldown = 0.0F;
      /** ticks of the current shock left, 0 = no emission going on */
      float active = 0.0F;
      float waveT = WAVE_TIME;
      float topRot = Mathf.random(360.0F);
      float chainTimer = 0.0F;
      final Seq<Unit> hit = new Seq();

      boolean ready() {
         return this.cooldown <= 0.0F && this.active <= 0.0F;
      }

      void infect(Unit u) {
         if (this.hit.contains(u)) return;
         this.hit.add(u);
         u.apply(EmpTower.shock, Math.max(this.active, 2.0F));
      }

      public void updateTile() {
         // no power: the emitter does not spin and the tower does not work
         if (this.efficiency <= 0.0F) return;
         if (this.cooldown > 0.0F) this.cooldown -= Time.delta;
         boolean server = !Vars.net.client();
         Team tm = this.team;
         float spin = 0.4F;

         if (this.active > 0.0F) {
            spin = 14.0F;
            this.active -= Time.delta;
            if (server) {
               // the wave grows from the tower and shocks every enemy flyer it reaches
               if (this.waveT < WAVE_TIME) {
                  float r = RANGE * Mathf.clamp(this.waveT / WAVE_TIME);
                  Units.nearbyEnemies(tm, this.x - RANGE, this.y - RANGE, RANGE * 2.0F, RANGE * 2.0F, u -> {
                     if (u.isFlying() && !u.dead && u.within(this, r)) this.infect(u);
                  });
               }
               // the current jumps to the flyers close to a shocked one
               this.chainTimer += Time.delta;
               if (this.chainTimer >= 5.0F) {
                  this.chainTimer = 0.0F;
                  this.hit.removeAll(u -> u.dead || !u.isAdded());
                  for (int i = 0; i < this.hit.size; ++i) {
                     Unit a = this.hit.get(i);
                     Units.nearbyEnemies(tm, a.x - CHAIN, a.y - CHAIN, CHAIN * 2.0F, CHAIN * 2.0F, v -> {
                        if (v.isFlying() && !v.dead && v.within(a, CHAIN)) this.infect(v);
                     });
                  }
               }
               // keep the status until the end of the emission
               for (int i = 0; i < this.hit.size; ++i) {
                  Unit u = this.hit.get(i);
                  if (!u.dead && u.isAdded()) u.apply(EmpTower.shock, Math.max(this.active, 2.0F));
               }
            }
            this.waveT += Time.delta;
            if (this.active <= 0.0F) {
               this.active = 0.0F;
               this.hit.clear();
            }
         } else if (this.cooldown <= 0.0F) {
            Unit target = Units.closestEnemy(tm, this.x, this.y, RANGE, u -> u.isFlying() && !u.dead);
            if (target != null) {
               // emission
               this.cooldown = COOLDOWN;
               this.active = DURATION;
               this.waveT = 0.0F;
               this.chainTimer = 0.0F;
               this.hit.clear();
               if (!Vars.headless) {
                  if (pulseSound == null) pulseSound = ModSounds.empPulse();
                  pulseSound.at(this.x, this.y);
               }
            }
         } else if (this.cooldown < COOLDOWN - 60.0F) {
            spin = 0.4F;
         }
         this.topRot += spin * Time.delta;
      }

      public void draw() {
         super.draw();
         float readyAmount = this.efficiency <= 0.0F ? 0.0F : this.active > 0.0F ? 1.0F : (this.cooldown <= 0.0F ? 0.35F : 0.0F);
         if (EmpTower.this.topRegion != null && EmpTower.this.topRegion.found()) {
            Draw.z(Layer.block + 1.0F);
            Draw.rect(EmpTower.this.topRegion, this.x, this.y, this.topRot);
            if (readyAmount > 0.0F) {
               float pulse = 0.7F + 0.3F * Mathf.absin(Time.time, 6.0F, 1.0F);
               Draw.blend(Blending.additive);
               Draw.color(glow, readyAmount * pulse * 0.8F);
               Draw.rect(EmpTower.this.topRegion, this.x, this.y, this.topRot);
               Draw.blend();
               Draw.reset();
            }
         }
         if (this.active > 0.0F) {
            Drawf.light(this.x, this.y, 36.0F, glow, 0.6F);
         }

         Draw.z(Layer.flyingUnit + 2.0F);
         Draw.blend(Blending.additive);
         // the yellow radiation wave
         if (this.waveT < WAVE_TIME) {
            float t = Mathf.clamp(this.waveT / WAVE_TIME);
            float r = RANGE * t;
            Draw.color(glow, 0.12F * (1.0F - t));
            Fill.circle(this.x, this.y, r);
            Draw.color(glow, 0.9F * (1.0F - t));
            Lines.stroke(4.0F * (1.0F - t) + 1.0F);
            Lines.circle(this.x, this.y, r);
            Draw.color(core, 0.8F * (1.0F - t));
            Lines.stroke(1.5F);
            Lines.circle(this.x, this.y, r * 0.96F);
         }
         // the electric current: sparks around every shocked enemy flyer and lightning between the close ones
         long frame = (long)(Time.time / 3.0F);
         Team tm = this.team;
         Seq<Unit> shocked = new Seq();
         Groups.unit.each(u -> {
            if (u.team != tm && !u.dead && u.hasEffect(EmpTower.shock) && u.within(this, RANGE * 3.0F)) shocked.add(u);
         });
         for (int i = 0; i < shocked.size; ++i) {
            Unit a = shocked.get(i);
            float rad = a.hitSize * 0.6F + 2.0F;
            for (int k = 0; k < 3; ++k) {
               float ang = Mathf.randomSeed(frame * 31L + a.id * 7L + k, 360.0F);
               float len = rad + Mathf.randomSeed(frame * 17L + a.id + k, 3.0F, 7.0F);
               float ex = a.x + Mathf.cosDeg(ang) * len, ey = a.y + Mathf.sinDeg(ang) * len;
               Draw.color(glow, 0.9F);
               EmpTower.bolt(a.x, a.y, ex, ey, frame * 13L + a.id * 5L + k, 1.2F);
            }
            for (int j = i + 1; j < shocked.size; ++j) {
               Unit b = shocked.get(j);
               if (a.within(b, CHAIN)) {
                  long seed = frame * 11L + a.id * 3L + b.id;
                  Draw.color(glow, 0.9F);
                  EmpTower.bolt(a.x, a.y, b.x, b.y, seed, 2.2F);
                  Draw.color(core, 0.9F);
                  EmpTower.bolt(a.x, a.y, b.x, b.y, seed, 0.9F);
               }
            }
         }
         Draw.blend();
         Draw.reset();
      }
   }
}
