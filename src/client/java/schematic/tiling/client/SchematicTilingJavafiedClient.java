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
import schematic.tiling.cannon.PearlFlightSimulator;
import schematic.tiling.client.gui.GuiReturnCannon;
import schematic.tiling.client.gui.GuiReturnCannonDebug;
import schematic.tiling.client.util.ChunkFlightRouteRenderer;

public class SchematicTilingJavafiedClient implements ClientModInitializer {
	private static boolean pendingOpenDebugGui = false;

	@Override
	public void onInitializeClient() {
		ChunkFlightRouteRenderer.init();
		schematic.tiling.client.util.PearlTrajectoryLineRenderer.init();
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
				.then(ClientCommandManager.literal("hud")
					.executes(ctx -> {
						ChunkFlightRouteRenderer.toggleHud();
						GuiReturnCannon.setLastShowRouteHud(ChunkFlightRouteRenderer.isHudEnabled());
						String state = ChunkFlightRouteRenderer.isHudEnabled() ? "\u00a7aVISIBLE" : "\u00a7cCLOAKED/HIDDEN";
						ctx.getSource().sendFeedback(Component.literal("\u00a76[Schematic Tiling] \u00a7fRoute scan HUD is now " + state + "."));
						return 1;
					})
				)
				.then(ClientCommandManager.literal("rescan")
					.executes(ctx -> {
						net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
						if (client.level != null) {
							ChunkFlightRouteRenderer.restartScan(client.level);
							ChunkFlightRouteRenderer.setHudEnabled(true);
							GuiReturnCannon.setLastShowRouteHud(true);

							int totalChunks = PearlFlightSimulator.getTotalCandidateChunksCount();
							int loadedChunks = PearlFlightSimulator.getVerifiedCandidateChunksCount(client.level);
							int remaining = Math.max(0, totalChunks - loadedChunks);
							int colCount = PearlFlightSimulator.getCollidingRoutesCount();

							if (totalChunks == 0) {
								ctx.getSource().sendFeedback(Component.literal("\u00a76[Schematic Tiling] \u00a7eRoute scan restarted. No active cannon solutions loaded."));
							} else if (remaining > 0) {
								String colStr = (colCount > 0)
										? String.format(java.util.Locale.ROOT, " \u00a7c\u26a0 %d collision%s detected in loaded chunks!", colCount, colCount == 1 ? "" : "s")
										: " \u00a7eNo collisions in loaded chunks.";
								ctx.getSource().sendFeedback(Component.literal(String.format(java.util.Locale.ROOT,
										"\u00a76[Schematic Tiling] \u00a7fRoute scan started! Checked \u00a7e%d / %d \u00a7fchunks (\u00a7e%d \u00a7fremaining).%s",
										loadedChunks, totalChunks, remaining, colStr)));
							} else {
								String colStr = (colCount == 0)
										? "\u00a7a0 collisions detected (all clear)!"
										: String.format(java.util.Locale.ROOT, "\u00a7c\u26a0 %d collision%s detected!", colCount, colCount == 1 ? "" : "s");
								ctx.getSource().sendFeedback(Component.literal(String.format(java.util.Locale.ROOT,
										"\u00a76[Schematic Tiling] \u00a7fRoute scan complete! All \u00a7a%d \u00a7fchunks verified. %s",
										totalChunks, colStr)));
							}
						}
						return 1;
					})
				)
			);
		});
	}
}
