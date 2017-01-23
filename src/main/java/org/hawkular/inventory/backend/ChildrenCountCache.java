/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.inventory.backend;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.hawkular.inventory.paths.CanonicalPath;

/**
 * This implements "atomic counters" to store the number of children of each entity in the entity tree.
 *
 * <p>This does NOT WORK in a clustered environment and will need to be replaced by some kind of true distributed
 * counter when inventory is deployed in a distributed fashion. JGroups counter service lends itself for this purpose.
 *
 * @author Lukas Krejci
 * @since 2.0.0
 */
final class ChildrenCountCache {

    private final Map<CanonicalPath, Integer> childCounts = new HashMap<>();

    public void initialize(Statements statements) {
        synchronized (childCounts) {
            childCounts.clear();
            Iterator<CanonicalPath> paths = statements.getAllEntityPaths()
                    .map(r -> CanonicalPath.fromString(r.getString(0)))
                    .toBlocking().getIterator();

            while (paths.hasNext()) {
                CanonicalPath cp = paths.next();

                CanonicalPath parent = cp.up();

                incrementAndGetNonSynced(parent);
                initParents(parent);
            }
        }
    }

    public int incrementAndGet(CanonicalPath parentPath) {
        synchronized (childCounts) {
            return incrementAndGetNonSynced(parentPath);
        }
    }

    private int incrementAndGetNonSynced(CanonicalPath parentPath) {
        return childCounts.merge(parentPath, 1, (old, one) -> old + one);
    }

    private void initParents(CanonicalPath entityPath) {
        for (CanonicalPath parent : entityPath.up()) {
            if (childCounts.containsKey(parent)) {
                break;
            } else {
                childCounts.put(parent, 1);
            }
        }
    }
}
