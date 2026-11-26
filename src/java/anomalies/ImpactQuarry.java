package anomalies;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.ObjectFloatMap;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Items;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.meta.BuildVisibility;

/**
 * Impact quarry ("ударный карьер"), 2x2. Every strike knocks out 15-18 ore. Each of the 4 tiles under it is mined on its own,
 * so a quarry over 2, 3 or 4 different ores gives all of them, the strike's ore split between the tiles (a tile without ore
 * gives nothing). The rates below are items per second over 4 tiles of that ore; the time between strikes is 16.5 / rate
 * (the mean over the ore tiles when they differ), e.g. 22 s on copper.
 *
 * It only works when fed: copper, graphite or lead, 5 of them used per strike. Graphite gives 10% more ore, lead strikes 5%
 * faster, copper is the plain fuel. The fuel is kept apart from the mined ore (so mined copper is never burnt), 20 of each.
 *
 * Sprites from sprite01.ase: impact-quarry.png the frame, impact-quarry-top.png the hammer.
 */
public class ImpactQuarry extends Block {
   public static final int MIN_ORE = 15, MAX_ORE = 18;
   public static final int FUEL_PER_STRIKE = 5;
   public static final int FUEL_CAPACITY = 20;
   public static final float GRAPHITE_BONUS = 1.1F, LEAD_SPEED = 1.05F;
   static final float SHOCK_TIME = 26.0F;
   static final Color shockColor = Color.valueOf("c9c9cc");
   static final Color dustColor = Color.valueOf("4a4a4f");

   /** items per second for each ore this quarry can take */
   public final ObjectFloatMap<Item> rates = new ObjectFloatMap<>();
   /** fuel in the order it is used: graphite first (more ore), then lead (faster), then copper */
   public Item[] fuels;
   /** false: works without any fuel (the jackhammer drill) */
   public boolean needsFuel = true;
   /** quarry: region = frame, -top = still top part. Jackhammer: -bottom under everything, -hammer the rhombus that
    *  rises before each strike, region = frame drawn over it. */
   public TextureRegion topRegion, bottomRegion, hammerRegion;
   static final Seq<Tile> tmpTiles = new Seq<>();

   /** dark grey dust rising over the quarry */
   public static final Effect dust = new Effect(80.0F, e -> {
      Draw.color(dustColor, Color.valueOf("2e2e31"), e.fin());
      Draw.alpha(0.85F * e.fout());
      Angles.randLenVectors(e.id, 12, 3.0F + e.finpow() * 15.0F, (x, y) ->
         Fill.circle(e.x + x, e.y + y + e.fin() * 5.0F, 1.6F + e.fin() * 3.6F));
      Draw.reset();
   }).layer(Layer.effect + 0.5F);

   /** ore fragments flying out from under the dust, in the ore colour */
   public static final Effect fragments = new Effect(42.0F, e -> {
      Draw.color(e.color, Color.gray, e.fin() * 0.4F);
      Draw.alpha(e.fout());
      Angles.randLenVectors(e.id, 8, 4.0F + e.finpow() * 17.0F, (x, y) ->
         Fill.square(e.x + x, e.y + y - e.fin() * e.fin() * 3.0F, 0.5F + 1.1F * e.fout(), 45.0F + e.fin() * 90.0F));
      Draw.reset();
   }).layer(Layer.effect);

   public ImpactQuarry(String name) {
      super(name);
      this.size = 2;
      this.solid = true;
      this.update = true;
      this.destructible = true;
      this.hasItems = true;
      this.acceptsItems = true;
      this.itemCapacity = 20;
      this.health = 320;
      this.alwaysUnlocked = true;
      this.requirements(Category.production, BuildVisibility.shown, ItemStack.with(new Object[]{AnomalyMod.goldNugget, 20, Items.copper, 30, Items.graphite, 10}));
      this.rates.put(Items.copper, 0.75F);
      this.rates.put(Items.lead, 0.77F);
      this.rates.put(Items.sand, 0.65F);
      this.rates.put(Items.coal, 0.65F);
      this.rates.put(Items.titanium, 0.85F);
      this.rates.put(Items.scrap, 0.55F);
      this.rates.put(AnomalyMod.gold, 0.75F);
      this.rates.put(AnomalyMod.polcim, 0.80F);
      this.rates.put(AnomalyMod.ruby, 0.68F);
      this.fuels = new Item[]{Items.graphite, Items.lead, Items.copper};
      this.buildType = () -> new QuarryBuild();
   }

   @Override
   public void load() {
      super.load();
      this.topRegion = Core.atlas.find(this.name + "-top");
      this.bottomRegion = Core.atlas.find(this.name + "-bottom");
      this.hammerRegion = Core.atlas.find(this.name + "-hammer");
   }

   boolean layered() {
      return this.bottomRegion != null && this.bottomRegion.found();
   }

