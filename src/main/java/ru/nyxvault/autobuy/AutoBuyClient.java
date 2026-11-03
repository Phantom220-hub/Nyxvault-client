package ru.nyxvault.autobuy;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public final class AutoBuyClient implements ClientModInitializer {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Pattern NUMBER = Pattern.compile("([0-9][0-9 ,.]*)(?:\\s*(?:монет|монета|монеты|coins?|⛃))", Pattern.CASE_INSENSITIVE);
    private static final Map<String, BuyRule> RULES = new LinkedHashMap<>();
    private static KeyMapping toggleKey;
    private static boolean enabled = false;
    private static long lastAction = 0;
    private static String lastNotice = "";

    private record BuyRule(double maxPrice, boolean single) {}

    @Override public void onInitializeClient() {
        loadConfig();
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nyxvault_autobuy.toggle", GLFW.GLFW_KEY_K, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                enabled = !enabled;
                tell("AutoBuy " + (enabled ? "включён" : "выключен"));
            }
            if (!enabled || client.player == null || client.gameMode == null || !(client.screen instanceof AbstractContainerScreen<?>)) return;
            if (System.currentTimeMillis() - lastAction < 650) return;
            var menu = client.player.containerMenu;
            for (var slot : menu.slots) {
                if (!slot.hasItem() || slot.container == client.player.getInventory()) continue;
                ItemStack stack = slot.getItem();
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                BuyRule rule = RULES.get(id);
                if (rule == null) continue;
                double price = readPrice(stack);
                if (price > 0 && price <= rule.maxPrice()) {
                    lastAction = System.currentTimeMillis();
                    String mode = rule.single() ? "ПОШТУЧНО (ПКМ)" : "ЦЕЛЫЙ ЛОТ (ЛКМ)";
                    String msg = "Найдено: " + stack.getHoverName().getString() + " за " + price;
                    if (!msg.equals(lastNotice)) {
                        tell(msg + " — покупка: " + mode);
                        lastNotice = msg;
                    }
                    int mouseButton = rule.single() ? 1 : 0;
                    client.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, mouseButton, ClickType.PICKUP, client.player);
                    break;
                }
            }
        });
        tell("NYXVAULT AutoBuy загружен. K — вкл/выкл. Настройки: config/nyxvault-autobuy.properties");
    }

    private static double readPrice(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return -1;
        for (Component line : lore.lines()) {
            Matcher m = NUMBER.matcher(line.getString());
            if (m.find()) {
                try { return Double.parseDouble(m.group(1).replace(" ", "").replace(",", "").replace(".", "")); }
                catch (NumberFormatException ignored) { }
            }
        }
        return -1;
    }

    private static void loadConfig() {
        try {
            Path file = MC.gameDirectory.toPath().resolve("config/nyxvault-autobuy.properties");
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) Files.writeString(file,
                "# Формат: minecraft:item_id=максимальная_цена,режим\n" +
                "# Режим full — весь лот (ЛКМ), single — поштучно (ПКМ)\n" +
                "minecraft:netherite_ingot=100,full\n" +
                "# minecraft:elytra=1000,full\n" +
                "# minecraft:potion=500,single\n");
            Properties p = new Properties();
            try (var in = Files.newInputStream(file)) { p.load(in); }
            for (String key : p.stringPropertyNames()) {
                try {
                    String[] parts = p.getProperty(key).trim().split(",", 2);
                    double maxPrice = Double.parseDouble(parts[0].trim());
                    boolean single = parts.length > 1 && parts[1].trim().equalsIgnoreCase("single");
                    RULES.put(key.trim(), new BuyRule(maxPrice, single));
                } catch (RuntimeException ignored) { }
            }
        } catch (Exception e) { System.err.println("[NYXVAULT] Не удалось прочитать конфиг: " + e); }
    }
    private static void tell(String text) {
        if (MC.player != null) MC.player.displayClientMessage(Component.literal("§b[NYXVAULT] §f" + text), false);
    }
}
