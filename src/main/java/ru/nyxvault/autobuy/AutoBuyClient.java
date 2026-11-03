package ru.nyxvault.autobuy;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
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
    private static KeyMapping toggleKey, guiKey;
    private static boolean enabled = false;
    private static long lastAction = 0;
    private static String lastNotice = "";
    private record BuyRule(double maxPrice, boolean single) {}

    @Override public void onInitializeClient() {
        loadConfig();
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nyxvault_autobuy.toggle", GLFW.GLFW_KEY_K, KeyMapping.Category.MISC));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nyxvault_autobuy.gui", GLFW.GLFW_KEY_J, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (guiKey.consumeClick()) if (client.player != null) client.setScreen(new SettingsScreen());
            while (toggleKey.consumeClick()) { enabled = !enabled; tell("AutoBuy " + (enabled ? "включён" : "выключен")); }
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
                    String msg = "Найдено: " + stack.getHoverName().getString() + " за " + price;
                    if (!msg.equals(lastNotice)) { tell(msg + (rule.single() ? " — ПКМ" : " — ЛКМ")); lastNotice = msg; }
                    client.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, rule.single() ? 1 : 0, ClickType.PICKUP, client.player);
                    break;
                }
            }
        });
        tell("NYXVAULT загружен. K — запуск/остановка, J — настройки.");
    }

    private static double readPrice(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return -1;
        for (Component line : lore.lines()) {
            Matcher m = NUMBER.matcher(line.getString());
            if (m.find()) try { return Double.parseDouble(m.group(1).replace(" ", "").replace(",", "").replace(".", "")); }
            catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    private static Path configPath() { return MC.gameDirectory.toPath().resolve("config/nyxvault-autobuy.properties"); }
    private static void loadConfig() {
        try {
            Path file = configPath(); Files.createDirectories(file.getParent());
            if (!Files.exists(file)) Files.writeString(file, "# ID=макс_цена,режим (full или single)\\nminecraft:netherite_ingot=100,full\\n");
            Properties p = new Properties(); try (var in = Files.newInputStream(file)) { p.load(in); }
            RULES.clear();
            for (String key : p.stringPropertyNames()) try {
                String[] a = p.getProperty(key).trim().split(",", 2);
                double price = Double.parseDouble(a[0].trim());
                if (price >= 0) RULES.put(key.trim(), new BuyRule(price, a.length > 1 && a[1].trim().equalsIgnoreCase("single")));
            } catch (RuntimeException ignored) {}
        } catch (Exception e) { System.err.println("[NYXVAULT] config load: " + e); }
    }
    private static void saveConfig() {
        try {
            StringBuilder s = new StringBuilder("# NYXVAULT AutoBuy\\n# full=ЛКМ, single=ПКМ\\n");
            RULES.forEach((id, r) -> s.append(id).append("=").append(String.format(Locale.ROOT, "%.0f", r.maxPrice())).append(r.single() ? ",single\\n" : ",full\\n"));
            Files.createDirectories(configPath().getParent()); Files.writeString(configPath(), s.toString());
        } catch (Exception e) { tell("Ошибка сохранения: " + e.getMessage()); }
    }
    private static void tell(String text) { if (MC.player != null) MC.player.displayClientMessage(Component.literal("§b[NYXVAULT] §f" + text), false); }

    private static final class SettingsScreen extends Screen {
        private EditBox idBox, priceBox;
        private boolean single = false;
        private int page = 0, pageSize = 5;
        SettingsScreen() { super(Component.literal("NYXVAULT AutoBuy")); }

        @Override protected void init() {
            int c = width / 2;
            int formY = height - 78;
            idBox = new EditBox(font, c - 145, formY, 190, 20, Component.literal("Item ID"));
            idBox.setMaxLength(100); idBox.setHint(Component.literal("minecraft:elytra")); addRenderableWidget(idBox);
            priceBox = new EditBox(font, c + 50, formY, 85, 20, Component.literal("Max price"));
            priceBox.setMaxLength(15); priceBox.setHint(Component.literal("1000")); addRenderableWidget(priceBox);
            addRenderableWidget(Button.builder(Component.literal(modeLabel()), b -> { single = !single; b.setMessage(Component.literal(modeLabel())); }).bounds(c - 145, formY + 25, 115, 20).build());
            addRenderableWidget(Button.builder(Component.literal("＋ ДОБАВИТЬ"), b -> addRule()).bounds(c - 25, formY + 25, 95, 20).build());
            addRenderableWidget(Button.builder(Component.literal("ЗАКРЫТЬ"), b -> onClose()).bounds(c + 75, formY + 25, 70, 20).build());

            pageSize = Math.max(1, Math.min(5, (formY - 90) / 27));
            List<String> ids = new ArrayList<>(RULES.keySet());
            int pages = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
            page = Math.min(page, pages - 1);
            for (int n = page * pageSize; n < Math.min(ids.size(), (page + 1) * pageSize); n++) {
                String id = ids.get(n); BuyRule r = RULES.get(id); int y = 82 + (n - page * pageSize) * 27;
                String label = (r.single() ? "ПКМ " : "ЛКМ ") + (id.length() > 23 ? id.substring(0, 20) + "..." : id);
                addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    BuyRule old = RULES.get(id); if (old != null) RULES.put(id, new BuyRule(old.maxPrice(), !old.single()));
                    saveConfig(); rebuildWidgets();
                }).bounds(c - 145, y, 185, 22).build());
                addRenderableWidget(Button.builder(Component.literal(String.format(Locale.ROOT, "%.0f", r.maxPrice())), b -> {
                    idBox.setValue(id); priceBox.setValue(String.format(Locale.ROOT, "%.0f", r.maxPrice())); single = r.single();
                }).bounds(c + 45, y, 85, 22).build());
                addRenderableWidget(Button.builder(Component.literal("X"), b -> {
                    RULES.remove(id); saveConfig(); rebuildWidgets();
                }).bounds(c + 135, y, 25, 22).build());
            }
            addRenderableWidget(Button.builder(Component.literal("‹"), b -> { if (page > 0) { page--; rebuildWidgets(); } }).bounds(c - 35, 48, 30, 20).build());
            addRenderableWidget(Button.builder(Component.literal((page + 1) + " / " + pages), b -> {}).bounds(c, 48, 55, 20).build());
            addRenderableWidget(Button.builder(Component.literal("›"), b -> { if (page + 1 < pages) { page++; rebuildWidgets(); } }).bounds(c + 60, 48, 30, 20).build());
        }

        private String modeLabel() { return single ? "ПОШТУЧНО (ПКМ)" : "ВЕСЬ ЛОТ (ЛКМ)"; }
        private void addRule() {
            String id = idBox.getValue().trim(), raw = priceBox.getValue().trim().replace(" ", "").replace(",", "");
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) { tell("ID пример: minecraft:elytra"); return; }
            try {
                double price = Double.parseDouble(raw);
                if (!Double.isFinite(price) || price < 0) throw new NumberFormatException();
                RULES.put(id, new BuyRule(price, single)); saveConfig();
                page = Math.max(0, (RULES.size() - 1) / pageSize); rebuildWidgets(); tell("Правило сохранено: " + id);
            } catch (NumberFormatException e) { tell("Введи корректную цену."); }
        }
        @Override public void render(GuiGraphics g, int mx, int my, float delta) {
            // Avoid the vanilla blur pipeline: FCL can invoke it more than once per frame.
            // A simple dim overlay keeps the settings GUI readable without triggering the blur crash.
            g.fill(0, 0, width, height, 0xC0101420);
            g.drawCenteredString(font, "NYXVAULT AUTO BUY", width / 2, 15, 0x55FFFF);
            g.drawCenteredString(font, "J — GUI | K — запуск/остановка", width / 2, 30, 0xFFFFFF);
            g.drawString(font, "СПИСОК ПРЕДМЕТОВ (" + RULES.size() + ")", width / 2 - 145, 67, 0xAAAAAA);
            super.render(g, mx, my, delta);
            g.drawString(font, "ID предмета", width / 2 - 145, height - 94, 0xFFFFFF);
            g.drawString(font, "Макс. цена", width / 2 + 50, height - 94, 0xFFFFFF);
        }
    }
}