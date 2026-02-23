package com.gerritforge.gerrit.plugins.cachedrefdb;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.lib.ObjectId;

/**
 * A composite cache key combining an ObjectId with a compact project index.
 *
 * <p>The project index is a 4-byte integer that maps to a project name via a shared in-memory
 * registry, avoiding repeated storage of the full project name string in every key.
 */
public final class ObjectIdAndProject {
  private static final ConcurrentHashMap<String, Integer> projectNameToIndex = new ConcurrentHashMap<>();
  private static final AtomicInteger nextIndex = new AtomicInteger(0);

  private final ObjectId objectId;
  private final int projectIndex;

  private ObjectIdAndProject(ObjectId objectId, int projectIndex) {
    this.objectId = objectId;
    this.projectIndex = projectIndex;
  }

  public static ObjectIdAndProject create(ObjectId objectId, String projectName) {
    int index =
        projectNameToIndex.computeIfAbsent(
            projectName,
            name -> nextIndex.getAndIncrement());
    return new ObjectIdAndProject(objectId, index);
  }

  public ObjectId getObjectId() {
    return objectId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ObjectIdAndProject)) return false;
    ObjectIdAndProject other = (ObjectIdAndProject) o;
    return projectIndex == other.projectIndex && objectId.equals(other.objectId);
  }

  @Override
  public int hashCode() {
    return 31 * objectId.hashCode() + projectIndex;
  }

  @Override
  public String toString() {
    return objectId.name() + ":" + projectIndex;
  }
}
