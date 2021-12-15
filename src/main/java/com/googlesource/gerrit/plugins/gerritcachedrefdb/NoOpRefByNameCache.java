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

import com.google.common.flogger.FluentLogger;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.eclipse.jgit.lib.Ref;

class NoOpRefByNameCache implements RefByNameCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public Ref computeIfAbsent(
      String identifier, String ref, Callable<? extends Optional<Ref>> loader) {
    try {
      return loader.call().orElse(null);
    } catch (Exception e) {
      logger.atSevere().withCause(e).log(
          "Repository '%s', getting ref '%s' failed", identifier, ref);
    }
    return null;
  }

  @Override
  public void evict(String identifier, String ref) {
    // do nothing as there is no cache to be evicted
  }
}
