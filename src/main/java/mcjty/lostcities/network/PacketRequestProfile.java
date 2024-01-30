package mcjty.lostcities.network;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketRequestProfile {

    private RegistryKey<World> dimension;

    public PacketRequestProfile(PacketBuffer buf) {
        dimension = RegistryKey.create(Registry.DIMENSION_REGISTRY, buf.readResourceLocation());
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeResourceLocation(dimension.location());
    }

    public PacketRequestProfile() {
    }

    public PacketRequestProfile(RegistryKey<World> dimension) {
        this.dimension = dimension;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // @todo 1.14
        });
        ctx.get().setPacketHandled(true);
    }
}
