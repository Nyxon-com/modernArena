//made with love by nyxon

package com.modern.arena.milestone;

import java.util.List;

public final class MilestoneDefinition {

  public final int threshold;
  public final String message;
  public final String sound;
  public final List<String> commands;

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
