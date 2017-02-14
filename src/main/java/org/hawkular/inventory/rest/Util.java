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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;

import rx.Subscriber;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
final class Util {

    static CanonicalPath getPath(UriInfo uriInfo, HttpServletRequest request, int excludedPrefixLength,
                                 int excludeSuffixLength) {
        String chopped = uriInfo.getPath(false).substring(excludedPrefixLength);
        if (excludeSuffixLength > 0) {
            chopped = chopped.substring(0, chopped.length() - excludeSuffixLength);
        }

        String tenantId = request.getHeader(AutoCreateTenantRequestFilter.TENANT_HEADER_NAME);

        return CanonicalPath.fromPartiallyUntypedString(chopped, CanonicalPath.of().tenant(tenantId).get(),
                (SegmentType) null);
    }

    public static String getConfigValue(Map<String, String> defaultProps, String propName, List<String> systemProps,
                                        List<String> envVars) {
        for (String sp : systemProps) {
            String val = System.getProperty(sp);
            if (val != null) {
                return val;
            }
        }

        for (String ev : envVars) {
            String val = System.getenv(ev);
            if (val != null) {
                return val;
            }
        }

        return defaultProps.get(propName);
    }

    public static <T> Subscriber<T> emitSingleResult(AsyncResponse response, Function<T, Response> responseBuilder) {
        return new SingleItemEmitter<>(response, responseBuilder);
    }

    public static final class SingleItemEmitter<T> extends Subscriber<T> {
        private final AsyncResponse response;
        private final Function<T, Response> outputBuilder;
        private T item;

        public SingleItemEmitter(AsyncResponse response, Function<T, Response> outputBuilder) {
            this.response = response;
            this.outputBuilder = outputBuilder;
        }

        @Override public void onCompleted() {
            response.resume(outputBuilder.apply(item));
        }

        @Override public void onError(Throwable e) {
            response.resume(e);
        }

        @Override public void onNext(T t) {
            item = t;
        }
    }

    public static final class ListEmitter<T> extends Subscriber<T> {
        private final AsyncResponse response;
        private final Function<List<T>, Response> outputBuilder;
        private List<T> items = new ArrayList<>();

        public ListEmitter(AsyncResponse response, Function<List<T>, Response> outputBuilder) {
            this.response = response;
            this.outputBuilder = outputBuilder;
        }

        @Override public void onCompleted() {
            response.resume(outputBuilder.apply(items));
        }

        @Override public void onError(Throwable e) {
            response.resume(e);
        }

        @Override public void onNext(T t) {
            items.add(t);
        }
    }
}
