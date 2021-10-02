package dev.thomashanson.wizards.game.spell;

import org.bukkit.Material;

public enum WandElement {

    DARK (Material.STICK),
    EARTH (Material.STONE_HOE),
    FIRE (Material.GOLDEN_HOE),
    ICE (Material.DIAMOND_HOE),
    LIFE (Material.WOODEN_HOE),
    MANA (Material.IRON_HOE);

    WandElement(Material material) {
        this.material = material;
    }

    private final Material material;

    public Material getMaterial() {
        return material;
    }
}