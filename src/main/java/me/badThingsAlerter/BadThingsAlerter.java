package me.badThingsAlerter;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

import javax.net.ssl.HttpsURLConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BadThingsAlerter - single-file plugin (webhook-based alerts, no JDA)
 *
 * Config (plugins/BadThingsAlerter/config.yml):
 *   role_mention: ""          # optional text to prepend to webhook message
 *   message_channel: ""       # unused for webhook but kept for compatibility
 *   webhook_url: ""           # your Discord webhook URL (preferred)
 *
 * Command:
 *   /what_is_detected [page]  # paginated list of detections (10 per page)
 *
 * Many thresholds are constants here. If you want these configurable, say so and I'll move them to config.yml.
 */
public final class BadThingsAlerter extends JavaPlugin implements Listener {

    // ---------- Discord webhook ----------
    private String roleMention = "";
    private String webhookUrl = "";

    // ---------- Detection storage ----------
    private final List<Long> tntExplosions = Collections.synchronizedList(new ArrayList<>());

    private final Map<UUID, List<Long>> playerKills = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> blockBreaks = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> bucketPlaces = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> redstonePlacements = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> dropTimes = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> tpCommandsIssued = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> commandTimes = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> firePlaces = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> tntPlaces = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> containerBreaks = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> trapPlacements = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> noteBellSpam = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> boatMinecartPlace = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> scaffoldingPlace = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> itemFramePlace = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> beaconBreaks = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> glassPaneBreaks = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> farmWaterRemovals = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> farmlandReplace = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> clusterBoatEntities = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> minecartCounts = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> villagerKills = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> petKills = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> spawnEggUse = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> endCrystalPlaces = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> witherSkullPlaces = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> privateMsgSpam = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> meSpam = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> resourceCmdSpam = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> filledShulkerDrops = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> longNameRenames = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> nbtHopperPrep = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> endCrystalUse = new ConcurrentHashMap<>();

    // ---------- thresholds ----------


    private static final int TNT_COUNT_THRESHOLD = 10;         // TNT explosions in the interval
    private static final long TNT_WINDOW_MS = 60_000L;        // 60 seconds

    private static final int MASS_KILL_THRESHOLD = 20;        // kills within window
    private static final long MASS_KILL_WINDOW_MS = 5_000L;   // 5 seconds

    private static final int MASS_BREAK_THRESHOLD = 80;       // blocks broken within window
    private static final long MASS_BREAK_WINDOW_MS = 30_000L; // 30 seconds

    private static final int BUCKET_PLACE_THRESHOLD = 10;     // lava/water bucket placements in window
    private static final long BUCKET_WINDOW_MS = 30_000L;     // 30 seconds

    private static final int REDSTONE_PLACE_THRESHOLD = 40;   // hoppers/observers/pistons placed in window
    private static final long REDSTONE_WINDOW_MS = 60_000L;   // 60 seconds

    private static final int ITEM_DROP_THRESHOLD = 200;       // items dropped in single window
    private static final long ITEM_DROP_WINDOW_MS = 5_000L;   // 5 seconds

    private static final int TELEPORT_CMD_THRESHOLD = 6;      // tp commands issued targeting players within window
    private static final long TELEPORT_CMD_WINDOW_MS = 60_000L;

    private static final int COMMAND_SPAM_THRESHOLD = 25;     // commands in window
    private static final long COMMAND_SPAM_WINDOW_MS = 5_000L;

    private static final int FIRE_PLACE_THRESHOLD = 20;
    private static final long FIRE_PLACE_WINDOW_MS = 20_000L;

    private static final int TNT_PLACE_THRESHOLD = 50;
    private static final long TNT_PLACE_WINDOW_MS = 60_000L;

    private static final int CONTAINER_BREAK_THRESHOLD = 30;
    private static final long CONTAINER_BREAK_WINDOW_MS = 10_000L;

    private static final int TRAP_PLACE_THRESHOLD = 12;
    private static final long TRAP_PLACE_WINDOW_MS = 20_000L;

    private static final int NOTE_BELL_SPAM_THRESHOLD = 500;
    private static final long NOTE_BELL_WINDOW_MS = 6_000L;

    private static final int BOAT_MINECART_PLACE_THRESHOLD = 30;
    private static final long BOAT_MINECART_WINDOW_MS = 10_000L;

    private static final int SCAFFOLD_PLACE_THRESHOLD = 100;
    private static final long SCAFFOLD_WINDOW_MS = 30_000L;

    private static final int ITEM_FRAME_PLACE_THRESHOLD = 40;
    private static final long ITEM_FRAME_WINDOW_MS = 20_000L;

    private static final int BEACON_BREAK_THRESHOLD = 3;
    private static final long BEACON_BREAK_WINDOW_MS = 60_000L;

    private static final int GLASS_BREAK_THRESHOLD = 30;
    private static final long GLASS_BREAK_WINDOW_MS = 20_000L;

    private static final int WATER_REMOVAL_THRESHOLD = 8;
    private static final long WATER_REMOVAL_WINDOW_MS = 20_000L;

    private static final int FARMLAND_REPLACE_THRESHOLD = 8;
    private static final long FARMLAND_REPLACE_WINDOW_MS = 20_000L;

    private static final int MINECART_CLUSTER_THRESHOLD = 6;
    private static final long MINECART_CLUSTER_WINDOW_MS = 10_000L;

    private static final int VILLAGER_KILL_THRESHOLD = 8;
    private static final long VILLAGER_KILL_WINDOW_MS = 20_000L;

    private static final int PET_KILL_THRESHOLD = 6;
    private static final long PET_KILL_WINDOW_MS = 15_000L;

    private static final int SPAWN_EGG_THRESHOLD = 30;
    private static final long SPAWN_EGG_WINDOW_MS = 20_000L;

    private static final int END_CRYSTAL_THRESHOLD = 15;
    private static final long END_CRYSTAL_WINDOW_MS = 10_000L;

    private static final int WITHER_SKULL_THRESHOLD = 12;
    private static final long WITHER_SKULL_WINDOW_MS = 20_000L;

