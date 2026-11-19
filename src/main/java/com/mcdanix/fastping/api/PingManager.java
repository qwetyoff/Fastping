package com.mcdanix.fastping.api;

import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PingManager {

    public static final long PING_LIFETIME_MS = 10_000L;

    public static final long PING_FADE_MS = 2_000L;

    private static final List<PingMarker>   PINGS     = new CopyOnWriteArrayList<>();
    private static final List<PingListener> LISTENERS = new CopyOnWriteArrayList<>();

    private PingManager() {}

    public static void addPing(Vec3d pos, String authorName, int color) {
        long now = System.currentTimeMillis();

        PINGS.removeIf(existing -> {
            if (!Objects.equals(existing.authorName(), authorName)) {
                return false;
            }
            long remain = PING_LIFETIME_MS - (now - existing.createdAt());
            boolean isFading = remain <= PING_FADE_MS;
            return !isFading;
        });

        PINGS.add(new PingMarker(pos, authorName, color, now));
        for (PingListener l : LISTENERS) {
            try {
                l.onLocalPingCreated(pos);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public static List<PingMarker> getPings() {
        return PINGS;
    }

    public static void addListener(PingListener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(PingListener listener) {
        LISTENERS.remove(listener);
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        PINGS.removeIf(p -> now - p.createdAt() >= PING_LIFETIME_MS);
    }
}
