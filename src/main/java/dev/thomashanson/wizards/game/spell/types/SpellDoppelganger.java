package dev.thomashanson.wizards.game.spell.types;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.mojang.authlib.GameProfile;

import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.game.Tickable;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.StatContext;

public class SpellDoppelganger extends Spell implements Tickable {

    private static final Map<UUID, DoppelgangerInstance> ACTIVE_CLONES = new ConcurrentHashMap<>();
    private static final AtomicInteger NPC_ENTITY_ID_COUNTER = new AtomicInteger(Integer.MIN_VALUE / 2);

    public SpellDoppelganger(@NotNull WizardsPlugin plugin, @NotNull String key, @NotNull ConfigurationSection config) {
        super(plugin, key, config);
    }

    @Override
    public boolean cast(Player player, int level) {
        if (ACTIVE_CLONES.containsKey(player.getUniqueId())) {
            return false;
        }

        getWizard(player).ifPresent(wizard -> {
            DoppelgangerInstance instance = new DoppelgangerInstance(this, wizard, level);
            ACTIVE_CLONES.put(player.getUniqueId(), instance);

            long lifespanTicks = (long) getStat("lifespan-ticks", level);
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, (int) lifespanTicks + 60, 0, false, false, false));
            wizard.setManaRegenMultiplier(0F, true);
            player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.0F, 1.0F);
        });

        return true;
    }

    @Override
    public void tick(long gameTick) {
        if (ACTIVE_CLONES.isEmpty()) return;

        Iterator<Map.Entry<UUID, DoppelgangerInstance>> iterator = ACTIVE_CLONES.entrySet().iterator();
        while (iterator.hasNext()) {
            DoppelgangerInstance instance = iterator.next().getValue();
            if (instance.tick(gameTick)) {
                iterator.remove();
            }
        }
    }

    @Override
    public int getTickInterval() {
        return 1; // Needs frequent position updates
    }
    
    @Override
    public void cleanup() {
        ACTIVE_CLONES.values().forEach(DoppelgangerInstance::cleanup);
        ACTIVE_CLONES.clear();
    }
    
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        DoppelgangerInstance instance = ACTIVE_CLONES.remove(event.getPlayer().getUniqueId());
        if (instance != null) {
            instance.cleanup();
        }
    }
    
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        DoppelgangerInstance instance = ACTIVE_CLONES.get(event.getEntity().getUniqueId());
        if (instance != null) {
            instance.cleanup();
            ACTIVE_CLONES.remove(event.getEntity().getUniqueId());
        }
    }

    private static class DoppelgangerInstance {
        final SpellDoppelganger parentSpell;
        final Wizard wizard;
        final Player caster;
        final int entityId;
        final UUID npcUuid;
        final GameProfile gameProfile;
        
        final long lifespanTicks;
        final float manaDrainPerSecond;
        private int ticksLived = 0;

        DoppelgangerInstance(SpellDoppelganger parent, Wizard wizard, int level) {
            this.parentSpell = parent;
            this.wizard = wizard;
            this.caster = wizard.getPlayer();
            this.entityId = NPC_ENTITY_ID_COUNTER.getAndIncrement();
            this.npcUuid = UUID.randomUUID();

            StatContext context = StatContext.of(level);
            this.lifespanTicks = (long) parent.getStat("lifespan-ticks", level);
            this.manaDrainPerSecond = (float) parent.getStat("mana-drain", level);

            this.gameProfile = createGameProfile();
            spawn();
        }

        GameProfile createGameProfile() {
            GameProfile profile = new GameProfile(npcUuid, caster.getName());
            WrappedGameProfile playerProfile = WrappedGameProfile.fromPlayer(caster);
            playerProfile.getProperties().entries().forEach(entry ->
                profile.getProperties().put(entry.getKey(), new com.mojang.authlib.properties.Property(entry.getValue().getName(), entry.getValue().getValue(), entry.getValue().getSignature()))
            );
            return profile;
        }

        /** @return true if this instance should be removed. */
        boolean tick(long gameTick) {
            ticksLived++;
            if (ticksLived > lifespanTicks || !caster.isOnline()) {
                cleanup();
                return true;
            }

            // Mana drain logic (once per second)
            if (gameTick % 20 == 0) {
                if (wizard.getMana() < manaDrainPerSecond) {
                    cleanup();
                    return true;
                }
                wizard.removeMana(manaDrainPerSecond);
            }

            // Position update logic (every tick)
            updatePosition();
            return false;
        }

        void updatePosition() {
            // Location newLoc = caster.getLocation().clone().add(caster.getLocation().getDirection().normalize().multiply(1.5));
            // Packet logic to teleport NPC and update head rotation
        }

        void spawn() {
            // All ProtocolLib logic to send PlayerInfo, NamedEntitySpawn, Metadata, and Equipment packets
        }
        
        void cleanup() {
            // All ProtocolLib logic to send EntityDestroy and PlayerInfoRemove packets
            caster.removePotionEffect(PotionEffectType.INVISIBILITY);
            wizard.revert(); // Restores mana regen
        }
    }
}
