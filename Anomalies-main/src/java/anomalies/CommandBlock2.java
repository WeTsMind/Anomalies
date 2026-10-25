package anomalies;

import arc.Core;
import arc.scene.ui.layout.Table;
import mindustry.ui.Styles;

public class CommandBlock2 extends CommandBlock {
   public CommandBlock2(String var1) {
      super(var1);
      this.buildType = () -> new Build2();
   }

   public class Build2 extends CommandBlock.CommandBuild {
      public void buildConfiguration(Table var1) {
         super.buildConfiguration(var1);
         var1.row();
         var1.button(Core.bundle.get("anomalies.alpha", "Alpha anomalies (2-3x)"), Styles.togglet, () -> AnomalyNet.set("anom.alpha", AlphaSettings.enabled() ? "0" : "1")).checked((var0) -> AlphaSettings.enabled()).size(250.0F, 40.0F);
         var1.row();
         UiHint.add(var1, "anomalies.alpha.hint", "Anomalies spawn 2-3x bigger.", 1);
         TimeRule.addButton(var1);
      }
   }
}
