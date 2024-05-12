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

import com.google.common.base.CharMatcher;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.git.DelegateRepository;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.events.ListenerList;
import org.eclipse.jgit.events.RepositoryEvent;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RebaseTodoLine;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FS;

class CachedRefRepository extends DelegateRepository {
  interface Factory {
    CachedRefRepository create(String projectName, Repository repo);
  }

  @Singleton
  static class CachingFactory {
    private final Factory repoWrapperFactory;
    private final Map<String, CachedRefRepository> repos;

    @Inject
    CachingFactory(Factory repoWrapperFactory) {
      this.repoWrapperFactory = repoWrapperFactory;
      this.repos = new ConcurrentHashMap<>();
    }

    CachedRefRepository create(String projectName, Repository repo) {
      return repos.computeIfAbsent(projectName, name -> repoWrapperFactory.create(name, repo));
    }
  }

  private final String projectName;
  private final CachedRefDatabase refDb;
  private final RefUpdateWithCacheUpdate.Factory updateFactory;
  private final RefRenameWithCacheUpdate.Factory renameFactory;

  private static final CharMatcher REVISION_CHARS = CharMatcher.anyOf("^~:@");

  @Inject
  CachedRefRepository(
      CachedRefDatabase.Factory refDbFactory,
      RefUpdateWithCacheUpdate.Factory updateFactory,
      RefRenameWithCacheUpdate.Factory renameFactory,
      @Assisted String projectName,
      @Assisted Repository repo) {
    super(repo);
    this.projectName = projectName;
    this.updateFactory = updateFactory;
    this.renameFactory = renameFactory;
    this.refDb = refDbFactory.create(this, repo.getRefDatabase());
  }

  @Override
  public RefDatabase getRefDatabase() {
    return refDb;
  }

  @Override
  public ListenerList getListenerList() {
    return delegate.getListenerList();
  }

  @Override
  public void fireEvent(RepositoryEvent<?> event) {
    delegate.fireEvent(event);
  }

  @Override
  public File getDirectory() {
    return delegate.getDirectory();
  }

  @Override
  public ObjectInserter newObjectInserter() {
    return delegate.newObjectInserter();
  }

  @Override
  public ObjectReader newObjectReader() {
    return delegate.newObjectReader();
  }

  @Override
  public FS getFS() {
    return delegate.getFS();
  }

  @Override
  public ObjectLoader open(AnyObjectId objectId) throws MissingObjectException, IOException {
    return delegate.open(objectId);
  }

