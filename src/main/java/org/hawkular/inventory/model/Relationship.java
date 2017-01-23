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

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public final class Relationship {
    private final CanonicalPath source;
    private final CanonicalPath target;
    private final String name;
    private final Map<String, String> properties;

    public Relationship(CanonicalPath source, CanonicalPath target, String name,
                        Map<String, String> properties) {
        this.source = source;
        this.target = target;
        this.name = name;
        this.properties = properties;
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
}
