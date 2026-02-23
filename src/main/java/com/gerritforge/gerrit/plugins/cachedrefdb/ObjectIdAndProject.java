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
public record ObjectIdAndProject(ObjectId objectId, int projectIndex) {
  private static final ConcurrentHashMap<String, Integer> projectNameToIndex =
      new ConcurrentHashMap<>();
  private static final AtomicInteger nextProjectIndex = new AtomicInteger(0);

  public static ObjectIdAndProject create(ObjectId objectId, String projectName) {
    int index =
        projectNameToIndex.computeIfAbsent(projectName, name -> nextProjectIndex.getAndIncrement());
    return new ObjectIdAndProject(objectId, index);
  }

  public ObjectId getObjectId() {
    return objectId;
  }
}
