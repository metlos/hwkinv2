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
import java.util.HashMap;
import java.util.Map;

import org.hawkular.inventory.paths.CanonicalPath;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public final class Entity {
    private final CanonicalPath path;
    private final String name;
    private final Map<String, String> properties;

    public static Builder at(CanonicalPath cp) {
        return new Builder(cp);
    }

    public static Builder at(String path) {
        return at(CanonicalPath.fromString(path));
    }

    public Entity(CanonicalPath path, String name, Map<String, String> properties) {
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
        private final String name;
        private final Map<String, String> properties;

        public Blueprint(String name, Map<String, String> properties) {
            this.name = name;
            this.properties = Collections.unmodifiableMap(properties);
        }

        public String getName() {
            return name;
        }

        public Map<String, String> getProperties() {
            return properties;
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
