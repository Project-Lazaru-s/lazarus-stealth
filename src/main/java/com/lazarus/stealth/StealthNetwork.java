package com.lazarus.stealth;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

// Server -> client only: the player's own noise class and an aggregate threat (0 none, 1 investigating, 2 pursuing).
// The threat byte is reserved for the future separate indicator and is not drawn in 0.1.0.
final class StealthNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(LazarusStealth.rl("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    record HudPacket(byte noise, byte threat) {
        void encode(FriendlyByteBuf buf) {
            buf.writeByte(noise);
            buf.writeByte(threat);
        }

        static HudPacket decode(FriendlyByteBuf buf) {
            return new HudPacket(buf.readByte(), buf.readByte());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHud.set(noise, threat)));
            ctx.get().setPacketHandled(true);
        }
    }

    private StealthNetwork() {}

    static void register() {
        CHANNEL.messageBuilder(HudPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HudPacket::encode).decoder(HudPacket::decode).consumerMainThread(HudPacket::handle).add();
    }

    static void sendHud(ServerPlayer player, int noise, int threat) {
        if (player.connection == null || !CHANNEL.isRemotePresent(player.connection.connection)) return;
        CHANNEL.sendTo(new HudPacket((byte) noise, (byte) threat), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
