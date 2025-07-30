package net.caffeinemc.mods.lithium.common.entity;

public interface EquipmentInfo {

    boolean lithium$shouldTickEnchantments();

    boolean lithium$hasUnsentEquipmentChanges();

    void lithium$onEquipmentChangesSent();
}
