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
package org.hawkular.inventory.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public final class InventoryStructure implements Serializable {

    private final Entity root;
    private final Map<CanonicalPath, Set<Entity>> children;
    private final Map<CanonicalPath, Entity> entities;

    private InventoryStructure(Entity root, Map<CanonicalPath, Entity> entities,
                               Map<CanonicalPath, Set<Entity>> children) {
        this.root = root;
        this.children = children;
        this.entities = Collections.unmodifiableMap(entities);
    }

    public static Builder of(Entity root) {
        return new Builder(root);
    }

    public Entity getRoot() {
        return root;
    }

    public Entity get(RelativePath path) {
        CanonicalPath cp = path.applyTo(root.getPath());
        return entities.get(cp);
    }

    public Set<Entity> getChildren(RelativePath parent) {
        CanonicalPath parentCp = parent.applyTo(root.getPath());
        return children.getOrDefault(parentCp, Collections.emptySet());
    }

    public Map<CanonicalPath, Entity> getAllEntities() {
        return entities;
    }

    public static class AbstractBuilder<This extends AbstractBuilder<?>> {
        final CanonicalPath myPath;
        final Map<CanonicalPath, Set<Entity>> children;
        final Map<CanonicalPath, Entity> entities;

        private AbstractBuilder(CanonicalPath myPath, Map<CanonicalPath, Set<Entity>> children,
                                Map<CanonicalPath, Entity> entities) {
            this.myPath = myPath;
            this.children = children;
            this.entities = entities;
        }

        public ChildBuilder<This> startChild(Entity e) {
            if (!e.getPath().up().equals(myPath)) {
                throw new IllegalArgumentException("Child's path doesn't extend the path of the current element.");
            }

            getChildrenOfType(e.getPath().getSegment().getElementType()).add(e);

            entities.put(e.getPath(), e);

            return new ChildBuilder<>(e.getPath(), castThis(), children, entities);
        }

        public This addChild(Entity e) {
            if (!e.getPath().up().equals(myPath)) {
                throw new IllegalArgumentException("Child's path doesn't extend the path of the current element.");
            }

            getChildrenOfType(e.getPath().getSegment().getElementType()).add(e);
            entities.put(e.getPath(), e);
            return castThis();
        }

        Set<Entity> getChildrenOfType(SegmentType type) {
            return children.computeIfAbsent(myPath, k -> new HashSet<>());
        }

        @SuppressWarnings("unchecked")
        private This castThis() {
            return (This) this;
        }
    }

    public static final class Builder extends AbstractBuilder<Builder> {
        final Entity root;
        private Builder(Entity root) {
            super(root.getPath(), new HashMap<>(), new HashMap<>());
            this.root = root;
            entities.put(root.getPath(), root);
        }

        public InventoryStructure build() {
            return new InventoryStructure(root, entities, children);
        }
    }

    public static final class ChildBuilder<Parent extends AbstractBuilder<?>> extends AbstractBuilder<ChildBuilder<Parent>> {
        private final Parent parent;

        private ChildBuilder(CanonicalPath myPath, Parent parent,
                             Map<CanonicalPath, Set<Entity>> children,
                             Map<CanonicalPath, Entity> entities) {
            super(myPath, children, entities);
            this.parent = parent;
        }

        public Parent end() {
            return parent;
        }
    }
}
