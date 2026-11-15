package com.example;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class BuildTools implements ModInitializer {
    public static final Item MENU = new Item(new FabricItemSettings().maxCount(1));
    static final int MAX = 2_000_000;

    record Change(BlockPos pos, BlockState old) {}
    record Cell(BlockPos off, BlockState st) {}
    record Edit(ServerWorld w, List<Change> ch) {}

    static class Session {
        BlockPos p1, p2;
        List<Cell> clip = new ArrayList<>();
        Deque<Edit> undo = new ArrayDeque<>();
    }

    static final Map<UUID, Session> SES = new HashMap<>();

    static Session ses(ServerPlayerEntity p) {
        return SES.computeIfAbsent(p.getUuid(), k -> new Session());
    }

    static void msg(ServerCommandSource s, String t) {
        s.sendFeedback(() -> Text.literal(t), false);
    }

    static void put(ServerWorld w, BlockPos p, BlockState st, List<Change> ch) {
        BlockState old = w.getBlockState(p);
        if (old == st) return;
        ch.add(new Change(p.toImmutable(), old));
        w.setBlockState(p, st, Block.NOTIFY_LISTENERS);
    }

    static void commit(Session s, ServerWorld w, List<Change> ch, ServerCommandSource src) {
        if (!ch.isEmpty()) {
            s.undo.push(new Edit(w, ch));
            while (s.undo.size() > 30) s.undo.removeLast();
        }
        msg(src, "Изменено блоков: " + ch.size());
    }

    record Reg(BlockPos a, BlockPos b) {
        int x0() { return Math.min(a.getX(), b.getX()); }
        int y0() { return Math.min(a.getY(), b.getY()); }
        int z0() { return Math.min(a.getZ(), b.getZ()); }
        int x1() { return Math.max(a.getX(), b.getX()); }
        int y1() { return Math.max(a.getY(), b.getY()); }
        int z1() { return Math.max(a.getZ(), b.getZ()); }
        long vol() { return (long) (x1() - x0() + 1) * (y1() - y0() + 1) * (z1() - z0() + 1); }
    }

    static Reg region(Session s, ServerCommandSource src) {
        if (s.p1 == null || s.p2 == null) {
            msg(src, "Сначала выдели область: /ax wand (ЛКМ = точка 1, ПКМ = точка 2)");
            return null;
        }
        Reg r = new Reg(s.p1, s.p2);
        if (r.vol() > MAX) {
            msg(src, "Область слишком большая (максимум " + MAX + ")");
            return null;
        }
        return r;
    }

    interface Each { void at(BlockPos.Mutable p); }

    static void each(Reg r, Each e) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = r.x0(); x <= r.x1(); x++)
            for (int y = r.y0(); y <= r.y1(); y++)
                for (int z = r.z0(); z <= r.z1(); z++) {
                    m.set(x, y, z);
                    e.at(m);
                }
    }

    static BlockState bs(CommandContext<ServerCommandSource> c, String name) {
        return BlockStateArgumentType.getBlockState(c, name).getBlockState();
    }

    static Direction dir(CommandContext<ServerCommandSource> c, ServerPlayerEntity p) {
        String n = StringArgumentType.getString(c, "dir").toLowerCase();
        if (n.equals("me")) return Direction.getEntityFacingOrder(p)[0];
        return Direction.byName(n);
    }

    // ---------- команды ----------

    static int pos(CommandContext<ServerCommandSource> c, int n) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
        Session s = ses(p);
        if (n == 1) s.p1 = p.getBlockPos(); else s.p2 = p.getBlockPos();
        msg(c.getSource(), "Точка " + n + ": " + p.getBlockPos().toShortString());
        return 1;
    }

    // mode 0 = fill, 1 = walls, 2 = hollow
    static int fill(CommandContext<ServerCommandSource> c, int mode) throws CommandSyntaxException {
        ServerCommandSource src = c.getSource();
        ServerPlayerEntity p = src.getPlayerOrThrow();
        Session s = ses(p);
        Reg r = region(s, src);
        if (r == null) return 0;
        BlockState st = mode == 2 ? Blocks.AIR.getDefaultState() : bs(c, "block");
        ServerWorld w = src.getWorld();
        List<Change> ch = new ArrayList<>();
        each(r, m -> {
            boolean edge = m.getX() == r.x0() || m.getX() == r.x1() || m.getZ() == r.z0() || m.getZ() == r.z1();
            boolean edgeY = m.getY() == r.y0() || m.getY() == r.y1();
            if (mode == 0 || (mode == 1 && edge)) put(w, m, st, ch);
            else if (mode == 2 && !edge && !edgeY) put(w, m, st, ch);
        });
        commit(s, w, ch, src);
        return 1;
    }

    static int replace(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerCommandSource src = c.getSource();
        ServerPlayerEntity p = src.getPlayerOrThrow();
        Session s = ses(p);
        Reg r = region(s, src);
        if (r == null) return 0;
        Block from = bs(c, "from").getBlock();
        BlockState to = bs(c, "to");
        ServerWorld w = src.getWorld();
        List<Change> ch = new ArrayList<>();
        each(r, m -> { if (w.getBlockState(m).isOf(from)) put(w, m, to, ch); });
        commit(s, w, ch, src);
        return 1;
    }

    // type 0 = sphere, 1 = hollow sphere, 2 = cylinder
    static int shape(CommandContext<ServerCommandSource> c, int type) throws CommandSyntaxException {
        ServerCommandSource src = c.getSource();
        ServerPlayerEntity p = src.getPlayerOrThrow();
        Session s = ses(p);
        BlockState st = bs(c, "block");
        int rad = IntegerArgumentType.getInteger(c, "r");
        int h = type == 2 ? IntegerArgumentType.getInteger(c, "h") : 0;
        BlockPos o = p.getBlockPos();
        ServerWorld w = src.getWorld();
        List<Change> ch = new ArrayList<>();
        for (int dx = -rad; dx <= rad; dx++)
            for (int dz = -rad; dz <= rad; dz++) {
                if (type == 2) {
                    if (dx * dx + dz * dz > rad * rad + rad) continue;
                    for (int dy = 0; dy < h; dy++) put(w, o.add(dx, dy, dz), st, ch);
                } else {
                    for (int dy = -rad; dy <= rad; dy++) {
                        int d2 = dx * dx + dy * dy + dz * dz;
                        boolean in = d2 <= rad * rad + rad;
                        boolean inner = d2 <= (rad - 1) * (rad - 1) + (rad - 1);
                        if (in && (type == 0 || !inner)) put(w, o.add(dx, dy, dz), st, ch);
                    }
                }
            }
        commit(s, w, ch, src);
        return 1;
    }

    static int copy(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerCommandSource src = c.getSource();
        ServerPlayerEntity p = src.getPlayerOrThrow();
        Session s = ses(p);
        Reg r = region(s, src);
        if (r == null) return 0;
        BlockPos o = p.getBlockPos();
        ServerWorld w = src.getWorld();
        List<Cell> cl = new ArrayList<>();
        each(r, m -> cl.add(new Cell(m.toImmutable().subtract(o), w.getBlockState(m))));
        s.clip = cl;
        msg(src, "Скопировано блоков: " + cl.size());
        return 1;
    }

    static int paste(CommandContext<ServerCommandSource> c, boolean air) throws CommandSyntaxException {
        ServerCommandSource src = c.getSource();
        ServerPlayerEntity p = src.getPlayerOrThrow();
        Session s = ses(p);
        if (s.clip.isEmpty()) { msg(src, "Буфер пуст: /ax copy"); return 0; }
        BlockPos o = p.getBlockPos();
        ServerWorld w = src.getWorld();
        List<Change> ch = new ArrayList<>();
        for (Cell cell : s.clip) {
            if (!air && cell.st().isAir()) continue;
            put(w, o.add(cell.off()), cell.st(), ch);
        }
        commit(s, w, ch, src);
        return 1;
    }

    static int flip(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerCommandSource src = c.getSource();
        Session s = ses(src.getPlayerOrThrow());
        String a = StringArgumentType.getString(c, "axis").toLowerCase();
        List<Cell> out = new ArrayList<>();
        for (Cell cell : s.clip) {
            BlockPos q = cell.off();
            BlockState st = cell.st();
            switch (a) {
                case "x" -> { q = new BlockPos(-q.getX(), q.getY(), q.getZ()); st = st.mirror(BlockMirror.FRONT_BACK); }
                case "y" -> q = new BlockPos(q.getX(), -q.getY(), q.getZ());
                case "z" -> { q = new BlockPos(q.getX(), q.getY(), -q.getZ()); st = st.mirror(BlockMirror.LEFT_RIGHT); }
                default -> { msg(src, "Ось: x, y или z"); return 0; }
            }
            out.add(new Cell(q, st));
        }
        s.clip = out;
        msg(src, "Буфер отражён по " + a);
        return 1;
    }

    static int rotate(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerCommandSource src = c.getSource();
        Session s = ses(src.getPlayerOrThrow());
        int deg = IntegerArgumentType.getInteger(c, "deg");
        int times = deg / 90;
        BlockRotation rot = switch (times) {
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            default -> BlockRotation.COUNTERCLOCKWISE_90;
        };
        List<Cell> out = new ArrayList<>();
        for (Cell cell : s.clip) {
            int x = cell.off().getX(), z = cell.off().getZ();
            for (int i = 0; i < times; i++) { int nx = -z; z = x; x = nx; }
            out.add(new Cell(new BlockPos(x, cell.off().getY(), z), cell.st().rotate(rot)));
        }
        s.clip = out;
        msg(src, "Буфер повёрнут на " + deg);
        return 1;
    }

    static List<Cell> cells(Reg r, ServerWorld w) {
        List<Cell> l = new ArrayList<>();
        each(r, m -> l.add(new Cell(m.toImmutable(), w.getBlockState(m))));
        return l;
    }

    static int size(Reg r, Direction d) {
        return switch (d.getAxis()) {
            case X -> r.x1() - r.x0() + 1;
            case Y -> r.y1() - r.y0() + 1;
            case Z -> r.z1() - r.z0() + 1;
        };
    }

    static int stack(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerCommandSource src = c.getSource();
        ServerPlayerEntity p = src.getPlayerOrThrow();
        Session s = ses(p);
        Reg r = region(s, src);
        if (r == null) return 0;
        Direction d = dir(c, p);
        if (d == null) { msg(src, "Направление: up down north south east west me"); return 0; }
        int n = IntegerArgumentType.getInteger(c, "n");
        ServerWorld w = src.getWorld();
        List<Cell> base = cells(r, w);
        int step = size(r, d);
        if ((long) base.size() * n > MAX) { msg(src, "Слишком много блоков"); return 0; }
        List<Change> ch = new ArrayList<>();
        for (int i = 1; i <= n; i++)
            for (Cell cell : base) put(w, cell.off().offset(d, step * i), cell.st(), ch);
        commit(s, w, ch, src);
        return 1;
    }

    static int move(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerCommandSource src = c.getSource();
        ServerPlayerEntity p = src.getPlayerOrThrow();
        Session s = ses(p);
        Reg r = region(s, src);
        if (r == null) return 0;
        Direction d = dir(c, p);
        if (d == null) { msg(src, "Направление: up down north south east west me"); return 0; }
        int n = IntegerArgumentType.getInteger(c, "n");
        ServerWorld w = src.getWorld();
        List<Cell> base = cells(r, w);
        List<Change> ch = new ArrayList<>();
        BlockState air = Blocks.AIR.getDefaultState();
        for (Cell cell : base) put(w, cell.off(), air, ch);
        for (Cell cell : base) put(w, cell.off().offset(d, n), cell.st(), ch);
        s.p1 = s.p1.offset(d, n);
        s.p2 = s.p2.offset(d, n);
        commit(s, w, ch, src);
        return 1;
    }

    static int undo(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerCommandSource src = c.getSource();
        Session s = ses(src.getPlayerOrThrow());
        Edit e = s.undo.poll();
        if (e == null) { msg(src, "Нечего отменять"); return 0; }
        for (int i = e.ch().size() - 1; i >= 0; i--) {
            Change ch = e.ch().get(i);
            e.w().setBlockState(ch.pos(), ch.old(), Block.NOTIFY_LISTENERS);
        }
        msg(src, "Отменено блоков: " + e.ch().size());
        return 1;
    }

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, new Identifier("mymod", "build_menu"), MENU);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> e.add(MENU));
        // Топор: ЛКМ = точка 1, ПКМ = точка 2 (только в креативе)
        AttackBlockCallback.EVENT.register((player, world, hand, pos, d) -> {
            if (hand == Hand.MAIN_HAND && player.isCreative() && player.getStackInHand(hand).isOf(Items.WOODEN_AXE)) {
                if (!world.isClient && player instanceof ServerPlayerEntity sp) {
                    ses(sp).p1 = pos.toImmutable();
                    sp.sendMessage(Text.literal("Точка 1: " + pos.toShortString()), true);
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand == Hand.MAIN_HAND && player.isCreative() && player.getStackInHand(hand).isOf(Items.WOODEN_AXE)) {
                if (!world.isClient && player instanceof ServerPlayerEntity sp) {
                    ses(sp).p2 = hit.getBlockPos().toImmutable();
                    sp.sendMessage(Text.literal("Точка 2: " + hit.getBlockPos().toShortString()), true);
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        CommandRegistrationCallback.EVENT.register((d, ra, env) -> d.register(
            literal("ax").requires(src -> src.hasPermissionLevel(2))
                .then(literal("wand").executes(c -> {
                    c.getSource().getPlayerOrThrow().giveItemStack(new ItemStack(Items.WOODEN_AXE));
                    c.getSource().getPlayerOrThrow().giveItemStack(new ItemStack(MENU));
                    msg(c.getSource(), "Топор выдан. ЛКМ = точка 1, ПКМ = точка 2 (креатив)");
                    return 1;
                }))
                .then(literal("pos1").executes(c -> pos(c, 1)))
                .then(literal("pos2").executes(c -> pos(c, 2)))
                .then(literal("fill").then(argument("block", BlockStateArgumentType.blockState(ra)).executes(c -> fill(c, 0))))
                .then(literal("walls").then(argument("block", BlockStateArgumentType.blockState(ra)).executes(c -> fill(c, 1))))
                .then(literal("hollow").executes(c -> fill(c, 2)))
                .then(literal("replace").then(argument("from", BlockStateArgumentType.blockState(ra))
                    .then(argument("to", BlockStateArgumentType.blockState(ra)).executes(BuildTools::replace))))
                .then(literal("sphere").then(argument("block", BlockStateArgumentType.blockState(ra))
                    .then(argument("r", IntegerArgumentType.integer(1, 100)).executes(c -> shape(c, 0)))))
                .then(literal("hsphere").then(argument("block", BlockStateArgumentType.blockState(ra))
                    .then(argument("r", IntegerArgumentType.integer(1, 100)).executes(c -> shape(c, 1)))))
                .then(literal("cyl").then(argument("block", BlockStateArgumentType.blockState(ra))
                    .then(argument("r", IntegerArgumentType.integer(1, 100))
                        .then(argument("h", IntegerArgumentType.integer(1, 256)).executes(c -> shape(c, 2))))))
                .then(literal("copy").executes(BuildTools::copy))
                .then(literal("paste").executes(c -> paste(c, false))
                    .then(literal("air").executes(c -> paste(c, true))))
                .then(literal("flip").then(argument("axis", StringArgumentType.word()).executes(BuildTools::flip)))
                .then(literal("rotate").then(argument("deg", IntegerArgumentType.integer(90, 270)).executes(BuildTools::rotate)))
                .then(literal("stack").then(argument("dir", StringArgumentType.word())
                    .then(argument("n", IntegerArgumentType.integer(1, 100)).executes(BuildTools::stack))))
                .then(literal("move").then(argument("dir", StringArgumentType.word())
                    .then(argument("n", IntegerArgumentType.integer(1, 500)).executes(BuildTools::move))))
                .then(literal("undo").executes(BuildTools::undo))
        ));
    }
}
