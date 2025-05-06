package org.example;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.CompletableFuture;
import org.bukkit.GameMode;

public class DisasterMenu implements InventoryHolder {
    private final Inventory inv;
    private final Player player;
    private final DisasterManager disasterManager;
    private final JavaPlugin plugin;

    public DisasterMenu(Player player, DisasterManager disasterManager, JavaPlugin plugin) {
        this.player = player;
        this.disasterManager = disasterManager;
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(this, 9, ChatColor.DARK_RED + "Selecteer Ramp");
        initializeItems();
    }

    private void initializeItems() {
        // Earthquake Item
        inv.setItem(2, createItem(
            Material.IRON_PICKAXE, 
            ChatColor.RED + "Aardbeving",
            ChatColor.GRAY + "Veroorzaakt grondtrillingen en scheuren",
            ChatColor.DARK_GRAY + "Bereik: 100x100 blokken"
        ));

        // Meteor Shower Item
        inv.setItem(4, createItem(
            Material.FIRE_CHARGE, 
            ChatColor.GOLD + "Meteorenregen",
            ChatColor.GRAY + "Regent vurige meteoren uit de lucht",
            ChatColor.DARK_GRAY + "Bereik: 100x100 blokken"
        ));

        // Tornado Item
        inv.setItem(6, createItem(
            Material.WHITE_WOOL, 
            ChatColor.AQUA + "Tornado",
            ChatColor.GRAY + "Creëert een vernietigende wervelwind",
            ChatColor.DARK_GRAY + "Bereik: 100x100 blokken"
        ));
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    public void open() {
        player.openInventory(inv);
    }

    public boolean handleClick(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        String displayName = item.getItemMeta().getDisplayName();
        
        List<Player> allPlayers = Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.getGameMode() == GameMode.SURVIVAL)
            .collect(Collectors.toList());
        
        if(!allPlayers.isEmpty()) {
            Player target = allPlayers.get(new Random().nextInt(allPlayers.size()));
            // Player-relative: 50-100 blocks from target
            getRandomNearbyLocation(target.getLocation(), 100, 50).thenAccept(loc -> {
                plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
                    player.sendMessage(ChatColor.YELLOW + "Disaster targeting " + target.getName() + "'s area!");
                    triggerDisaster(displayName, loc);
                });
            });
            return true;
        } else {
            // Spawn-relative: 150-300 blocks from world spawn
            Location spawn = player.getWorld().getSpawnLocation();
            getRandomNearbyLocation(spawn, 300, 150).thenAccept(loc -> {
                plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
                    player.sendMessage(ChatColor.YELLOW + "No players online - targeting safe zone (" 
                        + (int)loc.distance(spawn) + " blocks from spawn)");
                    triggerDisaster(displayName, loc);
                });
            });
            return true;
        }
    }

    private void triggerDisaster(String displayName, Location location) {
        if (displayName.contains("Earthquake")) {
            disasterManager.triggerDisaster(location, DisasterManager.DisasterType.EARTHQUAKE);
        } else if (displayName.contains("Meteor Shower")) {
            disasterManager.triggerDisaster(location, DisasterManager.DisasterType.METEOR_SHOWER);
        } else if (displayName.contains("Tornado")) {
            disasterManager.triggerDisaster(location, DisasterManager.DisasterType.TORNADO);
        }
    }

    private CompletableFuture<Location> getRandomNearbyLocation(Location center, int radius, int minDistance) {
        CompletableFuture<Location> future = new CompletableFuture<>();
        plugin.getServer().getRegionScheduler().run(plugin, center, task -> {
            Random random = new Random();
            World world = center.getWorld();
            
            // Generate coordinates within radius but outside minDistance
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = minDistance + (radius - minDistance) * random.nextDouble();
            
            int x = center.getBlockX() + (int)(Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int)(Math.sin(angle) * distance);
            
            // Get highest block at location
            int y = world.getHighestBlockYAt(x, z);
            future.complete(new Location(world, x, y, z));
        });
        return future;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
} 