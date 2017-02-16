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
package org.hawkular.inventory.logging;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.ValidIdRange;

/**
 * @author Lukas Krejci
 * @since 2.0.0
 */
@MessageLogger(projectCode = "HAWKINV")
@ValidIdRange(max = 1000)
public interface Log extends BasicLogger {
    Log LOG = Logger.getMessageLogger(Log.class, "org.hawkular.inventory");

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1, value = "Invalid CQL port specified in the configuration: %s. Using the default %s.")
    void warnInvalidCqlPort(String found, String defaultValue, @Cause Throwable throwable);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 2, value = "Invalid max connections per host specified in the configuration: %s." +
            " Using the default %s.")
    void warnInvalidMaxConnections(String found, String defaultValue, @Cause Throwable throwable);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 3, value = "Invalid max requests per connection specified in the configuration: %s." +
            " Using the default %s.")
    void warnInvalidMaxRequests(String found, String defaultValue, @Cause Throwable throwable);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 4, value = "Invalid request timeout specified in the configuration: %s. Using the default %s.")
    void warnInvalidRequestTimeout(String found, String defaultValue, @Cause Throwable throwable);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 5, value = "Invalid connection timeout specified in the configuration: %s. Using the default %s.")
    void warnInvalidConnectionTimeout(String found, String defaultValue, @Cause Throwable throwable);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 6, value = "Invalid schema refresh interval specified in the configuration: %s." +
            " Using the default %s.")
    void warnInvalidSchemaRefreshInterval(String found, String defaultValue, @Cause Throwable throwable);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 7, value = "Invalid page size specified in the configuration: %s. Using the default %s.")
    void warnInvalidPageSize(String found, String defaultValue, @Cause Throwable throwable);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 8, value = "Could not contact the configured Cassandra instance. Will retry in %dms. The error message was: %s")
    void infoCouldNotConnect(int nextAttempt, String errorMessage);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 9, value = "Failed to auto-create tenant '%s'. Subsequent requests will probably fail.")
    void warnFailedToAutocreateTenant(String tenant, @Cause Throwable cause);
}
