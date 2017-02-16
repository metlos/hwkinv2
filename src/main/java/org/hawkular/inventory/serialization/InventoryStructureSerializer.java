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
import java.util.Map;
import java.util.Set;

import org.hawkular.inventory.model.Entity;
import org.hawkular.inventory.model.InventoryStructure;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public final class InventoryStructureSerializer extends JsonSerializer<InventoryStructure> {
    @Override public void serialize(InventoryStructure inventoryStructure, JsonGenerator jsonGenerator,
                                    SerializerProvider serializers) throws IOException {
        Entity.Blueprint root = inventoryStructure.getRoot();

        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("type", entityTypeName(inventoryStructure.getRootType()));
        writeData(jsonGenerator, inventoryStructure.getRoot());

        jsonGenerator.writeFieldName("children");
        jsonGenerator.writeStartObject();
        serializeLevel(inventoryStructure, RelativePath.empty(), jsonGenerator);
        jsonGenerator.writeEndObject();
        jsonGenerator.writeEndObject();
    }

    private void serializeLevel(InventoryStructure structure, RelativePath.Extender root, JsonGenerator gen)
            throws IOException {
        RelativePath rootPath = root.get();
        for (Map.Entry<SegmentType, Set<Entity.Blueprint>> e : structure.getAllChildren(rootPath).entrySet()) {
            SegmentType type = e.getKey();
            Set<Entity.Blueprint> children = e.getValue();

            if (!children.isEmpty()) {
                gen.writeFieldName(entityTypeName(type));
                gen.writeStartArray();
                for (Entity.Blueprint c : children) {
                    gen.writeStartObject();
                    writeData(gen, c);
                    gen.writeFieldName("children");
                    gen.writeStartObject();
                    serializeLevel(structure, rootPath.modified().extend(type, c.getId()), gen);
                    gen.writeEndObject();
                    gen.writeEndObject();
                }
                gen.writeEndArray();
            }
        }
    }

    public static String entityTypeName(SegmentType type) {
        String simpleName = type.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    private void writeData(JsonGenerator gen, Entity.Blueprint entity) throws IOException {
        gen.writeFieldName("data");
        gen.writeStartObject();
        gen.writeStringField("id", entity.getId());
        gen.writeStringField("name", entity.getName());
        gen.writeObjectField("properties", entity.getProperties());
        gen.writeEndObject();
    }
}
