// Copyright (C) 2026 GerritForge, Inc.
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

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

/**
 * A {@link TernarySearchTree} specialised for {@link Ref} values, keyed by ref
 * name, with the ability to look up refs by the {@link ObjectId} they point to.
 *
 * @since 6.5
 */
public class RefTernarySearchTree extends TernarySearchTree<Ref> {

}
