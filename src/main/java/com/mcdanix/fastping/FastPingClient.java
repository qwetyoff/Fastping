package com.mcdanix.fastping;

import com.mcdanix.fastping.api.PingManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class FastPingClient implements ClientModInitializer {

    private static final double PING_RANGE = 100.0;
    private static boolean middleDownPrev = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(FastPingClient::onEndTick);
        PingRenderer.init();
    }


    private static void onEndTick(MinecraftClient client) {
        PingManager.tick();

        if (client.player == null || client.world == null || client.currentScreen != null) {
            middleDownPrev = false;
            return;
        }

        long handle = client.getWindow().getHandle();
        boolean middleDown =
                GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;

        if (middleDown && !middleDownPrev) {
            onMiddleClick(client);
        }
        middleDownPrev = middleDown;
    }

    private static void onMiddleClick(MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player == null) return;

        HitResult hit = player.raycast(PING_RANGE, 0.0f, false);

        Vec3d targetPos;
        if (hit.getType() == HitResult.Type.MISS) {
            Vec3d eye  = player.getEyePos();
            Vec3d look = player.getRotationVec(0.0f);
            targetPos = eye.add(look.multiply(PING_RANGE));
        } else {
            targetPos = hit.getPos();
        }

        String name = player.getName().getString();
        int color = pickRadarColor(player);

        PingManager.addPing(targetPos, name, color);
    }

    private static int pickRadarColor(PlayerEntity player) {
        Team team = player.getScoreboardTeam();
        if (team != null) {
            Formatting fmt = team.getColor();
            if (fmt != null) {
                Integer rgb = fmt.getColorValue();
                if (rgb != null) return 0xFF000000 | rgb;
            }
        }

        String n = player.getName().getString();
        if (n != null && !n.isEmpty()) {
            int h = n.hashCode();
            float hue = ((h % 360) + 360) % 360 / 360.0f;
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.85f, 1.0f);
            return 0xFF000000 | (rgb & 0xFFFFFF);
        }

        return 0xFFFF3030;
    }
}