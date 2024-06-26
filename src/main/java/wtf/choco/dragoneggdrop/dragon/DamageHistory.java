package wtf.choco.dragoneggdrop.dragon;

import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import wtf.choco.dragoneggdrop.utils.JsonUtils;

/**
 * Represents a recordable history of damage for an entity.
 *
 * @author Parker Hawke - Choco
 */
public final class DamageHistory {

    private static final Map<@NotNull UUID, @NotNull DamageHistory> ENTITY_DAMAGE_HISTORY = new HashMap<>();

    private final UUID entityUUID;
    private final Map<@NotNull UUID, @NotNull Double> totalDamage = new HashMap<>();
    private final Deque<@NotNull DamageEntry> damageHistory = new ArrayDeque<>();

    private DamageHistory(@NotNull UUID entityUUID) {
        this.entityUUID = entityUUID;
    }

    /**
     * Get the UUID of the entity to which this damage history belongs.
     *
     * @return the belonging entity's UUID
     *
     * @see #getEntity()
     */
    @NotNull
    public UUID getEntityUUID() {
        return entityUUID;
    }

    /**
     * Get the Entity instance to which this damage history belongs. Note that internally
     * this makes a call to {@link Bukkit#getEntity(UUID)} on {@link #getEntityUUID()}.
     * Therefore this may return null! One should take caution in using the result of this
     * method without first checking for nullability.
     *
     * @return the belonging entity
     *
     * @see #getEntityUUID()
     */
    @Nullable
    public Entity getEntity() {
        return Bukkit.getEntity(entityUUID);
    }

    /**
     * Get the total amount of damage caused by the specified entity.
     *
     * @param entity the entity whose damage to retrieve
     *
     * @return the amount of damage caused by the entity
     */
    public double getTotalDamageFrom(@NotNull Entity entity) {
        return (entity != null) ? getTotalDamageFrom(entity.getUniqueId()) : 0.0;
    }

    /**
     * Get the total amount of damage caused by the specified entity's UUID.
     *
     * @param uuid the UUID of the entity whose damage to retrieve
     *
     * @return the amount of damage caused by the entity
     */
    public double getTotalDamageFrom(@NotNull UUID uuid) {
        return (uuid != null && totalDamage.containsKey(uuid)) ? totalDamage.get(uuid) : 0.0;
    }

    /**
     * Get the entity that caused the most amount of damage to this entity. This comes
     * paired with the amount of total damage done.
     *
     * @return the top damager
     */
    @Nullable
    public DamageEntry getTopDamager() {
        if (totalDamage.isEmpty()) {
            return null;
        }

        return totalDamage.entrySet().stream().sorted(Map.Entry.comparingByValue((v1, v2) -> -v1.compareTo(v2))).findFirst().map(DamageEntry::new).get();
    }

    /**
     * Get the top damager at the given offset (from most amount of damage + offset). For
     * example, if fetching at the offset of 1, the second top damager will be retrieved.
     * If offset of 2, third most. So on and so forth. Offset 0 would be equivalent to
     * {@link #getTopDamager()}.
     *
     * @param offset the damage entry offset. Must be {@literal <} {@link #uniqueDamagers()}
     *
     * @return the top damager at the given offset
     */
    @Nullable
    public DamageEntry getTopDamager(int offset) {
        if (totalDamage.isEmpty()) {
            return null;
        }

        if (offset >= totalDamage.size()) {
            throw new IllegalArgumentException("Tried to get top damager at unavailable offset (damagers recorded = " + totalDamage.size() + ")");
        }

        List<Entry<UUID, Double>> entries = new ArrayList<>(totalDamage.entrySet());
        entries.sort(Map.Entry.comparingByValue((v1, v2) -> -v1.compareTo(v2)));
        return new DamageEntry(entries.get(offset));
    }

