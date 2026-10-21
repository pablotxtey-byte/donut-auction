package dev.donutauction;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class DonutAuctionClient implements ClientModInitializer {
	public static final String MOD_ID = "donutauction";

	private static KeyBinding openKey;

	@Override
	public void onInitializeClient() {
		AuctionConfig.load();

		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.donutauction.open",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_K,
			category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.wasPressed()) {
				if (client.player != null && client.currentScreen == null) {
					client.setScreen(new AuctionScreen());
				}
			}
			AuctionManager.tick(client);
		});

		// /pay messages from DonutSMP arrive as system ("game") messages, not as player chat.
		// Only these are read, so other players can't fake a bid by typing in chat.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) AuctionManager.onGameMessage(MinecraftClient.getInstance(), message.getString());
		});

		HudElementRegistry.addLast(Identifier.of(MOD_ID, "auction_status"), (context, tickCounter) -> AuctionHud.render(context));
	}
}
