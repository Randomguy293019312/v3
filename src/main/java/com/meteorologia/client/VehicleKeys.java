package com.meteorologia.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** Vehicle key bindings (configurable in Options > Controls > Meteorology). */
public final class VehicleKeys {
    private VehicleKeys() {}

    private static final String CATEGORY = "key.categories.meteorologia";

    public static final KeyMapping ANCHOR = new KeyMapping("key.meteorologia.anchor", InputConstants.KEY_R, CATEGORY);
    public static final KeyMapping LIGHTS = new KeyMapping("key.meteorologia.lights", InputConstants.KEY_K, CATEGORY);
    public static final KeyMapping HORN = new KeyMapping("key.meteorologia.horn", InputConstants.KEY_H, CATEGORY);
}
