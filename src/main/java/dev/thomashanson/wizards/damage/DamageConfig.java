package dev.thomashanson.wizards.damage;

import java.util.List;
import java.util.Map;

import org.bukkit.Sound;

/**
 * A data-centric record to hold all configurable values related to the
 * damage and death system, loaded from config.yml.
 */
public record DamageConfig(
    long logTimeoutMillis,
    int assistThresholdPercentage,
    long justNowThresholdMillis,
    double meleeRangeThreshold,
    Map<String, SoundConfig> armorHitSounds,
    DeathMessageConfig deathMessages
) {

    public record SoundConfig(Sound sound, float volume, float pitch) {}

    public record DeathMessageConfig(
        Map<String, String> byBlock,
        String defaultBlock,
        Map<String, String> byCause,
        String fall,
        VoidConfig voidMessages,
        PvpConfig pvp,
        MonsterConfig monster,
        BySpellConfig bySpell,
        String defaultMessage
    ) {}
    
    public record VoidConfig(
        String knockedByPlayer,
        String knockedByMonster,
        String fell
    ) {}

    public record PvpConfig(
        MeleeConfig melee,
        String rangedFormat
    ) {}

    public record MeleeConfig(
        List<String> verbs,
        String format
    ) {}
    
    public record MonsterConfig(
        String melee,
        String ranged
    ) {}

    public record BySpellConfig(
        Map<String, String> overrides,
        GenericSpellConfig generic,
        String suicideFormat
    ) {}

    public record GenericSpellConfig(
        List<String> verbs,
        String format
    ) {}
}