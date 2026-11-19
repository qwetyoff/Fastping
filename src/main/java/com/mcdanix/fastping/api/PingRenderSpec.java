package com.mcdanix.fastping.api;

import net.minecraft.util.Identifier;

public record PingRenderSpec(
        Identifier texture,
        float u1, float v1,
        float u2, float v2,
        float size,
        boolean throughWalls
) {
    public static final PingRenderSpec DEFAULT = fromPixels(
            Identifier.of("fastping", "textures/ping.png"),
            32, 32,
            0, 0, 32, 32,
            1.0f,
            true
    );

    public static PingRenderSpec fromPixels(
            Identifier texture,
            int texWidth, int texHeight,
            int x1, int y1, int x2, int y2,
            float size,
            boolean throughWalls
    ) {
        return new PingRenderSpec(
                texture,
                (float) x1 / texWidth,
                (float) y1 / texHeight,
                (float) x2 / texWidth,
                (float) y2 / texHeight,
                size,
                throughWalls
        );
    }
}