   @Override
   protected TextureRegion[] icons() {
      return this.layered() ? new TextureRegion[]{this.bottomRegion, this.hammerRegion, this.region} : new TextureRegion[]{this.region, this.topRegion};
   }

   /** The "Drills:" panel of the vanilla drills: every floor/ore it mines with its rate. drillables shows
    * 60 / (drillTime / multiplier) * size, so drillTime 60 and size 1 make it show exactly the rate from {@link #rates}. */
   @Override
   public void setStats() {
      super.setStats();
      this.stats.add(mindustry.world.meta.Stat.drillTier, mindustry.world.meta.StatValues.drillables(60.0F, 0.0F, 1.0F, this.rates,
         b -> b instanceof mindustry.world.blocks.environment.Floor && !((mindustry.world.blocks.environment.Floor)b).wallOre
            && b.itemDrop != null && this.rates.containsKey(b.itemDrop)));
   }

   @Override
   public void setBars() {
      super.setBars();
      if (this.needsFuel) this.addBar("anomalies-quarry-fuel", (QuarryBuild b) -> new Bar(
         () -> Core.bundle.format("anomalies.quarry.fuel", b.fuelTotal(), FUEL_CAPACITY),
         () -> b.fuelItem() == null ? Pal.remove : Pal.ammo,
         () -> b.fuelTotal() / (float)FUEL_CAPACITY));
      this.addBar("anomalies-quarry-progress", (QuarryBuild b) -> new Bar(
         () -> Core.bundle.format("anomalies.quarry.strike", (int)(b.progress * 100.0F)),
         () -> Pal.accent,
         () -> b.progress));
   }

   /** the ore a tile gives this quarry, or null */
   Item oreOf(Tile t) {
      Item i = t == null ? null : t.drop();
      return i != null && this.rates.containsKey(i) ? i : null;
   }

   /** Can only stand where at least one of its 4 tiles has an ore it can mine. */
   @Override
   public boolean canPlaceOn(Tile tile, Team team, int rotation) {
      if (tile == null) return false;
      tile.getLinkedTilesAs(this, tmpTiles);
      for (Tile t : tmpTiles) {
         if (this.oreOf(t) != null) return true;
      }
      return false;
   }

   public class QuarryBuild extends Building {
      /** 0..1 towards the next strike */
      public float progress;
      /** fuel kept apart from the mined ore, in the order of {@link #fuels} */
      public int[] fuel = new int[3];
      public int strikes;
      public float lastStrike = -1000.0F;
      /** ore tiles under the quarry, counted once */
      Item[] ores;
      int[] oreTiles;
      float interval;
      /** fractions of ore left over from the split, carried to the next strike */
      float[] carry;

      void countOres() {
         Seq<Item> items = new Seq<>();
         Seq<Integer> counts = new Seq<>();
         float rateSum = 0.0F;
         int tiles = 0;
         this.tile.getLinkedTiles(ImpactQuarry.tmpTiles);
         for (Tile t : ImpactQuarry.tmpTiles) {
            Item i = ImpactQuarry.this.oreOf(t);
            if (i == null) continue;
            int at = items.indexOf(i);
            if (at < 0) {
               items.add(i);
               counts.add(1);
            } else {
               counts.set(at, counts.get(at) + 1);
            }
            rateSum += ImpactQuarry.this.rates.get(i, 0.75F);
            tiles++;
         }
         this.ores = items.toArray(Item.class);
         this.oreTiles = new int[this.ores.length];
         for (int k = 0; k < this.ores.length; k++) this.oreTiles[k] = counts.get(k);
         this.carry = new float[this.ores.length];
         float meanRate = tiles == 0 ? 0.75F : rateSum / tiles;
         this.interval = 60.0F * ((MIN_ORE + MAX_ORE) / 2.0F) / meanRate;
      }

      public int fuelTotal() {
         return this.fuel[0] + this.fuel[1] + this.fuel[2];
      }

      /** index of the fuel the next strike uses, or -1 */
      int fuelIndex() {
         for (int k = 0; k < this.fuel.length; k++) {
            if (this.fuel[k] >= FUEL_PER_STRIKE) return k;
         }
         return -1;
      }

      public Item fuelItem() {
         int k = this.fuelIndex();
         return k < 0 ? null : ImpactQuarry.this.fuels[k];
      }

      @Override
      public boolean acceptItem(Building source, Item item) {
         if (!ImpactQuarry.this.needsFuel || source == this || this.fuelTotal() >= FUEL_CAPACITY) return false;
         for (Item f : ImpactQuarry.this.fuels) {
            if (f == item) return true;
         }
         return false;
      }

      @Override
      public int acceptStack(Item item, int amount, mindustry.gen.Teamc source) {
         if (!this.acceptItem(null, item)) return 0;
         return Math.min(amount, FUEL_CAPACITY - this.fuelTotal());
      }

      @Override
      public void handleItem(Building source, Item item) {
         this.handleStack(item, 1, null);
      }

