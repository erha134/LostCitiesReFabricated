package mcjty.lostcities.varia;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Teleporter;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.ITeleporter;

import java.util.function.Function;

public class CustomTeleporter extends Teleporter {

    public CustomTeleporter(ServerWorld world, double x, double y, double z) {
        super(world);
        this.worldServer = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    private final ServerWorld worldServer;
    private final double x;
    private final double y;
    private final double z;

    @Override
    public Entity placeEntity(Entity entity, ServerWorld currentWorld, ServerWorld destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
        this.worldServer.getBlockState(new BlockPos((int) this.x, (int) this.y, (int) this.z));

        entity.setPos(this.x, this.y, this.z);
        entity.setDeltaMovement(0, 0, 0);
        return entity;
    }

    public static void teleportToDimension(PlayerEntity player, ServerWorld dimension, BlockPos pos){
        teleportToDimension(player, dimension, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    public static void teleportToDimension(PlayerEntity player, ServerWorld dimension, double x, double y, double z) {
        player.changeDimension(dimension, new ITeleporter() {
            @Override
            public Entity placeEntity(Entity entity, ServerWorld currentWorld, ServerWorld destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
                entity = repositionEntity.apply(false);
                entity.teleportTo(x, y, z);
                return entity;
            }
        });
    }

}
