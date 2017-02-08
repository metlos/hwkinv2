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
package org.hawkular.inventory.test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.hawkular.inventory.model.Entity;
import org.hawkular.inventory.model.InventoryStructure;
import org.hawkular.inventory.paths.RelativePath;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public class InventoryStructureTest {

    private Entity fd = Entity.at("/t;t/f;fd").build();
    private Entity r1 = Entity.at("/t;t/f;fd/r;r1").build();
    private Entity m1 = Entity.at("/t;t/f;fd/r;r1/m;m1").build();
    private Entity r2 = Entity.at("/t;t/f;fd/r;r2").build();
    private Entity m2 = Entity.at("/t;t/f;fd/r;r2/m;m2").build();
    private Entity m3 = Entity.at("/t;t/f;fd/r;r2/m;m3").build();
    private Entity rt1 = Entity.at("/t;t/f;fd/rt;rt1").build();
    private Entity mt1 = Entity.at("/t;t/f;fd/mt;mt1").build();

    private InventoryStructure struct = InventoryStructure.of(fd)
            .startChild(r1)
            .addChild(m1)
            .end()
            .startChild(r2)
            .addChild(m2)
            .addChild(m3)
            .end()
            .addChild(rt1)
            .addChild(mt1)
            .build();

    @Test
    public void testGetByPath() {
        Assert.assertEquals(fd, struct.getRoot());
        Assert.assertEquals(fd, struct.get(RelativePath.empty().get()));
        Assert.assertEquals(r1, struct.get(RelativePath.to().resource("r1").get()));
        Assert.assertEquals(m1, struct.get(RelativePath.to().resource("r1").metric("m1").get()));
        Assert.assertEquals(r2, struct.get(RelativePath.to().resource("r2").get()));
        Assert.assertEquals(m2, struct.get(RelativePath.to().resource("r2").metric("m2").get()));
        Assert.assertEquals(m3, struct.get(RelativePath.to().resource("r2").metric("m3").get()));
        Assert.assertEquals(rt1, struct.get(RelativePath.to().resourceType("rt1").get()));
        Assert.assertEquals(mt1, struct.get(RelativePath.to().metricType("mt1").get()));
    }

    @Test
    public void testAllEntitiesMap() {
        Assert.assertEquals(8, struct.getAllEntities().size());
        Assert.assertEquals(fd, struct.getAllEntities().get(fd.getPath()));
        Assert.assertEquals(r1, struct.getAllEntities().get(r1.getPath()));
        Assert.assertEquals(m1, struct.getAllEntities().get(m1.getPath()));
        Assert.assertEquals(r2, struct.getAllEntities().get(r2.getPath()));
        Assert.assertEquals(m2, struct.getAllEntities().get(m2.getPath()));
        Assert.assertEquals(m3, struct.getAllEntities().get(m3.getPath()));
        Assert.assertEquals(rt1, struct.getAllEntities().get(rt1.getPath()));
        Assert.assertEquals(mt1, struct.getAllEntities().get(mt1.getPath()));
    }

    @Test
    public void testGetChildren() {
        Assert.assertEquals(setOf(r1, r2, rt1, mt1), struct.getChildren(RelativePath.empty().get()));
        Assert.assertEquals(setOf(m1), struct.getChildren(RelativePath.to().resource("r1").get()));
        Assert.assertEquals(setOf(), struct.getChildren(RelativePath.to().resource("r1").metric("m1").get()));
        Assert.assertEquals(setOf(m2, m3), struct.getChildren(RelativePath.to().resource("r2").get()));
        Assert.assertEquals(setOf(), struct.getChildren(RelativePath.to().resource("r2").metric("m2").get()));
        Assert.assertEquals(setOf(), struct.getChildren(RelativePath.to().resource("r2").metric("m3").get()));
        Assert.assertEquals(setOf(), struct.getChildren(RelativePath.to().resourceType("rt1").get()));
        Assert.assertEquals(setOf(), struct.getChildren(RelativePath.to().metricType("mt1").get()));
    }

    private Set<Entity> setOf(Entity... entities) {
        return new HashSet<>(Arrays.asList(entities));
    }
}
