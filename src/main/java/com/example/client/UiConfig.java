package com.example.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class UiConfig {
    public static boolean visible = true, closeAfter = false, showStatus = true, snap = true;
    public static int sizeI = 1, opacity = 80;
    public static float fx = 0.92f, fy = 0.35f;
    public static final int[] SIZES = {18, 24, 32};

    static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("mymod_ui.properties");
    }

    static boolean b(Properties p, String k, boolean d) {
        String v = p.getProperty(k);
        return v == null ? d : Boolean.parseBoolean(v);
    }

    static int i(Properties p, String k, int d) {
        try { return Integer.parseInt(p.getProperty(k)); } catch (Exception e) { return d; }
    }

    static float f(Properties p, String k, float d) {
        try { return Float.parseFloat(p.getProperty(k)); } catch (Exception e) { return d; }
    }

    public static void load() {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (Exception e) {
            return;
        }
        visible = b(p, "visible", true);
        closeAfter = b(p, "closeAfter", false);
        showStatus = b(p, "showStatus", true);
        snap = b(p, "snap", true);
        sizeI = Math.max(0, Math.min(2, i(p, "sizeI", 1)));
        opacity = Math.max(20, Math.min(100, i(p, "opacity", 80)));
        fx = Math.max(0f, Math.min(1f, f(p, "fx", 0.92f)));
        fy = Math.max(0f, Math.min(1f, f(p, "fy", 0.35f)));
        AxMenuScreen.block = p.getProperty("block", "stone");
        AxMenuScreen.from = p.getProperty("from", "stone");
        AxMenuScreen.rS = p.getProperty("r", "5");
        AxMenuScreen.nS = p.getProperty("n", "1");
        AxMenuScreen.cmd = p.getProperty("cmd", "say Привет");
        AxMenuScreen.dirI = Math.max(0, Math.min(AxMenuScreen.DIRS.length - 1, i(p, "dir", 0)));
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("visible", String.valueOf(visible));
        p.setProperty("closeAfter", String.valueOf(closeAfter));
        p.setProperty("showStatus", String.valueOf(showStatus));
        p.setProperty("snap", String.valueOf(snap));
        p.setProperty("sizeI", String.valueOf(sizeI));
        p.setProperty("opacity", String.valueOf(opacity));
        p.setProperty("fx", String.valueOf(fx));
        p.setProperty("fy", String.valueOf(fy));
        p.setProperty("block", AxMenuScreen.block);
        p.setProperty("from", AxMenuScreen.from);
        p.setProperty("r", AxMenuScreen.rS);
        p.setProperty("n", AxMenuScreen.nS);
        p.setProperty("cmd", AxMenuScreen.cmd);
        p.setProperty("dir", String.valueOf(AxMenuScreen.dirI));
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            p.store(w, "MyMod UI");
        } catch (Exception ignored) {
        }
    }
}
