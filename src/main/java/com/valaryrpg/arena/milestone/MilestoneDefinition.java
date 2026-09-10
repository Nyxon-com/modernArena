package com.valaryrpg.arena.milestone;

import java.util.List;

/** Soglia milestone cachata da modules/milestones.yml. */
public final class MilestoneDefinition {

    public final int threshold;
    public final String message;
    public final String sound;
    public final List<String> commands;

    public MilestoneDefinition(int threshold, String message, String sound, List<String> commands) {
        this.threshold = threshold;
        this.message = message;
        this.sound = sound;
        this.commands = List.copyOf(commands);
    }
}
