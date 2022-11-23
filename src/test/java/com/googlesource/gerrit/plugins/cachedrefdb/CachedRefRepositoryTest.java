// Copyright (C) 2022 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.RefNames;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CachedRefRepositoryTest {
  private static final String PROJECT = "foo";

  @Mock CachedRefDatabase.Factory refDbFactoryMock;
  @Mock RefUpdateWithCacheUpdate.Factory updateFactoryMock;
  @Mock RefRenameWithCacheUpdate.Factory renameFactoryMock;
  @Mock Repository repoMock;
  @Mock CachedRefDatabase cachedRefDbMock;

  CachedRefRepository objectUnderTest;

  @Before
  public void setUp() {
    RefDatabase refDbMock = mock(RefDatabase.class);
    when(repoMock.isBare()).thenReturn(true);
    when(repoMock.getRefDatabase()).thenReturn(refDbMock);
    when(refDbFactoryMock.create(any(), eq(refDbMock))).thenReturn(cachedRefDbMock);

    objectUnderTest =
        new CachedRefRepository(
            refDbFactoryMock, updateFactoryMock, renameFactoryMock, PROJECT, repoMock);
  }

  @Test
  public void shouldResolveRefFromCache() throws Exception {
    String ref = RefNames.REFS_CONFIG;
    objectUnderTest.resolve(ref);

    verify(cachedRefDbMock).exactRef(ref);
  }

  @Test
  public void shouldNotResolveRefFromCache() throws Exception {
    String objectId = ObjectId.zeroId().getName();
    objectUnderTest.resolve(objectId);

    verifyNoInteractions(cachedRefDbMock);
  }
}
