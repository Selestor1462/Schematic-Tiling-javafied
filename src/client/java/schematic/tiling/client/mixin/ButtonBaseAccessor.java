package schematic.tiling.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import fi.dy.masa.malilib.gui.button.ButtonBase;

@Mixin(ButtonBase.class)
public interface ButtonBaseAccessor {
    @Accessor("displayString")
    String getDisplayString();
}

