//made with love by nyxon

package com.modern.arena.party;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class Party {

  private final UUID id;
  private UUID leader;
  private final LinkedHashSet<UUID> members = new LinkedHashSet<>();

  public Party(UUID leader) {
    this.id = UUID.randomUUID();
    this.leader = leader;
    this.members.add(leader);
  }

  public UUID id() {
    return this.id;
  }

  public UUID leader() {
    return this.leader;
  }

  public void leader(UUID leader) {
    this.leader = leader;
  }

  public Set<UUID> members() {
    return this.members;
  }

  public int size() {
    return this.members.size();
  }

  public boolean isLeader(UUID uuid) {
    return this.leader.equals(uuid);
  }

  public boolean contains(UUID uuid) {
    return this.members.contains(uuid);
  }
}
