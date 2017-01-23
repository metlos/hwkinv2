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
createKeyspace {
    version '2.0.0.0'
    name keyspace
    author 'Lukas Krejci'
    tags '2.0.0'
    description 'Ensure our keyspace exists.'
}

schemaChange {
    version '2.0.0.1'
    author 'Lukas Krejci'
    tags '2.0.0'
    description 'Create sysconfig table.'
    cql """
CREATE TABLE sys_config (
    config_id text,
    name text,
    value text,
    PRIMARY KEY (config_id, name)
) WITH compaction = { 'class': 'LeveledCompactionStrategy' }
"""
}

schemaChange {
    version '2.0.0.2'
    author 'Lukas Krejci'
    tags '2.0.0'
    description 'Create base tables.'
    cql (["""
CREATE TABLE entityTree (
    tenantId text,
    feedId text,
    entityType text,
    entityPath text,
    name text,
    properties map<text, text>,

    low decimal,
    high decimal,
    lowNum bigint,
    lowDen bigint,
    highNum bigint,
    highDen bigint,
    treePath list<int>,
    depth int,

    PRIMARY KEY ((tenantId, feedId), entityType, entityPath)
) WITH compaction = {'class': 'LeveledCompactionStrategy'}
""", """
CREATE INDEX entityTree_low ON entityTree (low)
""", """
CREATE INDEX entityTree_high ON entityTree (high)
""", """
CREATE INDEX entityTree_depth ON entityTree (depth)
"""])
}
