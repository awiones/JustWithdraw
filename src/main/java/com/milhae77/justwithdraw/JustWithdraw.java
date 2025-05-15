package com.milhae77.justwithdraw;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.NamespacedKey;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Item;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JustWithdraw extends JavaPlugin implements TabCompleter {
    private Economy econ = null;
    private NamespacedKey valueKey;

    @Override
    public void onEnable() {
        // Save default config first
        saveDefaultConfig();
        
        // Ensure new config options exist
        FileConfiguration config = getConfig();
        if (!config.isSet("show-note.enabled")) {
            config.set("show-note.enabled", true);
            config.set("show-note.name-format", "§e§l💰 %amount% 💰");
            config.set("show-note.glowing", true);
            config.set("show-note.pickup-delay", 10);
            config.set("show-note.floating", false); // Default to normal drop behavior
            saveConfig();
            getLogger().info("Added new configuration options for note display.");
        }
        
        if (!setupEconomy()) {
            getLogger().severe("Vault dependency not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        valueKey = new NamespacedKey(this, "money_value");
        getServer().getPluginManager().registerEvents(new JustWithdrawListener(this, valueKey), this);
        this.getCommand("withdraw").setTabCompleter(this);
        getLogger().info("JustWithdraw has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("JustWithdraw has been disabled.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c✖ Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        if (econ == null) {
            player.sendMessage("§c✖ Economy system not available.");
            return true;
        }
        // Add reload subcommand
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("gitwithdraw.reload")) {
                player.sendMessage(getConfig().getString("message-prefix", "§6[GitWithdraw] §r") + "§cYou do not have permission to reload the config.");
                return true;
            }
            reloadConfig();
            player.sendMessage(getConfig().getString("message-prefix", "§6[GitWithdraw] §r") + "§aConfiguration reloaded!");
            return true;
        }
        
        if (args.length == 1) {
            try {
                double amount = Double.parseDouble(args[0]);
                double minAmount = getConfig().getDouble("min-withdraw-amount", 1.0);
                if (amount < minAmount) {
                    player.sendMessage(getConfig().getString("message-prefix", "§6[GitWithdraw] §r") + "§cPlease enter an amount of at least " + minAmount + ".");
                    return true;
                }
                double balance = econ.getBalance(player);
                if (balance < amount) {
                    player.sendMessage("§c✖ You don't have enough balance. Your balance: §e" + econ.format(balance));
                    return true;
                }
                // Withdraw the money
                if (econ.withdrawPlayer(player, amount).transactionSuccess()) {
                    // Create the paper note
                    ItemStack note = createMoneyNote(amount);
                    // Give the player the item or drop it if inventory is full
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(note);
                    } else {
                        // Drop the note at the player's feet and let the listener handle styling
                        player.getWorld().dropItem(player.getLocation(), note);
                        player.sendMessage("§e��� Your inventory was full, so the note was dropped at your feet.");
                    }
                    player.sendMessage("§a✔ You have withdrawn §e" + econ.format(amount) + "§a and received a §6§lBank Note§a!\n§7(Right-click the note to deposit it back.)");
                } else {
                    player.sendMessage("§c✖ Transaction failed! Please contact an administrator.");
                }
                return true;
            } catch (NumberFormatException e) {
                player.sendMessage("§c✖ Invalid amount. Usage: /withdraw <amount>");
                return true;
            }
        } else {
            // Show balance if no arguments provided
            double balance = econ.getBalance(player);
            player.sendMessage("§bYour balance: §e" + econ.format(balance));
            player.sendMessage("§7Use §a/withdraw <amount>§7 to create a bank note.");
            return true;
        }
    }

    /**
     * Creates a money note item with the specified value
     */
    public ItemStack createMoneyNote(double amount) {
        ItemStack note = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = note.getItemMeta();
        // Set display name
        meta.setDisplayName("§6§lBank Note §7| §e" + econ.format(amount));
        // Set lore with value information
        List<String> lore = new ArrayList<>();
        lore.add("§f§m------------------------");
        lore.add("§7Value: §a§l" + econ.format(amount));
        lore.add("§7Right-click to deposit this money");
        lore.add("§7into your account.");
        lore.add("§f§m------------------------");
        lore.add("§8Bank Note ID: §b" + System.currentTimeMillis() % 10000);
        // Optionally, set a custom model data for resource pack support
        // meta.setCustomModelData(1234567);
        meta.setLore(lore);
        // Store the actual value in persistent data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(valueKey, PersistentDataType.DOUBLE, amount);
        note.setItemMeta(meta);
        return note;
    }

    /**
     * Gets the economy instance
     */
    public Economy getEconomy() {
        return econ;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("withdraw")) {
            if (args.length == 1) {
                List<String> completions = new ArrayList<>(Arrays.asList("<amount>", "100", "500", "1000"));
                
                // Remove admin debug command from tab completion
                if (sender.hasPermission("gitwithdraw.reload")) {
                    completions.add("reload");
                }
                
                return completions;
            }
        }
        return Collections.emptyList();
    }
}