package com.example.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AxMenuScreen extends Screen {
    static final String[] TABS = {"Выд", "Зал", "Фиг", "Блк", "КБ", "Мир", "Игр", "Мак", "Утл", "Нст"};
    static final String[] DIRS = {"up", "down", "north", "south", "east", "west", "me"};

    static int tab, page, dirI;
    static boolean presetFrom;
    static String block = "stone", from = "stone", rS = "5", nS = "1", cmd = "say Привет";
    static String status = "";

    record Act(String label, Runnable act) {}

    int pages = 1;
    int bx, fx, rx, nx;

    public AxMenuScreen() {
        super(Text.literal("Build Menu"));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void removed() { UiConfig.save(); }

    // ---------- helpers ----------
    static void send(String... cs) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        for (String c : cs) {
            mc.player.networkHandler.sendChatCommand(c);
            status = "/" + c;
        }
    }

    static Act c(String l, String... cs) { return new Act(l, () -> send(cs)); }
    static Act d(String l, Supplier<String> s) { return new Act(l, () -> send(s.get())); }
    static Act set(String l, Runnable r) { return new Act(l, r); }

    static Act m(String l, Supplier<String[]> s) { return new Act(l, () -> send(s.get())); }

    static String onoff(boolean v) { return v ? "ВКЛ" : "ВЫКЛ"; }

    static int num(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    static int r() { return Math.max(1, Math.min(100, num(rS, 5))); }
    static int n() { return Math.max(1, Math.min(500, num(nS, 1))); }
    static String dir() { return DIRS[dirI]; }

    static String esc(String s) {
        s = s.trim();
        if (s.startsWith("/")) s = s.substring(1);
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static Act cb(String l, String item, boolean auto, boolean cond) {
        return d(l, () -> "give @s " + item + "{BlockEntityTag:{Command:\"" + esc(cmd) + "\",auto:" + (auto ? 1 : 0)
            + "b,TrackOutput:0b},BlockStateTag:{conditional:\"" + cond + "\"}} 1");
    }

    static Act cmdPreset(String l, String text) { return set(l, () -> cmd = text); }
    static Act eff(String l, String id, int amp) {
        return c(l, "effect give @s minecraft:" + id + " 999999 " + amp + " true");
    }

    static Act preset(String id) {
        return set(id, () -> { if (presetFrom) from = id; else block = id; });
    }

    static List<Act> acts(int t) {
        List<Act> a = new ArrayList<>();
        switch (t) {
            case 0 -> {
                a.add(c("Топор + меню", "ax wand"));
                a.add(c("Точка 1 (здесь)", "ax pos1"));
                a.add(c("Точка 2 (здесь)", "ax pos2"));
                a.add(c("Отменить", "ax undo"));
                a.add(c("Копировать", "ax copy"));
                a.add(c("Вставить", "ax paste"));
                a.add(c("Вставить + воздух", "ax paste air"));
                a.add(c("Отразить X", "ax flip x"));
                a.add(c("Отразить Y", "ax flip y"));
                a.add(c("Отразить Z", "ax flip z"));
                a.add(c("Поворот 90", "ax rotate 90"));
                a.add(c("Поворот 180", "ax rotate 180"));
                a.add(c("Поворот 270", "ax rotate 270"));
                a.add(d("Сдвиг (Напр., N)", () -> "ax move " + dir() + " " + n()));
                a.add(d("Стак (Напр., N)", () -> "ax stack " + dir() + " " + Math.min(100, n())));
            }
            case 1 -> {
                a.add(d("Заливка", () -> "ax fill " + block));
                a.add(d("Стены", () -> "ax walls " + block));
                a.add(c("Выдолбить", "ax hollow"));
                a.add(d("Замена Из -> Блок", () -> "ax replace " + from + " " + block));
                a.add(c("Стереть всё", "ax fill air"));
                a.add(c("Вода -> воздух", "ax replace water air"));
                a.add(c("Лава -> воздух", "ax replace lava air"));
                a.add(c("Трава -> воздух", "ax replace grass air"));
                a.add(c("Снег -> воздух", "ax replace snow air"));
                a.add(c("Огонь -> воздух", "ax replace fire air"));
                a.add(c("Листва дуба -> воздух", "ax replace oak_leaves air"));
                a.add(c("Камень -> воздух", "ax replace stone air"));
            }
            case 2 -> {
                a.add(d("Сфера (R)", () -> "ax sphere " + block + " " + r()));
                a.add(d("Полая сфера (R)", () -> "ax hsphere " + block + " " + r()));
                a.add(d("Цилиндр (R, N=высота)", () -> "ax cyl " + block + " " + r() + " " + Math.min(256, n())));
                a.add(d("Вырезать сферу (R)", () -> "ax sphere air " + r()));
                a.add(d("Вырезать цилиндр (R, N)", () -> "ax cyl air " + r() + " " + Math.min(256, n())));
                a.add(d("Купол из стекла (R)", () -> "ax hsphere glass " + r()));
                a.add(d("Шар света (R)", () -> "ax hsphere sea_lantern " + r()));
                a.add(d("Колонна (R=1, N)", () -> "ax cyl " + block + " 1 " + Math.min(256, n())));
            }
            case 3 -> {
                a.add(set("Цель: " + (presetFrom ? "ИЗ" : "БЛОК") + " (нажми)", () -> presetFrom = !presetFrom));
                for (String id : new String[]{
                    "stone", "air", "glass", "stone_bricks", "smooth_stone", "oak_planks", "spruce_planks",
                    "dark_oak_planks", "quartz_block", "white_concrete", "black_concrete", "gray_concrete",
                    "red_concrete", "blue_concrete", "lime_concrete", "yellow_concrete", "orange_concrete",
                    "purple_concrete", "glowstone", "sea_lantern", "redstone_block", "redstone_lamp",
                    "obsidian", "iron_block", "gold_block", "diamond_block", "bricks", "sandstone",
                    "netherrack", "water", "lava", "barrier", "dirt", "grass_block", "sand", "deepslate",
                    "cobblestone", "tnt", "slime_block", "honey_block", "target", "observer"})
                    a.add(preset(id));
            }
            case 4 -> {
                a.add(c("Командный блок", "give @s command_block"));
                a.add(c("Повторяющийся", "give @s repeating_command_block"));
                a.add(c("Цепной", "give @s chain_command_block"));
                a.add(cb("КБ с командой (импульс)", "command_block", false, false));
                a.add(cb("КБ повторяющ. (всегда)", "repeating_command_block", true, false));
                a.add(cb("КБ цепной (всегда)", "chain_command_block", true, false));
                a.add(cb("КБ цепной условный", "chain_command_block", true, true));
                a.add(c("Набор редстоуна",
                    "give @s redstone 64", "give @s repeater 16", "give @s comparator 16", "give @s observer 16",
                    "give @s piston 16", "give @s sticky_piston 16", "give @s redstone_block 16",
                    "give @s redstone_lamp 16", "give @s lever 8", "give @s stone_button 8", "give @s target 8",
                    "give @s hopper 8", "give @s dropper 8", "give @s dispenser 8"));
                a.add(c("Без вывода КБ", "gamerule commandBlockOutput false"));
                a.add(c("Без сообщений команд", "gamerule sendCommandFeedback false"));
                a.add(c("Длинные цепочки", "gamerule maxCommandChainLength 1000000"));
                a.add(c("Не писать админ-лог", "gamerule logAdminCommands false"));
                a.add(cmdPreset("Шаблон: say", "say Привет"));
                a.add(cmdPreset("Шаблон: эффект всем", "effect give @a minecraft:speed 10 1 true"));
                a.add(cmdPreset("Шаблон: ТП игрока", "tp @p 0 100 0"));
                a.add(cmdPreset("Шаблон: заголовок", "title @a title {\"text\":\"Привет\"}"));
                a.add(cmdPreset("Шаблон: частицы", "execute as @a at @s run particle flame ~ ~1 ~ 0.3 0.5 0.3 0.01 10"));
                a.add(cmdPreset("Шаблон: выдать алмаз", "give @p diamond"));
                a.add(cmdPreset("Шаблон: звук", "playsound minecraft:entity.player.levelup master @a"));
                a.add(cmdPreset("Шаблон: убрать блок", "setblock ~ ~ ~ air"));
                a.add(cmdPreset("Шаблон: молния", "summon lightning_bolt ~ ~ ~"));
                a.add(cmdPreset("Шаблон: взрыв", "summon tnt ~ ~ ~ {Fuse:0}"));
                a.add(cmdPreset("Шаблон: батут", "execute as @a at @s if block ~ ~-0.5 ~ lime_concrete run effect give @s levitation 1 12 true"));
                a.add(cmdPreset("Шаблон: счётчик", "scoreboard players add #t timer 1"));
            }
            case 5 -> {
                a.add(c("День", "time set day"));
                a.add(c("Полдень", "time set noon"));
                a.add(c("Ночь", "time set night"));
                a.add(c("Полночь", "time set midnight"));
                a.add(c("Ясно", "weather clear"));
                a.add(c("Дождь", "weather rain"));
                a.add(c("Гроза", "weather thunder"));
                a.add(c("Стоп смена дня", "gamerule doDaylightCycle false"));
                a.add(c("Старт смена дня", "gamerule doDaylightCycle true"));
                a.add(c("Стоп погода", "gamerule doWeatherCycle false"));
                a.add(c("Мобы не спавнятся", "gamerule doMobSpawning false"));
                a.add(c("Мобы спавнятся", "gamerule doMobSpawning true"));
                a.add(c("Моб-грифинг выкл", "gamerule mobGriefing false"));
                a.add(c("Огонь не горит", "gamerule doFireTick false"));
                a.add(c("Сохранять вещи", "gamerule keepInventory true"));
                a.add(c("Рандом-тик 100", "gamerule randomTickSpeed 100"));
                a.add(c("Рандом-тик 3", "gamerule randomTickSpeed 3"));
                a.add(c("Мирная сложность", "difficulty peaceful"));
                a.add(c("Нормальная сложность", "difficulty normal"));
                a.add(c("Точка спавна здесь", "setworldspawn ~ ~ ~"));
                a.add(c("Убрать предметы", "kill @e[type=item]"));
                a.add(c("Убить всех мобов", "kill @e[type=!player]"));
            }
            case 6 -> {
                a.add(c("Креатив", "gamemode creative"));
                a.add(c("Выживание", "gamemode survival"));
                a.add(c("Наблюдатель", "gamemode spectator"));
                a.add(c("Приключение", "gamemode adventure"));
                a.add(eff("Ночное зрение", "night_vision", 0));
                a.add(eff("Скорость", "speed", 3));
                a.add(eff("Прыгучесть", "jump_boost", 3));
                a.add(eff("Спешка", "haste", 5));
                a.add(eff("Сопротивление", "resistance", 4));
                a.add(eff("Регенерация", "regeneration", 3));
                a.add(eff("Огнестойкость", "fire_resistance", 0));
                a.add(eff("Дыхание в воде", "water_breathing", 0));
                a.add(c("Убрать эффекты", "effect clear @s"));
                a.add(c("Лечение", "effect give @s minecraft:instant_health 1 10 true",
                    "effect give @s minecraft:saturation 1 10 true"));
                a.add(c("Скорость ходьбы x2", "attribute @s minecraft:generic.movement_speed base set 0.2"));
                a.add(c("Скорость ходьбы обычн.", "attribute @s minecraft:generic.movement_speed base set 0.1"));
                a.add(c("ТП вверх 10", "tp @s ~ ~10 ~"));
                a.add(c("ТП вниз 10", "tp @s ~ ~-10 ~"));
                a.add(c("1000 уровней опыта", "xp add @s 1000 levels"));
                a.add(c("Очистить инвентарь", "clear @s"));
                a.add(c("Барьер", "give @s barrier"));
                a.add(c("Свет", "give @s light"));
                a.add(c("Пустота структур", "give @s structure_void"));
                a.add(c("Структурный блок", "give @s structure_block"));
                a.add(c("Пазл-блок", "give @s jigsaw"));
                a.add(c("Палка отладки", "give @s debug_stick"));
            }
            case 7 -> {
                a.add(c("Режим билдера", "gamemode creative", "effect give @s minecraft:night_vision 999999 0 true",
                    "effect give @s minecraft:speed 999999 2 true", "gamerule doDaylightCycle false",
                    "gamerule doMobSpawning false", "gamerule doWeatherCycle false", "gamerule mobGriefing false",
                    "gamerule keepInventory true", "time set day", "weather clear"));
                a.add(c("Режим выживания", "gamemode survival", "effect clear @s", "gamerule doDaylightCycle true",
                    "gamerule doMobSpawning true", "gamerule doWeatherCycle true", "gamerule mobGriefing true"));
                a.add(c("Режим командных блоков", "gamerule commandBlockOutput false", "gamerule sendCommandFeedback false",
                    "gamerule logAdminCommands false", "gamerule maxCommandChainLength 1000000",
                    "gamerule commandModificationBlockLimit 1000000"));
                a.add(d("Платформа 21x21 (Блок)", () -> "fill ~-10 ~-1 ~-10 ~10 ~-1 ~10 " + block));
                a.add(d("Платформа 41x41 (Блок)", () -> "fill ~-20 ~-1 ~-20 ~20 ~-1 ~20 " + block));
                a.add(d("Арена 21x21x10 (Блок)", () -> "fill ~-10 ~-1 ~-10 ~10 ~8 ~10 " + block + " hollow"));
                a.add(c("Стеклянная коробка 11", "fill ~-5 ~-1 ~-5 ~5 ~9 ~5 glass hollow"));
                a.add(c("Очистить куб 21", "fill ~-10 ~ ~-10 ~10 ~20 ~10 air"));
                a.add(c("Супер-кирка", "give @s netherite_pickaxe{Enchantments:[{id:\"minecraft:efficiency\",lvl:10},"
                    + "{id:\"minecraft:unbreaking\",lvl:10},{id:\"minecraft:fortune\",lvl:5}],Unbreakable:1b}"));
                a.add(c("Убрать мобов и предметы", "kill @e[type=!player]"));
            }
            case 8 -> {
                a.add(c("Счёт: timer", "scoreboard objectives add timer dummy"));
                a.add(c("Счёт: kills", "scoreboard objectives add kills playerKillCount"));
                a.add(c("Счёт: deaths", "scoreboard objectives add deaths deathCount"));
                a.add(c("timer -> сбросить #t", "scoreboard players set #t timer 0"));
                a.add(c("Показать timer справа", "scoreboard objectives setdisplay sidebar timer"));
                a.add(c("Убрать табло справа", "scoreboard objectives setdisplay sidebar"));
                a.add(c("Маркер (armor stand)", "summon armor_stand ~ ~ ~ {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"mark\"]}"));
                a.add(c("Убить маркеры", "kill @e[type=armor_stand,tag=mark]"));
                a.add(c("Тег admin себе", "tag @s add admin"));
                a.add(c("Данные: я", "data get entity @s"));
                a.add(c("Данные: блок под ногами", "data get block ~ ~-1 ~"));
                a.add(c("Загружать чанк здесь", "forceload add ~ ~"));
                a.add(c("Снять все forceload", "forceload remove all"));
                a.add(c("Босс-бар создать", "bossbar add mybar {\"text\":\"Бар\"}"));
                a.add(c("Босс-бар показать", "bossbar set minecraft:mybar players @a"));
                a.add(c("Команда red", "team add red", "team modify red color red"));
                a.add(c("Спавн-радиус 0", "gamerule spawnRadius 0"));
                a.add(c("tellraw золотой", "tellraw @a {\"text\":\"Привет\",\"color\":\"gold\"}"));
            }
            case 9 -> {
                a.add(set("Кнопка: " + onoff(UiConfig.visible), () -> UiConfig.visible = !UiConfig.visible));
                a.add(set("Размер кнопки: " + new String[]{"S", "M", "L"}[UiConfig.sizeI],
                    () -> UiConfig.sizeI = (UiConfig.sizeI + 1) % 3));
                a.add(set("Прозрачность: " + UiConfig.opacity + "%",
                    () -> UiConfig.opacity = UiConfig.opacity >= 100 ? 20 : UiConfig.opacity + 20));
                a.add(set("Закрывать после действия: " + onoff(UiConfig.closeAfter),
                    () -> UiConfig.closeAfter = !UiConfig.closeAfter));
                a.add(set("Показывать статус: " + onoff(UiConfig.showStatus),
                    () -> UiConfig.showStatus = !UiConfig.showStatus));
                a.add(set("Прилипать к краю: " + onoff(UiConfig.snap), () -> UiConfig.snap = !UiConfig.snap));
                a.add(set("Кнопка слева", () -> UiConfig.fx = 0f));
                a.add(set("Кнопка справа", () -> UiConfig.fx = 1f));
                a.add(set("Кнопка по центру", () -> { UiConfig.fx = 0.5f; UiConfig.fy = 0.5f; }));
                a.add(set("Сбросить позицию кнопки", () -> { UiConfig.fx = 0.92f; UiConfig.fy = 0.35f; }));
                a.add(set("Сбросить поля", () -> { block = "stone"; from = "stone"; rS = "5"; nS = "1"; dirI = 0; cmd = "say Привет"; }));
            }
            default -> {}
        }
        return a;
    }

    // ---------- UI ----------
    TextFieldWidget tf(int x, int y, int w, String val, Consumer<String> on) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 16, Text.literal(""));
        f.setMaxLength(128);
        f.setText(val);
        f.setChangedListener(on);
        addDrawableChild(f);
        return f;
    }

    @Override
    protected void init() {
        int tw = (width - 8) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            String l = (i == tab ? ">" : "") + TABS[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(l), b -> { tab = idx; page = 0; clearAndInit(); })
                .dimensions(4 + i * tw, 4, tw - 2, 18).build());
        }

        int top = 28;
        if (tab == 4) {
            TextFieldWidget cf = new TextFieldWidget(textRenderer, 4, 38, width - 8, 16, Text.literal(""));
            cf.setMaxLength(256);
            cf.setText(cmd);
            cf.setChangedListener(t -> cmd = t);
            addDrawableChild(cf);
            top = 58;
        }

        // нижняя строка полей
        int avail = width - 8 - 16;
        int blockW = (int) (avail * 0.30), fromW = (int) (avail * 0.22);
        int rW = (int) (avail * 0.09), nW = (int) (avail * 0.09);
        int dirW = avail - blockW - fromW - rW - nW;
        int fy = height - 44;
        bx = 4; fx = bx + blockW + 4; rx = fx + fromW + 4; nx = rx + rW + 4;
        int dx = nx + nW + 4;
        tf(bx, fy, blockW, block, t -> block = t);
        tf(fx, fy, fromW, from, t -> from = t);
        tf(rx, fy, rW, rS, t -> rS = t);
        tf(nx, fy, nW, nS, t -> nS = t);
        addDrawableChild(ButtonWidget.builder(Text.literal("Напр: " + dir()), b -> {
            dirI = (dirI + 1) % DIRS.length;
            clearAndInit();
        }).dimensions(dx, fy - 2, dirW, 20).build());

        // сетка кнопок
        int bottom = height - 58;
        int rows = Math.max(1, (bottom - top + 4) / 24);
        int cols = 3;
        int cap = rows * cols;
        List<Act> list = acts(tab);
        pages = Math.max(1, (list.size() + cap - 1) / cap);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;
        int bw = (width - 8 - 8) / cols;
        for (int i = page * cap; i < Math.min(list.size(), (page + 1) * cap); i++) {
            Act ac = list.get(i);
            int k = i - page * cap;
            int x = 4 + (k % cols) * (bw + 4);
            int y = top + (k / cols) * 24;
            addDrawableChild(ButtonWidget.builder(Text.literal(ac.label()), b -> {
                ac.act().run();
                UiConfig.save();
                if (UiConfig.closeAfter && tab != 9) close(); else clearAndInit();
            }).dimensions(x, y, bw, 20).build());
        }

        // навигация
        int ny = height - 22;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page--; clearAndInit(); })
            .dimensions(4, ny, 30, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { page++; clearAndInit(); })
            .dimensions(38, ny, 30, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> close())
            .dimensions(width - 64, ny, 60, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        super.render(ctx, mx, my, delta);
        int ly = height - 54;
        ctx.drawTextWithShadow(textRenderer, "Блок", bx, ly, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "Из", fx, ly, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "R", rx, ly, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "N", nx, ly, 0xAAAAAA);
        if (tab == 4) ctx.drawTextWithShadow(textRenderer, "Команда для командного блока:", 4, 27, 0xAAAAAA);
        String info = (page + 1) + "/" + pages + (UiConfig.showStatus ? "  " + status : "");
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(info, width - 72 - 70), 74, height - 16, 0xFFFFFF);
    }
}
