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

import java.util.Map;

import org.hawkular.inventory.paths.CanonicalPath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public final class Relationship {
    private final CanonicalPath cp;
    private final CanonicalPath source;
    private final CanonicalPath target;
    private final String name;
    private final Map<String, String> properties;

    public static String componentsToId(CanonicalPath source, CanonicalPath target, String name) {
        return source.toString() + "\u0010" + name + "\u0010" + target.toString();
    }

    public static CanonicalPath componentsToCp(CanonicalPath source, CanonicalPath target, String name) {
        return CanonicalPath.of().relationship(componentsToId(source, target, name)).get();
    }

    public static Relationship fromCanonicalPath(CanonicalPath relationshipCp, Map<String, String> properties) {
        String[] components = relationshipCp.getSegment().getElementId().split("\u0010");
        CanonicalPath source = CanonicalPath.fromString(components[0]);
        String name = components[1];
        CanonicalPath target = CanonicalPath.fromString(components[2]);
        return new Relationship(source, target, name, properties);
    }

    public Relationship(CanonicalPath source, CanonicalPath target, String name,
                        Map<String, String> properties) {
        String id = componentsToId(source, target, name);
        this.cp = CanonicalPath.of().relationship(id).get();
        this.source = source;
        this.target = target;
        this.name = name;
        this.properties = properties;
    }

    public CanonicalPath getPath() {
        return cp;
    }

    public CanonicalPath getSource() {
        return source;
    }

    public CanonicalPath getTarget() {
        return target;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Relationship)) return false;

        Relationship that = (Relationship) o;

        if (!source.equals(that.source)) return false;
        if (!target.equals(that.target)) return false;
        return name.equals(that.name);
    }

    @Override public int hashCode() {
        int result = source.hashCode();
        result = 31 * result + target.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }

    @Override public String toString() {
        final StringBuilder sb = new StringBuilder("Relationship[");
        sb.append("name='").append(name).append('\'');
        sb.append(", source=").append(source);
        sb.append(", target=").append(target);
        sb.append(", properties=").append(properties);
        sb.append(']');
        return sb.toString();
    }

    public static final class Blueprint {
        private final CanonicalPath otherEnd;
        private final String name;
        private final Map<String, String> properties;

        @JsonCreator
        public Blueprint(@JsonProperty("otherEnd") CanonicalPath otherEnd, @JsonProperty("name") String name,
                         @JsonProperty("properties") Map<String, String> properties) {
            this.otherEnd = otherEnd;
            this.name = name;
            this.properties = properties;
        }

        public CanonicalPath getOtherEnd() {
            return otherEnd;
        }

        public String getName() {
            return name;
        }

        public Map<String, String> getProperties() {
            return properties;
        }
    }
}
