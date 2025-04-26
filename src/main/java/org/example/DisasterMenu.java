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
        this.inv = Bukkit.createInventory(this, 9, ChatColor.DARK_RED + "Select Disaster");
        initializeItems();
    }

    private void initializeItems() {
        // Earthquake Item
        inv.setItem(2, createItem(
            Material.IRON_PICKAXE, 
            ChatColor.RED + "Earthquake",
            ChatColor.GRAY + "Causes ground tremors and cracks",
            ChatColor.DARK_GRAY + "Range: 100x100 blocks"
        ));

        // Meteor Shower Item
        inv.setItem(4, createItem(
            Material.FIRE_CHARGE, 
            ChatColor.GOLD + "Meteor Shower",
            ChatColor.GRAY + "Rains fiery meteors from sky",
            ChatColor.DARK_GRAY + "Range: 100x100 blocks"
        ));

        // Tornado Item
        inv.setItem(6, createItem(
            Material.WHITE_WOOL, 
            ChatColor.AQUA + "Tornado",
            ChatColor.GRAY + "Creates destructive spinning vortex",
            ChatColor.DARK_GRAY + "Range: 100x100 blocks"
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
        
        // Get all online players excluding the executor
        List<Player> allPlayers = Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.getGameMode() == GameMode.SURVIVAL)
            .collect(Collectors.toList());
        
        Location targetLocation = player.getWorld().getSpawnLocation(); // Default value
        
        if(!allPlayers.isEmpty()) {
            // Select random player from entire server
            Player target = allPlayers.get(new Random().nextInt(allPlayers.size()));
            getRandomNearbyLocation(target.getLocation()).thenAcceptAsync(location -> {
                disasterManager.triggerDisaster(location, DisasterManager.DisasterType.METEOR_SHOWER);
                player.sendMessage(ChatColor.YELLOW + "Disaster targeting " + target.getName() + "'s location!");
            });
        } else {
            // Fallback to spawn if no other players
            player.sendMessage(ChatColor.YELLOW + "No other players online - targeting world spawn!");
        }
        
        if (displayName.contains("Earthquake")) {
            disasterManager.triggerDisaster(targetLocation, DisasterManager.DisasterType.EARTHQUAKE);
            return true;
        } else if (displayName.contains("Meteor Shower")) {
            disasterManager.triggerDisaster(targetLocation, DisasterManager.DisasterType.METEOR_SHOWER);
            return true;
        } else if (displayName.contains("Tornado")) {
            disasterManager.triggerDisaster(targetLocation, DisasterManager.DisasterType.TORNADO);
            return true;
        }
        return false;
    }

    private CompletableFuture<Location> getRandomNearbyLocation(Location center) {
        CompletableFuture<Location> future = new CompletableFuture<>();
        plugin.getServer().getRegionScheduler().run(plugin, center, task -> {
            Random random = new Random();
            World world = center.getWorld();
            
            // Generate valid coordinates
            int x = center.getBlockX() + random.nextInt(100) - 50;
            int z = center.getBlockZ() + random.nextInt(100) - 50;
            
            // Ensure minimum distance without async checks
            if(Math.abs(x - center.getBlockX()) < 20) x += Integer.signum(x - center.getBlockX()) * 20;
            if(Math.abs(z - center.getBlockZ()) < 20) z += Integer.signum(z - center.getBlockZ()) * 20;
            
            // Get highest block synchronously
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