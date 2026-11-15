package com.example.client;

import com.example.BuildTools;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.TypedActionResult;
import org.lwjgl.glfw.GLFW;

public class MyModClient implements ClientModInitializer {
    static KeyBinding KEY;

    @Override
    public void onInitializeClient() {
        UiConfig.load();

        // плавающая кнопка: в игре просто рисуется, на любом экране (чат, инвентарь, пауза) её можно таскать и нажимать
        HudRenderCallback.EVENT.register((ctx, td) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!UiConfig.visible || mc.currentScreen != null || mc.player == null || mc.options.hudHidden) return;
            FloatingButton.draw(ctx, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
        });
        ScreenEvents.AFTER_INIT.register((mc, screen, sw, sh) -> {
            if (screen instanceof AxMenuScreen || mc.world == null || !UiConfig.visible) return;
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, td) -> FloatingButton.render(ctx, s.width, s.height, mx, my));
            ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, b) -> !FloatingButton.press(s.width, s.height, mx, my, b));
            ScreenMouseEvents.allowMouseRelease(screen).register((s, mx, my, b) -> !FloatingButton.release(b));
        });

        KEY = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.mymod.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.mymod"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (KEY.wasPressed()) {
                if (mc.currentScreen == null && mc.player != null) mc.setScreen(new AxMenuScreen());
            }
        });

        // ПКМ предметом Build Menu открывает меню (удобно на телефоне)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack st = player.getStackInHand(hand);
            if (world.isClient && st.isOf(BuildTools.MENU)) {
                MinecraftClient.getInstance().setScreen(new AxMenuScreen());
                return TypedActionResult.success(st);
            }
            return TypedActionResult.pass(st);
        });

        // команда /axmenu
        ClientCommandRegistrationCallback.EVENT.register((d, ra) ->
            d.register(ClientCommandManager.literal("axmenu").executes(c -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.send(() -> mc.setScreen(new AxMenuScreen()));
                return 1;
            })));
    }
}
