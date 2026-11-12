package me.khajiitos.servercountryflags.common.mixin;

import me.khajiitos.servercountryflags.common.ServerCountryFlags;
import me.khajiitos.servercountryflags.common.config.Config;
import me.khajiitos.servercountryflags.common.screen.ServerMapScreen;
import me.khajiitos.servercountryflags.common.util.NetworkChangeDetector;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public class JoinMultiplayerScreenMixin extends Screen {
    @Unique
    private static final WidgetSprites MAP_BUTTON_SPRITES = new WidgetSprites(
            Identifier.fromNamespaceAndPath(ServerCountryFlags.MOD_ID, "widget/map_button"),
            Identifier.fromNamespaceAndPath(ServerCountryFlags.MOD_ID, "widget/map_button_focused")
    );

    @Unique
    private static final WidgetSprites MAP_BUTTON_SPRITES_HIGH_CONTRAST = new WidgetSprites(
            Identifier.fromNamespaceAndPath(ServerCountryFlags.MOD_ID, "widget/map_button_high_contrast"),
            Identifier.fromNamespaceAndPath(ServerCountryFlags.MOD_ID, "widget/map_button_focused_high_contrast")
    );

    @Unique
    private ImageButton servercountryflags$serverMapButton = null;

    @Shadow
    private ServerList servers;

    private JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    public void constructor(CallbackInfo info) {
        if (Config.cfg.reloadOnRefresh) {
            ServerCountryFlags.clearLocations();
        }

        if (NetworkChangeDetector.check() || ServerCountryFlags.localLocation == null) {
            ServerCountryFlags.updateLocalLocationInfo();
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/multiplayer/JoinMultiplayerScreen;repositionElements()V"), method = "init")
    public void init(CallbackInfo info) {
        ServerCountryFlags.serverList = this.servers;

        for (int i = 0; i < this.servers.size(); i++) {
            ServerCountryFlags.requestServerLocation(servers.get(i).ip);
        }

        WidgetSprites sprites = this.minecraft.options.highContrast().get() ? MAP_BUTTON_SPRITES_HIGH_CONTRAST : MAP_BUTTON_SPRITES;
        servercountryflags$serverMapButton = this.addRenderableWidget(new ImageButton(0, 0, 20, 20, sprites,
                button -> this.minecraft.setScreenAndShow(new ServerMapScreen(this)),
                Component.translatable("servercountryflags.servermap.title")));
    }

    @Inject(at = @At("TAIL"), method = "repositionElements")
    protected void repositionElements(CallbackInfo info) {
        if (servercountryflags$serverMapButton != null) {
            servercountryflags$serverMapButton.visible = Config.cfg.mapButton;
            int preferredX = this.width / 2 + (Config.cfg.mapButtonRight ? 159 : -179);
            boolean fitsInFooter = preferredX >= 0 && preferredX + 20 <= this.width;
            int posX = fitsInFooter ? preferredX : Config.cfg.mapButtonRight ? this.width - 24 : 4;
            int posY = fitsInFooter ? this.height - 28 : 6;
            servercountryflags$serverMapButton.setPosition(posX, posY);
        }
    }
}
