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

import java.util.List;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;

interface RefByNameCache {
  Ref get(String identifier, String ref, RefDatabase delegate);

  boolean containsKey(String identifier, String ref);

  void put(String identifier, Ref ref);

  void evictRefByNameCache(String identifier, String ref);

  List<Ref> allByPrefix(String identifier, String prefix, RefDatabase delegate)
      throws ExecutionException;

  List<Ref> all(String identifier, RefDatabase delegate) throws ExecutionException;

  void updateRefNamesByProjectCache(String identifier, String refName);

  void evictFromRefNamesByProjectCache(String identifier, String refName);
}
