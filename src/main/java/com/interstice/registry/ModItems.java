package com.interstice.registry;

import com.interstice.Interstice;
import com.interstice.config.ServerConfig;
import com.interstice.item.BatteryItem;
import com.interstice.item.FlashFlareItem;
import com.interstice.item.FlashlightItem;
import com.interstice.item.LureItem;
import com.interstice.item.MedicalItem;
import com.interstice.item.ResonanceSpikeItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.List;

/**
 * Survival equipment found in the Corridor: food (hunger only), medical supplies (the only healing), light and
 * protective devices. None of it is a weapon; see {@code docs/CORRIDOR.md}.
 */
public final class ModItems {
    public static final List<Item> SURVIVAL = new ArrayList<>();

    // food: restores hunger, never health (natural regeneration is off in the Interstice)
    public static final Item CANNED_RATION = register("canned_ration", new Item(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.6f).build())));
    public static final Item DRY_BISCUITS = register("dry_biscuits", new Item(new Item.Properties()
            .food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3f).fast().build())));
    public static final Item NUTRIENT_PASTE = register("nutrient_paste", new Item(new Item.Properties().stacksTo(16)
            .food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.9f).build())));

    // medical: the only way to heal
    public static final Item BANDAGE = register("bandage", new MedicalItem(new Item.Properties().stacksTo(16),
            () -> ServerConfig.INSTANCE.bandageHeal, () -> ServerConfig.INSTANCE.bandageUseTicks, true, 3f, SoundEvents.WOOL_PLACE));
    public static final Item MEDICAL_KIT = register("medical_kit", new MedicalItem(new Item.Properties().stacksTo(4).rarity(Rarity.UNCOMMON),
            () -> ServerConfig.INSTANCE.medkitHeal, () -> ServerConfig.INSTANCE.medkitUseTicks, true, 5f, SoundEvents.ARMOR_EQUIP_LEATHER.value()));
    public static final Item EMERGENCY_INJECTOR = register("emergency_injector", new MedicalItem(new Item.Properties().stacksTo(4).rarity(Rarity.RARE),
            () -> ServerConfig.INSTANCE.injectorHeal, () -> ServerConfig.INSTANCE.injectorUseTicks, false, 0f, SoundEvents.BOTTLE_EMPTY));

    // light
    public static final Item FLASHLIGHT = register("flashlight", new FlashlightItem(new Item.Properties()));
    public static final Item BATTERY = register("battery", new BatteryItem(new Item.Properties().stacksTo(16)));

    // protective devices (escape, not combat)
    public static final Item FLASH_FLARE = register("flash_flare", new FlashFlareItem(new Item.Properties().stacksTo(8)));
    public static final Item RATTLE_LURE = register("rattle_lure", new LureItem(new Item.Properties().stacksTo(8)));
    public static final Item RESONANCE_SPIKE = register("resonance_spike", new ResonanceSpikeItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // notes left behind (lore pages, written by the generator into loot)
    public static final Item TORN_NOTE = register("torn_note", new Item(new Item.Properties().stacksTo(1)));

    private static Item register(String name, Item item) {
        Registry.register(BuiltInRegistries.ITEM, Interstice.id(name), item);
        SURVIVAL.add(item);
        return item;
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(e -> {
            e.accept(CANNED_RATION);
            e.accept(DRY_BISCUITS);
            e.accept(NUTRIENT_PASTE);
            e.accept(BANDAGE);
            e.accept(MEDICAL_KIT);
            e.accept(EMERGENCY_INJECTOR);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> {
            e.accept(FLASHLIGHT);
            e.accept(BATTERY);
            e.accept(FLASH_FLARE);
            e.accept(RATTLE_LURE);
            e.accept(RESONANCE_SPIKE);
            e.accept(ModBlocks.WARD_EMITTER);
            e.accept(TORN_NOTE);
        });
    }

    private ModItems() {}
}
