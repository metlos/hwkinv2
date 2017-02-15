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
import org.hawkular.inventory.model.InventoryStructure;
import org.hawkular.inventory.model.SyncRequest;
import org.hawkular.inventory.paths.CanonicalPath;
import org.hawkular.inventory.paths.RelativePath;
import org.hawkular.rx.cassandra.driver.RxSession;
import org.hawkular.rx.cassandra.driver.RxSessionImpl;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.JdkSSLOptions;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.QueryLogger;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.Row;
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
        statements = new Statements(session, cSession);
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

        QueryLogger queryLogger = QueryLogger.builder()
                .withConstantThreshold(15000)
                .withMaxQueryStringLength(1024)
                .build();

        cluster.register(queryLogger);

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

    public Observable<Void> upsert(Entity entity) throws EntityNotFoundException {
        return _upsert(entity, false).map(e -> null);
    }

    private Observable<FullEntity> _upsert(Entity entity, boolean needFullEntity) {
        String tenantId = entity.getPath().ids().getTenantId();
        String fId = entity.getPath().ids().getFeedId();
        String feedId = fId == null ? FAKE_FEED_ID_FOR_TENANT : fId;
        //get a new standalone CP with no reference to the original path (which the mere .up() call keeps)
        CanonicalPath parentPath = entity.getPath().up().modified().get();

        String entityType = entity.getPath().getSegment().getElementType().toString();
        String entityPath = entity.getPath().toString();
        String name = entity.getName();
        Map<String, String> properties = entity.getProperties();

        return statements.updateIfExists(tenantId, feedId, entityType, entityPath, name, properties).flatMap(update -> {
            boolean applied = update.getBool(0);
            if (applied) {
                Log.LOG.trace("IN UPSERT: Found entity " + entityPath + " already exists.");

                if (!needFullEntity) {
                    return Observable.just(null);
                }

                //k, the entity already exists and we just updated what was possible...
                return statements.findByPath(tenantId, feedId, entityType, entityPath).map(FullEntity::fromRow);
            } else {
                Log.LOG.trace("IN UPSERT: Entity " + entityPath + " doesn't exist. Creating it.");
                //k, need to create it
                if (!parentPath.isDefined()) {
                    FullEntity fe = new FullEntity();
                    fe.entity = entity;
                    fe.low = BigDecimal.ZERO;
                    fe.high = BigDecimal.ONE;
                    fe.lowNum = 0L;
                    fe.lowDen = 1L;
                    fe.highNum = 1L;
                    fe.highDen = 1L;
                    fe.treePath = Collections.singletonList(1);
                    fe.depth = 1;

                    return statements.insertEntity(tenantId, feedId, entityType, entityPath, name, properties,
                            fe.low, fe.high, fe.lowNum, fe.lowDen, fe.highNum, fe.highDen, fe.treePath, fe.depth)
                            .doOnNext(r -> Log.LOG.trace("IN UPSERT: Created tenant " + fe.entity.getPath()))
                            .map(any -> fe);
                } else {
                    String parentType = parentPath.isDefined()
                            ? parentPath.getSegment().getElementType().toString()
                            : null;
                    String parentFeedId = parentPath.ids().getFeedId();
                    parentFeedId = parentFeedId == null ? FAKE_FEED_ID_FOR_TENANT : parentFeedId;

                    return statements.findByPath(tenantId, parentFeedId, parentType, parentPath.toString())
                            .flatMap(parentRow -> {
                                Log.LOG.trace("IN UPSERT: Found parent " + parentPath + " while creating "
                                        + entityPath);
                                List<Integer> treePath = parentRow.getList("treePath", Integer.class);
                                int myIndex = childrenCountCache.incrementAndGet(parentPath);
                                treePath.add(myIndex);

                                FareySequence.Interval interval = FareySequence.intervalForPath(treePath);

                                FullEntity fe = new FullEntity();
                                fe.entity = entity;
                                fe.low = interval.getLow().toDecimal();
                                fe.high = interval.getHigh().toDecimal();
                                fe.lowNum = interval.getLow().numerator;
                                fe.lowDen = interval.getLow().denominator;
                                fe.highNum = interval.getHigh().numerator;
                                fe.highDen = interval.getHigh().denominator;
                                fe.treePath = treePath;
                                fe.depth = treePath.size();

                                return statements.insertEntity(tenantId, feedId, entityType, entityPath, name,
                                        properties, fe.low, fe.high, fe.lowNum, fe.lowDen, fe.highNum, fe.highDen,
                                        fe.treePath, fe.depth)
                                        .doOnNext(r -> Log.LOG.trace("IN UPSERT: Created child tenantId: " + tenantId
                                                + ", feedId: " + feedId + ", entityType: " + entityType
                                                + ", entityPath: " + entityPath + ", fe: " + fe))
                                        .map(any -> fe);
                            }).switchIfEmpty(Observable.error(new EntityNotFoundException("Could not create "
                                    + entity.getPath() + ", because the parent (" + parentPath + ") was not found."
                                    + " (Executed findByPath with args: tenantId: " + tenantId + ", feedId: "
                                    + parentFeedId + ", entityType: " + parentType + ", entityPath: "
                                    + parentPath.toString() + ").")));
                }
            }
        });
    }

    public Observable<Void> delete(CanonicalPath cp) {
        String tenantId = cp.ids().getTenantId();
        String fid = cp.ids().getFeedId();
        String feedId = fid == null ? FAKE_FEED_ID_FOR_TENANT : fid;
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
                        chain = chain.mergeWith(statements.deleteEntity(tenantId, feedId, childType, childPath)
                                .doOnNext(any -> childrenCountCache.decrementAndGet(cp)));
                    }

                    //XXX we're issuing a delete statement for each child here... maybe it'd be better to use IN clause
                    //and do one big statement for all children?
                    return chain;
                }).toList().flatMap(allDone -> statements.deleteEntity(tenantId, feedId, entityType, entityPath)
                        .doOnNext(any -> childrenCountCache.decrementAndGet(cp.up())));
    }

    public Observable<Void> sync(SyncRequest syncRequest) {
        //first delete everything under the root
        CanonicalPath rootPath = syncRequest.getInventoryStructure().getRoot().getPath();
        String tenantId = rootPath.ids().getTenantId();
        String feedId = rootPath.ids().getFeedId();

        Map<CanonicalPath, Entity> entities = syncRequest.getInventoryStructure().getAllEntities();

        return _upsert(syncRequest.getInventoryStructure().getRoot(), true).flatMap(fe -> {
            Observable<Void> deleteWork = statements.getAllChildrenPaths(tenantId, feedId, fe.low, fe.high)
                    .map(r -> CanonicalPath.fromString(r.getString(0)))
                    .flatMap(cp -> {
                        if (!entities.containsKey(cp)) {
                            String childType = cp.getSegment().getElementType().toString();
                            String childPath = cp.toString();
                            Log.LOG.trace("IN SYNC: Deleting " + childPath + ", because it's not in the sync request.");
                            return statements.deleteEntity(tenantId, feedId, childType, childPath)
                                    .doOnNext(any -> childrenCountCache.decrementAndGet(cp.up()));
                        } else {
                            return Observable.empty();
                        }
                    });

            //concat the inserts after the deletes so that the child counts don't get mixed...
            return deleteWork
                    .concatWith(insertRecursively(syncRequest.getInventoryStructure(), RelativePath.empty().get()));
        });
    }

    private Observable<Void> insertRecursively(InventoryStructure struct, RelativePath parent) {
        Observable<Void> work = Observable.empty();
        for (Entity child : struct.getChildren(parent)) {
            RelativePath childAsNewParent = parent.modified().extend(child.getPath().getSegment()).get();

            Observable<Void> childWork = upsert(child).concatWith(insertRecursively(struct, childAsNewParent));

            work = work.mergeWith(childWork);
        }

        return work;
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

    private static final class FullEntity {
        Entity entity;
        BigDecimal low;
        BigDecimal high;
        long lowNum;
        long lowDen;
        long highNum;
        long highDen;
        List<Integer> treePath;
        int depth;

        static FullEntity fromRow(Row r) {
            FullEntity fe = new FullEntity();
            Entity e = new Entity(CanonicalPath.fromString(r.getString("entityPath")), r.getString("name"),
                    r.getMap("properties", String.class, String.class));

            fe.entity = e;
            fe.low = r.getDecimal("low");
            fe.high = r.getDecimal("high");
            fe.lowNum = r.getLong("lowNum");
            fe.lowDen = r.getLong("lowDen");
            fe.highNum = r.getLong("highNum");
            fe.highDen = r.getLong("highDen");
            fe.treePath = r.getList("treePath", Integer.class);
            fe.depth = r.getInt("depth");

            return fe;
        }

        @Override public String toString() {
            return "FullEntity[depth=" + depth +
                    ", entity=" + entity +
                    ", high=" + high +
                    ", highDen=" + highDen +
                    ", highNum=" + highNum +
                    ", low=" + low +
                    ", lowDen=" + lowDen +
                    ", lowNum=" + lowNum +
                    ", treePath=" + treePath +
                    ']';
        }
    }
}
