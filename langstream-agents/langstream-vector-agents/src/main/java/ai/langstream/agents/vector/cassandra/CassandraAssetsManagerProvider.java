/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.langstream.agents.vector.cassandra;

import ai.langstream.api.model.AssetDefinition;
import ai.langstream.api.runner.assets.AssetManager;
import ai.langstream.api.runner.assets.AssetManagerProvider;
import ai.langstream.api.util.ConfigurationUtils;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.servererrors.AlreadyExistsException;
import com.datastax.oss.streaming.ai.datasource.CassandraDataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CassandraAssetsManagerProvider implements AssetManagerProvider {

    @Override
    public boolean supports(String assetType) {
        return "cassandra-table".equals(assetType) || "cassandra-keyspace".equals(assetType);
    }

    @Override
    public AssetManager createInstance(String assetType) {

        switch (assetType) {
            case "cassandra-table":
                return new CassandraTableAssetManager();
            case "cassandra-keyspace":
                return new CassandraKeyspaceAssetManager();
            default:
                throw new IllegalArgumentException();
        }
    }

    private static class CassandraTableAssetManager implements AssetManager {

        @Override
        public boolean assetExists(AssetDefinition assetDefinition) throws Exception {
            String tableName =
                    ConfigurationUtils.getString("table-name", null, assetDefinition.getConfig());
            String keySpace =
                    ConfigurationUtils.getString("keyspace", null, assetDefinition.getConfig());
            log.info("Checking is table {} exists in keyspace {}", tableName, keySpace);
            try (CassandraDataSource datasource = buildDataSource(assetDefinition); ) {
                CqlSession session = datasource.getSession();
                log.info("Getting keyspace {} metadata", keySpace);
                Optional<KeyspaceMetadata> keyspace = session.getMetadata().getKeyspace(keySpace);
                if (!keyspace.isPresent()) {
                    throw new IllegalStateException(
                            "The keyspace "
                                    + keySpace
                                    + " does not exist, "
                                    + "you could use a cassandra-keyspace asset to create it.");
                }
                log.info("Getting table {} metadata", tableName);
                KeyspaceMetadata keyspaceMetadata = keyspace.get();
                Optional<TableMetadata> table = keyspaceMetadata.getTable(tableName);

                if (table.isPresent()) {
                    log.info("Table {} exists", tableName);
                    String describe = table.get().describe(true);
                    log.info("Describe table result: {}", describe);
                } else {
                    log.info("Table {} does not exist", tableName);
                }
                return table.isPresent();
            }
        }

        @Override
        public void deployAsset(AssetDefinition assetDefinition) throws Exception {
            try (CassandraDataSource datasource = buildDataSource(assetDefinition); ) {
                List<String> statements =
                        ConfigurationUtils.getList(
                                "create-statements", assetDefinition.getConfig());
                for (String statement : statements) {
                    log.info("Executing: {}", statement);
                    try {
                        datasource.executeStatement(statement, List.of());
                    } catch (AlreadyExistsException e) {
                        log.info(
                                "Table already exists, maybe it was created by another agent ({})",
                                e.toString());
                    }
                }
            }
        }
    }

    private static class CassandraKeyspaceAssetManager implements AssetManager {

        @Override
        public boolean assetExists(AssetDefinition assetDefinition) throws Exception {
            String keySpace =
                    ConfigurationUtils.getString("keyspace", null, assetDefinition.getConfig());
            log.info("Checking if keyspace {} exists", keySpace);
            try (CassandraDataSource datasource = buildDataSource(assetDefinition); ) {
                CqlSession session = datasource.getSession();
                Optional<KeyspaceMetadata> keyspace = session.getMetadata().getKeyspace(keySpace);
                keyspace.ifPresent(
                        keyspaceMetadata ->
                                log.info(
                                        "Describe keyspace result: {}",
                                        keyspaceMetadata.describe(true)));
                log.info("Result: {}", keyspace);
                return keyspace.isPresent();
            }
        }

        @Override
        public void deployAsset(AssetDefinition assetDefinition) throws Exception {
            try (CassandraDataSource datasource = buildDataSource(assetDefinition); ) {
                List<String> statements =
                        ConfigurationUtils.getList(
                                "create-statements", assetDefinition.getConfig());
                for (String statement : statements) {
                    log.info("Executing: {}", statement);
                    try {
                        datasource.executeStatement(statement, List.of());
                    } catch (AlreadyExistsException e) {
                        log.info(
                                "Keyspace already exists, maybe it was created by another agent ({})",
                                e.toString());
                    }
                }
            }
        }
    }

    private static CassandraDataSource buildDataSource(AssetDefinition assetDefinition) {
        CassandraDataSource dataSource = new CassandraDataSource();
        Map<String, Object> datasourceDefinition =
                (Map<String, Object>) assetDefinition.getConfig().get("datasource");
        Map<String, Object> configuration =
                (Map<String, Object>) datasourceDefinition.get("configuration");
        dataSource.initialize(configuration);
        return dataSource;
    }
}