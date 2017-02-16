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

import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.client.Entity.json;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.hawkular.inventory.model.Entity;
import org.hawkular.inventory.model.InventoryStructure;
import org.hawkular.inventory.model.SyncRequest;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;
import org.hawkular.inventory.serialization.JacksonConfig;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.extension.rest.client.ArquillianResteasyResource;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
@RunWith(Arquillian.class)
public class FrontendTest {

    private String tenantId;

    @Deployment
    public static WebArchive getDeployment() {
        return Deployments.getFullHawkularInventoryWar();
    }

    @Before
    public void newTenantId() {
        tenantId = UUID.randomUUID().toString();
    }

    //this should be an @After method, but JUnit can't handle params in it....
    private void deleteTenant(WebTarget webTarget) {
        onResponse(request(webTarget.path("/entity/t;" + tenantId)).delete(), response -> {
            Assert.assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
        });
    }

    @Test
    @RunAsClient
    public void testTenantAutoCreates(@ArquillianResteasyResource("") WebTarget webTarget) throws Exception {
        try {
            onResponse(request(webTarget.path("/entity/t;" + tenantId)).get(), response -> {
                Entity expected = new Entity(CanonicalPath.of().tenant(tenantId).get(), null, null);
                Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
                Entity actual = readResponse(response, Entity.class);

                Assert.assertEquals(expected, actual);
            });
        } finally {
            deleteTenant(webTarget);
        }
    }

    @Test
    @RunAsClient
    public void testEntityCreate(@ArquillianResteasyResource("") WebTarget webTarget) throws Exception {
        try {
            onResponse(request(webTarget.path("/entity/f;feed"))
                            .post(entity(new Entity.Blueprint(null, "my feed", null), MediaType.APPLICATION_JSON)),
                    response -> {
                        Assert.assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
                        URI expectedLocation = webTarget.path("/entity/f;feed").getUri();
                        Assert.assertEquals(expectedLocation.toString(), response.getHeaderString("Location"));
                    });

            Entity expected = new Entity(CanonicalPath.of().tenant(tenantId).feed("feed").get(), "my feed", null);

            onResponse(request(webTarget.path("/entity/f;feed")).get(), response -> {
                Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
                Entity actual = readResponse(response, Entity.class);
                Assert.assertEquals(expected, actual);
            });
        } finally {
            deleteTenant(webTarget);
        }
    }

    @Test
    @RunAsClient
    public void testEntityUpdate(@ArquillianResteasyResource("") WebTarget webTarget) throws Exception {
        try {
            onResponse(request(webTarget.path("/entity/feed"))
                            .post(json(new Entity.Blueprint("feed", "my feed", null))),
                    response -> {
                        Assert.assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
                        URI expectedLocation = webTarget.path("/entity/f;feed").getUri();
                        Assert.assertEquals(expectedLocation.toString(), response.getHeaderString("Location"));
                    });

            Entity expected = new Entity(CanonicalPath.of().tenant(tenantId).feed("feed").get(), "my feed", null);

            onResponse(request(webTarget.path("/entity/f;feed")).get(), response -> {
                Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
                Entity actual = readResponse(response, Entity.class);
                Assert.assertEquals(expected, actual);
            });

            Map<String, String> props = new HashMap<>();
            props.put("a", "b");

            onResponse(request(webTarget.path("/entity/f;feed"))
                            .put(entity(new Entity.Blueprint(null, "my update", props), MediaType.APPLICATION_JSON)),
                    response -> {
                        Assert.assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
                    });

            Entity expected2 = new Entity(CanonicalPath.of().tenant(tenantId).feed("feed").get(), "my update", props);

            onResponse(request(webTarget.path("/entity/f;feed")).get(), response -> {
                Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
                Entity actual = readResponse(response, Entity.class);
                Assert.assertEquals(expected2, actual);
            });
        } finally {
            deleteTenant(webTarget);
        }
    }

