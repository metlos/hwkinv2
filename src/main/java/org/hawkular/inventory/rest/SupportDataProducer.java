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

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import org.hawkular.inventory.annotations.Configured;
import org.hawkular.inventory.backend.InventoryStorage;
import org.hawkular.inventory.logging.Log;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
@ApplicationScoped
public class SupportDataProducer {
    private static final String EXTERNAL_CONF_FILE_PROPERTY_NAME = "hawkular-inventory.conf";

    private static Map<String, String> getConfiguration() throws IOException {
        Map<String, String> config = getDefaultConfiguration();

        Map<String, String> ret = new HashMap<>();
        ret.put("nodes", Util.getConfigValue(config, "hawkular.inventory.cassandra.nodes",
                Arrays.asList("hawkular.inventory.cassandra.nodes", "hawkular.metrics.cassandra.nodes"),
                Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_NODES", "CASSANDRA_NODES")));

        ret.put("port", Util.getConfigValue(config, "hawkular.inventory.cassandra.port",
                Arrays.asList("hawkular.inventory.cassandra.port", "hawkular.metrics.cassandra.port"),
                Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_PORT", "CASSANDRA_CQL_PORT")));

        ret.put("use-ssl", Util.getConfigValue(config, "hawkular.inventory.cassandra.use-ssl",
                Arrays.asList("hawkular.inventory.cassandra.use-ssl", "hawkular.metrics.cassandra.use-ssl"),
                Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_USE_SSL", "CASSANDRA_USESSL")));

        ret.put("max-connections-per-host",
                Util.getConfigValue(config, "hawkular.inventory.cassandra.max-connections-per-host",
                        Arrays.asList("hawkular.inventory.cassandra.max-connections-per-host",
                                "hawkular.metrics.cassandra.max-connections-per-host"),
                        Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_MAX_CONN_HOST", "CASSANDRA_MAX_CONN_HOST")));

        ret.put("max-requests-per-connection",
                Util.getConfigValue(config, "hawkular.inventory.cassandra.max-requests-per-connection",
                        Arrays.asList("hawkular.inventory.cassandra.max-requests-per-connection",
                                "hawkular.metrics.cassandra.max-requests-per-connection"),
                        Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_MAX_REQUEST_CONN", "CASSANDRA_MAX_REQUEST_CONN")));

        ret.put("request-timeout", Util.getConfigValue(config, "hawkular.inventory.cassandra.request-timeout",
                Arrays.asList("hawkular.inventory.cassandra.request-timeout",
                        "hawkular.metrics.cassandra.request-timeout"),
                Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_REQUEST_TIMEOUT", "CASSANDRA_REQUEST_TIMEOUT")));

        ret.put("connection-timeout", Util.getConfigValue(config, "hawkular.inventory.cassandra.connection-timeout",
                Arrays.asList("hawkular.inventory.cassandra.connection-timeout",
                        "hawkular.metrics.cassandra.connection-timeout"),
                Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_CONNECTION_TIMEOUT", "CASSANDRA_CONNECTION_TIMEOUT")));

        ret.put("refresh-interval", Util.getConfigValue(config, "hawkular.inventory.cassandra.schema.refresh-interval",
                Arrays.asList("hawkular.inventory.cassandra.schema.refresh-interval",
                        "hawkular.metrics.cassandra.schema.refresh-interval"),
                Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_SCHEMA_REFRESH_INTERVAL",
                        "CASSANDRA_SCHEMA_REFRESH_INTERVAL")));

        ret.put("page-size", Util.getConfigValue(config, "hawkular.inventory.cassandra.page-size",
                Arrays.asList("hawkular.inventory.cassandra.page-size",
                        "hawkular.metrics.cassandra.page-size"),
                Arrays.asList("HAWKULAR_INVENTORY_CASSANDRA_PAGE_SIZE", "PAGE_SIZE")));

        ret = ret.entrySet().stream().filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return ret;
    }

    private static Map<String, String> getDefaultConfiguration() throws IOException {
        URL location = getConfigurationFile();

        Map<String, String> definedProperties = new HashMap<>();
        for (String key : System.getProperties().stringPropertyNames()) {
            definedProperties.put(key, System.getProperty(key));
        }

        try (Reader rdr = new TokenReplacingReader(new InputStreamReader(location.openStream(), "UTF-8"),
                definedProperties)) {
            Properties ps = new Properties();
            ps.load(rdr);

            Map<String, String> ret = new HashMap<>();
            for (String key : ps.stringPropertyNames()) {
                ret.put(key, ps.getProperty(key));
            }

            return ret;
        }
    }

    private static URL getConfigurationFile() throws IOException {
        String confFileName = System.getProperty(EXTERNAL_CONF_FILE_PROPERTY_NAME);

        File confFile;

        if (confFileName == null) {
            confFile = new File(System.getProperty("user.home"), "." + EXTERNAL_CONF_FILE_PROPERTY_NAME);
            if (!confFile.exists()) {
                confFile = null;
            }
        } else {
            confFile = new File(confFileName);
        }

        return confFile == null
                ? SupportDataProducer.class.getClassLoader().getResource("/hawkular-inventory.properties")
                : confFile.toURI().toURL();
    }

    @Produces @ApplicationScoped @Configured
    public Map<String, String> createConfiguration() throws IOException {
        return getDefaultConfiguration();
    }

    @Produces @ApplicationScoped @Configured
    public InventoryStorage createInventoryStorage() throws IOException {
        Random rnd = new Random();
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Interrupted while waiting for cassandra to come up. Giving up..");
            }

            try {
                return new InventoryStorage(getConfiguration());
            } catch (Exception e) {
                int nextAttempt = rnd.nextInt(2000) + 1000;
                Log.LOG.infoCouldNotConnect(nextAttempt, e.getMessage());
                try {
                    Thread.sleep(nextAttempt);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
