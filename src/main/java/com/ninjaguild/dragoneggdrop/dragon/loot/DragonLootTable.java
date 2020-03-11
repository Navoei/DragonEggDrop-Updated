package com.ninjaguild.dragoneggdrop.dragon.loot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.ninjaguild.dragoneggdrop.DragonEggDrop;
import com.ninjaguild.dragoneggdrop.dragon.loot.elements.DragonLootElementCommand;
import com.ninjaguild.dragoneggdrop.dragon.loot.elements.DragonLootElementEgg;
import com.ninjaguild.dragoneggdrop.dragon.loot.elements.DragonLootElementItem;
import com.ninjaguild.dragoneggdrop.dragon.loot.elements.IDragonLootElement;
import com.ninjaguild.dragoneggdrop.dragon.loot.pool.ILootPool;
import com.ninjaguild.dragoneggdrop.dragon.loot.pool.LootPoolCommand;
import com.ninjaguild.dragoneggdrop.dragon.loot.pool.LootPoolItem;
import com.ninjaguild.dragoneggdrop.nms.DragonBattle;
import com.ninjaguild.dragoneggdrop.utils.math.MathUtils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

public class DragonLootTable {

    private double chestChance;
    private String chestName;
    private DragonLootElementEgg egg;

    private final String id;
    private final List<ILootPool<DragonLootElementCommand>> commandPools;
    private final List<ILootPool<DragonLootElementItem>> chestPools;

    public DragonLootTable(String id, DragonLootElementEgg egg, List<ILootPool<DragonLootElementCommand>> commandPools, List<ILootPool<DragonLootElementItem>> chestPools) {
        this.id = id;
        this.egg = (egg != null) ? egg : new DragonLootElementEgg(0.0);
        this.commandPools = new ArrayList<>(commandPools);
        this.chestPools = new ArrayList<>(chestPools);
    }

    public String getId() {
        return id;
    }

    public double getChestChance() {
        return chestChance;
    }

    public String getChestName() {
        return chestName;
    }

    public DragonLootElementEgg getEgg() {
        return egg;
    }

    public List<ILootPool<DragonLootElementCommand>> getCommandPools() {
        return ImmutableList.copyOf(commandPools);
    }

    public List<ILootPool<DragonLootElementItem>> getChestPools() {
        return ImmutableList.copyOf(chestPools);
    }

    public void generate(DragonBattle battle, EnderDragon dragon) {
        Preconditions.checkArgument(battle != null, "Attempted to generate loot for null dragon battle");
        Preconditions.checkArgument(dragon != null, "Attempted to generate loot for null ender dragon");

        Chest chest = null;
        Player killer = findDragonKiller(dragon);
        Location endPortalLocation = battle.getEndPortalLocation();

        ThreadLocalRandom random = ThreadLocalRandom.current();
        DragonEggDrop plugin = DragonEggDrop.getInstance();

        Block block = endPortalLocation.getBlock();
        block.breakNaturally(); // If there's a block already present, break it

        if (random.nextDouble(100) < chestChance) {
            block.setType(Material.CHEST);

            chest = (Chest) block.getState();
            if (chestName != null && !chestName.isEmpty()) {
                chest.setCustomName(chestName);
            }
        }

        // Generate the egg
        this.egg.generate(battle, dragon, killer, random, chest);

        // Generate the item loot pools
        this.generateLootPools(chestPools, plugin, battle, dragon, killer, random, chest);

        // Execute the command loot pools
        this.generateLootPools(commandPools, plugin, battle, dragon, killer, random, chest);
    }

    public JsonObject asJson() {
        return new JsonObject();
    }