    private static final int PRIVATE_MSG_SPAM_THRESHOLD = 10;
    private static final long PRIVATE_MSG_WINDOW_MS = 8_000L;

    private static final int ME_SPAM_THRESHOLD = 6;
    private static final long ME_SPAM_WINDOW_MS = 6_000L;

    private static final int RESOURCE_CMD_THRESHOLD = 6;
    private static final long RESOURCE_CMD_WINDOW_MS = 10_000L;

    private static final int FILLED_SHULKER_DROP_THRESHOLD = 16;
    private static final long FILLED_SHULKER_DROP_WINDOW_MS = 6_000L;

    private static final int LONG_NAME_THRESHOLD = 200; // chars
    private static final int LONG_NAME_TRIGGER_COUNT = 2;
    private static final long LONG_NAME_WINDOW_MS = 30_000L;

    private static final int NBT_HOPPER_PREP_THRESHOLD = 6;
    private static final long NBT_HOPPER_PREP_WINDOW_MS = 30_000L;

    private static final int END_CRYSTAL_USE_THRESHOLD = 20;
    private static final long END_CRYSTAL_USE_WINDOW_MS = 20_000L;

    // ---------- list of detection descriptions for /what_is_detected ----------
    private final List<String> detectionList = new ArrayList<>();

    private static class BlockBreakEntry {
        final Material type;
        final long timestamp;
        BlockBreakEntry(Material type, long timestamp) {
            this.type = type;
            this.timestamp = timestamp;
        }
    }

    private final Map<UUID, List<BlockBreakEntry>> blockBreakTracker = new HashMap<>();

    private String fmt(int threshold, long windowMs, String description) {
        double seconds = windowMs / 1000.0;

        String secStr = String.format("%.2f", seconds);

        secStr = secStr.replaceAll("\\.?0+$", "");

        return description + " (threshold=" + threshold + ", window=" + secStr + "s)";
    }


    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfigValues();

        // Build detection list (order matters for pagination)
        detectionList.clear();
        detectionList.addAll(Arrays.asList(
                fmt(TNT_COUNT_THRESHOLD, TNT_WINDOW_MS, "Excessive TNT explosion spam"),
                fmt(MASS_KILL_THRESHOLD, MASS_KILL_WINDOW_MS, "Mass kills in short time"),
                fmt(MASS_BREAK_THRESHOLD, MASS_BREAK_WINDOW_MS, "Massive block breaking"),
                fmt(BUCKET_PLACE_THRESHOLD, BUCKET_WINDOW_MS, "Rapid lava/water bucket placements"),
                fmt(FIRE_PLACE_THRESHOLD, FIRE_PLACE_WINDOW_MS, "Rapid fire placement"),
                fmt(TNT_PLACE_THRESHOLD, TNT_PLACE_WINDOW_MS, "Placing many TNT blocks"),
                fmt(CONTAINER_BREAK_THRESHOLD, CONTAINER_BREAK_WINDOW_MS, "Breaking many container blocks"),
                fmt(TRAP_PLACE_THRESHOLD, TRAP_PLACE_WINDOW_MS, "Placing trapping blocks (obsidian/cobwebs)"),
                fmt(NOTE_BELL_SPAM_THRESHOLD, NOTE_BELL_WINDOW_MS, "Door / trapdoor / note block / bell spam"),
                fmt(BOAT_MINECART_PLACE_THRESHOLD, BOAT_MINECART_WINDOW_MS, "Boat/minecart placement spam"),
                fmt(SCAFFOLD_PLACE_THRESHOLD, SCAFFOLD_WINDOW_MS, "Excessive scaffolding placement"),
                fmt(ITEM_FRAME_PLACE_THRESHOLD, ITEM_FRAME_WINDOW_MS, "Item frame spam"),
                fmt(BEACON_BREAK_THRESHOLD, BEACON_BREAK_WINDOW_MS, "Breaking beacon bases"),
                fmt(GLASS_BREAK_THRESHOLD, GLASS_BREAK_WINDOW_MS, "Breaking many glass panes"),
                fmt(WATER_REMOVAL_THRESHOLD, WATER_REMOVAL_WINDOW_MS, "Removing farm/village water sources"),
                fmt(FARMLAND_REPLACE_THRESHOLD, FARMLAND_REPLACE_WINDOW_MS, "Replacing farmland repeatedly"),
                fmt(MINECART_CLUSTER_THRESHOLD, MINECART_CLUSTER_WINDOW_MS, "Minecart clustering lag machine"),
                fmt(VILLAGER_KILL_THRESHOLD, VILLAGER_KILL_WINDOW_MS, "Villager mass killing"),
                fmt(PET_KILL_THRESHOLD, PET_KILL_WINDOW_MS, "Pet killing (not owner)"),
                fmt(SPAWN_EGG_THRESHOLD, SPAWN_EGG_WINDOW_MS, "Spawn egg spam"),
                fmt(END_CRYSTAL_THRESHOLD, END_CRYSTAL_WINDOW_MS, "End crystal spam"),
                fmt(WITHER_SKULL_THRESHOLD, WITHER_SKULL_WINDOW_MS, "Wither skull / wither attempts spam"),
                fmt(PRIVATE_MSG_SPAM_THRESHOLD, PRIVATE_MSG_WINDOW_MS, "/msg spam"),
                fmt(ME_SPAM_THRESHOLD, ME_SPAM_WINDOW_MS, "/me spam"),
                fmt(RESOURCE_CMD_THRESHOLD, RESOURCE_CMD_WINDOW_MS, "Resource pack command spam"),
                fmt(FILLED_SHULKER_DROP_THRESHOLD, FILLED_SHULKER_DROP_WINDOW_MS, "Dropping many filled shulkers"),
                "Using extremely long item names (>" + LONG_NAME_THRESHOLD + " chars, " +
                        LONG_NAME_TRIGGER_COUNT + " times in " + LONG_NAME_WINDOW_MS + "ms)",
                fmt(NBT_HOPPER_PREP_THRESHOLD, NBT_HOPPER_PREP_WINDOW_MS, "NBT-heavy hopper item dumping"),
                fmt(END_CRYSTAL_USE_THRESHOLD, END_CRYSTAL_USE_WINDOW_MS, "Rapid end crystal usage"),
                fmt(ITEM_DROP_THRESHOLD, ITEM_DROP_WINDOW_MS, "Mass dropping items to lag server"),
                fmt(COMMAND_SPAM_THRESHOLD, COMMAND_SPAM_WINDOW_MS, "General command spam"),
                fmt(TELEPORT_CMD_THRESHOLD, TELEPORT_CMD_WINDOW_MS, "Teleport command spam / TP traps"),
                fmt(REDSTONE_PLACE_THRESHOLD, REDSTONE_WINDOW_MS, "Redstone lag machine (hopper/observer/piston spam)")
        ));


        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Periodic cleanup task to avoid unbounded memory growth
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                tntExplosions.removeIf(t -> now - t > TNT_WINDOW_MS * 2);

