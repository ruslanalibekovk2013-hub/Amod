package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class MyMod implements ModInitializer {
    static final BooleanProperty ON = Properties.POWERED;

    static FabricBlockSettings st() { return FabricBlockSettings.create().strength(1.5f); }

    static class Emitter extends Block {
        Emitter(Settings s) {
            super(s);
            setDefaultState(getStateManager().getDefaultState().with(ON, false));
        }
        @Override protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(ON); }
        @Override public boolean emitsRedstonePower(BlockState s) { return true; }
        @Override public int getWeakRedstonePower(BlockState s, BlockView w, BlockPos p, Direction d) { return s.get(ON) ? 15 : 0; }
        @Override public int getStrongRedstonePower(BlockState s, BlockView w, BlockPos p, Direction d) { return s.get(ON) ? 15 : 0; }
    }

    public static final Block POWER = new Block(st()) {
        @Override public boolean emitsRedstonePower(BlockState s) { return true; }
        @Override public int getWeakRedstonePower(BlockState s, BlockView w, BlockPos p, Direction d) { return 15; }
        @Override public int getStrongRedstonePower(BlockState s, BlockView w, BlockPos p, Direction d) { return 15; }
    };

    public static final Block JUMP = new Block(st()) {
        @Override public void onSteppedOn(World w, BlockPos p, BlockState s, Entity e) {
            e.addVelocity(0, 1.2, 0);
            e.velocityModified = true;
            e.fallDistance = 0;
        }
    };

    public static final Block SPEED = new Block(st()) {
        @Override public void onSteppedOn(World w, BlockPos p, BlockState s, Entity e) {
            if (!w.isClient && e instanceof LivingEntity le)
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 4, false, false));
        }
    };

    public static final Block TIMER = new Emitter(st()) {
        @Override public void onBlockAdded(BlockState s, World w, BlockPos p, BlockState old, boolean moved) {
            w.scheduleBlockTick(p, this, 20);
        }
        @Override public void scheduledTick(BlockState s, ServerWorld w, BlockPos p, Random r) {
            boolean on = !s.get(ON);
            w.setBlockState(p, s.with(ON, on));
            w.scheduleBlockTick(p, this, on ? 10 : 30);
        }
    };

    public static final Block DETECTOR = new Emitter(st()) {
        @Override public void onBlockAdded(BlockState s, World w, BlockPos p, BlockState old, boolean moved) {
            w.scheduleBlockTick(p, this, 10);
        }
        @Override public void scheduledTick(BlockState s, ServerWorld w, BlockPos p, Random r) {
            boolean near = w.getClosestPlayer(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 8, false) != null;
            if (near != s.get(ON)) w.setBlockState(p, s.with(ON, near));
            w.scheduleBlockTick(p, this, 10);
        }
    };

    public static final Block LIGHTNING = new Block(st()) {
        { setDefaultState(getStateManager().getDefaultState().with(ON, false)); }
        @Override protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(ON); }
        @Override public void neighborUpdate(BlockState s, World w, BlockPos p, Block b, BlockPos from, boolean notify) {
            if (w.isClient) return;
            boolean pow = w.isReceivingRedstonePower(p);
            if (pow && !s.get(ON)) {
                w.setBlockState(p, s.with(ON, true), 3);
                LightningEntity l = EntityType.LIGHTNING_BOLT.create(w);
                if (l != null) {
                    l.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(p.up()));
                    w.spawnEntity(l);
                }
            } else if (!pow && s.get(ON)) {
                w.setBlockState(p, s.with(ON, false), 3);
            }
        }
    };

    static void reg(String id, Block b) {
        Registry.register(Registries.BLOCK, new Identifier("mymod", id), b);
        Registry.register(Registries.ITEM, new Identifier("mymod", id), new BlockItem(b, new FabricItemSettings()));
    }

    @Override
    public void onInitialize() {
        reg("power_block", POWER);
        reg("jump_pad", JUMP);
        reg("speed_pad", SPEED);
        reg("timer_block", TIMER);
        reg("detector_block", DETECTOR);
        reg("lightning_block", LIGHTNING);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(e -> {
            e.add(POWER); e.add(JUMP); e.add(SPEED); e.add(TIMER); e.add(DETECTOR); e.add(LIGHTNING);
        });
    }
}
