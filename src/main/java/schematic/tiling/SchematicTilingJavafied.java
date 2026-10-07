package schematic.tiling;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchematicTilingJavafied implements ModInitializer {
	public static final String MOD_ID = "schematic-tiling-javafied";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Schematic Tiling initialized.");
	}
}
