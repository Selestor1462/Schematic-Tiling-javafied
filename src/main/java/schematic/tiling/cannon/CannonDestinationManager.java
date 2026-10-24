package schematic.tiling.cannon;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import fi.dy.masa.malilib.util.StringUtils;

public class CannonDestinationManager {

    private static final CannonDestinationManager INSTANCE = new CannonDestinationManager();
    public static CannonDestinationManager getInstance() {
        return INSTANCE;
    }

    private static final String FILE_NAME = "cannon_destinations.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<CannonDestination> globalDestinations = new ArrayList<>();
    private final Map<String, List<CannonDestination>> worldDestinations = new LinkedHashMap<>();

    private CannonDestinationManager() {
        this.load();
    }

    public static String getCurrentWorldOrServerName() {
        return StringUtils.getWorldOrServerNameOrDefault("default");
    }

    public Path getStorageFilePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("schematic-tiling-javafied").resolve(FILE_NAME);
    }

    public synchronized void load() {
        this.globalDestinations.clear();
        this.worldDestinations.clear();

        Path path = getStorageFilePath();
        if (!Files.exists(path)) {
            return;
        }

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement rootEl = JsonParser.parseString(content);
            if (!rootEl.isJsonObject()) {
                return;
            }
            JsonObject root = rootEl.getAsJsonObject();

            if (root.has("global") && root.get("global").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("global");
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        JsonObject obj = el.getAsJsonObject();
                        String name = obj.has("name") ? obj.get("name").getAsString() : "";
                        double x = obj.has("x") ? obj.get("x").getAsDouble() : 0.0;
                        double z = obj.has("z") ? obj.get("z").getAsDouble() : 0.0;
                        if (!name.isEmpty()) {
                            this.globalDestinations.add(new CannonDestination(name, x, z, true, null));
                        }
                    }
                }
            }

            if (root.has("worlds") && root.get("worlds").isJsonObject()) {
                JsonObject worldsObj = root.getAsJsonObject("worlds");
                for (String wName : worldsObj.keySet()) {
                    JsonElement wEl = worldsObj.get(wName);
                    if (wEl.isJsonArray()) {
                        List<CannonDestination> list = new ArrayList<>();
                        for (JsonElement el : wEl.getAsJsonArray()) {
                            if (el.isJsonObject()) {
                                JsonObject obj = el.getAsJsonObject();
                                String name = obj.has("name") ? obj.get("name").getAsString() : "";
                                double x = obj.has("x") ? obj.get("x").getAsDouble() : 0.0;
                                double z = obj.has("z") ? obj.get("z").getAsDouble() : 0.0;
                                if (!name.isEmpty()) {
                                    list.add(new CannonDestination(name, x, z, false, wName));
                                }
                            }
                        }
                        this.worldDestinations.put(wName, list);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Schematic-Tiling] Failed to load cannon destinations: " + e.getMessage());
        }
    }

    public synchronized void save() {
        Path path = getStorageFilePath();
        try {
            JsonObject root = new JsonObject();

            JsonArray globalArr = new JsonArray();
            for (CannonDestination d : this.globalDestinations) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", d.name);
                obj.addProperty("x", d.x);
                obj.addProperty("z", d.z);
                globalArr.add(obj);
            }
            root.add("global", globalArr);

            JsonObject worldsObj = new JsonObject();
            for (Map.Entry<String, List<CannonDestination>> entry : this.worldDestinations.entrySet()) {
                JsonArray arr = new JsonArray();
                for (CannonDestination d : entry.getValue()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("name", d.name);
                    obj.addProperty("x", d.x);
                    obj.addProperty("z", d.z);
                    arr.add(obj);
                }
                worldsObj.add(entry.getKey(), arr);
            }
            root.add("worlds", worldsObj);

            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[Schematic-Tiling] Failed to save cannon destinations: " + e.getMessage());
        }
    }

    public synchronized List<CannonDestination> getGlobalDestinations() {
        return new ArrayList<>(this.globalDestinations);
    }

    public synchronized List<CannonDestination> getCurrentWorldDestinations() {
        String w = getCurrentWorldOrServerName();
        List<CannonDestination> list = this.worldDestinations.get(w);
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }

    public synchronized List<CannonDestination> getAllForCurrentContext() {
        List<CannonDestination> result = new ArrayList<>();
        result.addAll(this.getCurrentWorldDestinations());
        result.addAll(this.getGlobalDestinations());
        return result;
    }

    public synchronized void addDestination(String name, double x, double z, boolean isGlobal) {
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) {
            return;
        }

        if (isGlobal) {
            this.globalDestinations.removeIf(d -> d.name.equalsIgnoreCase(trimmedName));
            this.globalDestinations.add(new CannonDestination(trimmedName, x, z, true, null));
        } else {
            String w = getCurrentWorldOrServerName();
            List<CannonDestination> list = this.worldDestinations.computeIfAbsent(w, k -> new ArrayList<>());
            list.removeIf(d -> d.name.equalsIgnoreCase(trimmedName));
            list.add(new CannonDestination(trimmedName, x, z, false, w));
        }
        this.save();
    }

    public synchronized void removeDestination(CannonDestination dest) {
        if (dest == null) {
            return;
        }
        if (dest.isGlobal) {
            this.globalDestinations.removeIf(d -> d.name.equalsIgnoreCase(dest.name));
        } else {
            String w = dest.worldName != null ? dest.worldName : getCurrentWorldOrServerName();
            List<CannonDestination> list = this.worldDestinations.get(w);
            if (list != null) {
                list.removeIf(d -> d.name.equalsIgnoreCase(dest.name));
            }
        }
        this.save();
    }
}

