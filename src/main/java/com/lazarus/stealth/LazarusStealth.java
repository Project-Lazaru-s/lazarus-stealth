package com.lazarus.stealth;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(LazarusStealth.ID)
public final class LazarusStealth {
    public static final String ID = "lazarusstealth";
    static final Logger LOG = LogUtils.getLogger();

    // Entities that use stealth perception; blocks by footstep class; items for takedowns and held light.
    public static final TagKey<EntityType<?>> INFECTED = TagKey.create(Registries.ENTITY_TYPE, rl("infected"));
    public static final TagKey<EntityType<?>> HUMAN = TagKey.create(Registries.ENTITY_TYPE, rl("human"));
    public static final TagKey<EntityType<?>> FINISHER_SPECIAL = TagKey.create(Registries.ENTITY_TYPE, rl("finisher_special"));
    public static final TagKey<EntityType<?>> FINISHER_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, rl("finisher_immune"));
    public static final TagKey<EntityType<?>> NO_VISION = TagKey.create(Registries.ENTITY_TYPE, rl("no_vision"));
    public static final TagKey<Block> SURFACE_SOFT = TagKey.create(Registries.BLOCK, rl("surface_soft"));
    public static final TagKey<Block> SURFACE_METAL = TagKey.create(Registries.BLOCK, rl("surface_metal"));
    public static final TagKey<Block> SURFACE_GLASS = TagKey.create(Registries.BLOCK, rl("surface_glass"));
    public static final TagKey<Block> SEE_THROUGH = TagKey.create(Registries.BLOCK, rl("see_through"));
    public static final TagKey<Item> FINISHER_WEAPONS = TagKey.create(Registries.ITEM, rl("finisher_weapons"));
    public static final TagKey<Item> LIGHT_SOURCES = TagKey.create(Registries.ITEM, rl("light_sources"));

    // Sneak tree perks (Puffish Skills attribute rewards, removed automatically on respec).
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, ID);
    public static final RegistryObject<Attribute> FOOTSTEP_REDUCTION = attribute("footstep_reduction", 0.6);
    public static final RegistryObject<Attribute> VISUAL_STEALTH = attribute("visual_stealth", 0.6);
    public static final RegistryObject<Attribute> BURDEN_CONTROL = attribute("burden_control", 0.8);
    public static final RegistryObject<Attribute> FINISHER_MASTERY = attribute("finisher_mastery", 1);

    public LazarusStealth() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, StealthConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, StealthConfig.CLIENT_SPEC);
        ATTRIBUTES.register(modBus);
        modBus.addListener((EntityAttributeModificationEvent event) -> {
            for (RegistryObject<Attribute> a : ATTRIBUTES.getEntries()) event.add(EntityType.PLAYER, a.get());
        });
        StealthNetwork.register();
        if (ModList.get().isLoaded("tacz")) MinecraftForge.EVENT_BUS.register(TaczCompat.class);
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            LOG.info("[lazarusstealth] integrations: {}", Integrations.report());
            if (!ModList.get().isLoaded("lazarusmoodles") || !ModList.get().isLoaded("encumber"))
                LOG.warn("[lazarusstealth] Moodles/Encumbered missing: exhaustion and load stay neutral.");
        });
    }

    static ResourceLocation rl(String path) {
        return new ResourceLocation(ID, path);
    }

    static double attr(Player player, RegistryObject<Attribute> attribute) {
        AttributeInstance instance = player.getAttribute(attribute.get());
        return instance == null ? 0 : instance.getValue();
    }

    private static RegistryObject<Attribute> attribute(String name, double max) {
        return ATTRIBUTES.register(name, () -> new RangedAttribute("attribute.name." + ID + "." + name, 0, 0, max).setSyncable(true));
    }
}
