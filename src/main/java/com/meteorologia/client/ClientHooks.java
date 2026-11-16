package com.meteorologia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Bridge between common code (blocks) and client screens. Only loaded on the client. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void openRadar(BlockPos pos) {
        Minecraft.getInstance().setScreen(new RadarScreen(pos));
    }
}
