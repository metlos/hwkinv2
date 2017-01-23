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
package org.hawkular.inventory.backend;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

import javax.net.ssl.SSLContext;

import org.cassalog.core.Cassalog;
import org.cassalog.core.CassalogBuilder;
import org.hawkular.inventory.logging.Log;
import org.hawkular.inventory.model.Entity;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.rx.cassandra.driver.RxSession;
import org.hawkular.rx.cassandra.driver.RxSessionImpl;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.JdkSSLOptions;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.SSLOptions;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.google.common.collect.ImmutableMap;

import rx.Observable;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
public class InventoryStorage {
    private static final String FAKE_FEED_ID_FOR_TENANT = "<TENANT>";
    private final RxSession session;
    private final Statements statements;
    private final ChildrenCountCache childrenCountCache;

    @SuppressWarnings("unused")
    protected InventoryStorage() {
        session = null;
        statements = null;
        childrenCountCache = null;
    }

    public InventoryStorage(Map<String, String> configuration) {
        Session cSession = connect(configuration);
        String keyspace = configuration.getOrDefault("keyspace", "hawkular_inventory");

        initSchema(cSession, keyspace);

        session = new RxSessionImpl(cSession);
        statements = new Statements(session);
        childrenCountCache = new ChildrenCountCache();
        childrenCountCache.initialize(statements);
    }

    private static Session connect(Map<String, String> configuration) {
        Cluster.Builder clusterBuilder = new Cluster.Builder();
        int port;
        try {
            port = Integer.parseInt(configuration.getOrDefault("port", "9042"));
        } catch (NumberFormatException nfe) {
            Log.LOG.warnInvalidCqlPort(configuration.getOrDefault("port", null), "9042", nfe);
            port = 9042;
        }
        clusterBuilder.withPort(port);
        Arrays.stream(configuration.getOrDefault("nodes", "127.0.0.1").split(","))
                .forEach(clusterBuilder::addContactPoint);

        if (Boolean.parseBoolean(configuration.getOrDefault("use-ssl", "false"))) {
            SSLOptions sslOptions = null;
            try {
                String[] defaultCipherSuites = {"TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_AES_256_CBC_SHA"};
                sslOptions = JdkSSLOptions.builder().withSSLContext(SSLContext.getDefault())
                        .withCipherSuites(defaultCipherSuites).build();
                clusterBuilder.withSSL(sslOptions);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SSL support is required but is not available in the JVM.", e);
            }
        }

        clusterBuilder.withoutJMXReporting();

        int newMaxConnections;
        try {
            newMaxConnections = Integer.parseInt(configuration.getOrDefault("max-connections-per-host", "10"));
        } catch (NumberFormatException nfe) {
            Log.LOG.warnInvalidMaxConnections(configuration.getOrDefault("max-connections-per-host", null), "10", nfe);
            newMaxConnections = 10;
        }
        int newMaxRequests;
        try {
            newMaxRequests = Integer.parseInt(configuration.getOrDefault("max-requests-per-connection", "5000"));
        } catch (NumberFormatException nfe) {
            Log.LOG.warnInvalidMaxRequests(configuration.getOrDefault("max-requests-per-connection", null), "5000",
                    nfe);
            newMaxRequests = 5000;
        }
        int driverRequestTimeout;
        try {
            driverRequestTimeout = Integer.parseInt(configuration.getOrDefault("request-timeout", "12000"));
        } catch (NumberFormatException e) {
            Log.LOG.warnInvalidRequestTimeout(configuration.getOrDefault("request-timeout", null), "12000", e);
            driverRequestTimeout = 12000;
        }
        int driverConnectionTimeout;
        try {
            driverConnectionTimeout = Integer.parseInt(configuration.getOrDefault("connection-timeout", "5000"));
        } catch (NumberFormatException e) {
            Log.LOG.warnInvalidConnectionTimeout(configuration.getOrDefault("connection-timeout", null), "5000", e);
            driverConnectionTimeout = 5000;
        }
        int driverSchemaRefreshInterval;
        try {
            driverSchemaRefreshInterval = Integer.parseInt(
                    configuration.getOrDefault("refresh-interval", "1000"));
        } catch (NumberFormatException e) {
            Log.LOG.warnInvalidSchemaRefreshInterval(configuration.getOrDefault("refresh-interval", null),
                    "1000", e);
            driverSchemaRefreshInterval = 1000;
        }
        int driverPageSize;
        try {
            driverPageSize = Integer.parseInt(configuration.getOrDefault("page-size", "1000"));
        } catch (NumberFormatException e) {
            Log.LOG.warnInvalidPageSize(configuration.getOrDefault("page-size", null), "1000", e);
            driverPageSize = 1000;
        }
        clusterBuilder.withPoolingOptions(new PoolingOptions()
                .setMaxConnectionsPerHost(HostDistance.LOCAL, newMaxConnections)
                .setMaxConnectionsPerHost(HostDistance.REMOTE, newMaxConnections)
                .setMaxRequestsPerConnection(HostDistance.LOCAL, newMaxRequests)
                .setMaxRequestsPerConnection(HostDistance.REMOTE, newMaxRequests)
        ).withSocketOptions(new SocketOptions()
                .setReadTimeoutMillis(driverRequestTimeout)
                .setConnectTimeoutMillis(driverConnectionTimeout)
        ).withQueryOptions(new QueryOptions()
                .setFetchSize(driverPageSize)
                .setRefreshSchemaIntervalMillis(driverSchemaRefreshInterval));

        Cluster cluster = clusterBuilder.build();
        cluster.init();
        Session createdSession = null;
        try {
            createdSession = cluster.connect();
            return createdSession;
        } finally {
            if (createdSession == null) {
                cluster.close();
            }
        }
    }

