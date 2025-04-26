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

public class DisasterMenu implements InventoryHolder {
    private final Inventory inv;
    private final Player player;
    private final DisasterManager disasterManager;

    public DisasterMenu(Player player, DisasterManager disasterManager) {
        this.player = player;
        this.disasterManager = disasterManager;
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
        Location randomLocation = getRandomNearbyLocation(player.getLocation());
        
        if (displayName.contains("Earthquake")) {
            disasterManager.triggerDisaster(randomLocation, DisasterManager.DisasterType.EARTHQUAKE);
            return true;
        } else if (displayName.contains("Meteor Shower")) {
            disasterManager.triggerDisaster(randomLocation, DisasterManager.DisasterType.METEOR_SHOWER);
            return true;
        } else if (displayName.contains("Tornado")) {
            disasterManager.triggerDisaster(randomLocation, DisasterManager.DisasterType.TORNADO);
            return true;
        }
        return false;
    }

    private Location getRandomNearbyLocation(Location center) {
        Random random = new Random();
        // Generate location within 100 blocks but at least 20 blocks away
        int x = center.getBlockX() + random.nextInt(100) - 50;
        int z = center.getBlockZ() + random.nextInt(100) - 50;
        while (Math.abs(x - center.getBlockX()) < 20 && Math.abs(z - center.getBlockZ()) < 20) {
            x = center.getBlockX() + random.nextInt(100) - 50;
            z = center.getBlockZ() + random.nextInt(100) - 50;
        }
        
        World world = center.getWorld();
        int y = world.getHighestBlockYAt(x, z);
        return new Location(world, x, y, z);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
} 