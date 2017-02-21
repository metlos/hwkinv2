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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.hawkular.rx.cassandra.driver.RxSession;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

import rx.Observable;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
final class Statements {
    private static final String TBL_ENTITY_TREE = "entityTree";
    private static final String TBL_RELATIONSHIP = "relationship";
    private static final String TBL_RELATIONSHIP_OUT = "relationship_out";
    private static final String TBL_RELATIONSHIP_IN = "relationship_in";

    private final RxSession session;
    private final Session cassSession;
    private final PreparedStatement findByPath;
    private final PreparedStatement getAllEntityPaths;
    private final PreparedStatement insertEntity;
    private final PreparedStatement deleteEntity;
    private final PreparedStatement getAllChildrenPaths;
    private final PreparedStatement getAllChildren;
    private final PreparedStatement updateEntityIfExists;
    private final PreparedStatement insertRelationship;
    private final PreparedStatement insertRelationshipOut;
    private final PreparedStatement insertRelationshipIn;
    private final PreparedStatement findOutRelationships;
    private final PreparedStatement findInRelationships;
    private final PreparedStatement updateRelationshipIfExists;
    private final PreparedStatement updateOutRelationshipIfExists;
    private final PreparedStatement updateInRelationshipIfExists;
    private final PreparedStatement deleteRelationship;
    private final PreparedStatement deleteOutRelationship;
    private final PreparedStatement deleteInRelationship;

    public Statements(RxSession session, Session cassSession) {
        this.session = session;
        this.cassSession = cassSession;
        this.findByPath = prepare(session, "SELECT * FROM " + TBL_ENTITY_TREE + " WHERE tenantId = ? AND feedId = ?" +
                " AND entityType = ? AND entityPath = ?");
        this.getAllEntityPaths = prepare(session, "SELECT entityPath FROM " + TBL_ENTITY_TREE);
        //"update" intentional, because C*'s update is actually an upsert
        this.insertEntity = prepare(session, "INSERT INTO " + TBL_ENTITY_TREE
                + " (name, properties, low, high, lowNum, lowDen, highNum, highDen, treePath, depth, tenantId," +
                " feedId, entityType, entityPath) VALUES" +
                "   ( ?  ,    ?      ,  ? ,   ? ,   ?   ,    ?  ,   ?    ,   ?    ,   ?     ,  ?   ,       ? ," +
                "    ?  ,    ?      ,    ?      ) IF NOT EXISTS");
        this.deleteEntity = prepare(session, "DELETE FROM " + TBL_ENTITY_TREE + " WHERE tenantId = ? AND feedId = ?" +
                " AND entityType = ? AND entityPath = ?");
        this.getAllChildrenPaths = prepare(session,
                "SELECT entityPath FROM " + TBL_ENTITY_TREE + " WHERE tenantId = ? AND feedId = ? AND low > ?" +
                        " AND high <= ? ALLOW FILTERING");
        this.getAllChildren = prepare(session,
                "SELECT * FROM " + TBL_ENTITY_TREE + " WHERE tenantId = ? AND feedId = ? AND low > ?" +
                        " AND high <= ? ALLOW FILTERING");
        this.updateEntityIfExists = prepare(session,
                "UPDATE " + TBL_ENTITY_TREE + " SET name = ?, properties = ? WHERE tenantId = ? AND feedId = ? " +
                        "AND entityType = ? AND entityPath = ? IF EXISTS");
        this.insertRelationship = prepare(session, "INSERT INTO " + TBL_RELATIONSHIP + " (cp, name, properties)" +
                " VALUES (?, ?, ?) IF NOT EXISTS");
        this.insertRelationshipOut = prepare(session,
                "INSERT INTO " + TBL_RELATIONSHIP_OUT + " (source_cp, name, target_cp, properties)" +
                        " VALUES (?, ?, ?, ?)");
        this.insertRelationshipIn = prepare(session,
                "INSERT INTO " + TBL_RELATIONSHIP_IN + " (target_cp, name, source_cp, properties)" +
                        " VALUES (?, ?, ?, ?)");
        this.findOutRelationships = prepare(session,
                "SELECT * FROM " + TBL_RELATIONSHIP_OUT + " WHERE source_cp = ? AND name = ?");
        this.findInRelationships = prepare(session,
                "SELECT * FROM " + TBL_RELATIONSHIP_IN + " WHERE target_cp = ? AND name = ?");
        this.updateRelationshipIfExists = prepare(session,
                "UPDATE " + TBL_RELATIONSHIP + " SET properties = ? WHERE cp = ? IF EXISTS");
        this.updateOutRelationshipIfExists = prepare(session,
                "UPDATE " + TBL_RELATIONSHIP_OUT + " SET properties = ?" +
                        " WHERE source_cp = ? AND name = ? AND target_cp = ? IF EXISTS");
        this.updateInRelationshipIfExists = prepare(session,
                "UPDATE " + TBL_RELATIONSHIP_IN + " SET properties = ?" +
                        " WHERE target_cp = ? AND name = ? AND source_cp = ? IF EXISTS");
        this.deleteRelationship = prepare(session,
                "DELETE FROM " + TBL_RELATIONSHIP + " WHERE cp = ?");
        this.deleteOutRelationship = prepare(session,
                "DELETE FROM " + TBL_RELATIONSHIP_OUT + " WHERE source_cp = ? AND name = ? AND target_cp = ?");
        this.deleteInRelationship = prepare(session,
                "DELETE FROM " + TBL_RELATIONSHIP_IN + " WHERE target_cp = ? AND name = ? AND source_cp = ?");
    }

