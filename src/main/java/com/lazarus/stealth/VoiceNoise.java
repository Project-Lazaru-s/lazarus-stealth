package com.lazarus.stealth;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Voice Chat plugin: measures how loud each player talks. Simple Voice Chat finds this class by its annotation
 * and only loads it when the mod is installed. Runs on the voice network thread: it only decodes and records the level
 * (VoiceLevels); Noise turns it into a noise on the server thread.
 */
@ForgeVoicechatPlugin
public final class VoiceNoise implements VoicechatPlugin {
    /** Opus decoders keep state between frames, so one per player. */
    private static final Map<UUID, OpusDecoder> DECODERS = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() { return LazarusStealth.ID; }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, VoiceNoise::microphone);
        registration.registerEvent(PlayerDisconnectedEvent.class, event -> {
            OpusDecoder decoder = DECODERS.remove(event.getPlayerUuid());
            if (decoder != null) synchronized (decoder) { decoder.close(); }
            VoiceLevels.forget(event.getPlayerUuid());
        });
    }

    private static void microphone(MicrophonePacketEvent event) {
        if (!StealthConfig.VOICE_ENABLED.get()) return;
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null || sender.getPlayer() == null) return;
        byte[] opus = event.getPacket().getOpusEncodedData();
        UUID player = sender.getPlayer().getUuid();
        OpusDecoder decoder = DECODERS.computeIfAbsent(player, id -> event.getVoicechat().createDecoder());
        short[] pcm;
        synchronized (decoder) {
            if (decoder.isClosed()) return;
            if (opus == null || opus.length == 0) { // end of a sentence
                decoder.resetState();
                return;
            }
            pcm = decoder.decode(opus);
        }
        VoiceLevels.record(player, StealthMath.voiceDb(pcm), event.getPacket().isWhispering());
    }
}
