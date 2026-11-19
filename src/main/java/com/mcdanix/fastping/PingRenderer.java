package com.mcdanix.fastping;

import com.mcdanix.fastping.api.PingManager;
import com.mcdanix.fastping.api.PingMarker;
import com.mcdanix.fastping.api.PingRenderProvider;
import com.mcdanix.fastping.api.PingRenderSpec;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PingRenderer {

    private static final List<PingRenderProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private static volatile List<ExtractedPing> extractedPings = List.of();

    private static final float HEAD_MIN_X = (12f - 16f) / 32f; // -0.125
    private static final float HEAD_MAX_X = (19f - 16f) / 32f; // +0.09375

    private static final float HEAD_MAX_Y = (16f -  5f) / 32f; // +0.34375
    private static final float HEAD_MIN_Y = (16f - 12f) / 32f; // +0.125

    private static final float FACE_U1 = 8f / 64f,  FACE_V1 = 8f / 64f;
    private static final float FACE_U2 = 16f / 64f, FACE_V2 = 16f / 64f;
    private static final float HAT_U1  = 40f / 64f, HAT_V1  = 8f / 64f;
    private static final float HAT_U2  = 48f / 64f, HAT_V2  = 16f / 64f;

    private record ExtractedPing(
            Vec3d pos,
            PingRenderSpec spec,
            int pinColorArgb,
            int headColorArgb,
            Identifier skinTexture
    ) {}

    private PingRenderer() {}

    public static void registerRenderProvider(PingRenderProvider provider) {
        if (provider != null) {
            PROVIDERS.add(provider);
        }
    }

    public static void init() {
        WorldRenderEvents.END_EXTRACTION.register(PingRenderer::extract);
        WorldRenderEvents.END_MAIN.register(PingRenderer::render);
    }

    private static void extract(WorldExtractionContext ctx) {
        var pings = PingManager.getPings();
        if (pings.isEmpty()) {
            extractedPings = List.of();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        List<ExtractedPing> list = new ArrayList<>(pings.size());

        for (PingMarker marker : pings) {
            long age = now - marker.createdAt();
            long remain = PingManager.PING_LIFETIME_MS - age;
            if (remain <= 0L) continue;

            float fade = remain < PingManager.PING_FADE_MS
                    ? Math.max(0f, (float) remain / PingManager.PING_FADE_MS)
                    : 1f;

            int argb = marker.color();
            int baseAlpha = (argb >> 24) & 0xFF;
            int finalAlpha = Math.round(baseAlpha * fade);

            int pinArgb  = (finalAlpha << 24) | (argb & 0x00FFFFFF);
            int headArgb = (finalAlpha << 24) | 0x00FFFFFF;

            PingRenderSpec spec = null;
            for (PingRenderProvider provider : PROVIDERS) {
                spec = provider.getRenderSpec(marker, age, fade);
                if (spec != null) break;
            }
            if (spec == null) {
                spec = PingRenderSpec.DEFAULT;
            }

            Identifier skinTex = resolvePlayerSkin(mc, marker.authorName());
            list.add(new ExtractedPing(marker.pos(), spec, pinArgb, headArgb, skinTex));
        }

        extractedPings = list;
    }

    private static Identifier resolvePlayerSkin(MinecraftClient mc, String authorName) {
        if (mc.getNetworkHandler() != null && authorName != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(authorName);
            if (entry != null) {
                return entry.getSkinTextures().body().texturePath();
            }
        }
        return DefaultSkinHelper.getTexture();
    }
    public static void render(WorldRenderContext ctx) {
        List<ExtractedPing> pings = extractedPings;
        if (pings.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        MatrixStack matrices = ctx.matrices();
        VertexConsumerProvider consumers = ctx.consumers();
        if (matrices == null || consumers == null) return;

        Vec3d camPos = ctx.worldState().cameraRenderState.pos;
        Quaternionf camRot = ctx.worldState().cameraRenderState.orientation;
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        for (ExtractedPing ping : pings) {
            PingRenderSpec s = ping.spec;
            float size = s.size();
            float half = size * 0.5f;

            matrices.push();
            matrices.translate(ping.pos.x - camPos.x, ping.pos.y - camPos.y, ping.pos.z - camPos.z);
            matrices.multiply(camRot);

            Matrix4f mat = matrices.peek().getPositionMatrix();

            RenderLayer pinLayer = RenderLayers.textSeeThrough(s.texture());
            VertexConsumer pinVc = consumers.getBuffer(pinLayer);

            pinVc.vertex(mat, -half, -half, 0f).color(ping.pinColorArgb).texture(s.u1(), s.v2()).light(light);
            pinVc.vertex(mat,  half, -half, 0f).color(ping.pinColorArgb).texture(s.u2(), s.v2()).light(light);
            pinVc.vertex(mat,  half,  half, 0f).color(ping.pinColorArgb).texture(s.u2(), s.v1()).light(light);
            pinVc.vertex(mat, -half,  half, 0f).color(ping.pinColorArgb).texture(s.u1(), s.v1()).light(light);

            if (ping.skinTexture != null) {
                float hx1 = HEAD_MIN_X * size;
                float hx2 = HEAD_MAX_X * size;
                float hy1 = HEAD_MIN_Y * size;
                float hy2 = HEAD_MAX_Y * size;

                RenderLayer headLayer = RenderLayers.textSeeThrough(ping.skinTexture);
                VertexConsumer headVc = consumers.getBuffer(headLayer);

                headVc.vertex(mat, hx1, hy1, 0.002f).color(ping.headColorArgb).texture(FACE_U1, FACE_V2).light(light);
                headVc.vertex(mat, hx2, hy1, 0.002f).color(ping.headColorArgb).texture(FACE_U2, FACE_V2).light(light);
                headVc.vertex(mat, hx2, hy2, 0.002f).color(ping.headColorArgb).texture(FACE_U2, FACE_V1).light(light);
                headVc.vertex(mat, hx1, hy2, 0.002f).color(ping.headColorArgb).texture(FACE_U1, FACE_V1).light(light);

                headVc.vertex(mat, hx1, hy1, 0.004f).color(ping.headColorArgb).texture(HAT_U1, HAT_V2).light(light);
                headVc.vertex(mat, hx2, hy1, 0.004f).color(ping.headColorArgb).texture(HAT_U2, HAT_V2).light(light);
                headVc.vertex(mat, hx2, hy2, 0.004f).color(ping.headColorArgb).texture(HAT_U2, HAT_V1).light(light);
                headVc.vertex(mat, hx1, hy2, 0.004f).color(ping.headColorArgb).texture(HAT_U1, HAT_V1).light(light);
            }

            matrices.pop();
        }
    }

    public static void close() {
    }
}