    /**
     * Get an array of entities that caused the most amount of damage to this entity in
     * order of most amount of damage (first index) to least amount of damage (last
     * index). This comes paired with the amount of total damage done by each entity.
     * <p>
     * If more damagers are queried than is available, the resulting array will fill as
     * much as it can after which point null will be used to represent unrecorded or
     * unavailable data.
     *
     * @param amount the amount of damagers to retrieve. Must be greater than 0
     *
     * @return the top x damagers
     */
    @Nullable
    public DamageEntry @NotNull [] getTopDamagers(int amount) {
        Preconditions.checkArgument(amount > 0, "Invalid history amount. Must be > 0");
        DamageEntry[] topDamagers = new DamageEntry[amount];

        if (totalDamage.isEmpty()) {
            return topDamagers;
        }

        List<Entry<UUID, Double>> entries = new ArrayList<>(totalDamage.entrySet());
        entries.sort(Map.Entry.comparingByValue((v1, v2) -> -v1.compareTo(v2)));

        for (int i = 0; i < amount; i++) {
            if (i >= entries.size()) {
                break;
            }

            topDamagers[i] = new DamageEntry(entries.get(i));
        }

        return topDamagers;
    }

    /**
     * Record damage to this history.
     *
     * @param source the UUID of the damage's source entity
     * @param damage the damage to record
     */
    public void recordDamage(@NotNull UUID source, double damage) {
        Preconditions.checkArgument(source != null, "source must not be null");
        Preconditions.checkArgument(damage > 0.0, "damage must be greater than 0");

        this.totalDamage.merge(source, damage, Double::sum);
        this.damageHistory.push(new DamageEntry(source, damage));
    }

    /**
     * Record damage to this history.
     *
     * @param source the damage's source entity
     * @param damage the damage to record
     */
    public void recordDamage(@NotNull Entity source, double damage) {
        this.recordDamage(source.getUniqueId(), damage);
    }

    /**
     * Get the most recent damage entry to this history.
     *
     * @return the most recent damage. null if no damage has been recorded
     */
    @Nullable
    public DamageEntry getMostRecentDamage() {
        return damageHistory.peek();
    }

    /**
     * Get the damage entry at the given offset (from most recent + offset). For example,
     * if fetching at the offset of 1, the second most recent damage will be retrieved. If
     * offset of 2, third most. So on and so forth. Offset 0 would be equivalent to
     * {@link #getMostRecentDamage()}.
     *
     * @param offset the damage entry offset. Must be {@literal <} {@link #size()}
     *
     * @return the damage entry at the given offset
     */
    @Nullable
    public DamageEntry getMostRecentDamage(int offset) {
        if (offset >= damageHistory.size()) {
            throw new IllegalArgumentException("Tried to get recent damage at unavailable offset (oldest damage recorded = " + damageHistory.size() + ")");
        }

        if (offset == 0) { // Save unnecessary Iterator creation and just peek at the top value
            return getMostRecentDamage();
        }

        int i = 0;
        for (Iterator<@NotNull DamageEntry> iterator = damageHistory.iterator(); iterator.hasNext(); i++) {
            DamageEntry entry = iterator.next();
            if (i == offset) {
                return entry;
            }
        }

        return null;
    }

    /**
     * Get the amount of history recorded by this record.
     *
     * @return the history size
     */
    public int size() {
        return damageHistory.size();
    }

    /**
     * Get the amount of unique damagers recorded by this history.
     *
     * @return the amount of unique damagers
     */
    public int uniqueDamagers() {
        return totalDamage.size();
    }

    /**
     * Reset all total damage from all entities in this history to zero.
     */
    public void clearTotalDamage() {
        this.totalDamage.clear();
    }

    /**
     * Clear all damage history from this history.
     */
    public void clearDamageHistory() {
        this.damageHistory.clear();
    }

    /**
     * Clear all total damage and damage history from this history. This is equivalent to
     * calling both {@link #clearTotalDamage()} and {@link #clearDamageHistory()}.
     */
    public void clear() {
        this.clearTotalDamage();
        this.clearDamageHistory();
    }

    /**
     * Serialize this damage history to a {@link JsonObject}.
     *
     * @return the serialized history
     */
    @NotNull
    public JsonObject toJson() {
        JsonObject object = new JsonObject();

        object.addProperty("entityUUID", entityUUID.toString());

        if (!damageHistory.isEmpty()) {
            JsonArray damageEntriesArray = new JsonArray();
            this.damageHistory.forEach(entry -> {
                JsonObject entryObject = new JsonObject();

                entryObject.addProperty("source", entry.source.toString());
                entryObject.addProperty("damage", entry.getDamage());

                damageEntriesArray.add(entryObject);
            });

            object.add("damageEntries", damageEntriesArray);
        }

        return object;
    }

