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

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public final class Entity {
    public static final Set<SegmentType> SYNCABLE_TYPES =
            Collections.unmodifiableSet(EnumSet.of(SegmentType.f, SegmentType.rt, SegmentType.mt, SegmentType.ot,
                    SegmentType.m, SegmentType.r, SegmentType.d));

    private final CanonicalPath path;
    private final String name;
    private final Map<String, String> properties;

    public static boolean isSyncable(SegmentType entityType) {
        return SYNCABLE_TYPES.contains(entityType);
    }

    public static Builder at(CanonicalPath cp) {
        return new Builder(cp);
    }

    public static Builder at(String path) {
        return at(CanonicalPath.fromString(path));
    }

    public static Blueprint.Builder blueprint(String id) {
        return new Blueprint.Builder(id);
    }

    @JsonCreator
    public Entity(@JsonProperty("path") CanonicalPath path, @JsonProperty("name") String name,
                  @JsonProperty("properties") Map<String, String> properties) {
        this.path = path;
        this.name = name;
        this.properties = properties;
    }

    public CanonicalPath getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public Blueprint asBlueprint() {
        return new Blueprint(path.getSegment().getElementId(), name, properties);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entity)) return false;

        Entity entity = (Entity) o;

        return path.equals(entity.path);
    }

    @Override public int hashCode() {
        return path.hashCode();
    }

    @Override public String toString() {
        final StringBuilder sb = new StringBuilder("Entity[");
        sb.append("name='").append(name).append('\'');
        sb.append(", path=").append(path);
        sb.append(", properties=").append(properties);
        sb.append(']');
        return sb.toString();
    }

    public static final class Blueprint {
        private final String id;
        private final String name;
        private final Map<String, String> properties;

        private Blueprint() {
            id = null;
            name = null;
            properties = null;
        }

        public Blueprint(String id, String name, Map<String, String> properties) {
            this.id = id;
            this.name = name;
            this.properties = properties == null ? Collections.emptyMap() : Collections.unmodifiableMap(properties);
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Map<String, String> getProperties() {
            return properties == null ? Collections.emptyMap() : properties;
        }

        public Entity asEntity(CanonicalPath parent, SegmentType entityType) {
            return new Entity(parent.extend(entityType, id).get(), name, properties);
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Blueprint)) return false;

            Blueprint blueprint = (Blueprint) o;

            return id != null ? id.equals(blueprint.id) : blueprint.id == null;
        }

        @Override public int hashCode() {
            return id != null ? id.hashCode() : 0;
        }

        public static final class Builder {
            private final String id;
            private String name;
            private Map<String, String> properties;

            private Builder(String id) {
                this.id = id;
            }

            public Builder withName(String name) {
                this.name = name;
                return this;
            }

            public Builder withProperty(String key, String value) {
                if (properties == null) {
                    properties = new HashMap<>();
                }

                properties.put(key, value);
                return this;
            }

            public Builder withProperties(Map<String, String> props) {
                if (properties == null) {
                    properties = new HashMap<>();
                }

                properties.putAll(props);
                return this;
            }

            public Blueprint build() {
                return new Blueprint(id, name, properties);
            }
        }
    }

    public static final class Builder {
        private final CanonicalPath cp;
        private String name;
        private Map<String, String> properties = new HashMap<>();

        private Builder(CanonicalPath cp) {
            this.cp = cp;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withProperties(Map<String, String> properties) {
            this.properties.putAll(properties);
            return this;
        }

        public Builder withProperty(String key, String value) {
            this.properties.put(key, value);
            return this;
        }

        public Entity build() {
            return new Entity(cp, name, properties);
        }
    }
}
