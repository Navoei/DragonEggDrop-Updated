package com.ninjaguild.dragoneggdrop.tasks;

import com.google.common.base.Enums;
import com.ninjaguild.dragoneggdrop.DragonEggDrop;
import com.ninjaguild.dragoneggdrop.api.BattleState;
import com.ninjaguild.dragoneggdrop.api.BattleStateChangeEvent;
import com.ninjaguild.dragoneggdrop.dragon.DragonTemplate;
import com.ninjaguild.dragoneggdrop.dragon.loot.DragonLootTable;
import com.ninjaguild.dragoneggdrop.utils.math.ParticleShapeDefinition;
import com.ninjaguild.dragoneggdrop.world.EndWorldWrapper;
import com.ninjaguild.dragoneggdrop.world.RespawnReason;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Represents a BukkitRunnable that handles the generation and particle display of the
 * loot after the Ender Dragon's death.
 */
public class DragonDeathRunnable extends BukkitRunnable {

    private final DragonEggDrop plugin;

    private ParticleShapeDefinition particleShape;

    private final World world;
    private final EndWorldWrapper worldWrapper;

    private Particle particleType = null;
    private int particleAmount = 0;
    private double particleExtra = 0D;
    private double particleMultiplier = 1D;
    private int particleStreamInterval = 360;
    private double xOffset, yOffset, zOffset;
    private long particleInterval = 0L;
    private int lightningAmount;

    private EnderDragon dragon;
    private boolean respawnDragon = false;

    private Location location;
    private double animationTime = 0;
    private double theta = 0;
    private double currentY;

    /**
     * Construct a new DragonDeathRunnable object.
     *
     * @param plugin an instance of the DragonEggDrop plugin
     * @param worldWrapper the world in which the dragon death is taking place
     * @param dragon the dragon dying in this runnable
     */
    public DragonDeathRunnable(DragonEggDrop plugin, EndWorldWrapper worldWrapper, EnderDragon dragon) {
        this.plugin = plugin;
        this.worldWrapper = worldWrapper;
        this.world = worldWrapper.getWorld();
        this.dragon = dragon;

        FileConfiguration config = plugin.getConfig();
        this.particleType = Enums.getIfPresent(Particle.class, config.getString("Particles.type", "FLAME").toUpperCase()).or(Particle.FLAME);
        this.particleAmount = config.getInt("Particles.amount", 4);
        this.particleExtra = config.getDouble("Particles.extra", 0.0D);
        this.particleMultiplier = config.getDouble("Particles.speed-multiplier", 0.0D);
        this.particleStreamInterval = 360 / Math.max(1, config.getInt("Particles.stream-count"));
        this.xOffset = config.getDouble("Particles.xOffset");
        this.yOffset = config.getDouble("Particles.yOffset");
        this.zOffset = config.getDouble("Particles.zOffset");
        this.particleInterval = config.getLong("Particles.interval", 1L);
        this.lightningAmount = config.getInt("lightning-amount");

        // Portal location
        DragonBattle dragonBattle = dragon.getDragonBattle();
        Location portalLocation = dragonBattle.getEndPortalLocation().add(0, 4, 0);
        this.currentY = config.getDouble("Particles.egg-start-y");
        this.location = new Location(world, portalLocation.getX(), currentY, portalLocation.getZ());

        // Expression parsing
        String shape = config.getString("Particles.Advanced.preset-shape");
        String xCoordExpressionString = config.getString("Particles.Advanced.x-coord-expression");
        String zCoordExpressionString = config.getString("Particles.Advanced.z-coord-expression");

        this.particleShape = Enums.getIfPresent(ParticleShapeDefinition.Prefab.class, shape)
                .transform(p -> p.create(location))
                .or(new ParticleShapeDefinition(location, xCoordExpressionString, zCoordExpressionString));

        this.respawnDragon = config.getBoolean("respawn-on-death", false);
        this.runTaskTimer(plugin, 0, particleInterval);

        BattleStateChangeEvent bscEventCrystals = new BattleStateChangeEvent(dragonBattle, dragon, BattleState.BATTLE_END, BattleState.PARTICLES_START);
        Bukkit.getPluginManager().callEvent(bscEventCrystals);
    }

    @Override
    public void run() {
        this.animationTime++;
        this.theta += 5;

        this.location.subtract(0, 1 / particleMultiplier, 0);
        if (particleStreamInterval < 360) {
            for (int i = 0; i < 360; i += particleStreamInterval) {
                this.theta += particleStreamInterval;
                this.particleShape.updateVariables(location.getX(), location.getZ(), animationTime, theta);
                this.particleShape.executeExpression(particleType, particleAmount, xOffset, yOffset, zOffset, particleExtra);
            }
        }
        else {
            this.particleShape.updateVariables(location.getX(), location.getZ(), animationTime, theta);
            this.particleShape.executeExpression(particleType, particleAmount, xOffset, yOffset, zOffset, particleExtra);
        }

        // Particles finished, place reward
        if (location.getBlock().getType() == Material.BEDROCK) {
            this.location.add(0, 1, 0);

            // Summon Zeus!
            for (int i = 0; i < lightningAmount; i++) {
                this.worldWrapper.getWorld().strikeLightning(location);
            }

            DragonBattle dragonBattle = dragon.getDragonBattle();
            DragonTemplate activeTemplate = worldWrapper.getActiveTemplate();

            if (activeTemplate != null) {
                DragonLootTable lootTable = worldWrapper.hasLootTableOverride() ? worldWrapper.getLootTableOverride() : activeTemplate.getLootTable();
                if (lootTable != null) {
                    lootTable.generate(dragonBattle, dragon);
                }
                else {
                    this.plugin.getLogger().warning("Could not generate loot for template " + activeTemplate.getId() + ". Invalid loot table. Is \"loot\" defined in the template?");
                }

                this.worldWrapper.setLootTableOverride(null); // Reset the loot table override. Use the template's loot table next instead
            }

            this.worldWrapper.setActiveTemplate(null);

            if (respawnDragon && world.getPlayers().size() > 0 && plugin.getConfig().getBoolean("respawn-on-death", true)) {
                this.worldWrapper.startRespawn(RespawnReason.DEATH);
            }

            BattleStateChangeEvent bscEventCrystals = new BattleStateChangeEvent(dragonBattle, dragon, BattleState.PARTICLES_START, BattleState.LOOT_SPAWN);
            Bukkit.getPluginManager().callEvent(bscEventCrystals);
            this.cancel();
        }
    }

}