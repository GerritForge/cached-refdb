// Copyright (C) 2025 GerritForge, Inc.
//
// Licensed under the BSL 1.1 (the "License");
// you may not use this file except in compliance with the License.
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.gerritforge.gerrit.plugins.cachedrefdb;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushCertificate;

class RefUpdateWithCacheUpdate extends RefUpdate {
  interface Factory {
    RefUpdateWithCacheUpdate create(
        RefDatabase refDb, CachedRefRepository repo, RefUpdate delegate);
  }

  private static final String NOT_SUPPORTED_MSG = "Should never be called";
  private static final EnumSet<Result> SUCCESSFUL_UPDATES =
      EnumSet.of(Result.NEW, Result.FORCED, Result.FAST_FORWARD, Result.RENAMED);

  private final RefDatabaseCacheWrapper refsCache;
  private final RefDatabase refDb;
  private final RefDatabase delegateRefDb;
  private final CachedRefRepository repo;
  private final RefUpdate delegate;

  @Inject
  RefUpdateWithCacheUpdate(
      RefDatabaseCacheWrapper refsCache,
      @Assisted RefDatabase refDb,
      @Assisted CachedRefRepository repo,
      @Assisted RefUpdate delegate) {
    super(delegate.getRef());
    this.refsCache = refsCache;
    this.refDb = refDb;
    this.repo = repo;
    this.delegate = delegate;
    this.delegateRefDb = repo.getCachedRefDatabase().getDelegate();
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public Ref getRef() {
    return delegate.getRef();
  }

  @Override
  public ObjectId getNewObjectId() {
    return delegate.getNewObjectId();
  }

  @Override
  public void setDetachingSymbolicRef() {
    delegate.setDetachingSymbolicRef();
  }

  @Override
  public boolean isDetachingSymbolicRef() {
    return delegate.isDetachingSymbolicRef();
  }

  @Override
  public void setNewObjectId(AnyObjectId id) {
    delegate.setNewObjectId(id);
  }

  @Override
  public ObjectId getExpectedOldObjectId() {
    return delegate.getExpectedOldObjectId();
  }

  @Override
  public void setExpectedOldObjectId(AnyObjectId id) {
    delegate.setExpectedOldObjectId(id);
  }

  @Override
  public boolean isForceUpdate() {
    return delegate.isForceUpdate();
  }

  @Override
  public void setForceUpdate(boolean b) {
    delegate.setForceUpdate(b);
  }

  @Override
  public PersonIdent getRefLogIdent() {
    return delegate.getRefLogIdent();
  }

  @Override
  public void setRefLogIdent(PersonIdent pi) {
    delegate.setRefLogIdent(pi);
  }

  @Override
  public String getRefLogMessage() {
    return delegate.getRefLogMessage();
  }

  @Override
  public void setRefLogMessage(String msg, boolean appendStatus) {
    delegate.setRefLogMessage(msg, appendStatus);
  }

  @Override
  public void disableRefLog() {
    delegate.disableRefLog();
  }

  @Override
  public void setForceRefLog(boolean force) {
    delegate.setForceRefLog(force);
  }

  @Override
  public ObjectId getOldObjectId() {
    return delegate.getOldObjectId();
  }

  @Override
  public void setPushCertificate(PushCertificate cert) {
    delegate.setPushCertificate(cert);
  }

  @Override
  public Result getResult() {
    return delegate.getResult();
  }

  @Override
  public Result forceUpdate() throws IOException {
    return refreshCachesOnSuccessfulUpdate(delegate.forceUpdate());
  }

  @Override
  public Result update() throws IOException {
    return refreshCachesOnSuccessfulUpdate(delegate.update());
  }

  @Override
  public Result update(RevWalk walk) throws IOException {
    return refreshCachesOnSuccessfulUpdate(delegate.update(walk));
  }

  @Override
  public Result delete() throws IOException {
    return evictCacheOnSuccessfulUpdate(delegate.delete());
  }

  @Override
  public Result delete(RevWalk walk) throws IOException {
    return evictCacheOnSuccessfulUpdate(delegate.delete(walk));
  }

  @Override
  public Result link(String target) throws IOException {
    return refreshCachesOnSuccessfulUpdate(delegate.link(target));
  }

  @Override
  public void setCheckConflicting(boolean check) {
    delegate.setCheckConflicting(check);
  }

  @Override
  protected RefDatabase getRefDatabase() {
    return refDb;
  }

  @Override
  protected Repository getRepository() {
    return repo;
  }

  @Override
  protected boolean tryLock(boolean deref) throws IOException {
    throw new UnsupportedOperationException(NOT_SUPPORTED_MSG);
  }

  @Override
  protected void unlock() {
    throw new UnsupportedOperationException(NOT_SUPPORTED_MSG);
  }

  @Override
  protected Result doUpdate(Result desiredResult) throws IOException {
    throw new UnsupportedOperationException(NOT_SUPPORTED_MSG);
  }

  @Override
  protected Result doDelete(Result desiredResult) throws IOException {
    throw new UnsupportedOperationException(NOT_SUPPORTED_MSG);
  }

  @Override
  protected Result doLink(String target) throws IOException {
    throw new UnsupportedOperationException(NOT_SUPPORTED_MSG);
  }

  private Result evictCacheOnSuccessfulUpdate(Result r) throws IOException {
    try {
      if (SUCCESSFUL_UPDATES.contains(r)) {
        refsCache.evict(repo.getProjectName(), getName());
        refsCache.evictObjectIdCache(repo.getProjectName(), getOldObjectId());
      }
      return r;
    } catch (ExecutionException e) {
      throw new IOException(e);
    }
  }

  private Result refreshCachesOnSuccessfulUpdate(Result r) throws IOException {
    if (SUCCESSFUL_UPDATES.contains(r)) {
      try {
        refsCache.updateRef(repo.getProjectName(), getName(), delegateRefDb);
        // TODO See how much benefit combining these 2 calls so that we scan the tree only once
        // brings
        refsCache.updateObjectIdCache(repo.getProjectName(), getOldObjectId());
        refsCache.updateObjectIdCache(repo.getProjectName(), getNewObjectId());
      } catch (ExecutionException e) {
        throw new IOException(e);
      }
    }
    return r;
  }
}
