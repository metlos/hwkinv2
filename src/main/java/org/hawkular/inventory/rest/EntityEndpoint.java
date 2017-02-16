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

import static org.hawkular.inventory.rest.Util.emitSingleResult;

import java.net.URI;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.annotations.Configured;
import org.hawkular.inventory.backend.InventoryStorage;
import org.hawkular.inventory.model.Entity;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.SegmentType;
import org.jboss.resteasy.annotations.GZIP;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
@GZIP
@Path("/entity")
@Consumes("application/json")
@Produces("application/json")
public class EntityEndpoint {

    private static final int PATH_PREFIX_LENGTH = "/entity".length();

    @Inject @Configured
    private InventoryStorage storage;

    @Inject
    private HttpServletRequest request;

    @GET
    @Path("{path:.+}")
    public void get(@Suspended AsyncResponse response, @Context UriInfo uriInfo) {
        CanonicalPath cp = Util.getPath(uriInfo, request, PATH_PREFIX_LENGTH, 0);
        storage.findByPath(cp).subscribe(emitSingleResult(response, e -> e == null
                ? Response.status(Response.Status.NOT_FOUND).build()
                : Response.ok(e).build()));
    }

    @POST
    @Path("{path:.+}")
    public void create(@Suspended AsyncResponse response, Entity.Blueprint entity, @Context UriInfo uriInfo) {
        String path = uriInfo.getPath(false);
        int lastSlash = path.lastIndexOf(CanonicalPath.PATH_DELIM);
        if (lastSlash <= 0) {
            lastSlash = 0;
        }

        int lastSemicolon = path.lastIndexOf(CanonicalPath.TYPE_DELIM);
        if (lastSlash < lastSemicolon) {
            lastSlash = 0;
        } else if (lastSlash > 0){
            lastSlash = path.length() - lastSlash - 1;
        }

        CanonicalPath cp = Util.getPath(uriInfo, request, PATH_PREFIX_LENGTH, lastSlash);
        if (lastSlash > 0) {
            String suffix = path.substring(path.length() - lastSlash);
            int typeDelimIdx = suffix.indexOf(CanonicalPath.TYPE_DELIM);
            if (typeDelimIdx > 0) {
                //we got the full path in the URI... just check that the ID in the entity is the same as in the path
                String idInPath = suffix.substring(typeDelimIdx + 1);
                if (entity.getId() != null && !idInPath.equals(entity.getId())) {
                    throw new IllegalArgumentException(
                            "The entity ID in the payload is different from the one in the path.");
                }
            } else {
                if (entity.getId() == null) {
                    throw new IllegalArgumentException("Entity ID not supplied in the payload.");
                }
                SegmentType type = Util.getSegmentTypeFromSimpleName(suffix);
                cp = cp.modified().extend(type, entity.getId()).get();
            }
        }

        Entity e = new Entity(cp, entity.getName(), entity.getProperties());

        //need to create this before the async call, because it would fail if invoked asyncly
        String tenantlessPath = cp.toString().substring(Util.getTenantId(request).length() + 3); //3 = "/t;".length()
        URI uri = uriInfo.getBaseUri().resolve("entity" + tenantlessPath);

        storage.upsert(e).subscribe(emitSingleResult(response, x -> Response.created(uri).build()));
    }

    @PUT
    @Path("{path:.+}")
    public void update(@Suspended AsyncResponse response, Entity.Blueprint entity, @Context UriInfo uriInfo) {
        CanonicalPath cp = Util.getPath(uriInfo, request, PATH_PREFIX_LENGTH, 0);
        Entity e = new Entity(cp, entity.getName(), entity.getProperties());
        storage.upsert(e).subscribe(emitSingleResult(response, x -> Response.noContent().build()));
    }

    @DELETE
    @Path("{path:.+}")
    public void delete(@Suspended AsyncResponse response, @Context UriInfo uriInfo) {
        CanonicalPath cp = Util.getPath(uriInfo, request, PATH_PREFIX_LENGTH, 0);
        storage.delete(cp).subscribe(emitSingleResult(response, x -> Response.noContent().build()));
    }
}