    public Observable<Entity> findByPath(CanonicalPath path) {
        String tenantId = path.ids().getTenantId();
        String feedId = path.ids().getFeedId();
        feedId = feedId == null ? FAKE_FEED_ID_FOR_TENANT : feedId;
        String entityType = path.getSegment().getElementType().toString();
        String entityPath = path.toString();

        return statements.findByPath(tenantId, feedId, entityType, entityPath).map(r -> {
            String cp = r.getString("entityPath");
            String name = r.getString("name");
            Map<String, String> props = r.getMap("properties", String.class, String.class);

            return new Entity(CanonicalPath.fromString(cp), name, props);
        });
    }

    public Observable<Void> insert(Entity entity) {
        String tenantId = entity.getPath().ids().getTenantId();
        String fId = entity.getPath().ids().getFeedId();
        String feedId = fId == null ? FAKE_FEED_ID_FOR_TENANT : fId;
        CanonicalPath parentPath = entity.getPath().up();

        String entityType = entity.getPath().getSegment().getElementType().toString();
        String entityPath = entity.getPath().toString();
        String name = entity.getName();
        Map<String, String> properties = entity.getProperties();

        if (!parentPath.isDefined()) {
            return statements.insertEntity(tenantId, feedId, entityType, entityPath, name, properties,
                    BigDecimal.ZERO, BigDecimal.ONE, 0L, 1L, 1L, 1L, Collections.singletonList(1), 1);
        } else {
            String parentType = parentPath.isDefined()
                    ? parentPath.getSegment().getElementType().toString()
                    : null;

            return statements.findByPath(tenantId, feedId, parentType, parentPath.toString()).flatMap(parentRow -> {
                List<Integer> treePath = parentRow.getList("treePath", Integer.class);
                int myIndex = childrenCountCache.incrementAndGet(parentPath);
                treePath.add(myIndex);

                FareySequence.Interval interval = FareySequence.intervalForPath(treePath);

                BigDecimal low = interval.getLow().toDecimal();
                BigDecimal high = interval.getHigh().toDecimal();
                long lowNum = interval.getLow().numerator;
                long lowDen = interval.getLow().denominator;
                long highNum = interval.getLow().numerator;
                long highDen = interval.getLow().denominator;
                int depth = treePath.size();

                return statements.insertEntity(tenantId, feedId, entityType, entityPath, name, properties, low, high,
                        lowNum, lowDen, highNum, highDen, treePath, depth);
            });
        }
    }

    public Observable<Void> delete(CanonicalPath cp) {
        String tenantId = cp.ids().getTenantId();
        String feedId = cp.ids().getFeedId();
        String entityType = cp.getSegment().getElementType().toString();
        String entityPath = cp.toString();

        return statements.findByPath(tenantId, feedId, entityType, entityPath)
                .flatMap(entityRow -> {
                    BigDecimal low = entityRow.getDecimal("low");
                    BigDecimal high = entityRow.getDecimal("high");

                    return statements.getAllChildrenPaths(tenantId, feedId, low, high);
                })
                .map(r -> r.getString(0))
                .toSortedList((a, b) -> a.length() - b.length())
                .flatMap(childPaths -> {
                    Observable<Void> chain = Observable.empty();
                    for (String childPath : childPaths) {
                        String childType = CanonicalPath.fromString(childPath).getSegment().getElementType().toString();
                        chain = chain.concatWith(statements.deleteEntity(tenantId, feedId, childType, childPath));
                    }

                    //XXX we're issuing a delete statement for each child here... maybe it'd be better to use IN clause
                    //and do one big statement for all children?
                    return chain;
                }).toList().flatMap(allDone -> statements.deleteEntity(tenantId, feedId, entityType, entityPath));
    }

    private void initSchema(Session session, String keyspace) {
        session.execute("USE system");

        CassalogBuilder builder = new CassalogBuilder();
        Cassalog cassalog = builder.withKeyspace(keyspace).withSession(session).build();
        Map<String, ?> vars = ImmutableMap.of(
                "keyspace", keyspace,
                "reset", true,
                "session", session
        );

        URI script;
        try {
            script = getClass().getResource("/schema/cassalog-schema.groovy").toURI();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to locate the cassalog script to bring up the schema.");
        }
        cassalog.execute(script, vars);

        //CQL injection, anyone?
        session.execute("USE " + keyspace);

        session.execute("INSERT INTO " + keyspace + ".sys_config (config_id, name, value) VALUES " +
                "('org.hawkular.metrics', 'version', '" + getCassandraInventoryVersion() + "')");

    }

    private String getCassandraInventoryVersion() {
        try {
            Enumeration<URL> resources = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                Manifest manifest = new Manifest(resource.openStream());
                String vendorId = manifest.getMainAttributes().getValue("Implementation-Vendor-Id");
                if (vendorId != null && vendorId.equals("org.hawkular.inventory")) {
                    return manifest.getMainAttributes().getValue("Implementation-Version");
                }
            }
            throw new IllegalStateException("Failed to extract the version of Cassandra backend for Hawkular" +
                    " Inventory from the manifest file.");
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to extract the version of Cassandra backend for Hawkular Inventory from the manifest file.",
                    e);
        }
    }
}
