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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.git.MultiBaseLocalDiskRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Test;

@UseLocalDisk
@NoHttpd
public class CachedRefDbIT extends AbstractDaemonTest {
  @Inject(optional = true)
  @Named("LocalDiskRepositoryManager")
  private GitRepositoryManager localGitRepositoryManager;

  @Inject private GitRepositoryManager gitRepoManager;

  @Inject private RefByNameCacheWrapper refByNameCacheWrapper;

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibDbModule")
  @GerritConfig(
      name = "gerrit.installModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibSysModule")
  public void shouldBeAbleToInstallCachedGitRepoManager() {
    assertThat(gitRepoManager).isInstanceOf(CachedGitRepositoryManager.class);
    assertThat(((CachedGitRepositoryManager) gitRepoManager).getRepoManager().getClass())
        .isEqualTo(LocalDiskRepositoryManager.class);
    assertThat(refByNameCacheWrapper.cache()).isInstanceOf(RefByNameCacheImpl.class);
  }

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibDbModule")
  @GerritConfig(
      name = "gerrit.installModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibSysModule")
  @GerritConfig(name = "repository.r1.basePath", value = "/tmp/git1")
  public void shouldMultiBaseRepoManagerBeUsedWhenConfigured() {
    assertThat(gitRepoManager).isInstanceOf(CachedGitRepositoryManager.class);
    assertThat(((CachedGitRepositoryManager) gitRepoManager).getRepoManager())
        .isInstanceOf(MultiBaseLocalDiskRepositoryManager.class);
    assertThat(refByNameCacheWrapper.cache()).isInstanceOf(RefByNameCacheImpl.class);
  }

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibModule")
  @GerritConfig(
      name = "gerrit.installModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibSysModule")
  public void shouldBeAbleToInstallCachedGitRepoManagerAsNamedBinding() {
    assertThat(localGitRepositoryManager).isNotNull();
    assertThat(localGitRepositoryManager).isInstanceOf(CachedGitRepositoryManager.class);
  }

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibModule")
  @GerritConfig(
      name = "gerrit.installModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibSysModule")
  public void shouldResolveNewRefsUpdatesByPrefix() throws Exception {
    String refNamePrefix = RefNames.REFS_HEADS + "myprefix/";
    String refNameOne = refNamePrefix + "one";
    String refNameTwo = refNamePrefix + "two";
    try (Repository repo = localGitRepositoryManager.openRepository(project)) {
      ObjectId headObjectId = repo.exactRef("HEAD").getObjectId();
      assertThat(forcedUpdateRef(repo, refNameOne, headObjectId)).isEqualTo(RefUpdate.Result.NEW);
      assertThat(forcedUpdateRef(repo, refNameTwo, headObjectId)).isEqualTo(RefUpdate.Result.NEW);

      List<Ref> refsByPrefix = repo.getRefDatabase().getRefsByPrefix(refNamePrefix);
      List<String> changeRefNames = refsByPrefix.stream().map(Ref::getName).toList();
      assertThat(changeRefNames).containsExactly(refNameOne, refNameTwo);
      for (Ref ref : refsByPrefix) {
        assertThat(ref).isEqualTo(repo.exactRef(ref.getName()));
      }
    }
  }

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibModule")
  @GerritConfig(
      name = "gerrit.installModule",
      value = "com.gerritforge.gerrit.plugins.cachedrefdb.LibSysModule")
  public void shouldResolveNewBatchRefsUpdatesByPrefix() throws Exception {
    String refNamePrefix = RefNames.REFS_HEADS + "myprefix/";
    String refNameOne = refNamePrefix + "one";
    String refNameTwo = refNamePrefix + "two";
    try (Repository repo = localGitRepositoryManager.openRepository(project)) {
      ObjectId headObjectId = repo.exactRef("HEAD").getObjectId();
      List<ReceiveCommand.Result> batchRefUpdateResults =
          forcedBatchUpdateRef(repo, refNameOne, headObjectId, refNameTwo, headObjectId);
      assertThat(batchRefUpdateResults)
          .containsExactly(ReceiveCommand.Result.OK, ReceiveCommand.Result.OK);

      List<Ref> refsByPrefix = repo.getRefDatabase().getRefsByPrefix(refNamePrefix);
      List<String> changeRefNames = refsByPrefix.stream().map(Ref::getName).toList();
      assertThat(changeRefNames).containsExactly(refNameOne, refNameTwo);
      for (Ref ref : refsByPrefix) {
        assertThat(ref).isEqualTo(repo.exactRef(ref.getName()));
      }
    }
  }

  private RefUpdate.Result forcedUpdateRef(Repository repo, String ref, ObjectId objectId)
      throws IOException {
    RefUpdate refUpdate = repo.updateRef(ref);
    refUpdate.setForceUpdate(true);
    refUpdate.setNewObjectId(objectId);
    return refUpdate.update();
  }

  private List<ReceiveCommand.Result> forcedBatchUpdateRef(Repository repo, Object... refsAndObjs)
      throws IOException {
    BatchRefUpdate batchRefUpdate = repo.getRefDatabase().newBatchUpdate();
    for (int i = 0; i < refsAndObjs.length; i += 2) {
      batchRefUpdate.setForceRefLog(true);
      batchRefUpdate.addCommand(
          new ReceiveCommand(
              ObjectId.zeroId(), (ObjectId) refsAndObjs[i + 1], (String) refsAndObjs[i]));
    }
    try (RevWalk revWalk = new RevWalk(repo)) {
      batchRefUpdate.execute(revWalk, NullProgressMonitor.INSTANCE);
    }

    return batchRefUpdate.getCommands().stream().map(ReceiveCommand::getResult).toList();
  }
}
