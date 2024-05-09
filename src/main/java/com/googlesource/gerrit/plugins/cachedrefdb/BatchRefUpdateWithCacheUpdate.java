// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.cachedrefdb;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushCertificate;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.time.ProposedTimestamp;

class BatchRefUpdateWithCacheUpdate extends BatchRefUpdate {
  interface Factory {
    BatchRefUpdateWithCacheUpdate create(CachedRefRepository repo, BatchRefUpdate delegate);
  }

  private final CachedRefRepository repo;
  private final RefByNameCacheWrapper refsCache;
  private final BatchRefUpdate delegate;

  @Inject
  BatchRefUpdateWithCacheUpdate(
      RefByNameCacheWrapper refsCache,
      @Assisted CachedRefRepository repo,
      @Assisted BatchRefUpdate delegate) {
    super(repo.getRefDatabase());
    this.refsCache = refsCache;
    this.repo = repo;
    this.delegate = delegate;
  }

  @Override
  public boolean isAllowNonFastForwards() {
    return delegate.isAllowNonFastForwards();
  }

  @Override
  public BatchRefUpdate setAllowNonFastForwards(boolean allow) {
    delegate.setAllowNonFastForwards(allow);
    return this;
  }

  @Override
  public PersonIdent getRefLogIdent() {
    return delegate.getRefLogIdent();
  }

  @Override
  public BatchRefUpdate setRefLogIdent(PersonIdent pi) {
    delegate.setRefLogIdent(pi);
    return this;
  }

  @Override
  public String getRefLogMessage() {
    return delegate.getRefLogMessage();
  }

  @Override
  public boolean isRefLogIncludingResult() {
    return delegate.isRefLogIncludingResult();
  }

  @Override
  public BatchRefUpdate setRefLogMessage(String msg, boolean appendStatus) {
    delegate.setRefLogMessage(msg, appendStatus);
    return this;
  }

  @Override
  public BatchRefUpdate disableRefLog() {
    delegate.disableRefLog();
    return this;
  }

  @Override
  public BatchRefUpdate setForceRefLog(boolean force) {
    delegate.setForceRefLog(force);
    return this;
  }

  @Override
  public boolean isRefLogDisabled() {
    return delegate.isRefLogDisabled();
  }

  @Override
  public BatchRefUpdate setAtomic(boolean atomic) {
    delegate.setAtomic(atomic);
    return this;
  }

  @Override
  public boolean isAtomic() {
    return delegate.isAtomic();
  }

  @Override
  public void setPushCertificate(PushCertificate cert) {
    delegate.setPushCertificate(cert);
  }

  @Override
  public List<ReceiveCommand> getCommands() {
    return delegate.getCommands();
  }

  @Override
  public BatchRefUpdate addCommand(ReceiveCommand cmd) {
    delegate.addCommand(cmd);
    return this;
  }

  @Override
  public BatchRefUpdate addCommand(ReceiveCommand... cmd) {
    delegate.addCommand(cmd);
    return this;
  }

  @Override
  public BatchRefUpdate addCommand(Collection<ReceiveCommand> cmd) {
    delegate.addCommand(cmd);
    return this;
  }

  @Override
  public List<String> getPushOptions() {
    return delegate.getPushOptions();
  }

  @Override
  public List<ProposedTimestamp> getProposedTimestamps() {
    return delegate.getProposedTimestamps();
  }

  @Override
  public BatchRefUpdate addProposedTimestamp(ProposedTimestamp ts) {
    delegate.addProposedTimestamp(ts);
    return this;
  }

  @Override
  public void execute(RevWalk walk, ProgressMonitor monitor, List<String> options)
      throws IOException {
    delegate.execute(walk, monitor, options);
    evictCacheAndReload();
  }

  @Override
  public void execute(RevWalk walk, ProgressMonitor monitor) throws IOException {
    delegate.execute(walk, monitor);
    evictCacheAndReload();
  }

  private void evictCacheAndReload() {

    delegate
        .getCommands()
        .forEach(
            cmd -> {
              if (cmd.getResult() == ReceiveCommand.Result.OK) {
                String projectName = repo.getProjectName();
                String refName = cmd.getRefName();

                refsCache.evict(projectName, refName);
                try {
                  Ref updatedRef = repo.exactRef(refName);
                  refsCache.computeIfAbsent(projectName, refName, () -> Optional.of(updatedRef));
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }

              }
            });
  }
}