    /**
     * Deserialize a {@link DamageHistory} instance from the given {@link JsonObject}.
     *
     * @param object the object from which to deserialize
     *
     * @return the damage history instance
     */
    @NotNull
    public static DamageHistory fromJson(@NotNull JsonObject object) {
        UUID entityUUID = JsonUtils.getRequiredField(object, "entityUUID", element -> UUID.fromString(element.getAsString()));
        JsonArray damageEntriesArray = JsonUtils.getOptionalField(object, "damageEntries", JsonElement::getAsJsonArray, new JsonArray());

        DamageHistory damageHistory = new DamageHistory(entityUUID);

        damageEntriesArray.forEach(damageEntryElement -> {
            if (!damageEntryElement.isJsonObject()) {
                return;
            }

            JsonObject damageEntryObject = damageEntryElement.getAsJsonObject();

            UUID source = UUID.fromString(damageEntryObject.get("source").getAsString());
            double damage = damageEntryObject.get("damage").getAsDouble();

            damageHistory.recordDamage(source, damage);
        });

        return damageHistory;
    }

    /**
     * Get the {@link DamageHistory} associated with the given entity UUID. This method will
     * never return null.
     *
     * @param uuid the entity whose history to get
     *
     * @return the entity's damage history
     */
    @NotNull
    public static DamageHistory forEntity(@NotNull UUID uuid) {
        Preconditions.checkArgument(uuid != null, "Cannot get damage history for null UUID");
        return ENTITY_DAMAGE_HISTORY.computeIfAbsent(uuid, DamageHistory::new);
    }

    /**
     * Get the {@link DamageHistory} associated with the given entity. This method will
     * never return null.
     *
     * @param entity the entity whose history to get
     *
     * @return the entity's damage history
     */
    @NotNull
    public static DamageHistory forEntity(@NotNull Entity entity) {
        Preconditions.checkArgument(entity != null, "Cannot get damage history for null entity");
        return forEntity(entity.getUniqueId());
    }

    /**
     * Construct and return a new dummy {@link DamageHistory} object. This history is not
     * registered and cannot be fetched at a later point, the UUID returned by {@link #getEntityUUID()}
     * is nonsense generated by {@link UUID#randomUUID()}.
     *
     * @return the dummy history
     */
    @NotNull
    public static DamageHistory dummy() {
        return new DamageHistory(UUID.randomUUID());
    }

    /**
     * Clear all damage history from all entities.
     */
    public static void clearGlobalDamageHistory() {
        ENTITY_DAMAGE_HISTORY.clear();
    }


    /**
     * Represents a mapping of an entity's UUID to an amount of damage.
     *
     * @author Parker Hawke - Choco
     */
    public final class DamageEntry {

        private final UUID source;
        private final double damage;

        private DamageEntry(@NotNull UUID source, double damage) {
            this.source = source;
            this.damage = damage;
        }

        private DamageEntry(@NotNull Map.Entry<@NotNull UUID, @NotNull Double> entry) {
            this(entry.getKey(), entry.getValue());
        }

        /**
         * Get the UUID of the entity to which this damage entry is attributed.
         *
         * @return the source entity's UUID
         *
         * @see #getSourceEntity()
         */
        @NotNull
        public UUID getSource() {
            return source;
        }

        /**
         * Get the Entity instance to which this damage entry is attributed. Note that
         * internally this makes a call to {@link Bukkit#getEntity(UUID)} on
         * {@link #getEntityUUID()}. Therefore this may return null! One should take
         * caution in using the result of this method without first checking for
         * nullability.
         *
         * @return the source entity
         *
         * @see #getSource()
         */
        @Nullable
        public Entity getSourceEntity() {
            return Bukkit.getEntity(source);
        }

        /**
         * Get the amount of damage caused by the entity.
         *
         * @return the damage
         */
        public double getDamage() {
            return damage;
        }

    }

}
