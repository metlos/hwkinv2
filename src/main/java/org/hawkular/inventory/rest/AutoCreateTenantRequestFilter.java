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
package org.hawkular.inventory.rest;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.hawkular.inventory.annotations.Configured;
import org.hawkular.inventory.backend.InventoryStorage;
import org.hawkular.inventory.logging.Log;
import org.hawkular.inventory.model.Entity;
import org.hawkular.inventory.paths.CanonicalPath;

import rx.Subscriber;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
@Provider
@PreMatching
public class AutoCreateTenantRequestFilter implements ContainerRequestFilter {
    /* URI chunks to which this filter should not be applied */
    private static final List<Pattern> URI_EXCEPTION_PATTERNS = Stream.of(".*/inventory/status/?",
            ".*/inventory/ping/?", ".*/inventory/?").map(Pattern::compile).collect(Collectors.toList());

    static final String TENANT_HEADER_NAME = "Hawkular-Tenant";

    private final Set<String> existingTenantIds = ConcurrentHashMap.newKeySet();

    @Inject @Configured
    private InventoryStorage storage;

    @Override public void filter(ContainerRequestContext requestContext) throws IOException {
        boolean shouldSkip = shouldSkip(requestContext.getUriInfo().getRequestUri().getPath());
        if (shouldSkip) {
            return;
        }

        String tenantId = requestContext.getHeaderString(TENANT_HEADER_NAME);
        if (tenantId != null) {
            if (!existingTenantIds.contains(tenantId)) {
                Log.LOG.tracef("Tenant [%s] needs to be created", tenantId);

                //we stop the request processing until the tenant is successfully created...
                CountDownLatch waiter = new CountDownLatch(1);
                Throwable[] failure = new Throwable[1];
                storage.upsert(Entity.at(CanonicalPath.of().tenant(tenantId).get()).build())
                        .subscribe(new Subscriber<Void>() {
                            @Override
                            public void onCompleted() {
                                waiter.countDown();
                            }

                            @Override
                            public void onError(Throwable t) {
                                failure[0] = t;
                                waiter.countDown();
                                Log.LOG.warnFailedToAutocreateTenant(tenantId, t);
                            }

                            @Override
                            public void onNext(Void ignored) {
                                //do nothing
                            }
                        });

                try {
                    waiter.await();
                } catch (InterruptedException e) {
                    //reset the flag, but continue with the request processing. The container should react accordingly.
                    Thread.currentThread().interrupt();
                }

                if (failure[0] != null) {
                    requestContext.abortWith(
                            Response.serverError().entity("Failed to auto-create tenant '" + tenantId
                                    + "'. Error message: " + failure[0].getMessage()).build());
                } else {
                    existingTenantIds.add(tenantId);
                }
            } else {
                Log.LOG.tracef("Tenant [%s] exists already", tenantId);
            }
        }
    }


    private boolean shouldSkip(String uri) {
        return URI_EXCEPTION_PATTERNS.stream().anyMatch((p -> p.matcher(uri).matches()));
    }
}
