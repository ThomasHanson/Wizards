package dev.thomashanson.wizards.commands.give;

import java.util.Collection;
import java.util.stream.Stream;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.thomashanson.wizards.WizardsPlugin;
import dev.thomashanson.wizards.commands.WizardsCommand;
import dev.thomashanson.wizards.game.Wizard;
import dev.thomashanson.wizards.game.Wizards;
import dev.thomashanson.wizards.game.spell.Spell;
import dev.thomashanson.wizards.game.spell.SpellManager;

public class GiveSpellCommand {

    private final WizardsCommand command;

    public GiveSpellCommand(WizardsCommand command) {
        this.command = command;
    }

    public Argument<String> getCommand(WizardsPlugin plugin) {
        return new LiteralArgument("spell")
            // The list of spells is now fetched dynamically from the SpellManager
            .then(new MultiLiteralArgument("spell_key", getAllSpellKeys(plugin))
                .then(new StringArgument("level")
                    .executes((sender, args) -> {

                        Player target = (Player) args.get("player");
                        String spellKey = (String) args.get("spell_key");
                        String levelArg = (String) args.get("level");

                        command.executeAction(plugin, target, (wizard, game) -> {
                            // The execution logic is now cleaner
                            giveSpell(sender, target, wizard, game, spellKey, levelArg);
                        });
                    })
                )
            );
    }

    
    /**
     * The main dispatcher for the command.
     */
    private void giveSpell(CommandSender sender, Player target, Wizard wizard, Wizards game, String spellKey, String levelArg) {
        SpellManager spellManager = game.getPlugin().getSpellManager();

        if (spellKey.equalsIgnoreCase("all")) {
            // Iterate through all loaded spells from the SpellManager
            int level = -1;
            Collection<Spell> allSpells = spellManager.getAllSpells().values();

            for (Spell spell : allSpells) {
                level = spell.getMaxLevel();
                giveSingleSpell(target, wizard, game, spell, level);
            }

            sender.sendMessage("Gave all spells at level " + level + " to " + target.getName());

        } else {
            // Handle a specific spell
            Spell spell = spellManager.getSpell(spellKey);

            if (spell == null) {
                sender.sendMessage("Error: The spell '" + spellKey + "' does not exist.");
                return;
            }

            int level = parseSpellLevel(levelArg, wizard, spell, game);
            giveSingleSpell(target, wizard, game, spell, level);
            sender.sendMessage("Gave " + spell.getName() + " at level " + level + " to " + target.getName());
        }
    }

    /**
     * Gives a single spell to a wizard up to the specified level.
     */
    private void giveSingleSpell(Player target, Wizard wizard, Wizards game, Spell spell, int level) {
        int currentLevel = wizard.getLevel(spell.getKey());

        if (level > currentLevel) {
            int levelsToGive = level - currentLevel;
            for (int i = 0; i < levelsToGive; i++) {
                // The learnSpell method in the main game class should be used
                game.learnSpell(target, spell);
            }
        }
    }

    private int parseSpellLevel(String levelArg, Wizard wizard, Spell spell, Wizards game) {
        if (levelArg.equalsIgnoreCase("max")) {

            // If the command was for "all" spells, 'spell' will be null.
            // Return a high number to ensure every spell gets maxed out.
            if (spell == null) {
                return 99;
            }

            // If it's for a specific spell, use the game's helper method.
            // The game.getMaxLevel method already correctly takes a Spell object.
            return game.getMaxLevel(wizard.getPlayer(), spell);

        } else {
            // This part remains the same.
            try {
                return Integer.parseInt(levelArg);
            } catch (NumberFormatException e) {
                // Return a safe default if the input is not a valid number.
                return 1;
            }
        }
    }

    /**
     * Gets all registered spell keys for the command auto-completion.
     */
    private String[] getAllSpellKeys(WizardsPlugin plugin) {
        return Stream.concat(
            Stream.of("all"),
            plugin.getSpellManager().getAllSpells().keySet().stream().map(String::toLowerCase)
        ).toArray(String[]::new);
    }
}
