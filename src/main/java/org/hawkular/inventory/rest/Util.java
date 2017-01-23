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

import javax.ws.rs.core.UriInfo;

import org.hawkular.inventory.paths.CanonicalPath;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
final class Util {

    static CanonicalPath getPath(UriInfo uriInfo, int excludedPrefixLength, int excludeSuffixLength) {
        String chopped = uriInfo.getPath(false).substring(excludedPrefixLength);
        if (excludeSuffixLength > 0) {
            chopped = chopped.substring(0, chopped.length() - excludeSuffixLength);
        }

        return CanonicalPath.fromString(chopped);
    }
}
