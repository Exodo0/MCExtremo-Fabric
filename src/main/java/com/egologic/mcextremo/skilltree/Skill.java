package com.egologic.mcextremo.skilltree;

import com.egologic.mcextremo.config.ModConfig;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public enum Skill {
    BASE("base", "\u00A7e\u2605 \u00A7e\u00A7lBase", Items.NETHER_STAR, 0, null,
        "\u00A77Desbloquea las ramas del \u00e1rbol", null, 0.0),

    VIDA_T1("vida_1", "\u00A7c\u2764 Vida I", Items.GOLDEN_APPLE, 1, BASE,
        "\u00A7a+2 Vida Maxima", EntityAttributes.GENERIC_MAX_HEALTH, 4.0),
    VIDA_T2("vida_2", "\u00A7c\u2764 Vida II", Items.GOLDEN_APPLE, 2, VIDA_T1,
        "\u00A7a+2 Vida Maxima", EntityAttributes.GENERIC_MAX_HEALTH, 4.0),
    VIDA_T3("vida_3", "\u00A7c\u2764 Vida III", Items.ENCHANTED_GOLDEN_APPLE, 3, VIDA_T2,
        "\u00A7a+2 Vida Maxima", EntityAttributes.GENERIC_MAX_HEALTH, 4.0),
    VIDA_T4("vida_4", "\u00A7c\u2764 Vida IV", Items.ENCHANTED_GOLDEN_APPLE, 4, VIDA_T3,
        "\u00A7a+4 Vida Maxima", EntityAttributes.GENERIC_MAX_HEALTH, 8.0),
    VIDA_T5("vida_5", "\u00A7c\u2764 \u00A7lVida V", Items.TOTEM_OF_UNDYING, 5, VIDA_T4,
        "\u00A7a+4 Vida Maxima", EntityAttributes.GENERIC_MAX_HEALTH, 8.0),

    FUERZA_T1("fuerza_1", "\u00A79\u2694 Fuerza I", Items.IRON_SWORD, 1, BASE,
        "\u00A7a+1.5 Dano", EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.5),
    FUERZA_T2("fuerza_2", "\u00A79\u2694 Fuerza II", Items.DIAMOND_SWORD, 2, FUERZA_T1,
        "\u00A7a+1.5 Dano", EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.5),
    FUERZA_T3("fuerza_3", "\u00A79\u2694 Fuerza III", Items.NETHERITE_SWORD, 3, FUERZA_T2,
        "\u00A7a+1.5 Dano", EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.5),
    FUERZA_T4("fuerza_4", "\u00A79\u2694 Fuerza IV", Items.NETHERITE_SWORD, 4, FUERZA_T3,
        "\u00A7a+2.5 Dano", EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.5),
    FUERZA_T5("fuerza_5", "\u00A79\u2694 \u00A7lFuerza V", Items.NETHERITE_SWORD, 5, FUERZA_T4,
        "\u00A7a+2.5 Dano", EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.5),

    RESISTENCIA_T1("resistencia_1", "\u00A7b\u26E8 Resistencia I", Items.SHIELD, 1, BASE,
        "\u00A7a+2 Armadura", EntityAttributes.GENERIC_ARMOR, 2.0),
    RESISTENCIA_T2("resistencia_2", "\u00A7b\u26E8 Resistencia II", Items.IRON_CHESTPLATE, 2, RESISTENCIA_T1,
        "\u00A7a+2 Armadura", EntityAttributes.GENERIC_ARMOR, 2.0),
    RESISTENCIA_T3("resistencia_3", "\u00A7b\u26E8 Resistencia III", Items.DIAMOND_CHESTPLATE, 3, RESISTENCIA_T2,
        "\u00A7a+2 Armadura", EntityAttributes.GENERIC_ARMOR, 2.0),
    RESISTENCIA_T4("resistencia_4", "\u00A7b\u26E8 Resistencia IV", Items.NETHERITE_CHESTPLATE, 4, RESISTENCIA_T3,
        "\u00A7a+3 Armadura", EntityAttributes.GENERIC_ARMOR, 3.0),
    RESISTENCIA_T5("resistencia_5", "\u00A7b\u26E8 \u00A7lResistencia V", Items.NETHERITE_CHESTPLATE, 5, RESISTENCIA_T4,
        "\u00A7a+3 Armadura", EntityAttributes.GENERIC_ARMOR, 3.0),

    AGILIDAD_T1("agilidad_1", "\u00A7a\u27A4 Agilidad I", Items.RABBIT_FOOT, 1, BASE,
        "\u00A7a+3% Velocidad", EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.003),
    AGILIDAD_T2("agilidad_2", "\u00A7a\u27A4 Agilidad II", Items.FEATHER, 2, AGILIDAD_T1,
        "\u00A7a+3% Velocidad", EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.003),
    AGILIDAD_T3("agilidad_3", "\u00A7a\u27A4 Agilidad III", Items.SUGAR, 3, AGILIDAD_T2,
        "\u00A7a+3% Velocidad", EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.003),
    AGILIDAD_T4("agilidad_4", "\u00A7a\u27A4 Agilidad IV", Items.ENDER_PEARL, 4, AGILIDAD_T3,
        "\u00A7a+3.5% Velocidad", EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0035),
    AGILIDAD_T5("agilidad_5", "\u00A7a\u27A4 \u00A7lAgilidad V", Items.ELYTRA, 5, AGILIDAD_T4,
        "\u00A7a+3.5% Velocidad", EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0035),

    TENACIDAD_T1("tenacidad_1", "\u00A7d\u25C6 Tenacidad I", Items.IRON_INGOT, 1, BASE,
        "\u00A7a+0.5 Dureza de Armadura", EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 0.5),
    TENACIDAD_T2("tenacidad_2", "\u00A7d\u25C6 Tenacidad II", Items.DIAMOND, 2, TENACIDAD_T1,
        "\u00A7a+0.5 Dureza de Armadura", EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 0.5),
    TENACIDAD_T3("tenacidad_3", "\u00A7d\u25C6 Tenacidad III", Items.NETHERITE_SCRAP, 3, TENACIDAD_T2,
        "\u00A7a+0.75 Dureza de Armadura", EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 0.75),
    TENACIDAD_T4("tenacidad_4", "\u00A7d\u25C6 Tenacidad IV", Items.NETHERITE_INGOT, 4, TENACIDAD_T3,
        "\u00A7a+0.75 Dureza de Armadura", EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 0.75),
    TENACIDAD_T5("tenacidad_5", "\u00A7d\u25C6 \u00A7lTenacidad V", Items.ANCIENT_DEBRIS, 5, TENACIDAD_T4,
        "\u00A7a+1 Dureza de Armadura", EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 1.0),

    CAZADOR_T1("cazador_1", "\u00A76\u2691 Cazador I", Items.EXPERIENCE_BOTTLE, 1, BASE,
        "\u00A7a+10% EXP al completar hordas", null, 0.0),
    CAZADOR_T2("cazador_2", "\u00A76\u2691 Cazador II", Items.AMETHYST_SHARD, 2, CAZADOR_T1,
        "\u00A7a+1 fragmento en hordas grandes o superiores", null, 0.0),
    CAZADOR_T3("cazador_3", "\u00A76\u2691 Cazador III", Items.SPYGLASS, 3, CAZADOR_T2,
        "\u00A7aMarca los ultimos 2 enemigos de una horda", null, 0.0),
    CAZADOR_T4("cazador_4", "\u00A76\u2691 Cazador IV", Items.GOLDEN_CARROT, 4, CAZADOR_T3,
        "\u00A7aRegeneracion I por 8s al completar una horda", null, 0.0),
    CAZADOR_T5("cazador_5", "\u00A76\u2691 \u00A7lCazador V", Items.NETHERITE_AXE, 5, CAZADOR_T4,
        "\u00A7aFuerza I por 20s al despertar el jefe del trial", null, 0.0),

    SUPERVIVENCIA_T1("supervivencia_1", "\u00A72\u2726 Supervivencia I", Items.COOKED_BEEF, 1, BASE,
        "\u00A7aRecupera 1 hambre al matar hostiles (10s CD)", null, 0.0),
    SUPERVIVENCIA_T2("supervivencia_2", "\u00A72\u2726 Supervivencia II", Items.IRON_CHESTPLATE, 2, SUPERVIVENCIA_T1,
        "\u00A7aResistencia I por 5s tras un golpe fuerte (35s CD)", null, 0.0),
    SUPERVIVENCIA_T3("supervivencia_3", "\u00A72\u2726 Supervivencia III", Items.FEATHER, 3, SUPERVIVENCIA_T2,
        "\u00A7aCaida Lenta por 6s si caes con poca vida (60s CD)", null, 0.0),
    SUPERVIVENCIA_T4("supervivencia_4", "\u00A72\u2726 Supervivencia IV", Items.GOLDEN_APPLE, 4, SUPERVIVENCIA_T3,
        "\u00A7aAbsorcion I por 10s bajo 30% HP (90s CD)", null, 0.0),
    SUPERVIVENCIA_T5("supervivencia_5", "\u00A72\u2726 \u00A7lSupervivencia V", Items.TOTEM_OF_UNDYING, 5, SUPERVIVENCIA_T4,
        "\u00A7aBuff defensivo al ganar el trial", null, 0.0);

    private final String id;
    private final String displayName;
    private final Item icon;
    private final int tier;
    private final Skill prerequisite;
    private final String description;
    private final EntityAttribute attribute;
    private final double attributeValue;

    Skill(String id, String displayName, Item icon, int tier, Skill prerequisite,
          String description, EntityAttribute attribute, double attributeValue) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.tier = tier;
        this.prerequisite = prerequisite;
        this.description = description;
        this.attribute = attribute;
        this.attributeValue = attributeValue;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Item getIcon() { return icon; }
    public int getTier() { return tier; }
    public Skill getPrerequisite() { return prerequisite; }
    public String getDescription() { return description; }
    public EntityAttribute getAttribute() { return attribute; }
    public double getAttributeValue() { return attributeValue; }

    public boolean isRoot() { return this == BASE; }

    public int getCost() {
        ModConfig.SkillTree config = ModConfig.get().skillTree;
        switch (tier) {
            case 0: return 0;
            case 1: return config.costoTier1;
            case 2: return config.costoTier2;
            case 3: return config.costoTier3;
            case 4: return config.costoTier4;
            case 5: return config.costoTier5;
            default: return 999;
        }
    }

    public static Skill fromId(String id) {
        for (Skill skill : values()) {
            if (skill.id.equals(id)) return skill;
        }
        return null;
    }
}
