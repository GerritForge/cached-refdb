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

import com.google.common.collect.ListMultimap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

interface RefByNameCache {
  Ref computeIfAbsent(String identifier, String ref, Callable<? extends Optional<Ref>> loader);

  void evict(String identifier, String ref);

  List<Ref> all(String identifier);

  boolean hasRefs(String identifier);

  ListMultimap<ObjectId, Ref> refsByObjectId(String identifier);
}
