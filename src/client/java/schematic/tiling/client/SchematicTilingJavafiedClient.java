package schematic.tiling.client;

import java.io.File;
import java.nio.file.Path;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.malilib.gui.GuiBase;

import schematic.tiling.cannon.PearlCalculator;
import schematic.tiling.client.gui.GuiReturnCannonDebug;

public class SchematicTilingJavafiedClient implements ClientModInitializer {
	private static boolean pendingOpenDebugGui = false;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (pendingOpenDebugGui) {
				pendingOpenDebugGui = false;
				Path gameDir = Path.of(".");
				try {
					File schemDir = DataManager.getSchematicsBaseDirectory();
					if (schemDir != null && schemDir.getParentFile() != null) {
						gameDir = schemDir.getParentFile().toPath();
					}
				} catch (Throwable ignored) {}
				GuiBase.openGui(new GuiReturnCannonDebug(null, PearlCalculator.loadSettings(gameDir)));
			}
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("tiling")
				.then(ClientCommandManager.literal("debug")
					.executes(ctx -> {
						GuiReturnCannonDebug.debugModeEnabled = !GuiReturnCannonDebug.debugModeEnabled;
						String state = GuiReturnCannonDebug.debugModeEnabled ? "\u00a7aENABLED" : "\u00a7cDISABLED";
						ctx.getSource().sendFeedback(Component.literal("\u00a76[Schematic Tiling] \u00a7fReturn Cannon debug menu is now " + state + "."));
						return 1;
					})
					.then(ClientCommandManager.literal("open")
						.executes(ctx -> {
							GuiReturnCannonDebug.debugModeEnabled = true;
							pendingOpenDebugGui = true;
							return 1;
						})
					)
				)
			);
		});
	}
}