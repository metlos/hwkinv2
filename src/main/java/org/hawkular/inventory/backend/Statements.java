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

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;

import rx.Observable;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
final class Statements {
    private static final String TBL_ENTITY_TREE = "entityTree";

    private final RxSession session;
    private final PreparedStatement findByPath;
    private final PreparedStatement getAllEntityPaths;
    private final PreparedStatement insertEntity;
    private final PreparedStatement deleteEntity;
    private final PreparedStatement getAllChildrenPaths;

    public Statements(RxSession session) {
        this.session = session;
        this.findByPath = prepare(session, "SELECT * FROM " + TBL_ENTITY_TREE + " WHERE tenantId = ? AND feedId = ?" +
                " AND entityType = ? AND entityPath = ?");
        this.getAllEntityPaths = prepare(session, "SELECT entityPath FROM " + TBL_ENTITY_TREE);
        this.insertEntity = prepare(session, "INSERT INTO " + TBL_ENTITY_TREE
                + "(tenantId, feedId, entityType, entityPath, name, properties, low, high, lowNum, lowDen, highNum," +
                " highDen, treePath, depth) VALUES" +
                " (    ?    ,    ?  ,      ?    ,     ?     ,   ? ,    ?      ,  ? ,  ?  ,   ?   ,   ?   ,   ?    ," +
                "    ?   ,    ?    ,   ?  )");
        this.deleteEntity = prepare(session, "DELETE FROM " + TBL_ENTITY_TREE + " WHERE tenantId = ? AND feedId = ?" +
                " AND entityType = ? AND entityPath = ?");
        this.getAllChildrenPaths = prepare(session,
                "SELECT entityPath FROM " + TBL_ENTITY_TREE + " WHERE tenantId = ? AND feedId = ? AND low > ?" +
                        " AND high < ? ALLOW FILTERING");
    }

    public Observable<Row> findByPath(String tenantId, String feedId, String entityType, String entityPath) {
        return session.executeAndFetch(findByPath.bind(tenantId, feedId, entityType, entityPath));
    }

    public Observable<Row> getAllEntityPaths() {
        return session.executeAndFetch(getAllEntityPaths.bind());
    }

    public Observable<Void>
    insertEntity(String tenantId, String feedId, String entityType, String entityPath, String name,
                 Map<String, String> properties, BigDecimal low, BigDecimal high, long lowNum, long lowDen,
                 long highNum, long highDen, List<Integer> treePath, int depth) {

        return session.execute(insertEntity.bind(tenantId, feedId, entityType, entityPath, name, properties, low, high,
                lowNum, lowDen, highNum, highDen, treePath, depth)).map(r -> null);
    }

    public Observable<Void> deleteEntity(String tenantId, String feedId, String entityType, String entityPath) {
        return session.execute(deleteEntity.bind(tenantId, feedId, entityType, entityPath)).map(r -> null);
    }

    public Observable<Row> getAllChildrenPaths(String tenantId, String feedId, BigDecimal low, BigDecimal high) {
        return session.executeAndFetch(getAllChildrenPaths.bind(tenantId, feedId, low, high));
    }

    private static PreparedStatement prepare(RxSession session, String statement) {
        return session.prepare(statement).toBlocking().first();
    }
}