                cleanupMap(playerKills, now, MASS_KILL_WINDOW_MS);
                cleanupMap(blockBreaks, now, MASS_BREAK_WINDOW_MS);
                cleanupMap(bucketPlaces, now, BUCKET_WINDOW_MS);
                cleanupMap(redstonePlacements, now, REDSTONE_WINDOW_MS);
                cleanupMap(dropTimes, now, ITEM_DROP_WINDOW_MS);
                cleanupMap(tpCommandsIssued, now, TELEPORT_CMD_WINDOW_MS);
                cleanupMap(commandTimes, now, COMMAND_SPAM_WINDOW_MS);
                cleanupMap(firePlaces, now, FIRE_PLACE_WINDOW_MS);
                cleanupMap(tntPlaces, now, TNT_PLACE_WINDOW_MS);
                cleanupMap(containerBreaks, now, CONTAINER_BREAK_WINDOW_MS);
                cleanupMap(trapPlacements, now, TRAP_PLACE_WINDOW_MS);
                cleanupMap(noteBellSpam, now, NOTE_BELL_WINDOW_MS);
                cleanupMap(boatMinecartPlace, now, BOAT_MINECART_WINDOW_MS);
                cleanupMap(scaffoldingPlace, now, SCAFFOLD_WINDOW_MS);
                cleanupMap(itemFramePlace, now, ITEM_FRAME_WINDOW_MS);
                cleanupMap(beaconBreaks, now, BEACON_BREAK_WINDOW_MS);
                cleanupMap(glassPaneBreaks, now, GLASS_BREAK_WINDOW_MS);
                cleanupMap(farmWaterRemovals, now, WATER_REMOVAL_WINDOW_MS);
                cleanupMap(farmlandReplace, now, FARMLAND_REPLACE_WINDOW_MS);
                cleanupMap(clusterBoatEntities, now, MINECART_CLUSTER_WINDOW_MS);
                cleanupMap(minecartCounts, now, MINECART_CLUSTER_WINDOW_MS);
                cleanupMap(villagerKills, now, VILLAGER_KILL_WINDOW_MS);
                cleanupMap(petKills, now, PET_KILL_WINDOW_MS);
                cleanupMap(spawnEggUse, now, SPAWN_EGG_WINDOW_MS);
                cleanupMap(endCrystalPlaces, now, END_CRYSTAL_WINDOW_MS);
                cleanupMap(witherSkullPlaces, now, WITHER_SKULL_WINDOW_MS);
                cleanupMap(privateMsgSpam, now, PRIVATE_MSG_WINDOW_MS);
                cleanupMap(meSpam, now, ME_SPAM_WINDOW_MS);
                cleanupMap(resourceCmdSpam, now, RESOURCE_CMD_WINDOW_MS);
                cleanupMap(filledShulkerDrops, now, FILLED_SHULKER_DROP_WINDOW_MS);
                cleanupMap(longNameRenames, now, LONG_NAME_WINDOW_MS);
                cleanupMap(nbtHopperPrep, now, NBT_HOPPER_PREP_WINDOW_MS);
                cleanupMap(endCrystalUse, now, END_CRYSTAL_USE_WINDOW_MS);
            }
        }.runTaskTimerAsynchronously(this, 20L * 15L, 20L * 15L); // every 15s

        getLogger().info("BadThingsAlerter Enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("BadThingsAlerter Disabled!");
    }

    private void reloadConfigValues() {
        FileConfiguration cfg = getConfig();
        roleMention = cfg.getString("role_mention", "").trim();
        webhookUrl = cfg.getString("webhook_url", "").trim();
        // keep message_channel for compatibility but we use webhook
    }

    // ---------- small reusable helpers ----------
    private void cleanupMap(Map<UUID, List<Long>> map, long now, long window) {
        map.values().forEach(list -> list.removeIf(t -> now - t > window));
    }

    private void recordEvent(Map<UUID, List<Long>> map, UUID id, long now) {
        map.putIfAbsent(id, Collections.synchronizedList(new ArrayList<>()));
        map.get(id).add(now);
    }
    // Store last sent message info
    private String lastWebhookMessageId = null;
    private String lastWebhookContent = null;
    private long lastWebhookTimestamp = 0;

    // Similarity threshold
    private static final int MAX_SIMILARITY_CHANGES = 20;
    private static final long TWO_MINUTES = 120_000;

    private void alert(String message) {

        // Prepend role mention if configured
        String out = (roleMention == null || roleMention.isEmpty())
                ? message
                : (roleMention + " " + message);

        // If no webhook defined → just console log
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            getLogger().info("[ALERT] " + out);
            return;
        }

        long now = System.currentTimeMillis();

        // Determine if the message is "similar"
        boolean similar = false;
        if (lastWebhookContent != null && now - lastWebhookTimestamp <= TWO_MINUTES) {
            int distance = levenshtein(lastWebhookContent, out);
            similar = distance <= MAX_SIMILARITY_CHANGES;
        }

        if (similar && lastWebhookMessageId != null) {
            // EDIT previous Discord webhook message
            editWebhookMessage(webhookUrl, lastWebhookMessageId, out);
            lastWebhookContent = out;
            lastWebhookTimestamp = now;
        } else {
            // SEND new Discord webhook message
            String msgId = sendWebhook(webhookUrl, out);
            if (msgId != null) {
                lastWebhookMessageId = msgId;
                lastWebhookContent = out;
                lastWebhookTimestamp = now;
            }
        }
    }

    private String sendWebhook(String webhook, String content) {
        try {
            URL url = new URL(webhook);
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("User-Agent", "Mozilla/5.0");

            String json = "{\"content\":\"" + escapeJson(content) + "\"}";
            byte[] out = json.getBytes(StandardCharsets.UTF_8);

            con.setFixedLengthStreamingMode(out.length);
            con.connect();

            try (OutputStream os = con.getOutputStream()) {
                os.write(out);
            }

            // Read response from Discord
            try (InputStream is = con.getInputStream()) {
                String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                // Extract message ID from JSON
                int idIndex = response.indexOf("\"id\":");
                if (idIndex != -1) {
                    int start = response.indexOf("\"", idIndex + 5) + 1;
                    int end = response.indexOf("\"", start);
                    return response.substring(start, end);
                }
            }

        } catch (Exception ex) {
            getLogger().warning("Failed to send webhook: " + ex.getMessage());
        }

        return null;
    }

    private void editWebhookMessage(String webhookBase, String msgId, String newContent) {
        try {
            // Discord format: https://discord.com/api/webhooks/{id}/{token}/messages/{message.id}
            String editUrl = webhookBase + "/messages/" + msgId;

            URL url = new URL(editUrl);
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setRequestMethod("PATCH");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("User-Agent", "Mozilla/5.0");

            String json = "{\"content\":\"" + escapeJson(newContent) + "\"}";
            byte[] out = json.getBytes(StandardCharsets.UTF_8);

            con.setFixedLengthStreamingMode(out.length);
            con.connect();

            try (OutputStream os = con.getOutputStream()) {
                os.write(out);
            }

            int code = con.getResponseCode();
            if (code < 200 || code >= 300) {
                getLogger().warning("Failed to edit webhook message (HTTP " + code + ")");
            }

        } catch (Exception ex) {
            getLogger().warning("Failed to edit webhook message: " + ex.getMessage());
        }
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[a.length()][b.length()];
    }





    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    // ---------- EVENT HANDLERS (existing + added detectors) ----------

    // TNT explosion counting (already present)
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntityType() == EntityType.TNT) {

            // Track TNT explosion timestamps
            long now = System.currentTimeMillis();
            tntExplosions.add(now);
            tntExplosions.removeIf(t -> now - t > TNT_WINDOW_MS);

            // Find closest player to the explosion
            Player closest = null;
            double closestDist = Double.MAX_VALUE;

            for (Player p : Bukkit.getOnlinePlayers()) {
                double d = p.getLocation().distance(event.getLocation());
                if (d < closestDist) {
                    closestDist = d;
                    closest = p;
                }
            }

            String closestName = (closest == null) ? "None" : closest.getName();

            // Trigger alert when threshold reached
            if (tntExplosions.size() >= TNT_COUNT_THRESHOLD) {
                alert(
                        "⚠️ High TNT activity detected: " +
                                tntExplosions.size() + " TNT explosions in last " + (TNT_WINDOW_MS/1000) + "s. " +
                                "(Closest player: " + closestName + ")"
                );
            }
        }
    }


    // Mass mob/player kills
    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity().getKiller() == null) return;
        Player killer = e.getEntity().getKiller();
        UUID id = killer.getUniqueId();

        long now = System.currentTimeMillis();
        recordEvent(playerKills, id, now);
        playerKills.get(id).removeIf(t -> now - t > MASS_KILL_WINDOW_MS);
        if (playerKills.get(id).size() >= MASS_KILL_THRESHOLD) {
            alert("⚠️ Player **" + killer.getName() + "** killed " + playerKills.get(id).size() + " entities within " + (MASS_KILL_WINDOW_MS/1000.0) + "s — possible harassment.");
            playerKills.get(id).clear();
        }

        // Villager kill detection
        if (e.getEntity() instanceof Villager) {
            recordEvent(villagerKills, id, now);
            villagerKills.get(id).removeIf(t -> now - t > VILLAGER_KILL_WINDOW_MS);
            if (villagerKills.get(id).size() >= VILLAGER_KILL_THRESHOLD) {
                alert("⚠️ Player **" + killer.getName() + "** killed " + villagerKills.get(id).size() + " villagers in " + (VILLAGER_KILL_WINDOW_MS/1000.0) + "s — village massacre suspected.");
                villagerKills.get(id).clear();
            }
        }

        // pet kill detection (tameable)
        if (e.getEntity() instanceof Tameable) {
            Tameable t = (Tameable) e.getEntity();
            if (t.getOwner() != null && t.getOwner() != killer) {
                recordEvent(petKills, id, now);
                petKills.get(id).removeIf(ti -> now - ti > PET_KILL_WINDOW_MS);
                if (petKills.get(id).size() >= PET_KILL_THRESHOLD) {
                    alert("⚠️ Player **" + killer.getName() + "** killed " + petKills.get(id).size() + " pets in " + (PET_KILL_WINDOW_MS/1000.0) + "s — pet griefing suspected.");
                    petKills.get(id).clear();
                }
            }
        }
    }

    // Mass block breaking + specialized block types
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Material broken = event.getBlock().getType();

        // Ignore common mining blocks entirely
        if (broken == Material.SAND ||
                broken == Material.DIRT ||
                broken == Material.GRASS_BLOCK ||
                broken == Material.NETHERRACK ||
                broken == Material.STONE) {
            return; // skip everything for these blocks
        }

        // ==========================
        // MASS BREAK GRIEF CHECK
        // ==========================

        recordEvent(blockBreaks, id, now);
        blockBreaks.get(id).removeIf(t -> now - t > MASS_BREAK_WINDOW_MS);

        // Track block types for "same block type" detection
        blockBreakTracker.putIfAbsent(id, new ArrayList<>());
        blockBreakTracker.get(id).add(new BlockBreakEntry(broken, now));
        blockBreakTracker.get(id).removeIf(e -> now - e.timestamp > MASS_BREAK_WINDOW_MS);

        if (blockBreaks.get(id).size() >= MASS_BREAK_THRESHOLD) {

            // Check if all breaking events in the window are the same block type
            List<BlockBreakEntry> entries = blockBreakTracker.get(id);
            boolean allSame = true;
            Material first = null;

            for (BlockBreakEntry e : entries) {
                if (first == null) first = e.type;
                if (e.type != first) {
                    allSame = false;
                    break;
                }
            }

            // Prevent false positives
            if (!allSame) {
                alert("⚠️ Player **" + p.getName() + "** broke " +
                        blockBreaks.get(id).size() + " blocks in " +
                        (MASS_BREAK_WINDOW_MS / 1000) +
                        "s — possible griefing.");
            }

            // Reset regardless
            blockBreaks.get(id).clear();
            blockBreakTracker.get(id).clear();
        }

        // ==========================
        // CONTAINER BREAK CHECK
        // ==========================
        if (broken == Material.CHEST || broken == Material.TRAPPED_CHEST || broken == Material.BARREL) {
            recordEvent(containerBreaks, id, now);
            containerBreaks.get(id).removeIf(t -> now - t > CONTAINER_BREAK_WINDOW_MS);

            if (containerBreaks.get(id).size() >= CONTAINER_BREAK_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** broke " +
                        containerBreaks.get(id).size() + " containers in " +
                        (CONTAINER_BREAK_WINDOW_MS/1000) +
                        "s — possible looting/grief.");
                containerBreaks.get(id).clear();
            }
        }

        // ==========================
        // BEACON BREAK CHECK
        // ==========================
        if (broken == Material.BEACON) {
            recordEvent(beaconBreaks, id, now);
            beaconBreaks.get(id).removeIf(t -> now - t > BEACON_BREAK_WINDOW_MS);

            if (beaconBreaks.get(id).size() >= BEACON_BREAK_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** broke " +
                        beaconBreaks.get(id).size() + " beacons in " +
                        (BEACON_BREAK_WINDOW_MS/1000) +
                        "s — possible targeted vandalism.");
                beaconBreaks.get(id).clear();
            }
        }

        // ==========================
        // GLASS BREAK CHECK
        // ==========================
        if (broken == Material.GLASS_PANE || broken == Material.GLASS) {
            recordEvent(glassPaneBreaks, id, now);
            glassPaneBreaks.get(id).removeIf(t -> now - t > GLASS_BREAK_WINDOW_MS);

            if (glassPaneBreaks.get(id).size() >= GLASS_BREAK_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** broke " +
                        glassPaneBreaks.get(id).size() + " glass panes in " +
                        (GLASS_BREAK_WINDOW_MS/1000) +
                        "s — sign/decoration grief suspected.");
                glassPaneBreaks.get(id).clear();
            }
        }
    }


    // Block placements: many detectors (lava / water / fire / tnt / obsidian / cobweb / scaffolding / item frames / end crystals / wither skulls)
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        Material placed = event.getBlockPlaced().getType();
        long now = System.currentTimeMillis();

        // Lava/water griefing
        if (placed == Material.LAVA || placed == Material.LAVA_CAULDRON || placed == Material.WATER || placed == Material.WATER_CAULDRON) {
            recordEvent(bucketPlaces, id, now);
            bucketPlaces.get(id).removeIf(t -> now - t > BUCKET_WINDOW_MS);
            if (bucketPlaces.get(id).size() >= BUCKET_PLACE_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** placed " + bucketPlaces.get(id).size() + " liquids in " + (BUCKET_WINDOW_MS/1000) + "s — possible flooding/grief.");
                bucketPlaces.get(id).clear();
            }
        }

        // Player placing lava next to other player
        if (placed == Material.LAVA || placed == Material.LAVA_CAULDRON) {
            // check proximity of other players within 3 blocks of placed block
            event.getBlockPlaced().getLocation().getWorld().getNearbyEntities(event.getBlockPlaced().getLocation(), 3, 3, 3).forEach(ent -> {
                if (ent instanceof Player && !ent.getUniqueId().equals(id)) {
                    alert("⚠️ Player **" + p.getName() + "** placed lava near player **" + ((Player) ent).getName() + "** — possible intent to trap/burn.");
                }
            });
        }

        // Fire placing
        if (placed == Material.FIRE) {
            recordEvent(firePlaces, id, now);
            firePlaces.get(id).removeIf(t -> now - t > FIRE_PLACE_WINDOW_MS);
            if (firePlaces.get(id).size() >= FIRE_PLACE_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** placed " + firePlaces.get(id).size() + " fire blocks in " + (FIRE_PLACE_WINDOW_MS/1000) + "s — arson suspected.");
                firePlaces.get(id).clear();
            }
        }

        // TNT placing
        if (placed == Material.TNT) {
            recordEvent(tntPlaces, id, now);
            tntPlaces.get(id).removeIf(t -> now - t > TNT_PLACE_WINDOW_MS);
            if (tntPlaces.get(id).size() >= TNT_PLACE_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** placed " + tntPlaces.get(id).size() + " TNT blocks in " + (TNT_PLACE_WINDOW_MS/1000) + "s — potential mass-grief.");
                tntPlaces.get(id).clear();
            }
        }

        // obsidian & cobweb trapping
        if (placed == Material.OBSIDIAN) {
            // check if placed block adjacent to player -> trapping attempt
            event.getBlockPlaced().getWorld().getNearbyEntities(event.getBlockPlaced().getLocation(), 2, 2, 2).forEach(ent -> {
                if (ent instanceof Player && !ent.getUniqueId().equals(id)) {
                    alert("⚠️ Player **" + p.getName() + "** placed obsidian near player **" + ((Player) ent).getName() + "** — possible trapping.");
                }
            });
        }
        if (placed == Material.COBWEB) {
            event.getBlockPlaced().getWorld().getNearbyEntities(event.getBlockPlaced().getLocation(), 2, 2, 2).forEach(ent -> {
                if (ent instanceof Player && !ent.getUniqueId().equals(id)) {
                    alert("⚠️ Player **" + p.getName() + "** placed cobweb near player **" + ((Player) ent).getName() + "** — possible trapping.");
                }
            });
        }

        // doors / trapdoors / note_block / bell place spam
        if (placed == Material.OAK_TRAPDOOR || placed == Material.IRON_TRAPDOOR || placed.name().endsWith("_DOOR")) {
            recordEvent(trapPlacements, id, now);
            trapPlacements.get(id).removeIf(t -> now - t > TRAP_PLACE_WINDOW_MS);
            if (trapPlacements.get(id).size() >= TRAP_PLACE_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** placed many doors/trapdoors quickly (" + trapPlacements.get(id).size() + ") — possible spam to lag/block paths.");
                trapPlacements.get(id).clear();
            }
        }
        if (placed == Material.NOTE_BLOCK || placed == Material.BELL) {
            recordEvent(noteBellSpam, id, now);
            noteBellSpam.get(id).removeIf(t -> now - t > NOTE_BELL_WINDOW_MS);
            if (noteBellSpam.get(id).size() >= NOTE_BELL_SPAM_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** spammed note-blocks/bells (" + noteBellSpam.get(id).size() + ") — sound spam / annoyance.");
                noteBellSpam.get(id).clear();
            }
        }

        // boats / minecarts placed rapidly
        if (
                placed == Material.OAK_BOAT ||
                        placed == Material.SPRUCE_BOAT ||
                        placed == Material.BIRCH_BOAT ||
                        placed == Material.JUNGLE_BOAT ||
                        placed == Material.ACACIA_BOAT ||
                        placed == Material.DARK_OAK_BOAT ||
                        placed == Material.MANGROVE_BOAT ||
                        placed == Material.CHERRY_BOAT ||
                        placed == Material.BAMBOO_RAFT ||
                        placed == Material.BAMBOO_CHEST_RAFT ||
                        placed == Material.OAK_CHEST_BOAT ||
                        placed == Material.SPRUCE_CHEST_BOAT ||
                        placed == Material.BIRCH_CHEST_BOAT ||
                        placed == Material.JUNGLE_CHEST_BOAT ||
                        placed == Material.ACACIA_CHEST_BOAT ||
                        placed == Material.DARK_OAK_CHEST_BOAT ||
                        placed == Material.MANGROVE_CHEST_BOAT ||
                        placed == Material.CHERRY_CHEST_BOAT ||
                        placed == Material.MINECART
        ) {

        recordEvent(boatMinecartPlace, id, now);
            boatMinecartPlace.get(id).removeIf(t -> now - t > BOAT_MINECART_WINDOW_MS);
            if (boatMinecartPlace.get(id).size() >= BOAT_MINECART_PLACE_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** placed many boats/minecarts quickly (" + boatMinecartPlace.get(id).size() + ") — possible lag machine.");
                boatMinecartPlace.get(id).clear();
            }
        }

        // scaffolding spam
        if (placed == Material.SCAFFOLDING) {
            recordEvent(scaffoldingPlace, id, now);
            scaffoldingPlace.get(id).removeIf(t -> now - t > SCAFFOLD_WINDOW_MS);
            if (scaffoldingPlace.get(id).size() >= SCAFFOLD_PLACE_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** placed many scaffolding blocks quickly (" + scaffoldingPlace.get(id).size() + ") — potential chunk load abuse.");
                scaffoldingPlace.get(id).clear();
            }
        }

        // item frames place / rotate are handled by other events (rotation = interact)
        if (placed == Material.ITEM_FRAME || placed == Material.GLOW_ITEM_FRAME) {
            recordEvent(itemFramePlace, id, now);
            itemFramePlace.get(id).removeIf(t -> now - t > ITEM_FRAME_WINDOW_MS);
            if (itemFramePlace.get(id).size() >= ITEM_FRAME_PLACE_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** placed many item frames quickly (" + itemFramePlace.get(id).size() + ") — possible decorative spam.");
                itemFramePlace.get(id).clear();
            }
        }

        // end crystals / wither skulls
        if (placed == Material.END_CRYSTAL) {
            recordEvent(endCrystalPlaces, id, now);
            endCrystalPlaces.get(id).removeIf(t -> now - t > END_CRYSTAL_WINDOW_MS);
            if (endCrystalPlaces.get(id).size() >= END_CRYSTAL_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** placed many end crystals quickly (" + endCrystalPlaces.get(id).size() + ") — explosion/lag risk.");
                endCrystalPlaces.get(id).clear();
            }
        }
        if (placed == Material.WITHER_SKELETON_SKULL || placed == Material.WITHER_SKELETON_WALL_SKULL) {
            recordEvent(witherSkullPlaces, id, now);
            witherSkullPlaces.get(id).removeIf(t -> now - t > WITHER_SKULL_WINDOW_MS);
            if (witherSkullPlaces.get(id).size() >= WITHER_SKULL_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** placed many wither skulls quickly (" + witherSkullPlaces.get(id).size() + ") — possible wither grief attempt.");
                witherSkullPlaces.get(id).clear();
            }
        }
    }

    // Player uses bucket (lava/water) — duplicates earlier placed checks for emptying buckets specifically
    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player p = event.getPlayer();
        Material bucket = event.getBucket();
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        if (bucket == Material.LAVA_BUCKET || bucket == Material.WATER_BUCKET) {
            recordEvent(bucketPlaces, id, now);
            bucketPlaces.get(id).removeIf(t -> now - t > BUCKET_WINDOW_MS);
            if (bucketPlaces.get(id).size() >= BUCKET_PLACE_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** emptied " + bucketPlaces.get(id).size() + " buckets in " + (BUCKET_WINDOW_MS/1000) + "s — possible liquid griefing.");
                bucketPlaces.get(id).clear();
            }

            // check proximity of other players for lava specifically
            if (bucket == Material.LAVA_BUCKET) {
                event.getBlock().getWorld().getNearbyEntities(event.getBlock().getLocation(), 3, 3, 3).forEach(ent -> {
                    if (ent instanceof Player && !ent.getUniqueId().equals(id)) {
                        alert("⚠️ Player **" + p.getName() + "** emptied lava near player **" + ((Player) ent).getName() + "** — possible trap.");
                    }
                });
            }
        }
    }

    // Interact event for item-frame rotation, spawn eggs usage, shulker drops, renames via anvil etc.
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        // spawn egg usage detection (if item in hand is spawn egg)
        if (event.getItem() != null && event.getItem().getType().name().endsWith("_SPAWN_EGG")) {
            recordEvent(spawnEggUse, id, now);
            spawnEggUse.get(id).removeIf(t -> now - t > SPAWN_EGG_WINDOW_MS);
            if (spawnEggUse.get(id).size() >= SPAWN_EGG_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** used spawn eggs " + spawnEggUse.get(id).size() + " times in " + (SPAWN_EGG_WINDOW_MS/1000) + "s — possible mob spam.");
                spawnEggUse.get(id).clear();
            }
        }

        // rotating item frames (right click with empty hand or item)
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            if (event.getHand() == EquipmentSlot.HAND && event.getClickedBlock() == null) {
                // nothing general to do here — rely on PlayerInteractEntityEvent for item frames
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        if (event.getRightClicked() instanceof ItemFrame || event.getRightClicked() instanceof GlowItemFrame) {
            // rotation/interaction
            recordEvent(itemFramePlace, id, now);
            itemFramePlace.get(id).removeIf(t -> now - t > ITEM_FRAME_WINDOW_MS);
            if (itemFramePlace.get(id).size() >= ITEM_FRAME_PLACE_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** rotated/interacted with item frames " + itemFramePlace.get(id).size() + " times in " + (ITEM_FRAME_WINDOW_MS/1000) + "s — possible annoyance/spam.");
                itemFramePlace.get(id).clear();
            }
        }
    }

    // Vehicle create can detect minecarts/boats created (minecart cluster detection)
    @EventHandler
    public void onVehicleCreate(VehicleCreateEvent event) {
        if (!(event.getVehicle() instanceof Minecart || event.getVehicle() instanceof Boat)) return;
        // find nearby vehicles and count cluster
        int nearbyCount = 0;
        for (Entity ent : event.getVehicle().getNearbyEntities(6, 2, 6)) {
            if (ent instanceof Minecart || ent instanceof Boat) nearbyCount++;
        }
        if (nearbyCount >= MINECART_CLUSTER_THRESHOLD) {
            alert("⚠️ Large cluster of vehicles detected near " + locationString(event.getVehicle().getLocation()) + " — possible lag machine.");
        }
    }

    // Player dropping items (drop many shulkers or many items)
    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        recordEvent(dropTimes, id, now);
        dropTimes.get(id).removeIf(t -> now - t > ITEM_DROP_WINDOW_MS);
        if (dropTimes.get(id).size() >= ITEM_DROP_THRESHOLD) {
            alert("⚠️ Player **" + p.getName() + "** dropped many items (" + dropTimes.get(id).size() + ") in a short time — possible item spam.");
            dropTimes.get(id).clear();
        }

        ItemStack is = event.getItemDrop().getItemStack();
        if (is != null && is.getType().name().endsWith("_SHULKER_BOX")) {
            recordEvent(filledShulkerDrops, id, now);
            filledShulkerDrops.get(id).removeIf(t -> now - t > FILLED_SHULKER_DROP_WINDOW_MS);
            if (filledShulkerDrops.get(id).size() >= FILLED_SHULKER_DROP_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** dropped many shulker boxes (" + filledShulkerDrops.get(id).size() + ") — possible item-drop lag attempt.");
                filledShulkerDrops.get(id).clear();
            }
        }
    }

    // Monitor player commands (private msg spam, /me spam, resource commands, command spam, tp commands)
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player issuer = event.getPlayer();
        String msg = event.getMessage().trim();
        UUID id = issuer.getUniqueId();
        long now = System.currentTimeMillis();

        // generic command spam tracking
        recordEvent(commandTimes, id, now);
        commandTimes.get(id).removeIf(t -> now - t > COMMAND_SPAM_WINDOW_MS);
        if (commandTimes.get(id).size() >= COMMAND_SPAM_THRESHOLD) {
            alert("⚠️ Player **" + issuer.getName() + "** executed " + commandTimes.get(id).size() + " commands in " + (COMMAND_SPAM_WINDOW_MS/1000.0) + "s — possible command spam.");
            commandTimes.get(id).clear();
        }

        String lower = msg.toLowerCase(Locale.ROOT);

        // tp-ish commands detection for tp-trap spam
        if (lower.startsWith("/tp ") || lower.startsWith("/teleport ") || lower.startsWith("/tpa ") || lower.startsWith("/tpahere ")) {
            recordEvent(tpCommandsIssued, id, now);
            tpCommandsIssued.get(id).removeIf(t -> now - t > TELEPORT_CMD_WINDOW_MS);
            if (tpCommandsIssued.get(id).size() >= TELEPORT_CMD_THRESHOLD) {
                alert("⚠️ Player **" + issuer.getName() + "** issued " + tpCommandsIssued.get(id).size() + " teleport commands in " + (TELEPORT_CMD_WINDOW_MS/1000) + "s — possible teleport harassment.");
                tpCommandsIssued.get(id).clear();
            }
        }

        // private message commands (common aliases)
        if (lower.startsWith("/msg ") || lower.startsWith("/tell ") || lower.startsWith("/whisper ") || lower.startsWith("/pm ")) {
            recordEvent(privateMsgSpam, id, now);
            privateMsgSpam.get(id).removeIf(t -> now - t > PRIVATE_MSG_WINDOW_MS);
            if (privateMsgSpam.get(id).size() >= PRIVATE_MSG_SPAM_THRESHOLD) {
                alert("⚠️ Player **" + issuer.getName() + "** sent " + privateMsgSpam.get(id).size() + " private messages in " + (PRIVATE_MSG_WINDOW_MS/1000) + "s — possible harassment via PM.");
                privateMsgSpam.get(id).clear();
            }
        }

        // /me spam
        if (lower.startsWith("/me ")) {
            recordEvent(meSpam, id, now);
            meSpam.get(id).removeIf(t -> now - t > ME_SPAM_WINDOW_MS);
            if (meSpam.get(id).size() >= ME_SPAM_THRESHOLD) {
                alert("⚠️ Player **" + issuer.getName() + "** used /me " + meSpam.get(id).size() + " times quickly — emote spam.");
                meSpam.get(id).clear();
            }
        }

        // resource pack prompt commands (catch any command containing 'resource')
        if (lower.contains("resource")) {
            recordEvent(resourceCmdSpam, id, now);
            resourceCmdSpam.get(id).removeIf(t -> now - t > RESOURCE_CMD_WINDOW_MS);
            if (resourceCmdSpam.get(id).size() >= RESOURCE_CMD_THRESHOLD) {
                alert("⚠️ Player **" + issuer.getName() + "** executed resource-related commands " + resourceCmdSpam.get(id).size() + " times — resourcepack spam suspicion.");
                resourceCmdSpam.get(id).clear();
            }
        }
    }

    // Detect renames in an anvil (approx) to catch extremely long item names
    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getInventory() instanceof AnvilInventory && event.getCurrentItem() != null) {
            ItemStack result = ((AnvilInventory) event.getInventory()).getItem(2);
            if (result != null && result.hasItemMeta() && result.getItemMeta().hasDisplayName()) {
                String name = result.getItemMeta().getDisplayName();
                if (name.length() >= LONG_NAME_THRESHOLD) {
                    Player p = (Player) event.getWhoClicked();
                    UUID id = p.getUniqueId();
                    long now = System.currentTimeMillis();
                    recordEvent(longNameRenames, id, now);
                    longNameRenames.get(id).removeIf(t -> now - t > LONG_NAME_WINDOW_MS);
                    if (longNameRenames.get(id).size() >= LONG_NAME_TRIGGER_COUNT) {
                        alert("⚠️ Player **" + p.getName() + "** created extremely long item names repeatedly — possible string-length abuse.");
                        longNameRenames.get(id).clear();
                    }
                }
            }
        }
    }

    // Approx detection: placing a hopper then quickly interacting with it many times (NBT-heavy items into a hopper)
    @EventHandler
    public void onBlockPlaceForHopperNBT(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.HOPPER) {
            Player p = event.getPlayer();
            UUID id = p.getUniqueId();
            long now = System.currentTimeMillis();
            recordEvent(nbtHopperPrep, id, now);
            nbtHopperPrep.get(id).removeIf(t -> now - t > NBT_HOPPER_PREP_WINDOW_MS);
            if (nbtHopperPrep.get(id).size() >= NBT_HOPPER_PREP_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** placed multiple hoppers quickly — possible preparation for NBT-heavy hopper lag.");
                nbtHopperPrep.get(id).clear();
            }
        }
    }

    // End crystal / end crystal use (rapid placing / explosive use)
    @EventHandler
    public void onEntityPlaceForEndCrystal(PlayerInteractEvent event) {
        // End crystals are also BlockPlaceEvent in some versions; we already track BlockPlaceEvent END_CRYSTAL.
        // Here we detect if a player used end crystal item rapidly (some versions differ)
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.END_CRYSTAL) {
            Player p = event.getPlayer();
            UUID id = p.getUniqueId();
            long now = System.currentTimeMillis();
            recordEvent(endCrystalUse, id, now);
            endCrystalUse.get(id).removeIf(t -> now - t > END_CRYSTAL_USE_WINDOW_MS);
            if (endCrystalUse.get(id).size() >= END_CRYSTAL_USE_THRESHOLD) {
                alert("⚠️ Player **" + p.getName() + "** used end crystals " + endCrystalUse.get(id).size() + " times quickly — explosion/lag risk.");
                endCrystalUse.get(id).clear();
            }
        }
    }

    // Vehicle destroy or large creation detection: cluster minecarts detection (counts per area)
    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        // nothing specific here, but vehicle create handles cluster detection
    }

    // Utility: entity spawn (for wither and end crystals that may spawn as entity)
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // not attributing to player in general; we rely on BlockPlace and PlayerInteract where possible
    }




    // Helper for location string
    private String locationString(org.bukkit.Location loc) {
        return loc.getWorld().getName() + "@" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    // ---------- /what_is_detected command with pagination + clickable chat buttons ----------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equalsIgnoreCase("what_is_detected")) return false;

        int page = 1;
        if (args.length >= 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cInvalid page number. Showing page 1.");
                page = 1;
            }
        }

        int perPage = 10;
        int total = detectionList.size();
        int pages = (int) Math.ceil((double) total / perPage);
        page = Math.min(page, Math.max(1, pages));

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, total);

        sender.sendMessage("§6BadThingsAlerter - detections (page " + page + " / " + pages + "):");
        for (int i = start; i < end; i++) {
            sender.sendMessage(" §e- " + detectionList.get(i));
        }

        // Show clickable buttons if a player is viewing it
        if (sender instanceof Player) {
            Player p = (Player) sender;

            TextComponent spacer = new TextComponent(" ");
            TextComponent prev = new TextComponent("[Prev]");
            if (page > 1) {
                prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/what_is_detected " + (page - 1)));
                prev.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            } else {
                prev.setColor(net.md_5.bungee.api.ChatColor.GRAY);
            }

            TextComponent next = new TextComponent("[Next]");
            if (page < pages) {
                next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/what_is_detected " + (page + 1)));
                next.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            } else {
                next.setColor(net.md_5.bungee.api.ChatColor.GRAY);
            }

            p.spigot().sendMessage(prev, spacer, next);
        } else {
            sender.sendMessage("Use /what_is_detected <page> to view other pages.");
        }

        return true;
    }


}
