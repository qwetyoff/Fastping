package com.mcdanix.fastping.api;

@FunctionalInterface
public interface PingRenderProvider {
    PingRenderSpec getRenderSpec(PingMarker marker, long ageMs, float defaultAlpha);
}
