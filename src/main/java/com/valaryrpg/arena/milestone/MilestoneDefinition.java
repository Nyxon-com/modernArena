package com.valaryrpg.arena.milestone;

import java.util.List;

/** Soglia milestone cachata da modules/milestones.yml. */
public final class MilestoneDefinition {

    public final int threshold;
    public final String message;
    public final String sound;
    public final List<String> commands;
    /** Se non vuoto, ne viene eseguito uno a caso (oltre a commands). */
    public final List<String> randomCommands;

    public MilestoneDefinition(
            int threshold,
            String message,
            String sound,
            List<String> commands,
            List<String> randomCommands
    ) {
        this.threshold = threshold;
        this.message = message;
        this.sound = sound;
        this.commands = List.copyOf(commands);
        this.randomCommands = List.copyOf(randomCommands);
    }
}
