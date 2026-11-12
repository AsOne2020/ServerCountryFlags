package me.khajiitos.servercountryflags.common.mixin;

import me.khajiitos.servercountryflags.common.ServerCountryFlags;
import me.khajiitos.servercountryflags.common.config.Config;
import me.khajiitos.servercountryflags.common.util.APIResponse;
import me.khajiitos.servercountryflags.common.util.FlagPosition;
import me.khajiitos.servercountryflags.common.util.FlagRenderInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public class OnlineServerEntryMixin {

    @Shadow
    @Final
    private ServerData serverData;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void servercountryflags$requestLocation(CallbackInfo info) {
        ServerCountryFlags.requestServerLocation(serverData.ip);
    }

    @ModifyArg(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", ordinal = 0), method = "extractContent", index = 2)
    private int servercountryflags$serverNameX(int oldX) {
        if (Config.cfg.flagPosition == FlagPosition.BEHIND_NAME) {
            APIResponse apiResponse = ServerCountryFlags.servers.get(serverData.ip);
            FlagRenderInfo renderInfo = ServerCountryFlags.getFlagRenderInfo(apiResponse);

            if (renderInfo != null) {
                return oldX + (int)(renderInfo.flagAspectRatio() * 8.f) + 3;
            }
        }

        return oldX;
    }

    @Inject(at = @At("TAIL"), method = "extractContent")
    private void servercountryflags$extractFlag(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo info) {
        APIResponse apiResponse = ServerCountryFlags.servers.get(serverData.ip);
        FlagRenderInfo flagRenderInfo = ServerCountryFlags.getFlagRenderInfo(apiResponse);

        if (flagRenderInfo == null) {
            return;
        }
        ServerSelectionList.OnlineServerEntry entry = (ServerSelectionList.OnlineServerEntry)(Object)this;
        int x = entry.getContentX();
        int y = entry.getContentY();
        int entryWidth = entry.getContentWidth();
        int entryHeight = entry.getContentHeight();

        if (Config.cfg.flagPosition == FlagPosition.TOOLTIP_SERVER_NAME) {
            int serverNameStartX = x + 35;
            int serverNameStartY = y + 1;

            int serverNameWidth = this.minecraft.font.width(this.serverData.name);
            int serverNameHeight = 8;

            if (mouseX >= serverNameStartX && mouseX <= serverNameStartX + serverNameWidth && mouseY >= serverNameStartY && mouseY <= serverNameStartY + serverNameHeight) {
                guiGraphics.setTooltipForNextFrame(flagRenderInfo.tooltip(), mouseX, mouseY);
            }

            return;
        } else if (Config.cfg.flagPosition == FlagPosition.TOOLTIP_PING) {
            return;
        }

        int height = Config.cfg.flagPosition == FlagPosition.BEHIND_NAME ? 8 : 12;
        int width = (int)(flagRenderInfo.flagAspectRatio() * height);

        int startingX, startingY;
        switch (Config.cfg.flagPosition) {
            case LEFT -> {
                startingX = x - width - 6;
                startingY = y + (entryHeight / 2) - (height / 2);
            }
            case RIGHT -> {
                startingX = x + entryWidth + 10;
                startingY = y + (entryHeight / 2) - (height / 2);
            }
            case BEHIND_NAME -> {
                startingX = x + 35;
                startingY = y + 2;
            }
            default -> {
                startingX = x + entryWidth - width - 6;
                startingY = y + entryHeight - height - 4;
            }
        }

        Identifier textureId = Identifier.fromNamespaceAndPath(ServerCountryFlags.MOD_ID, "textures/gui/flags/" + flagRenderInfo.countryCode() + ".png");

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, textureId, startingX, startingY, 0.0F, 0.F, width, height, width, height);

        if (Config.cfg.flagBorder) {
            guiGraphics.outline(startingX - 1, startingY - 1, width + 2, height + 2, Config.cfg.borderColor.toARGB());
        }

        if (mouseX >= startingX && mouseX <= startingX + width && mouseY >= startingY && mouseY <= startingY + height) {
            guiGraphics.setTooltipForNextFrame(flagRenderInfo.tooltip(), mouseX, mouseY);
        }
    }

    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/network/chat/Component;II)V", ordinal = 0), method = "extractContent", require = 1)
    private void servercountryflags$appendPingTooltip(GuiGraphicsExtractor guiGraphics, Component component, int x, int y) {
        if (Config.cfg.flagPosition == FlagPosition.TOOLTIP_PING) {
            APIResponse apiResponse = ServerCountryFlags.servers.get(serverData.ip);
            FlagRenderInfo flagRenderInfo = ServerCountryFlags.getFlagRenderInfo(apiResponse);

            if (flagRenderInfo != null) {
                List<FormattedCharSequence> newTooltip = new ArrayList<>();
                newTooltip.add(component.getVisualOrderText());
                newTooltip.add(Component.literal(" ").getVisualOrderText());
                newTooltip.addAll(flagRenderInfo.tooltip());
                guiGraphics.setTooltipForNextFrame(newTooltip, x, y);
                return;
            }
        }

        guiGraphics.setTooltipForNextFrame(component, x, y);
    }
}
