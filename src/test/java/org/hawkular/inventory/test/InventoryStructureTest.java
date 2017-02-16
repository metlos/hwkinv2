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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hawkular.inventory.backend.FareySequence;
import org.hawkular.inventory.model.Entity;
import org.hawkular.inventory.model.InventoryStructure;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.inventory.paths.SegmentType;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public class InventoryStructureTest {

    private CanonicalPath tp = CanonicalPath.of().tenant("t").get();
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
        Assert.assertEquals(fd, struct.getRoot().asEntity(tp, SegmentType.f));
        Assert.assertEquals(fd, struct.get(RelativePath.empty().get()).asEntity(tp, SegmentType.f));
        Assert.assertEquals(r1,
                struct.get(RelativePath.to().resource("r1").get()).asEntity(fd.getPath(), SegmentType.r));
        Assert.assertEquals(m1,
                struct.get(RelativePath.to().resource("r1").metric("m1").get()).asEntity(r1.getPath(), SegmentType.m));
        Assert.assertEquals(r2,
                struct.get(RelativePath.to().resource("r2").get()).asEntity(fd.getPath(), SegmentType.r));
        Assert.assertEquals(m2,
                struct.get(RelativePath.to().resource("r2").metric("m2").get()).asEntity(r2.getPath(), SegmentType.m));
        Assert.assertEquals(m3,
                struct.get(RelativePath.to().resource("r2").metric("m3").get()).asEntity(r2.getPath(), SegmentType.m));
        Assert.assertEquals(rt1,
                struct.get(RelativePath.to().resourceType("rt1").get()).asEntity(fd.getPath(), SegmentType.rt));
        Assert.assertEquals(mt1,
                struct.get(RelativePath.to().metricType("mt1").get()).asEntity(fd.getPath(), SegmentType.mt));
    }

    @Test
    public void testAllEntitiesMap() {
        Assert.assertEquals(8, struct.getAllEntities().size());
        Assert.assertEquals(fd.asBlueprint(), struct.getAllEntities().get(fd.getPath().relativeTo(fd.getPath())));
        Assert.assertEquals(r1.asBlueprint(), struct.getAllEntities().get(r1.getPath().relativeTo(fd.getPath())));
        Assert.assertEquals(m1.asBlueprint(), struct.getAllEntities().get(m1.getPath().relativeTo(fd.getPath())));
        Assert.assertEquals(r2.asBlueprint(), struct.getAllEntities().get(r2.getPath().relativeTo(fd.getPath())));
        Assert.assertEquals(m2.asBlueprint(), struct.getAllEntities().get(m2.getPath().relativeTo(fd.getPath())));
        Assert.assertEquals(m3.asBlueprint(), struct.getAllEntities().get(m3.getPath().relativeTo(fd.getPath())));
        Assert.assertEquals(rt1.asBlueprint(), struct.getAllEntities().get(rt1.getPath().relativeTo(fd.getPath())));
        Assert.assertEquals(mt1.asBlueprint(), struct.getAllEntities().get(mt1.getPath().relativeTo(fd.getPath())));
    }

    @Test
    public void testGetChildren() {
        Assert.assertEquals(setOf(r1.asBlueprint(), r2.asBlueprint()),
                struct.getChildren(RelativePath.empty().get(), SegmentType.r));
        Assert.assertEquals(setOf(rt1.asBlueprint()),
                struct.getChildren(RelativePath.empty().get(), SegmentType.rt));
        Assert.assertEquals(setOf(mt1.asBlueprint()),
                struct.getChildren(RelativePath.empty().get(), SegmentType.mt));
        Assert.assertEquals(setOf(m1.asBlueprint()),
                struct.getChildren(RelativePath.to().resource("r1").get(), SegmentType.m));
        Assert.assertEquals(Collections.emptyMap(),
                struct.getAllChildren(RelativePath.to().resource("r1").metric("m1").get()));
        Assert.assertEquals(setOf(m2.asBlueprint(), m3.asBlueprint()),
                struct.getChildren(RelativePath.to().resource("r2").get(), SegmentType.m));
        Assert.assertEquals(Collections.emptyMap(),
                struct.getAllChildren(RelativePath.to().resource("r2").metric("m2").get()));
        Assert.assertEquals(Collections.emptyMap(),
                struct.getAllChildren(RelativePath.to().resource("r2").metric("m3").get()));
        Assert.assertEquals(Collections.emptyMap(),
                struct.getAllChildren(RelativePath.to().resourceType("rt1").get()));
        Assert.assertEquals(Collections.emptyMap(),
                struct.getAllChildren(RelativePath.to().metricType("mt1").get()));
    }

    @Test
    @Ignore
    public void testMaxDepth() {
        List<Integer> path = Arrays.asList(1, 500_000, 100, 100, 100, 100, 100);

        FareySequence.Interval interval = FareySequence.intervalForPath(path);
        System.out.println(interval);
        System.out.println(interval.getLow().toDecimal() + " - " + interval.getHigh().toDecimal());
        System.out.println(interval.getLow().toDecimal().equals(interval.getHigh().toDecimal()));
    }

    private <T> Set<T> setOf(T... entities) {
        return new HashSet<>(Arrays.asList(entities));
    }
}
