package schematic.tiling.client.mixin;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;

@Mixin(GuiBase.class)
public interface GuiBaseAccessor {
    @Accessor("buttons")
    List<ButtonBase> getButtons();
}

