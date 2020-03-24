package com.ninjaguild.dragoneggdrop.listeners;

import java.util.List;

import com.ninjaguild.dragoneggdrop.DragonEggDrop;
import com.ninjaguild.dragoneggdrop.api.BattleState;
import com.ninjaguild.dragoneggdrop.api.BattleStateChangeEvent;
import com.ninjaguild.dragoneggdrop.api.PortalCrystal;
import com.ninjaguild.dragoneggdrop.dragon.DragonTemplate;
import com.ninjaguild.dragoneggdrop.tasks.DragonDeathRunnable;
import com.ninjaguild.dragoneggdrop.utils.ReflectionUtil;
import com.ninjaguild.dragoneggdrop.world.EndWorldWrapper;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class DragonLifeListeners implements Listener {

    private static final ItemStack END_CRYSTAL_ITEM = new ItemStack(Material.END_CRYSTAL);

    private final DragonEggDrop plugin;

    public DragonLifeListeners(DragonEggDrop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }

        EnderDragon dragon = (EnderDragon) event.getEntity();
        if (dragon.getWorld().getEnvironment() != Environment.THE_END) {
            return;
        }

        DragonBattle dragonBattle = dragon.getDragonBattle();
        EndWorldWrapper world = EndWorldWrapper.of(dragon.getWorld());

        DragonTemplate template = world.getRespawningTemplate();
        if (plugin.getConfig().getBoolean("strict-countdown") && world.isRespawnInProgress()) {
            world.stopRespawn();
        }

        world.setActiveTemplate((template != null) ? template : (template = DragonTemplate.randomTemplate()));
        world.setRespawningTemplate(null);

        template.applyToBattle(dragon, dragonBattle);

        if (template.shouldAnnounceSpawn()) {
            List<String> announcement = template.getSpawnAnnouncement();
            // Cannot use p::sendMessage here. Compile-error with Maven. Compiler assumes the wrong method overload
            Bukkit.getOnlinePlayers().forEach(p -> announcement.forEach(m -> p.sendMessage(m)));
        }

        BattleStateChangeEvent bscEventCrystals = new BattleStateChangeEvent(dragonBattle, dragon, BattleState.DRAGON_RESPAWNING, BattleState.BATTLE_COMMENCED);
        Bukkit.getPluginManager().callEvent(bscEventCrystals);
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }

        EnderDragon dragon = (EnderDragon) event.getEntity();
        DragonBattle dragonBattle = dragon.getDragonBattle();

        World world = event.getEntity().getWorld();
        EndWorldWrapper worldWrapper = EndWorldWrapper.of(world);

        BattleStateChangeEvent bscEventCrystals = new BattleStateChangeEvent(dragonBattle, dragon, BattleState.BATTLE_COMMENCED, BattleState.BATTLE_END);
        Bukkit.getPluginManager().callEvent(bscEventCrystals);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (dragon.getDeathAnimationTicks() >= 185) { // Dragon is dead at 200
                    new DragonDeathRunnable(plugin, worldWrapper, dragon);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    @EventHandler
    public void onAttemptRespawn(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (block == null || block.getType() != Material.BEDROCK || item == null || item.getType() != Material.END_CRYSTAL
                || plugin.getConfig().getBoolean("allow-crystal-respawns")) {
            return;
        }

        World world = player.getWorld();
        List<EnderCrystal> crystals = PortalCrystal.getAllSpawnedCrystals(world);

        // Check for 3 crystals because PlayerInteractEvent is fired first
        if (crystals.size() < 3) {
            return;
        }

        Vector portalLocationVector = world.getEnderDragonBattle().getEndPortalLocation().toVector();
        for (EnderCrystal crystal : crystals) {
            Location location = crystal.getLocation();
            location.getBlock().setType(Material.AIR); // Remove fire

            Item droppedCrystal = world.dropItem(location, END_CRYSTAL_ITEM);
            droppedCrystal.setVelocity(crystal.getLocation().toVector().subtract(portalLocationVector).normalize().multiply(0.15).setY(0.5));

            crystal.remove();
        }

        ReflectionUtil.sendActionBar(ChatColor.RED + "You cannot manually respawn a dragon!", player);
        player.sendMessage(ChatColor.RED + "You cannot manually respawn a dragon!");
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0F, 1.5F);
        event.setCancelled(true);
    }

}