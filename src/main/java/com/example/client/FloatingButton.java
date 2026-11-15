package com.example.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class FloatingButton {
    static boolean dragging, moved;
    static double sx, sy;
    static int ox, oy;

    static int size() { return UiConfig.SIZES[UiConfig.sizeI]; }
    static int px(int sw) { return Math.round(UiConfig.fx * (sw - size())); }
    static int py(int sh) { return Math.round(UiConfig.fy * (sh - size())); }

    static boolean inside(int sw, int sh, double mx, double my) {
        int x = px(sw), y = py(sh), s = size();
        return mx >= x && mx <= x + s && my >= y && my <= y + s;
    }

    static void draw(DrawContext ctx, int sw, int sh) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int s = size(), x = px(sw), y = py(sh);
        int a = Math.max(0, Math.min(255, (int) (UiConfig.opacity * 2.55f)));
        int fill = (a << 24) | (dragging ? 0x3A6EA5 : 0x1E1E1E);
        int line = (a << 24) | 0xFFFFFF;
        ctx.fill(x, y, x + s, y + s, fill);
        ctx.fill(x, y, x + s, y + 1, line);
        ctx.fill(x, y + s - 1, x + s, y + s, line);
        ctx.fill(x, y, x + 1, y + s, line);
        ctx.fill(x + s - 1, y, x + s, y + s, line);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, "AX", x + s / 2, y + (s - 8) / 2, 0xFFFFFF);
    }

    // вызывается каждый кадр на экране (чат, инвентарь, пауза и т.д.)
    static void render(DrawContext ctx, int sw, int sh, int mx, int my) {
        if (dragging) {
            if (Math.abs(mx - sx) + Math.abs(my - sy) > 3) moved = true;
            if (moved) {
                int span = Math.max(1, sw - size());
                int spanY = Math.max(1, sh - size());
                UiConfig.fx = Math.max(0f, Math.min(1f, (mx - ox) / (float) span));
                UiConfig.fy = Math.max(0f, Math.min(1f, (my - oy) / (float) spanY));
            }
        }
        draw(ctx, sw, sh);
    }

    static boolean press(int sw, int sh, double mx, double my, int btn) {
        if (btn != 0 || !inside(sw, sh, mx, my)) return false;
        dragging = true;
        moved = false;
        sx = mx;
        sy = my;
        ox = (int) mx - px(sw);
        oy = (int) my - py(sh);
        return true;
    }

    static boolean release(int btn) {
        if (!dragging || btn != 0) return false;
        dragging = false;
        if (moved) {
            if (UiConfig.snap) UiConfig.fx = UiConfig.fx < 0.5f ? 0f : 1f;
            UiConfig.save();
        } else {
            MinecraftClient.getInstance().setScreen(new AxMenuScreen());
        }
        return true;
    }
}
