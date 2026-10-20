package anomalies;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureAtlas;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Prop;

/** Куст из одинаковых отростков, расходящихся лучами от центра клетки. */
public class BushProp extends Prop {
   final int shoots;
   final float scale;
   final float pivotX;
   final float pivotY;
   final boolean randomRotation;
   TextureRegion shoot;

   /**
    * @param shoots число отростков
    * @param scale масштаб отростка
    * @param pivotX точка вращения по X в пикселях спрайта (от левого края)
    * @param pivotY точка вращения по Y в пикселях спрайта (от нижнего края)
    * @param randomRotation случайный поворот куста на каждой клетке (для кактуса-куста), иначе строго по осям
    */
   public BushProp(String var1, int var2, float var3, float var4, float var5, boolean var6) {
      super(var1);
      this.shoots = var2;
      this.scale = var3;
      this.pivotX = var4;
      this.pivotY = var5;
      this.randomRotation = var6;
      this.variants = 0;
   }

   public void load() {
      super.load();
      this.shoot = Core.atlas.find(this.name);

      try {
         TextureAtlas.AtlasRegion var1 = Core.atlas.find(this.name + "-preview");
         if (var1.found()) {
            this.fullIcon = var1;
            this.uiIcon = var1;
         }
      } catch (Throwable var2) {
      }

   }

   public void drawBase(Tile var1) {
      float var2 = var1.worldx();
      float var3 = var1.worldy();
      float var4 = (float)this.shoot.width * Draw.scl * this.scale;
      float var5 = (float)this.shoot.height * Draw.scl * this.scale;
      float var6 = this.pivotX * Draw.scl * this.scale;
      float var7 = this.pivotY * Draw.scl * this.scale;
      float var8 = 0.0F;
      if (this.randomRotation) {
         Mathf.randomSeed((long)var1.pos());
         var8 = Mathf.random(360.0F);
      }

      Draw.z(this.layer);

      for(int var9 = 0; var9 < this.shoots; ++var9) {
         float var10 = var8 + 360.0F / (float)this.shoots * (float)var9;
         Draw.rect(this.shoot, var2 - var6, var3 - var7, var4, var5, var6, var7, var10);
      }

   }
}