  @Override
  public ObjectLoader open(AnyObjectId objectId, int typeHint)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    return delegate.open(objectId, typeHint);
  }

  @Override
  public RefUpdate updateRef(String ref) throws IOException {
    return updateFactory.create(refDb, this, delegate.updateRef(ref));
  }

  @Override
  public RefUpdate updateRef(String ref, boolean detach) throws IOException {
    return updateFactory.create(refDb, this, delegate.updateRef(ref, detach));
  }

  @Override
  public RefRename renameRef(String fromRef, String toRef) throws IOException {
    return renameFactory.create(
        this, delegate.renameRef(fromRef, toRef), updateRef(fromRef), updateRef(toRef));
  }

  @Override
  public ObjectId resolve(String revstr)
      throws AmbiguousObjectException,
          IncorrectObjectTypeException,
          RevisionSyntaxException,
          IOException {
    if (isCacheableReference(revstr)) {
      Ref ref = refDb.exactRef(revstr);
      if (ref != null) {
        return ref.getLeaf().getObjectId();
      }
    }

    return delegate.resolve(revstr);
  }

  @Override
  public String simplify(String revstr) throws AmbiguousObjectException, IOException {
    return delegate.simplify(revstr);
  }

  @Override
  public void incrementOpen() {
    delegate.incrementOpen();
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public String getFullBranch() throws IOException {
    return delegate.getFullBranch();
  }

  @Override
  public String getBranch() throws IOException {
    return delegate.getBranch();
  }

  @Override
  public Set<ObjectId> getAdditionalHaves() throws IOException {
    return delegate.getAdditionalHaves();
  }

  @Deprecated
  @Override
  public Map<String, Ref> getAllRefs() {
    return delegate.getAllRefs();
  }

  @Deprecated
  @Override
  public Map<String, Ref> getTags() {
    return delegate.getTags();
  }

  @Override
  public Map<AnyObjectId, Set<Ref>> getAllRefsByPeeledObjectId() throws IOException {
    return delegate.getAllRefsByPeeledObjectId();
  }

  @Override
  public File getIndexFile() throws NoWorkTreeException {
    return delegate.getIndexFile();
  }

  @Override
  public RevCommit parseCommit(AnyObjectId id)
      throws IncorrectObjectTypeException, IOException, MissingObjectException {
    return delegate.parseCommit(id);
  }

  @Override
  public DirCache readDirCache() throws NoWorkTreeException, CorruptObjectException, IOException {
    return delegate.readDirCache();
  }

  @Override
  public DirCache lockDirCache() throws NoWorkTreeException, CorruptObjectException, IOException {
    return delegate.lockDirCache();
  }

  @Override
  public RepositoryState getRepositoryState() {
    return delegate.getRepositoryState();
  }

  @Override
  public boolean isBare() {
    return delegate.isBare();
  }

  @Override
  public File getWorkTree() throws NoWorkTreeException {
    return delegate.getWorkTree();
  }

  @Override
  public String shortenRemoteBranchName(String refName) {
    return delegate.shortenRemoteBranchName(refName);
  }

  @Override
  public String getRemoteName(String refName) {
    return delegate.getRemoteName(refName);
  }

  @Override
  public String getGitwebDescription() throws IOException {
    return delegate.getGitwebDescription();
  }

  @Override
  public void setGitwebDescription(String description) throws IOException {
    delegate.setGitwebDescription(description);
  }

  @Override
  public String readMergeCommitMsg() throws IOException, NoWorkTreeException {
    return delegate.readMergeCommitMsg();
  }

  @Override
  public void writeMergeCommitMsg(String msg) throws IOException {
    delegate.writeMergeCommitMsg(msg);
  }

  @Override
  public String readCommitEditMsg() throws IOException, NoWorkTreeException {
    return delegate.readCommitEditMsg();
  }

  @Override
  public void writeCommitEditMsg(String msg) throws IOException {
    delegate.writeCommitEditMsg(msg);
  }

  @Override
  public List<ObjectId> readMergeHeads() throws IOException, NoWorkTreeException {
    return delegate.readMergeHeads();
  }

  @Override
  public void writeMergeHeads(List<? extends ObjectId> heads) throws IOException {
    delegate.writeMergeHeads(heads);
  }

  @Override
  public ObjectId readCherryPickHead() throws IOException, NoWorkTreeException {
    return delegate.readCherryPickHead();
  }

  @Override
  public ObjectId readRevertHead() throws IOException, NoWorkTreeException {
    return delegate.readRevertHead();
  }

  @Override
  public void writeCherryPickHead(ObjectId head) throws IOException {
    delegate.writeCherryPickHead(head);
  }

  @Override
  public void writeRevertHead(ObjectId head) throws IOException {
    delegate.writeRevertHead(head);
  }

  @Override
  public void writeOrigHead(ObjectId head) throws IOException {
    delegate.writeOrigHead(head);
  }

  @Override
  public ObjectId readOrigHead() throws IOException, NoWorkTreeException {
    return delegate.readOrigHead();
  }

  @Override
  public String readSquashCommitMsg() throws IOException {
    return delegate.readSquashCommitMsg();
  }

  @Override
  public void writeSquashCommitMsg(String msg) throws IOException {
    delegate.writeSquashCommitMsg(msg);
  }

  @Override
  public List<RebaseTodoLine> readRebaseTodo(String path, boolean includeComments)
      throws IOException {
    return delegate.readRebaseTodo(path, includeComments);
  }

  @Override
  public void writeRebaseTodoFile(String path, List<RebaseTodoLine> steps, boolean append)
      throws IOException {
    delegate.writeRebaseTodoFile(path, steps, append);
  }

  @Override
  public Set<String> getRemoteNames() {
    return delegate.getRemoteNames();
  }

  @Override
  public void autoGC(ProgressMonitor monitor) {
    delegate.autoGC(monitor);
  }

  @Override
  public void create() throws IOException {
    delegate.create();
  }

  public String getProjectName() {
    return projectName;
  }

  private boolean isCacheableReference(String ref) {
    return ref.startsWith(RefNames.REFS) && REVISION_CHARS.matchesNoneOf(ref);
  }
}
