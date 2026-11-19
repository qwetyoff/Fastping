package com.mcdanix.fastping.api;

import net.minecraft.util.math.Vec3d;

public record PingMarker(Vec3d pos, String authorName, int color, long createdAt) {
}