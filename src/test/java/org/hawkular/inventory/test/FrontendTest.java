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

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.hawkular.inventory.model.Entity;
import org.hawkular.inventory.paths.CanonicalPath;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.extension.rest.client.ArquillianResteasyResource;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
@RunWith(Arquillian.class)
public class FrontendTest {

    @Deployment
    public static WebArchive getDeployment() {
        return Deployments.getFullHawkularInventoryWar();
    }

    @Test
    @RunAsClient
    public void testTenantAutoCreates(@ArquillianResteasyResource WebTarget webTarget) {
        Response response = webTarget.path("/entity/t;tnt").request(MediaType.APPLICATION_JSON).get();
        Entity expected = new Entity(CanonicalPath.of().tenant("tnt").get(), null, null);

        Entity actual = (Entity) response.getEntity();

        Assert.assertEquals(expected, actual);
    }
}
