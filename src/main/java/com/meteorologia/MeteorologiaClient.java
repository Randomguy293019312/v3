package com.meteorologia;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point: adds the in-game config screen (Mods > Meteorology > Config). */
@Mod(value = Meteorologia.MODID, dist = Dist.CLIENT)
public class MeteorologiaClient {
    public MeteorologiaClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
