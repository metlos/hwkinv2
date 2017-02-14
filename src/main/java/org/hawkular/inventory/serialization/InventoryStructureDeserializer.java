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
package org.hawkular.inventory.serialization;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

import org.hawkular.inventory.model.Entity;
import org.hawkular.inventory.model.InventoryStructure;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public final class InventoryStructureDeserializer extends JsonDeserializer<InventoryStructure> {
    private static final String LEGAL_ENTITY_TYPES = Entity.SYNCABLE_TYPES.stream()
            .map(InventoryStructureSerializer::entityTypeName)
            .collect(Collectors.joining("', '", "'", "'"));

    private static final ThreadLocal<CanonicalPath> ROOT_PATH = new ThreadLocal<>();

    public static void setDeserializationRootPath(CanonicalPath rootPath) {
        ROOT_PATH.set(rootPath);
    }

    @Override public InventoryStructure deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode tree = p.readValueAsTree();
        if (tree == null) {
            throw new JsonParseException("Inventory structure expected but got nothing.", p.getCurrentLocation());
        }

        JsonToken token = tree.asToken();
        if (token != JsonToken.START_OBJECT) {
            throw new JsonParseException("Expected object but got " + token.asString(), JsonLocation.NA);
        }

        Entity root = parseDataAsEntity(tree.get("data"), ROOT_PATH.get());

        InventoryStructure.Builder bld = InventoryStructure.of(root);
        parseChildren(tree, bld, root.getPath());

        return bld.build();
    }

    private Entity parseDataAsEntity(JsonNode node, CanonicalPath parentPath, SegmentType entityType) {
        String id = node.get("id").asText();
        CanonicalPath entityPath = parentPath.extend(entityType, id).get();
        return parseDataAsEntity(node, entityPath);
    }

    private Entity parseDataAsEntity(JsonNode node, CanonicalPath entityPath) {
        String name = node.get("name") == null ? null : node.get("name").asText();
        JsonNode propsNode = node.get("properties");
        Map<String, String> properties;
        if (propsNode != null) {
            Iterator<Map.Entry<String, JsonNode>> it = propsNode.fields();
            properties = new HashMap<>();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                properties.put(e.getKey(), e.getValue().asText());
            }
        } else {
            properties = Collections.emptyMap();
        }

        return new Entity(entityPath, name, properties);
    }

    private void parseChildren(JsonNode root, InventoryStructure.AbstractBuilder<?> bld, CanonicalPath rootPath)
            throws IOException {

        JsonNode children = root.get("children");
        if (children == null) {
            return;
        }

        if (!children.isObject()) {
            throw new JsonParseException("The 'children' is supposed to be an object.", JsonLocation.NA);
        } else {
            Iterator<Map.Entry<String, JsonNode>> fields = children.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                String typeName = e.getKey();
                JsonNode childrenNode = e.getValue();

                if (!childrenNode.isArray()) {
                    continue;
                }

                SegmentType type = typeFromString(typeName);

                Iterator<JsonNode> childrenNodes = childrenNode.elements();
                while (childrenNodes.hasNext()) {
                    JsonNode childNode = childrenNodes.next();

                    Entity child = parseDataAsEntity(childNode.get("data"), rootPath, type);

                    InventoryStructure.ChildBuilder<?> childBld = bld.startChild(child);

                    parseChildren(childNode, childBld, child.getPath());

                    childBld.end();
                }
            }
        }
    }

    private SegmentType typeFromString(String type) throws JsonParseException {
        type = Character.toUpperCase(type.charAt(0)) + type.substring(1);
        for (SegmentType s : SegmentType.values()) {
            if (s.getSimpleName().equals(type)) {
                return s;
            }
        }

        throw new JsonParseException("Unrecognized value of 'type'. Supported values are " + LEGAL_ENTITY_TYPES
                + " but got '" + type + "'.", JsonLocation.NA);
    }
}
