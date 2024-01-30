package mcjty.lostcities.setup;

import mcjty.lostcities.LostCities;
import mcjty.lostcities.gui.GuiLCConfig;
import mcjty.lostcities.gui.LostCitySetup;
import mcjty.lostcities.worldgen.LostCityFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.CreateWorldScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClientEventHandlers {

    private Button lostCitiesButton = null;

    @SubscribeEvent
    public void onGuiDraw(GuiScreenEvent.DrawScreenEvent event) {
        if (event.getGui() instanceof CreateWorldScreen && lostCitiesButton != null) {
            CreateWorldScreen screen = (CreateWorldScreen) event.getGui();
            lostCitiesButton.visible = screen.displayOptions;
            if (lostCitiesButton.visible) {
                Minecraft.getInstance().getTextureManager().bind(new ResourceLocation(LostCities.MODID, "textures/gui/configicon.png"));
                AbstractGui.blit(event.getMatrixStack(), screen.width - 100, 30, 70, 70, 256, 256, 256, 256, 256, 256);
            }
        }
    }

    @SubscribeEvent
    public void onGuiPost(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof CreateWorldScreen) {
            CreateWorldScreen screen = (CreateWorldScreen) event.getGui();
            lostCitiesButton = new Button(screen.width - 100, 10, 70, 20, new StringTextComponent("Cities"), p_onPress_1_ -> {
//                WorldType worldType = WorldType.WORLD_TYPES[screen.selectedIndex];
                Minecraft.getInstance().setScreen(new GuiLCConfig(screen /* @todo 1.16, worldType*/));
            });
            event.addWidget(lostCitiesButton);
        }
    }

    // To clean up client-side and single player
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LostCitySetup.CLIENT_SETUP.reset();
        Config.reset();
        LostCityFeature.globalDimensionInfoDirtyCounter++;
    }
}