    private Player findDragonKiller(EnderDragon dragon) {
        EntityDamageEvent lastDamageCause = dragon.getLastDamageCause();
        if (!(lastDamageCause instanceof EntityDamageByEntityEvent)) {
            return null;
        }

        Entity damager = ((EntityDamageByEntityEvent) lastDamageCause).getDamager();
        if (damager instanceof Player) {
            return (Player) damager;
        }

        else if (damager instanceof Projectile) {
            ProjectileSource projectileSource = ((Projectile) damager).getShooter();
            if (!(projectileSource instanceof Player)) {
                return null; // Give up
            }

            return (Player) projectileSource;
        }

        return null;
    }

    private <T extends IDragonLootElement> void generateLootPools(List<ILootPool<T>> pools, DragonEggDrop plugin, DragonBattle battle, EnderDragon dragon, Player killer, ThreadLocalRandom random, Chest chest) {
        if (pools == null || pools.isEmpty()) {
            return;
        }

        for (ILootPool<T> lootPool : pools) {
            if (random.nextDouble(100) >= lootPool.getChance()) {
                continue;
            }

            int rolls = random.nextInt(lootPool.getMinRolls(), lootPool.getMaxRolls() + 1);
            for (int i = 0; i < rolls; i++) {
                IDragonLootElement loot = lootPool.roll(random);
                if (loot == null) {
                    plugin.getLogger().warning("Attempted to generate null loot element for loot pool with name \"" + lootPool.getName() + "\" (loot table: \"" + id + "\"). Ignoring...");
                    continue;
                }

                loot.generate(battle, dragon, killer, random, chest);
            }
        }
    }

    public static DragonLootTable fromJsonFile(File file) {
        String fileName = file.getName();
        if (!fileName.endsWith(".json")) {
            throw new IllegalArgumentException("Expected .json file. Got " + fileName.substring(fileName.lastIndexOf('.')) + " instead");
        }

        JsonObject root = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            root = DragonEggDrop.GSON.fromJson(reader, JsonObject.class);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (root == null) {
            throw new JsonParseException("Could not parse loot table");
        }

        String id = fileName.substring(0, fileName.lastIndexOf('.'));
        String chestName = null;
        double chestChance = root.has("chest") ? 100.0 : 0.0;

        DragonLootElementEgg egg = (root.has("egg") && root.get("egg").isJsonObject()) ? DragonEggDrop.GSON.fromJson(root.getAsJsonObject("egg"), DragonLootElementEgg.class) : new DragonLootElementEgg();

        List<ILootPool<DragonLootElementCommand>> commandPools = new ArrayList<>();
        List<ILootPool<DragonLootElementItem>> chestPools = new ArrayList<>();

        if (root.has("command_pools") && root.get("command_pools").isJsonArray()) {
            JsonArray commandPoolsRoot = root.getAsJsonArray("command_pools");
            for (JsonElement element : commandPoolsRoot) {
                if (!element.isJsonObject()) {
                    throw new JsonParseException("Invalid command pool for dragon loot table " + fileName);
                }

                commandPools.add(LootPoolCommand.fromJson(element.getAsJsonObject()));
            }
        }

        if (root.has("chest") && root.get("chest").isJsonObject()) {
            JsonObject chestRoot = root.getAsJsonObject("chest");

            if (chestRoot.has("chance")) {
                chestChance = MathUtils.clamp(chestRoot.get("chance").getAsDouble(), 0.0, 100.0);
            }

            if (chestRoot.has("name")) {
                chestName = chestRoot.get("name").getAsString();
            }

            if (chestRoot.has("pools") && chestRoot.get("pools").isJsonArray()) {
                JsonArray chestPoolsRoot = chestRoot.getAsJsonArray("pools");
                for (JsonElement element : chestPoolsRoot) {
                    if (!element.isJsonObject()) {
                        throw new JsonParseException("Invalid item pool for dragon loot table " + fileName);
                    }

                    chestPools.add(LootPoolItem.fromJson(element.getAsJsonObject()));
                }
            }
        }

        DragonLootTable lootTable = new DragonLootTable(id, egg, commandPools, chestPools);
        lootTable.chestName = chestName;
        lootTable.chestChance = chestChance;
        return lootTable;
    }

}