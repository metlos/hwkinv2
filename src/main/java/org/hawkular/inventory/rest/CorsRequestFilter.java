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
import java.util.Collections;
import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.PostConstruct;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;

import org.hawkular.inventory.annotations.Configured;
import org.hawkular.jaxrs.filter.cors.CorsFilters;
import org.hawkular.jaxrs.filter.cors.Headers;
import org.hawkular.jaxrs.filter.cors.OriginPredicate;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
@Provider
@PreMatching
@Priority(0)
public class CorsRequestFilter implements ContainerRequestFilter {
    @Inject @Configured
    private Map<String, String> configuration;

    private Predicate<String> originTest;

    @PostConstruct
    protected void init() {
        String allowedOrigins = Util.getConfigValue(configuration, "hawkular.allowed-cors-origins",
                Collections.singletonList("hawkular.allowed-cors-origins"),
                Collections.singletonList("ALLOWED_CORS_ORIGINS"));
        allowedOrigins = allowedOrigins == null ? Headers.ALLOW_ALL_ORIGIN : allowedOrigins;

        this.originTest = new OriginPredicate(allowedOrigins);
    }

    @Override public void filter(ContainerRequestContext requestContext) throws IOException {
        CorsFilters.filterRequest(requestContext, originTest);
    }
}
