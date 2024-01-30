package mcjty.lostcities.varia;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.*;
import net.minecraft.command.arguments.BlockStateParser;
import net.minecraft.state.properties.Half;
import net.minecraft.state.properties.RailShape;
import net.minecraft.state.properties.SlabType;
import net.minecraft.util.Direction;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.fixes.BlockStateFlatteningMap;
import net.minecraft.util.datafix.fixes.ItemStackDataFlattening;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.registry.MutableRegistry;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.*;

public class Tools {

    public static BlockState stringToState(String s) {
        if ("minecraft:double_stone_slab".equals(s)) {
            return Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        }
        int meta = 0;
        if (s.contains("[")) {
            BlockStateParser parser = new BlockStateParser(new StringReader(s), false);
            try {
                parser.parse(false);
            } catch (CommandSyntaxException e) {
                throw new RuntimeException(e);
            }
            return parser.getState();
        }
        if (s.contains("@")) {
            // Temporary fix to just remove the meta to get things rolling
            String[] split = s.split("@");
            meta = Integer.parseInt(split[1]);
            s = split[0];
        }

        switch (s) {
            case "minecraft:stone_brick_stairs":
                return getStairsState(meta, Blocks.STONE_BRICK_STAIRS.defaultBlockState());
            case "minecraft:quartz_stairs":
                return getStairsState(meta, Blocks.QUARTZ_STAIRS.defaultBlockState());
            case "minecraft:stone_stairs":
                return getStairsState(meta, Blocks.STONE_STAIRS.defaultBlockState());
            case "minecraft:rail":
                return getRailState(meta, Blocks.RAIL.defaultBlockState());
            case "minecraft:golden_rail":
                return getPoweredRailState(meta, Blocks.POWERED_RAIL.defaultBlockState());
            case "minecraft:stone_slab":
                return getStoneSlabState(meta, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            case "minecraft:redstone_torch":
                return getRedstoneTorchState(meta);
            case "minecraft:ladder":
                return getLadderState(meta);
        }

        String converted = ItemStackDataFlattening.updateItem(s, meta);
        if (converted == null) {
            converted = BlockStateFlatteningMap.upgradeBlock(s);
        }
        s = converted;
        Block value = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(s));
        if (value == null) {
            throw new RuntimeException("Cannot find block: '" + s + "'!");
        }
        return value.defaultBlockState();
    }

    @Nullable
    public static ResourceLocation getBiomeId(Biome biome) {
        // @todo use IWorld.registryAccess()
        if (biome.getRegistryName() == null) {
            Optional<MutableRegistry<Biome>> biomeRegistry = DynamicRegistries.builtin().registry(Registry.BIOME_REGISTRY);
            return biomeRegistry.flatMap(r -> r.getResourceKey(biome).map(RegistryKey::location)).orElse(null);
        } else {
            return biome.getRegistryName();
        }
    }

    private static BlockState getLadderState(int meta) {
        Direction direction = Direction.values()[MathHelper.abs(meta % Direction.values().length)];
        if (direction.getAxis() == Direction.Axis.Y) {
            direction = Direction.NORTH;
        }
        return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, direction);
    }

    private static BlockState getRedstoneTorchState(int meta) {
        switch (meta) {
            case 1: return Blocks.REDSTONE_WALL_TORCH.defaultBlockState().setValue(RedstoneWallTorchBlock.FACING, Direction.EAST);
            case 2: return Blocks.REDSTONE_WALL_TORCH.defaultBlockState().setValue(RedstoneWallTorchBlock.FACING, Direction.WEST);
            case 3: return Blocks.REDSTONE_WALL_TORCH.defaultBlockState().setValue(RedstoneWallTorchBlock.FACING, Direction.SOUTH);
            case 4: return Blocks.REDSTONE_WALL_TORCH.defaultBlockState().setValue(RedstoneWallTorchBlock.FACING, Direction.NORTH);
            case 5: return Blocks.REDSTONE_TORCH.defaultBlockState();
        }

        return Blocks.REDSTONE_TORCH.defaultBlockState();
    }

    private static BlockState getStoneSlabState(int meta, BlockState state) {
        state.setValue (SlabBlock.TYPE, (meta & 8) > 0 ? SlabType.TOP : SlabType.BOTTOM);
        return state;
    }

    private static BlockState getRailState(int meta, BlockState state) {
        return state.setValue(RailBlock.SHAPE, getRailShape(meta, false));
    }

    private static BlockState getPoweredRailState(int meta, BlockState state) {
        return state.setValue(PoweredRailBlock.SHAPE, getRailShape(meta, true))
                .setValue(PoweredRailBlock.POWERED, (meta & 8) > 0);
    }

    private static BlockState getStairsState(int meta, BlockState state) {
        return state
                .setValue(StairsBlock.FACING, getStairsDirection(meta))
                .setValue(StairsBlock.HALF, getStairsHalf(meta));
    }

    private static Direction getStairsDirection(int meta) {
        int index = 5 - (meta & 3);
        return Direction.values()[MathHelper.abs(index % Direction.values().length)];
    }

    private static Half getStairsHalf(int meta) {
        return (meta & 4) > 0 ? Half.TOP : Half.BOTTOM;
    }

    private static RailShape getRailShape(int meta, boolean powered) {
        if (powered) {
            meta = meta & 7;
        }
        switch (meta) {
            case 0: return RailShape.NORTH_SOUTH;
            case 1: return RailShape.EAST_WEST;
            case 2: return RailShape.ASCENDING_EAST;
            case 3: return RailShape.ASCENDING_WEST;
            case 4: return RailShape.ASCENDING_NORTH;
            case 5: return RailShape.ASCENDING_SOUTH;
            case 6: return RailShape.SOUTH_EAST;
            case 7: return RailShape.SOUTH_WEST;
            case 8: return RailShape.NORTH_WEST;
            case 9: return RailShape.NORTH_EAST;
        }
        return RailShape.NORTH_SOUTH;
    }

    public static String stateToString(BlockState state) {
        // @todo 1.14
        return Objects.requireNonNull(state.getBlock().getRegistryName()).toString();
    }

    public static String getRandomFromList(Random random, List<Pair<Float, String>> list) {
        if (list.isEmpty()) {
            return null;
        }
        List<Pair<Float, String>> elements = new ArrayList<>();
        float totalweight = 0;
        for (Pair<Float, String> pair : list) {
            elements.add(pair);
            totalweight += pair.getKey();
        }
        float r = random.nextFloat() * totalweight;
        for (Pair<Float, String> pair : elements) {
            r -= pair.getKey();
            if (r <= 0) {
                return pair.getRight();
            }
        }
        return null;
    }
}
