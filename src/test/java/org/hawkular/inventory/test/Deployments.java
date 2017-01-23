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

import java.io.File;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
final class Deployments {

    private Deployments() {

    }

    public static WebArchive getFullHawkularInventoryWar() {
        String resourceDir = System.getProperty("resources");
        String testResourceDir = System.getProperty("testResources");
        File schema = new File(resourceDir, "schema/cassalog-schema.groovy");
        File configProps = new File(resourceDir, "hawkular-inventory.properties");
        File manifest = new File(testResourceDir, "test-deployment-manifest.MF");
        File[] deps = Maven.resolver().loadPomFromFile("pom.xml")
                .importRuntimeDependencies().resolve().withTransitivity().asFile();
        return ShrinkWrap.create(WebArchive.class)
                .addPackages(true,
                        "org.hawkular.inventory.annotations",
                        "org.hawkular.inventory.backend",
                        "org.hawkular.inventory.model",
                        "org.hawkular.inventory.rest",
                        "org.hawkular.inventory.logging")
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsResource(new FileAsset(schema), "schema/cassalog-schema.groovy")
                .addAsResource(new FileAsset(configProps), "hawkular-inventory.properties")
                .addAsManifestResource(new FileAsset(manifest), "MANIFEST.MF")
                .addAsLibraries(deps);
    }
}
