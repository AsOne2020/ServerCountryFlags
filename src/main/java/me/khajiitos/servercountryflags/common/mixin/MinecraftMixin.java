package me.khajiitos.servercountryflags.common.mixin;

import me.khajiitos.servercountryflags.common.ServerCountryFlags;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void servercountryflags$init(CallbackInfo ci) {
        ServerCountryFlags.init();
    }
}
