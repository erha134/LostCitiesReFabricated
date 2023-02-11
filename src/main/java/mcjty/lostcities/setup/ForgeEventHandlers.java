package mcjty.lostcities.setup;

import mcjty.lostcities.commands.ModCommands;
import mcjty.lostcities.config.LostCityConfiguration;
import mcjty.lostcities.varia.CustomTeleporter;
import mcjty.lostcities.varia.WorldTools;
import mcjty.lostcities.worldgen.LostCityFeature;
import mcjty.lostcities.worldgen.lost.*;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;

public class ForgeEventHandlers {

    @SubscribeEvent
    public void commandRegister(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        BuildingInfo.cleanCache();
        Highway.cleanCache();
        Railway.cleanCache();
        BiomeInfo.cleanCache();
        City.cleanCache();
        CitySphere.cleanCache();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBiomeLoad(BiomeLoadingEvent event) {
        RegistryKey<Biome> biomeKey = RegistryKey.create(Registry.BIOME_REGISTRY, Objects.requireNonNull(event.getName()));
        if (!BiomeDictionary.hasType(biomeKey, BiomeDictionary.Type.VOID)) {
            event.getGeneration().getFeatures(GenerationStage.Decoration.RAW_GENERATION).add(() -> LostCityFeature.LOSTCITY_CONFIGURED_FEATURE);
        }
    }

    private boolean isValidStandingPosition(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
//        return state.getBlock().isTopSolid(state) && state.getBlock().isFullCube(state) && state.getBlock().isOpaqueCube(state) && world.isAirBlock(pos.up()) && world.isAirBlock(pos.up(2));
        // @todo 1.14
        return state.canOcclude();
    }

    private boolean isValidSpawnBed(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) {
            return false;
        }
        Direction direction = Blocks.BLACK_BED.getBedDirection(state, world, pos);
        Block b1 = world.getBlockState(pos.below()).getBlock();
        Block b2 = world.getBlockState(pos.relative(direction.getOpposite()).below()).getBlock();
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(LostCityConfiguration.SPECIAL_BED_BLOCK.get()));
        if (b1 != b || b2 != b) {
            return false;
        }
        // Check if the bed is surrounded by 6 skulls
        if (!(world.getBlockState(pos.relative(direction)).getBlock() instanceof AbstractSkullBlock)) {   // @todo 1.14 other skulls!
            return false;
        }
        if (!(world.getBlockState(pos.relative(direction.getClockWise())).getBlock() instanceof AbstractSkullBlock)) {
            return false;
        }
        if (!(world.getBlockState(pos.relative(direction.getCounterClockWise())).getBlock() instanceof AbstractSkullBlock)) {
            return false;
        }
        if (!(world.getBlockState(pos.relative(direction.getOpposite(), 2)).getBlock() instanceof AbstractSkullBlock)) {
            return false;
        }
        if (!(world.getBlockState(pos.relative(direction.getOpposite()).relative(direction.getOpposite().getClockWise())).getBlock() instanceof AbstractSkullBlock)) {
            return false;
        }
        return world.getBlockState(pos.relative(direction.getOpposite()).relative(direction.getOpposite().getCounterClockWise())).getBlock() instanceof AbstractSkullBlock;
    }

    private BlockPos findValidTeleportLocation(World world, BlockPos start) {
        int chunkX = start.getX()>>4;
        int chunkZ = start.getZ()>>4;
        int y = start.getY();
        BlockPos pos = findValidTeleportLocation(world, chunkX, chunkZ, y);
        if (pos != null) {
            return pos;
        }
        for (int r = 1 ; r < 50 ; r++) {
            for (int i = -r ; i < r ; i++) {
                pos = findValidTeleportLocation(world, chunkX + i, chunkZ - r, y);
                if (pos != null) {
                    return pos;
                }
                pos = findValidTeleportLocation(world, chunkX + r, chunkZ + i, y);
                if (pos != null) {
                    return pos;
                }
                pos = findValidTeleportLocation(world, chunkX + r - i, chunkZ + r, y);
                if (pos != null) {
                    return pos;
                }
                pos = findValidTeleportLocation(world, chunkX - r, chunkZ + r - i, y);
                if (pos != null) {
                    return pos;
                }
            }
        }
        return null;
    }

    private BlockPos findValidTeleportLocation(World world, int chunkX, int chunkZ, int y) {
        BlockPos bestSpot = null;
        for (int dy = 0 ; dy < 255 ; dy++) {
            for (int x = 0 ; x < 16 ; x++) {
                for (int z = 0 ; z < 16 ; z++) {
                    if ((y + dy) < 250) {
                        BlockPos p = new BlockPos(chunkX * 16 + x, y + dy, chunkZ * 16 + z);
                        if (isValidSpawnBed(world, p)) {
                            return p.above();
                        }
                        if (bestSpot == null && isValidStandingPosition(world, p)) {
                            bestSpot = p.above();
                        }
                    }
                    if ((y - dy) > 1) {
                        BlockPos p = new BlockPos(chunkX * 16 + x, y - dy, chunkZ * 16 + z);
                        if (isValidSpawnBed(world, p)) {
                            return p.above();
                        }
                        if (bestSpot == null && isValidStandingPosition(world, p)) {
                            bestSpot = p.above();
                        }
                    }
                }
            }
        }
        return bestSpot;
    }

    @SubscribeEvent
    public void onPlayerSleepInBedEvent(PlayerSleepInBedEvent event) {

        World world = event.getPlayer().getCommandSenderWorld();
        if (world.isClientSide) {
            return;
        }
        BlockPos bedLocation = event.getPos();
        if (bedLocation == null || !isValidSpawnBed(world, bedLocation)) {
            return;
        }

        if (world.dimension() == Registration.DIMENSION) {
            event.setResult(PlayerEntity.SleepResult.OTHER_PROBLEM);
            ServerWorld destWorld = WorldTools.getOverworld(world);
            BlockPos location = findLocation(bedLocation, destWorld);
            CustomTeleporter.teleportToDimension(event.getPlayer(), destWorld, location);
        } else {
            event.setResult(PlayerEntity.SleepResult.OTHER_PROBLEM);
            ServerWorld destWorld = Objects.requireNonNull(event.getEntity().getCommandSenderWorld().getServer()).getLevel(Registration.DIMENSION);
            BlockPos location = findLocation(bedLocation, destWorld);
            CustomTeleporter.teleportToDimension(event.getPlayer(), destWorld, location);
        }
    }

    private BlockPos findLocation(BlockPos bedLocation, ServerWorld destWorld) {
        BlockPos top = bedLocation.above(5);//destWorld.getHeight(Heightmap.Type.MOTION_BLOCKING, bedLocation).up(10);
        BlockPos location = top;
        while (top.getY() > 1 && destWorld.getBlockState(location).isAir(destWorld, location)) {
            location = location.below();
        }
//        BlockPos location = findValidTeleportLocation(destWorld, top);
        if (destWorld.isEmptyBlock(location.below())) {
            // No place to teleport
            destWorld.setBlockAndUpdate(bedLocation, Blocks.COBBLESTONE.defaultBlockState());
        }
        return location.above(1);
    }
}
