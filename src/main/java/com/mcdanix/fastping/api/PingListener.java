package com.mcdanix.fastping.api;

import net.minecraft.util.math.Vec3d;

public interface PingListener {
    void onLocalPingCreated(Vec3d pos);
}