    public Observable<Row> findByPath(String tenantId, String feedId, String entityType, String entityPath) {
        return lazyRows(findByPath.bind(tenantId, feedId, entityType, entityPath));
    }

    public Observable<Row> getAllEntityPaths() {
        return lazyRows(getAllEntityPaths.bind());
    }

    public Observable<Void>
    insertEntity(String tenantId, String feedId, String entityType, String entityPath, String name,
                 Map<String, String> properties, BigDecimal low, BigDecimal high, long lowNum, long lowDen,
                 long highNum, long highDen, List<Integer> treePath, int depth) {

        return lazyResultSet(insertEntity.bind(name, properties, low, high, lowNum, lowDen, highNum, highDen,
                treePath, depth, tenantId, feedId, entityType, entityPath)).map(r -> null);
    }

    public Observable<Void> insertRelationship(String relCp, String name, Map<String, String> properties) {
        return lazyResultSet(insertRelationship.bind(relCp, name, properties)).map(r -> null);
    }

    public Observable<Void> insertRelationshipOut(String sourceCp, String name, String targetCp,
                                                  Map<String, String> properties) {
        return lazyResultSet(insertRelationshipOut.bind(sourceCp, name, targetCp, properties)).map(r -> null);
    }

    public Observable<Void> insertRelationshipIn(String targetCp, String name, String sourceCp,
                                                 Map<String, String> properties) {
        return lazyResultSet(insertRelationshipIn.bind(targetCp, name, sourceCp, properties)).map(r -> null);
    }

    public Observable<Void> updateRelationshipIfExists(String relCp, Map<String, String> properties) {
        return lazyResultSet(updateRelationshipIfExists.bind(properties, relCp)).map(r -> null);
    }

    public Observable<Void> updateInRelationshipIfExists(String targetCp, String name, String sourceCp,
                                                         Map<String, String> properties) {
        return lazyResultSet(updateInRelationshipIfExists.bind(properties, targetCp, name, sourceCp)).map(r -> null);
    }

    public Observable<Void> updateOutRelationshipIfExists(String sourceCp, String name, String targetCp,
                                                          Map<String, String> properties) {
        return lazyResultSet(updateOutRelationshipIfExists.bind(properties, sourceCp, name, targetCp)).map(r -> null);
    }

    public Observable<Row> findOutRelationships(String sourceCp, String name) {
        return lazyRows(findOutRelationships.bind(sourceCp, name));
    }

    public Observable<Row> findInRelationships(String targetCp, String name) {
        return lazyRows(findInRelationships.bind(targetCp, name));
    }

    public Observable<Void> deleteEntity(String tenantId, String feedId, String entityType, String entityPath) {
        return lazyResultSet(deleteEntity.bind(tenantId, feedId, entityType, entityPath)).map(r -> null);
    }

    public Observable<Void> deleteRelationship(String relCp) {
        return lazyResultSet(deleteRelationship.bind(relCp)).map(x -> null);
    }

    public Observable<Void> deleteOutRelationship(String sourceCp, String name, String targetCp) {
        return lazyResultSet(deleteOutRelationship.bind(sourceCp, name, targetCp)).map(x -> null);
    }

    public Observable<Void> deleteInRelationship(String targetCp, String name, String sourceCp) {
        return lazyResultSet(deleteInRelationship.bind(targetCp, name, sourceCp)).map(x -> null);
    }

    public Observable<Row> getAllChildrenPaths(String tenantId, String feedId, BigDecimal low, BigDecimal high) {
        return lazyRows(getAllChildrenPaths.bind(tenantId, feedId, low, high));
    }

    public Observable<Row> getAllChildren(String tenantId, String feedId, BigDecimal low, BigDecimal high) {
        return lazyRows(getAllChildren.bind(tenantId, feedId, low, high));
    }

    public Observable<Row> updateIfExists(String tenantId, String feedId, String entityType, String entityPath,
                                          String name, Map<String, String> properties) {
        return lazyRows(updateEntityIfExists.bind(name, properties, tenantId, feedId, entityType, entityPath));
    }

    private Observable<Row> lazyRows(BoundStatement st) {
        return Observable.just(1).flatMap(one -> session.executeAndFetch(st));
    }

    private Observable<ResultSet> lazyResultSet(BoundStatement st) {
        return Observable.just(1).flatMap(one -> session.execute(st));
    }
    private static PreparedStatement prepare(RxSession session, String statement) {
        return session.prepare(statement).toBlocking().first();
    }
}
