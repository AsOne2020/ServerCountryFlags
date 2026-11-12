//? if neoforge {
/*package me.khajiitos.servercountryflags.neoforged;

import me.khajiitos.servercountryflags.common.ServerCountryFlags;
import me.khajiitos.servercountryflags.common.config.ClothConfigCheck;
import me.khajiitos.servercountryflags.common.config.ClothConfigScreenMaker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = ServerCountryFlags.MOD_ID, dist = Dist.CLIENT)
public final class ServerCountryFlagsNeoforged {
    public ServerCountryFlagsNeoforged(ModContainer container) {
        if (ClothConfigCheck.isInstalled()) {
            container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> ClothConfigScreenMaker.create(parent));
        }
    }
}
*///?}
