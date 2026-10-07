package schematic.tiling.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.util.FileType;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase;
import fi.dy.masa.malilib.util.FileNameUtils;

public class SchematicTilerUtil {

    /**
     * Determines whether a directory entry is a tiling parts schematic.
     * Checks if the filename contains "parts" or if any region name contains "//".
     */
    public static boolean isPartsSchematic(WidgetFileBrowserBase.DirectoryEntry entry) {
        if (entry == null) {
            return false;
        }

        String name = entry.getName() != null ? entry.getName().toLowerCase() : "";
        Path path = entry.getFullPath();
        String fileName = (path != null && path.getFileName() != null)
                ? path.getFileName().toString().toLowerCase()
                : name;

        // If either name contains "parts"
        if (name.contains("parts") || fileName.contains("parts")) {
            return true;
        }

        // If it doesn't say "parts" in the name, check inside the NBT if any region has "//"
        if (path != null && Files.isRegularFile(path) && fileName.endsWith(".litematic")) {
            try {
                CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                CompoundTag regions = root.getCompoundOrEmpty("Regions");
                for (String regName : regions.keySet()) {
                    if (regName.contains(TilingUnit.SEP)) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return false;
    }

    public static CompoundTag createVec3iTag(Vec3i vec) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", vec.getX());
        tag.putInt("y", vec.getY());
        tag.putInt("z", vec.getZ());
        return tag;
    }

    public static CompoundTag createBlockPosTag(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    public static TilingTree loadTilingTree(Path file) throws Exception {
        LitematicaSchematic schem = LitematicaSchematic.createFromFile(file.getParent(), file.getFileName().toString());
        if (schem == null) {
            throw new IOException("Failed to load schematic file: " + file.getFileName());
        }
        return new TilingTree(schem, file.getFileName().toString());
    }

    public static GenerationResult generateAndSaveSchematic(
            TilingTree tree,
            int width,
            int length,
            String author,
            Path outputDirectory
    ) throws Exception {
        Map<String, NormalizedRegion> stackedRegions = tree.generateRegions(width, length);

        if (author == null || author.isBlank()) {
            author = tree.getSourceSchematic().getMetadata().getAuthor();
            if (author == null || author.isBlank()) {
                author = "Tiler";
            }
        }

        String suffix = (length > 0) ? (width + "x" + length) : String.valueOf(width);
        String schematicName = tree.getName() + " " + suffix;
        String fileName = FileNameUtils.generateSafeFileName(schematicName + ".litematic");
        Path outputFile = outputDirectory.resolve(fileName);

        // Build Litematica NBT CompoundTag
        CompoundTag root = new CompoundTag();
        root.putInt("MinecraftDataVersion", LitematicaSchematic.MINECRAFT_DATA_VERSION);
        root.putInt("Version", LitematicaSchematic.SCHEMATIC_VERSION);
        root.putInt("SubVersion", LitematicaSchematic.SCHEMATIC_VERSION_SUB);

        // Calculate metadata totals and enclosing size
        int totalVolume = 0;
        int totalBlocks = 0;
        int entityCount = 0;
        int blockEntityCount = 0;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        CompoundTag regionsData = new CompoundTag();

        for (Map.Entry<String, NormalizedRegion> entry : stackedRegions.entrySet()) {
            String regionName = entry.getKey();
            NormalizedRegion reg = entry.getValue();

            int rVol = reg.getWidth() * reg.getHeight() * reg.getLength();
            totalVolume += rVol;
            entityCount += reg.getEntities().size();
            blockEntityCount += reg.getTileEntities().size();

            minX = Math.min(minX, reg.getMinX());
            minY = Math.min(minY, reg.getMinY());
            minZ = Math.min(minZ, reg.getMinZ());
            maxX = Math.max(maxX, reg.getMaxX());
            maxY = Math.max(maxY, reg.getMaxY());
            maxZ = Math.max(maxZ, reg.getMaxZ());

            // Count non-air blocks in container
            for (long count : reg.getContainer().getBlockCounts()) {
                totalBlocks += (int) count;
            }

            CompoundTag regTag = new CompoundTag();
            regTag.put("BlockStatePalette", reg.getContainer().getPalette().writeToNBT());
            regTag.put("BlockStates", new LongArrayTag(reg.getContainer().getBackingLongArray()));

            // Tile entities
            ListTag tileList = new ListTag();
            for (CompoundTag teTag : reg.getTileEntities().values()) {
                tileList.add(teTag);
            }
            regTag.put("TileEntities", tileList);

            // Entities
            ListTag entList = new ListTag();
            for (LitematicaSchematic.EntityInfo entInfo : reg.getEntities()) {
                entList.add(entInfo.nbt);
            }
            regTag.put("Entities", entList);

            // Position and Size
            BlockPos pos = new BlockPos(reg.getMinX(), reg.getMinY(), reg.getMinZ());
            BlockPos size = new BlockPos(reg.getWidth(), reg.getHeight(), reg.getLength());
            regTag.put("Position", createBlockPosTag(pos));
            regTag.put("Size", createBlockPosTag(size));

            regionsData.put(regionName, regTag);
        }

        Vec3i enclosingSize = (minX <= maxX)
                ? new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1)
                : Vec3i.ZERO;

        CompoundTag meta = new CompoundTag();
        meta.putString("Name", schematicName);
        meta.putString("Author", author);
        meta.putString("Description", "Generated by Schematic-Tiling");
        meta.putInt("RegionCount", stackedRegions.size());
        meta.putInt("TotalVolume", totalVolume);
        meta.putInt("TotalBlocks", totalBlocks);
        meta.putInt("EntityCount", entityCount);
        meta.putInt("BlockEntityCount", blockEntityCount);
        meta.putLong("TimeCreated", System.currentTimeMillis());
        meta.putLong("TimeModified", System.currentTimeMillis());
        meta.put("EnclosingSize", createVec3iTag(enclosingSize));

        root.put("Metadata", meta);
        root.put("Regions", regionsData);

        // Save compressed file
        NbtIo.writeCompressed(root, outputFile);

        // Load into LitematicaSchematic instance
        LitematicaSchematic schematic = new LitematicaSchematic(outputFile, root, FileType.LITEMATICA_SCHEMATIC);

        return new GenerationResult(schematic, outputFile);
    }

    public record GenerationResult(LitematicaSchematic schematic, Path savedPath) {}
}

