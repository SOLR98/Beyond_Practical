package com.solr98.beyondpractical.network;

import com.solr98.beyondpractical.api.ids.BPConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class BPNetwork
{
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryBuild(BPConstants.MODID, "channel"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int id = 0;

    public static void register()
    {
        CHANNEL.registerMessage(id++, FillPatternPacket.class,
                FillPatternPacket::encode, FillPatternPacket::decode, FillPatternPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
