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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public final class InventoryStructure implements Serializable {

    private final Entity.Blueprint root;
    private final SegmentType rootType;
    private final Map<RelativePath, Map<SegmentType, Set<Entity.Blueprint>>> children;
    private final Map<RelativePath, Entity.Blueprint> entities;

    private InventoryStructure(SegmentType rootType, Entity.Blueprint root, Map<RelativePath,
                               Entity.Blueprint> entities,
                               Map<RelativePath, Map<SegmentType, Set<Entity.Blueprint>>> children) {
        this.root = root;
        this.rootType = rootType;
        this.children = children;
        this.entities = Collections.unmodifiableMap(entities);
    }

    public static Builder of(Entity entity) {
        return of(entity.getPath().getSegment().getElementType(), entity.asBlueprint());
    }

    public static Builder of(SegmentType rootType, Entity.Blueprint root) {
        return new Builder(rootType, root);
    }

    public Entity.Blueprint getRoot() {
        return root;
    }

    public SegmentType getRootType() {
        return rootType;
    }

    public Entity.Blueprint get(RelativePath path) {
        return entities.get(path);
    }

    public Set<Entity.Blueprint> getChildren(RelativePath parent, SegmentType childrenType) {
        return children.getOrDefault(parent, Collections.emptyMap()).getOrDefault(childrenType, Collections.emptySet());
    }

    public Map<SegmentType, Set<Entity.Blueprint>> getAllChildren(RelativePath parent) {
        return children.getOrDefault(parent, Collections.emptyMap());
    }

    public Map<RelativePath, Entity.Blueprint> getAllEntities() {
        return entities;
    }

    public static class AbstractBuilder<This extends AbstractBuilder<?>> {
        final RelativePath myPath;
        final Map<RelativePath, Map<SegmentType, Set<Entity.Blueprint>>> children;
        final Map<RelativePath, Entity.Blueprint> entities;

        private AbstractBuilder(RelativePath myPath,
                                Map<RelativePath, Map<SegmentType, Set<Entity.Blueprint>>> children,
                                Map<RelativePath, Entity.Blueprint> entities) {
            this.myPath = myPath;
            this.children = children;
            this.entities = entities;
        }

        public ChildBuilder<This> startChild(Entity child) {
            return startChild(child.getPath().getSegment().getElementType(), child.asBlueprint());
        }

        public ChildBuilder<This> startChild(SegmentType childType, Entity.Blueprint e) {
            RelativePath.Extender newPath = myPath.modified();

            if (!newPath.canExtendTo(childType)) {
                throw new IllegalArgumentException("Child's path cannot extend the path of the current element.");
            }

            getChildrenOfType(childType).add(e);

            RelativePath childPath = newPath.extend(childType, e.getId()).get();
            entities.put(childPath, e);

            return new ChildBuilder<>(childPath, castThis(), children, entities);
        }

        public This addChild(Entity entity) {
            return addChild(entity.getPath().getSegment().getElementType(), entity.asBlueprint());
        }

        public This addChild(SegmentType childType, Entity.Blueprint e) {
            return startChild(childType, e).end();
        }

        Set<Entity.Blueprint> getChildrenOfType(SegmentType type) {
            return children
                    .computeIfAbsent(myPath, k -> new EnumMap<>(SegmentType.class))
                    .computeIfAbsent(type, k -> new HashSet<>());
        }

        @SuppressWarnings("unchecked")
        private This castThis() {
            return (This) this;
        }
    }

    public static final class Builder extends AbstractBuilder<Builder> {
        final Entity.Blueprint root;
        final SegmentType rootType;

        private Builder(SegmentType rootType, Entity.Blueprint root) {
            super(RelativePath.empty().get(), new HashMap<>(), new HashMap<>());
            this.root = root;
            this.rootType = rootType;
            entities.put(myPath, root);
        }

        public InventoryStructure build() {
            return new InventoryStructure(rootType, root, entities, children);
        }
    }

    public static final class ChildBuilder<Parent extends AbstractBuilder<?>> extends AbstractBuilder<ChildBuilder<Parent>> {
        private final Parent parent;

        private ChildBuilder(RelativePath myPath, Parent parent,
                             Map<RelativePath, Map<SegmentType, Set<Entity.Blueprint>>> children,
                             Map<RelativePath, Entity.Blueprint> entities) {
            super(myPath, children, entities);
            this.parent = parent;
        }

        public Parent end() {
            return parent;
        }
    }
}
