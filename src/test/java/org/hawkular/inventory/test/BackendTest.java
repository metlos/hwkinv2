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

import java.util.Collections;

import javax.inject.Inject;

import org.hawkular.inventory.annotations.Configured;
import org.hawkular.inventory.backend.InventoryStorage;
import org.hawkular.inventory.model.Entity;
import org.hawkular.inventory.paths.CanonicalPath;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import rx.Observable;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
@RunWith(Arquillian.class)
public class BackendTest {

    @Inject @Configured
    private InventoryStorage storage;

    @Deployment
    public static WebArchive getDeployment() {
        return Deployments.getFullHawkularInventoryWar();
    }

    @Before
    public void createTenant() throws Exception {
        storage.insert(new Entity(CanonicalPath.of().tenant("t").get(), "tenant", Collections.emptyMap()))
                .toBlocking().last();
    }

    @After
    public void dropTenant() throws Exception {
        storage.delete(CanonicalPath.of().tenant("t").get());
    }

    @Test
    public void testTenantCreated() throws Exception {
        Observable<Entity> res = storage.findByPath(CanonicalPath.of().tenant("t").get());
        Assert.assertEquals(1, count(res));
    }

    private int count(Observable<?> col) {
        return col.count().toBlocking().single();
    }
}
