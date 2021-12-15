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

package com.googlesource.gerrit.plugins.gerritcachedrefdb;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import org.junit.Test;

@UseLocalDisk
@NoHttpd
public class GerritCachedRefDbIT extends AbstractDaemonTest {
  @Inject private GitRepositoryManager gitRepoManager;

  @Inject private RefByNameCacheWrapper refByNameCacheWrapper;

  @Test
  @GerritConfig(
      name = "gerrit.installDbModule",
      value = "com.googlesource.gerrit.plugins.gerritcachedrefdb.LibDbModule")
  @GerritConfig(
      name = "gerrit.installModule",
      value = "com.googlesource.gerrit.plugins.gerritcachedrefdb.LibSysModule")
  public void shouldBeAbleToInstallGerritCachedGitRepoManager() {
    assertThat(gitRepoManager).isInstanceOf(GerritCachedGitRepositoryManager.class);
    assertThat(refByNameCacheWrapper.cache()).isInstanceOf(RefByNameGerritCache.class);
  }
}
