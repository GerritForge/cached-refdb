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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;

interface RefDatabaseCache {
  Ref get(String identifier, String ref, RefDatabase delegate) throws IOException;

  boolean containsKey(String identifier, String ref);

  void put(String identifier, Ref ref) throws IOException;

  void evict(String identifier, String ref) throws ExecutionException;

  List<Ref> allByPrefix(String identifier, String prefix, RefDatabase delegate)
      throws ExecutionException;

  List<Ref> all(String identifier, RefDatabase delegate) throws ExecutionException;

  void renameRef(String identifier, Ref srcRef, Ref destRef) throws ExecutionException;

  void updateRef(String identifier, String refName, RefDatabase delete) throws IOException;
}
