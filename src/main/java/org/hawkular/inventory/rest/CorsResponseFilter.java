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

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

import org.hawkular.inventory.annotations.Configured;
import org.hawkular.jaxrs.filter.cors.CorsFilters;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
@Provider
public class CorsResponseFilter implements ContainerResponseFilter {
    @Inject @Configured
    private Map<String, String> configuration;

    private String allowHeaders;

    @PostConstruct
    protected void init() {
        allowHeaders = "authorization";

        String additionalAllowHeaders = Util.getConfigValue(configuration,
                "hawkular.allowed-cors-access-control-allow-headers",
                Collections.singletonList("hawkular.allowed-cors-access-control-allow-headers"),
                Collections.singletonList("ALLOWED_CORS_ACCESS_CONTROL_ALLOW_HEADERS"));
        if (additionalAllowHeaders != null) {
            allowHeaders += "," + additionalAllowHeaders;
        }
    }

    @Override public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        CorsFilters.filterResponse(requestContext, responseContext, allowHeaders);
    }
}
