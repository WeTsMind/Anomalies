package anomalies;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

/**
 * Anomaly Absorber: while powered, it picks the nearest enemy anomaly within 8 tiles and absorbs it with a beam (same look as the
 * Anomaly Hunter's beam) in 5 seconds. It never touches permanent anomalies, ball lightning of a turret, Alloy mirror shields
 * or anomalies of its own team.
 */
public class AnomalyAbsorber extends Block {
   static final Color glow = Color.valueOf("1f6bff");
   static final Color mid = Color.valueOf("4aa3ff");
   static final Color core = Color.valueOf("d8edff");
   /** Absorb radius, tiles. */
   public static final float RANGE = 8.0F;
   /** Time of the beam needed to absorb one anomaly, ticks (5 s). */
   public static final float ABSORB_TIME = 300.0F;
   /** Distance from the block centre to the beam start (the gap of the ring), world units. */
   static final float MUZZLE = 6.5F;

   public TextureRegion ringRegion;

   public AnomalyAbsorber(String name) {
      super(name);
      this.size = 2;
      this.solid = true;
      this.update = true;
      this.hasPower = true;
      this.destructible = true;
      this.health = 500;
      this.requirements(Category.effect, BuildVisibility.shown, ItemStack.with(new Object[]{AnomalyMod.polcim, 40, Items.silicon, 80, Items.titanium, 40, Items.plastanium, 30}));
      this.consumePower(2.0F);
      this.buildType = () -> new AbsorberBuild();
   }

   public void load() {
      super.load();
      this.ringRegion = Core.atlas.find(this.name + "-ring");
   }

   public void drawPlace(int x, int y, int rotation, boolean valid) {
      super.drawPlace(x, y, rotation, valid);
      Drawf.dashCircle((float)x * 8.0F + this.offset, (float)y * 8.0F + this.offset, RANGE * 8.0F, mid);
   }

   public class AbsorberBuild extends Building {
      Anomaly target;
      float beam;
      float ringRot;
      float heat;
      float scanTimer;

      boolean active() {
         return this.power != null && this.power.status > 0.01F;
      }

      /** Enemy anomalies only; permanent ones, turret ball lightning and Alloy mirror shields are never absorbed. */
      boolean valid(Anomaly a) {
         if (a == null || a.dead || a.permanent || a.life - a.age < 60.0F) {
            return false;
         }

         if (a instanceof TurretBall || AlloyUnit.alloyMirrors.contains(a)) {
            return false;
         }

         if (a instanceof MirrorAnomaly && ((MirrorAnomaly)a).team != null && ((MirrorAnomaly)a).team == this.team) {
            return false;
         }

         return Mathf.dst(this.x, this.y, a.x, a.y) <= RANGE * 8.0F;
      }

      public void updateTile() {
         boolean on = this.active();
         this.heat = Mathf.lerpDelta(this.heat, on ? 1.0F : 0.0F, 0.04F);
         if (!on) {
            this.target = null;
            this.beam = Math.max(0.0F, this.beam - Time.delta * 2.0F);
            return;
         }

         if (this.target != null && !this.valid(this.target)) {
            this.target = null;
            this.beam = 0.0F;
         }

         if (this.target == null) {
            this.beam = Math.max(0.0F, this.beam - Time.delta * 2.0F);
            this.ringRot += 0.6F * Time.delta;
            this.scanTimer += Time.delta;
            if (this.scanTimer >= 10.0F) {
               this.scanTimer = 0.0F;
               float best = Float.MAX_VALUE;

               for(int i = 0; i < AnomalyManager.list.size; ++i) {
                  Anomaly a = (Anomaly)AnomalyManager.list.get(i);
                  float d;
                  if (this.valid(a) && (d = Mathf.dst(this.x, this.y, a.x, a.y)) < best) {
                     best = d;
                     this.target = a;
                  }
               }
            }

            return;
         }

         // the ring turns its gap towards the target; the beam only charges once it is aimed
         float want = Angles.angle(this.x, this.y, this.target.x, this.target.y) - 90.0F;
         this.ringRot = Angles.moveToward(this.ringRot, want, 7.0F * Time.delta);
         if (Angles.angleDist(this.ringRot, want) < 8.0F) {
            this.beam = Math.min(ABSORB_TIME, this.beam + Time.delta * Mathf.clamp(this.power.status));
         } else {
            this.beam = Math.max(0.0F, this.beam - Time.delta * 2.0F);
         }

         if (this.beam >= ABSORB_TIME) {
            if (!Vars.net.client()) {
               if (this.target instanceof MirrorAnomaly && ((MirrorAnomaly)this.target).team != null) {
                  this.target.dead = true;
               } else {
                  this.target.life = Math.min(this.target.life, this.target.age + 40.0F);
               }
            }

            AnomalyFx.pulse.at(this.target.x, this.target.y, (float)this.target.tileRadius * 8.0F + 8.0F, mid);
            this.target = null;
            this.beam = 0.0F;
         }
      }

      public void draw() {
         Draw.rect(this.block.region, this.x, this.y);
         float pulse = 0.75F + 0.25F * (float)Math.sin((double)(Time.time / 18.0F));
         Draw.z(Layer.block + 0.1F);
         Draw.rect(AnomalyAbsorber.this.ringRegion, this.x, this.y, this.ringRot);
         if (this.heat < 0.01F) {
            return;
         }

         float p = this.target == null ? 0.0F : Math.min(1.0F, this.beam / 20.0F);
         Draw.z(116.0F);
         Draw.blend(Blending.additive);
         Draw.color(mid, 0.3F * this.heat * pulse);
         Fill.circle(this.x + Mathf.cosDeg(this.ringRot + 90.0F) * MUZZLE, this.y + Mathf.sinDeg(this.ringRot + 90.0F) * MUZZLE, 2.2F * this.heat);
         if (this.target != null && p > 0.01F) {
            float sx = this.x + Mathf.cosDeg(this.ringRot + 90.0F) * MUZZLE;
            float sy = this.y + Mathf.sinDeg(this.ringRot + 90.0F) * MUZZLE;
            float tx = this.target.x;
            float ty = this.target.y;
            float flick = 0.8F + Mathf.absin(Time.time, 3.0F, 0.2F);
            float[] widths = new float[]{6.0F, 3.2F, 1.4F};
            float[] alphas = new float[]{0.25F, 0.75F, 1.0F};

            for(int i = 0; i < 3; ++i) {
               Lines.stroke(widths[i] * (0.5F + 0.5F * p) * flick);
               Draw.color(i == 2 ? core : (i == 1 ? mid : glow), alphas[i] * p);
               Lines.line(sx, sy, tx, ty);
            }

            // the anomaly shrinks into the beam as it gets absorbed
            float left = 1.0F - this.beam / ABSORB_TIME;
            Draw.color(glow, 0.35F * p * flick);
            Fill.circle(tx, ty, (3.0F + 4.0F * left) * p);
            Draw.color(core, p);
            Fill.circle(tx, ty, 2.0F * p);
            Draw.color(mid, 0.6F * p);
            Fill.circle(sx, sy, 3.0F * p);
         }

         Draw.blend();
         Draw.reset();
         Drawf.light(this.x, this.y, 26.0F + 10.0F * this.heat, mid, 0.35F * this.heat * pulse);
      }

      public void drawSelect() {
         super.drawSelect();
         Drawf.dashCircle(this.x, this.y, RANGE * 8.0F, mid);
      }
   }
}
