package mcjty.lostcities.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mcjty.lostcities.setup.Registration;
import mcjty.lostcities.worldgen.IDimensionInfo;
import mcjty.lostcities.worldgen.lost.BuildingInfo;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public class CommandMap implements Command<CommandSource> {

    private static final CommandMap CMD = new CommandMap();

    public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher) {
        return Commands.literal("map")
                .requires(cs -> cs.hasPermission(0))
                .executes(CMD);
    }


    @Override
    public int run(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrException();
        if (player != null) {
            BlockPos position = player.blockPosition();
            IDimensionInfo dimInfo = Registration.LOSTCITY_FEATURE.getDimensionInfo(player.getLevel());
            if (dimInfo != null) {
                ChunkPos pos = new ChunkPos(position);
                for (int z = pos.z - 40 ; z <= pos.z + 40 ; z++) {
                    StringBuilder buf = new StringBuilder();
                    for (int x = pos.x - 40 ; x <= pos.x + 40 ; x++) {
                        BuildingInfo info = BuildingInfo.getBuildingInfo(pos.x + x, pos.z + z, dimInfo);
                        if (info.isCity && info.hasBuilding) {
                            buf.append("B");
                        } else if (info.isCity) {
                            buf.append("+");
                        } else if (info.highwayXLevel >= 0 || info.highwayZLevel >= 0) {
                            buf.append(".");
                        } else {
                            buf.append(" ");
                        }
                    }
                    System.out.println(buf);
                }
            }
        }
        return 0;
    }
}
