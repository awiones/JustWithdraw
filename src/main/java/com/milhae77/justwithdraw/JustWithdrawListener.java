package com.milhae77.justwithdraw;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Item;
import org.bukkit.configuration.file.FileConfiguration;

public class JustWithdrawListener implements Listener {
    private final JustWithdraw plugin;
    private final NamespacedKey valueKey;
    
    public JustWithdrawListener(JustWithdraw plugin, NamespacedKey valueKey) {
        this.plugin = plugin;
        this.valueKey = valueKey;
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only process right clicks with paper
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PAPER) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (!container.has(valueKey, PersistentDataType.DOUBLE)) {
            return;
        }
        // Get the money value from the note
        double amount = container.get(valueKey, PersistentDataType.DOUBLE);
        Player player = event.getPlayer();
        Economy econ = plugin.getEconomy();
        // Deposit the money
        if (econ.depositPlayer(player, amount).transactionSuccess()) {
            // Remove one paper from the hand
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            player.sendMessage("§a✔ You have deposited §e" + econ.format(amount) + "§a into your account!");
            player.sendMessage("§bNew balance: §e" + econ.format(econ.getBalance(player)));
            // Show thank you message if set in config and enabled
            boolean showThankYou = plugin.getConfig().getBoolean("show-message.enabled", true);
            String thankYouMsg = plugin.getConfig().getString("show-message.thank-you-message", "");
            if (showThankYou && thankYouMsg != null && !thankYouMsg.isEmpty()) {
                player.sendMessage(thankYouMsg);
            }
            // Cancel the event to prevent using the note for other purposes
            event.setCancelled(true);
        } else {
            player.sendMessage("§c✖ Failed to deposit the money. Please contact an administrator.");
        }
    }
    
    /**
     * Handle when a player drops a money note using the Q key
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        // Get the dropped item entity
        Item droppedItem = event.getItemDrop();
        ItemStack itemStack = droppedItem.getItemStack();
        
        // Check if it's a paper item (potential money note)
        if (itemStack.getType() != Material.PAPER) {
            return;
        }
        
        // Get the item meta
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return;
        }
        
        // Check if it has our money value data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (!container.has(valueKey, PersistentDataType.DOUBLE)) {
            return; // Not a money note
        }
        
        // It's a money note! Get the amount
        double amount = container.get(valueKey, PersistentDataType.DOUBLE);
        
        // Get config settings
        FileConfiguration config = plugin.getConfig();
        boolean showNote = config.getBoolean("show-note.enabled", true);
        String nameFormat = config.getString("show-note.name-format", "§e§l💰 %amount% 💰");
        
        plugin.getLogger().info("Player " + event.getPlayer().getName() + " dropped a money note worth " + amount);
        
        try {
            // Keep gravity enabled - let the item drop and stay on the ground naturally
            droppedItem.setGravity(true);
            
            // Set custom name if enabled
            if (showNote && nameFormat != null && !nameFormat.isEmpty()) {
                Economy econ = plugin.getEconomy();
                String displayName = nameFormat.replace("%amount%", econ.format(amount));
                
                // Set the custom name and make it visible
                droppedItem.setCustomName(displayName);
                droppedItem.setCustomNameVisible(true);
                
                plugin.getLogger().info("Set custom name on dropped item to: " + displayName);
            }
            
            // Add glowing effect if that setting is enabled
            try {
                boolean shouldGlow = config.getBoolean("show-note.glowing", true);
                if (shouldGlow) {
                    droppedItem.setGlowing(true);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not apply glowing effect: " + e.getMessage());
            }
            
            // Set pickup delay to prevent accidental pickup
            int pickupDelay = config.getInt("show-note.pickup-delay", 10);
            droppedItem.setPickupDelay(pickupDelay);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error processing dropped money note: " + e.getMessage());
            e.printStackTrace();
        }
    }
}