      @Override
      public void handleStack(Item item, int amount, mindustry.gen.Teamc source) {
         for (int k = 0; k < ImpactQuarry.this.fuels.length; k++) {
            if (ImpactQuarry.this.fuels[k] == item) {
               this.fuel[k] = Math.min(this.fuel[k] + amount, FUEL_CAPACITY);
               return;
            }
         }
      }

      @Override
      public void updateTile() {
         if (this.ores == null) this.countOres();
         this.dump();
         if (this.ores.length == 0) return;
         int f = ImpactQuarry.this.needsFuel ? this.fuelIndex() : -1;
         if (ImpactQuarry.this.needsFuel && f < 0) return;
         Item fuelItem = f < 0 ? null : ImpactQuarry.this.fuels[f];
         float speed = fuelItem == Items.lead ? LEAD_SPEED : 1.0F;
         if (this.progress < 1.0F) {
            this.progress = Math.min(1.0F, this.progress + this.edelta() * speed / this.interval);
         }
         // strike only when the whole knocked-out ore fits
         float bonus = fuelItem == Items.graphite ? GRAPHITE_BONUS : 1.0F;
         if (this.progress >= 1.0F && this.items.total() + Mathf.ceil(MAX_ORE * bonus) <= ImpactQuarry.this.itemCapacity) {
            this.strike(f, bonus);
         }
      }

      void strike(int f, float bonus) {
         this.progress = 0.0F;
         if (f >= 0) this.fuel[f] -= FUEL_PER_STRIKE;
         this.strikes++;
         this.lastStrike = Time.time;
         // same amount on every client: seeded by the building and the strike number
         float total = Mathf.randomSeed(this.id * 31L + this.strikes, MIN_ORE, MAX_ORE) * bonus;
         float perTile = total / (ImpactQuarry.this.size * ImpactQuarry.this.size);
         for (int k = 0; k < this.ores.length; k++) {
            float got = perTile * this.oreTiles[k] + this.carry[k];
            int n = (int)got;
            this.carry[k] = got - n;
            if (n > 0) this.items.add(this.ores[k], n);
            fragments.at(this.x + Mathf.range(3.0F), this.y + Mathf.range(3.0F), 0.0F, this.ores[k].color);
         }
         dust.at(this.x, this.y);
         Effect.shake(1.2F, 8.0F, this.x, this.y);
      }

      @Override
      public void draw() {
         boolean layered = ImpactQuarry.this.layered();
         Draw.rect(layered ? ImpactQuarry.this.bottomRegion : ImpactQuarry.this.region, this.x, this.y);
         // light grey shock wave spreading under the hammer
         float t = (Time.time - this.lastStrike) / SHOCK_TIME;
         // round wave, a little above the blocks so it is seen over the neighbours too (reaches ~3 tiles from the centre),
         // but still under this quarry's top part
         if (t >= 0.0F && t < 1.0F) {
            float z = Draw.z();
            Draw.z(Layer.block + 0.1F);
            float rad = 4.0F + Mathf.pow(t, 0.55F) * 24.0F;
            Draw.color(shockColor, 1.0F - t);
            Lines.stroke(2.6F * (1.0F - t) + 0.4F);
            Lines.circle(this.x, this.y, rad);
            Draw.alpha(0.25F * (1.0F - t));
            Fill.circle(this.x, this.y, rad * 0.8F);
            Draw.reset();
            Draw.z(z);
         }
         float z0 = Draw.z();
         Draw.z(Layer.block + 0.2F);
         if (layered) {
            // jackhammer: the rhombus rises (grows) over the last fifth of the charge, drops on the strike with a small squash,
            // and the frame is drawn over it
            float lift = Mathf.clamp((this.progress - 0.8F) / 0.2F);
            lift = lift * lift * (3.0F - 2.0F * lift);
            float squash = t >= 0.0F && t < 0.4F ? 0.1F * (1.0F - t / 0.4F) : 0.0F;
            float scl = 1.0F + 0.25F * lift - squash;
            TextureRegion h = ImpactQuarry.this.hammerRegion;
            Draw.rect(h, this.x, this.y, h.width * Draw.scl * scl, h.height * Draw.scl * scl);
            Draw.z(Layer.block + 0.3F);
            Draw.rect(ImpactQuarry.this.region, this.x, this.y);
         } else {
            // quarry: the top part stays still, it fires a (not drawn) shell into the ground
            Draw.rect(ImpactQuarry.this.topRegion, this.x, this.y);
         }
         Draw.z(z0);
      }

      @Override
      public void write(Writes w) {
         super.write(w);
         w.f(this.progress);
         w.i(this.strikes);
         for (int k = 0; k < 3; k++) w.s(this.fuel[k]);
      }

      @Override
      public void read(Reads r, byte revision) {
         super.read(r, revision);
         this.progress = r.f();
         this.strikes = r.i();
         for (int k = 0; k < 3; k++) this.fuel[k] = r.s();
      }
   }
}
