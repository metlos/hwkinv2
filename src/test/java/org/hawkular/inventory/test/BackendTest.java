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
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import javax.inject.Inject;

import org.hawkular.inventory.annotations.Configured;
import org.hawkular.inventory.backend.InventoryStorage;
import org.hawkular.inventory.logging.Log;
import org.hawkular.inventory.model.Entity;
import org.hawkular.inventory.model.InventoryStructure;
import org.hawkular.inventory.model.SyncRequest;
import org.hawkular.inventory.paths.CanonicalPath;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import rx.Observable;
import rx.Observer;

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
        storage.upsert(new Entity(CanonicalPath.of().tenant("t").get(), "tenant", Collections.emptyMap()))
                .toBlocking().last();
    }

    @After
    public void dropTenant() throws Exception {
        storage.delete(CanonicalPath.of().tenant("t").get()).toBlocking().first();
    }

    @Test
    public void testTenantCreated() throws Exception {
        Observable<Entity> res = storage.findByPath(CanonicalPath.of().tenant("t").get());
        Assert.assertEquals(1, count(res));
    }

    @Test
    public void testSync() throws Exception {
        Entity fd = Entity.at("/t;t/f;fd").build();
        Entity r1 = Entity.at("/t;t/f;fd/r;r1").build();
        Entity m1 = Entity.at("/t;t/f;fd/r;r1/m;m1").build();
        Entity r2 = Entity.at("/t;t/f;fd/r;r2").build();
        Entity m2 = Entity.at("/t;t/f;fd/r;r2/m;m2").build();
        Entity m3 = Entity.at("/t;t/f;fd/r;r2/m;m3").build();
        Entity rt1 = Entity.at("/t;t/f;fd/rt;rt1").build();
        Entity mt1 = Entity.at("/t;t/f;fd/mt;mt1").build();

        InventoryStructure struct = InventoryStructure.of(fd)
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

        CountDownLatch latch = new CountDownLatch(1);

        storage.sync(fd.getPath(), SyncRequest.syncEverything(struct)).subscribe(onceFinished(latch::countDown));

        latch.await();

        Assert.assertEquals(1, count(storage.findByPath(fd.getPath())));
        Assert.assertEquals(1, count(storage.findByPath(r1.getPath())));
        Assert.assertEquals(1, count(storage.findByPath(m1.getPath())));
        Assert.assertEquals(1, count(storage.findByPath(r2.getPath())));
        Assert.assertEquals(1, count(storage.findByPath(m2.getPath())));
        Assert.assertEquals(1, count(storage.findByPath(m3.getPath())));
        Assert.assertEquals(1, count(storage.findByPath(rt1.getPath())));
        Assert.assertEquals(1, count(storage.findByPath(mt1.getPath())));
    }

    @Test
    public void testResync() throws Exception {
        testSync();

        //and now sync again and check that stuff that's no longer in structure got deleted
        Entity fd = Entity.at("/t;t/f;fd").build();
        Entity r1 = Entity.at("/t;t/f;fd/r;r1").build();
        Entity m1 = Entity.at("/t;t/f;fd/r;r1/m;m1").build();
        Entity r2 = Entity.at("/t;t/f;fd/r;r2").build();
        Entity m2 = Entity.at("/t;t/f;fd/r;r2/m;m2").build();
        Entity m3 = Entity.at("/t;t/f;fd/r;r2/m;m3").build();
        Entity rt1 = Entity.at("/t;t/f;fd/rt;rt1").build();
        Entity mt1 = Entity.at("/t;t/f;fd/mt;mt1").build();

        InventoryStructure struct = InventoryStructure.of(fd)
                .addChild(r1)
                .addChild(r2)
                .addChild(rt1)
                .addChild(mt1)
                .build();

        CountDownLatch latch = new CountDownLatch(1);

        storage.sync(fd.getPath(), SyncRequest.syncEverything(struct)).subscribe(onceFinished(latch::countDown));

        latch.await();

        Assert.assertEquals(1, count(storage.findByPath(fd.getPath())));
        Assert.assertEquals(1, count(storage.findByPath(r1.getPath())));
        Assert.assertEquals(0, count(storage.findByPath(m1.getPath())));
        Assert.assertEquals(1, count(storage.findByPath(r2.getPath())));
        Assert.assertEquals(0, count(storage.findByPath(m2.getPath())));
        Assert.assertEquals(0, count(storage.findByPath(m3.getPath())));
        Assert.assertEquals(1, count(storage.findByPath(rt1.getPath())));
        Assert.assertEquals(1, count(storage.findByPath(mt1.getPath())));
    }

    @Test
    @Ignore
    public void testBigSync() throws Exception {
        //this is not really a test, just proto performance guesstimate
        Random rand = new Random();

        int attempts = 5;
        int maxTopLevel = 1000;
        int maxSecondLevelPerParent = 50;
        int maxThirdLevelPerParent = 10;

        for (int attempt = 0; attempt < attempts; ++attempt) {
            Entity root = Entity.at("/t;t/f;fd").build();
            int topLevels = rand.nextInt(maxTopLevel);
            InventoryStructure.Builder bld = InventoryStructure.of(root);
            for (int l1 = 0; l1 < topLevels; ++l1) {
                InventoryStructure.ChildBuilder<?> cb = bld.startChild(Entity.at("/t;t/f;fd/r;" + l1).build());
                int secondLevels = rand.nextInt(maxSecondLevelPerParent);
                for (int l2 = 0; l2 < secondLevels; ++l2) {
                    InventoryStructure.ChildBuilder<?> ccb =
                            cb.startChild(Entity.at("/t;t/f;fd/r;" + l1 + "/r;" + l2).build());

                    int thirdLevels = rand.nextInt(maxThirdLevelPerParent);
                    for (int l3 = 0; l3 < thirdLevels; ++l3) {
                        ccb.addChild(Entity.at("/t;t/f;fd/r;" + l1 + "/r;" + l2 + "/r;" + l3).build());
                    }

                    ccb.end();
                }

                cb.end();
            }

            InventoryStructure struct = bld.build();

            CountDownLatch latch = new CountDownLatch(1);

            storage.sync(root.getPath(), SyncRequest.syncEverything(struct)).subscribe(onceFinished(latch::countDown));

            latch.await();
        }
    }

    private int count(Observable<?> col) {
        return col.count().toBlocking().single();
    }

    private <T> Observer<T> onceFinished(Runnable action) {
        return new Observer<T>() {
            @Override public void onCompleted() {
                action.run();
            }

            @Override public void onError(Throwable e) {
                Log.LOG.warn("Sync threw an error", e);
                action.run();
            }

            @Override public void onNext(T o) {
            }
        };
    }
}