    @Test
    @RunAsClient
    public void testEntityDelete(@ArquillianResteasyResource("") WebTarget webTarget) throws Exception {
        try {
            onResponse(request(webTarget.path("/entity/f;feed"))
                            .post(entity(new Entity.Blueprint(null, "my feed", null), MediaType.APPLICATION_JSON)),
                    response -> {
                        Assert.assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
                        URI expectedLocation = webTarget.path("/entity/f;feed").getUri();
                        Assert.assertEquals(expectedLocation.toString(), response.getHeaderString("Location"));
                    });

            Entity expected = new Entity(CanonicalPath.of().tenant(tenantId).feed("feed").get(), "my feed", null);

            onResponse(request(webTarget.path("/entity/f;feed")).get(), response -> {
                Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
                Entity actual = readResponse(response, Entity.class);
                Assert.assertEquals(expected, actual);
            });

            onResponse(request(webTarget.path("/entity/f;feed")).delete(), response -> {
                Assert.assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
            });

            onResponse(request(webTarget.path("/entity/f;feed")).get(), response -> {
                Assert.assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
            });
        } finally {
            deleteTenant(webTarget);
        }
    }

    @Test
    @RunAsClient
    public void testSync(@ArquillianResteasyResource("") WebTarget webTarget) throws Exception {
        try {
            InventoryStructure structure = InventoryStructure.of(SegmentType.f, Entity.blueprint("feed").build())
                    .startChild(SegmentType.r, Entity.blueprint("r1").build())
                    /**/.addChild(SegmentType.m, Entity.blueprint("m1").build())
                    .end()
                    .startChild(SegmentType.r, Entity.blueprint("r2").build())
                    /**/.addChild(SegmentType.m, Entity.blueprint("m2").build())
                    /**/.addChild(SegmentType.m, Entity.blueprint("m3").build())
                    .end()
                    .addChild(SegmentType.rt, Entity.blueprint("rt1").build())
                    .addChild(SegmentType.mt, Entity.blueprint("mt1").build())
                    .build();

            ObjectMapper mapper = new JacksonConfig().getMapper();
            String data = mapper.writeValueAsString(SyncRequest.syncEverything(structure));

            onResponse(request(webTarget.path("/sync/f;feed")).post(json(data)),
                    response -> {
                        Assert.assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
                    });

            Entity feed = Entity.at(CanonicalPath.of().tenant(tenantId).feed("feed").get()).build();
            Entity r1 = Entity.at(feed.getPath().modified().extend(SegmentType.r, "r1").get()).build();
            Entity m1 = Entity.at(r1.getPath().modified().extend(SegmentType.m, "m1").get()).build();
            Entity r2 = Entity.at(feed.getPath().modified().extend(SegmentType.r, "r2").get()).build();
            Entity m2 = Entity.at(r2.getPath().modified().extend(SegmentType.m, "m2").get()).build();
            Entity m3 = Entity.at(r2.getPath().modified().extend(SegmentType.m, "m3").get()).build();
            Entity rt1 = Entity.at(feed.getPath().modified().extend(SegmentType.rt, "rt1").get()).build();
            Entity mt1 = Entity.at(feed.getPath().modified().extend(SegmentType.mt, "mt1").get()).build();

            ThrowingConsumer<Entity, Exception> exists =
                    e -> onResponse(request(webTarget.path("/entity" + e.getPath())).get(), response -> {
                        Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
                        Assert.assertEquals(e, readResponse(response, Entity.class));
                    });

            exists.accept(feed);
            exists.accept(r1);
            exists.accept(m1);
            exists.accept(r2);
            exists.accept(m2);
            exists.accept(m3);
            exists.accept(rt1);
            exists.accept(mt1);
        } finally {
            deleteTenant(webTarget);
        }
    }

    private Invocation.Builder request(WebTarget target) {
        return target.request(MediaType.APPLICATION_JSON).header("Hawkular-Tenant", tenantId);
    }

    private <T> T readResponse(Response response, Class<T> type) throws IOException {
        String data = response.readEntity(String.class);
        ObjectMapper mapper = new JacksonConfig().getMapper();

        return mapper.readValue(data, type);
    }

    private <E extends Throwable> void onResponse(Response response, ThrowingConsumer<Response, E> call) throws E {
        try {
            call.accept(response);
        } finally {
            response.close();
        }
    }

    private interface ThrowingConsumer<T, E extends Throwable> {
        void accept(T value) throws E;
    }
}
