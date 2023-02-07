// Copyright (C) 2023 The Android Open Source Project
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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.json.OutputFormat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.Assert;
import org.junit.Test;

public class RefByNameCacheIT extends AbstractDaemonTest {
  private static final Gson GSON = OutputFormat.JSON.newGson();

  @Test
  public void testLifetimeOfRefCacheInHttpRequest() throws Exception {
    BranchInfo branchBeforeUpdate =
        getBranchInfo(adminRestSession.get("/projects/" + project.get() + "/branches/master"));
    merge(createChange());
    BranchInfo branchAfterUpdate =
        getBranchInfo(adminRestSession.get("/projects/" + project.get() + "/branches/master"));

    Assert.assertNotEquals(branchAfterUpdate.revision, branchBeforeUpdate.revision);
  }

  public BranchInfo getBranchInfo(RestResponse res) throws Exception {
    res.assertOK();
    return GSON.fromJson(res.getReader(), new TypeToken<BranchInfo>() {}.getType());
  }
}
