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

import java.util.EnumSet;
import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.annotations.Configured;
import org.hawkular.inventory.backend.InventoryStorage;
import org.hawkular.inventory.model.SyncRequest;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
@Path("/sync")
@Consumes("application/json")
@Produces("application/json")
public class SyncEndpoint {
    private static final Set<SegmentType> SYNCABLE_TYPES =
            EnumSet.of(SegmentType.f, SegmentType.rt, SegmentType.mt, SegmentType.ot, SegmentType.m, SegmentType.r,
                    SegmentType.d);

    @Inject @Configured
    private InventoryStorage inventory;

    @POST
    @Path("{path:.+}")
    public void sync(@Suspended AsyncResponse response, SyncRequest request,
                         @Context UriInfo uriInfo) throws Exception {
        CanonicalPath root = Util.getPath(uriInfo, "/sync".length(), 0);

        if (!SYNCABLE_TYPES.contains(root.getSegment().getElementType())) {
            throw new IllegalArgumentException("Entities of type " + root.getSegment().getElementType().getSimpleName()
                    + " are not synchronizable.");
        }

        inventory.sync(request).subscribe(response::resume, response::resume);
    }

}
