/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.flink.doris.sink;

import org.apache.seatunnel.common.PropertiesUtil;
import org.apache.seatunnel.common.config.CheckConfigUtil;
import org.apache.seatunnel.common.config.CheckResult;
import org.apache.seatunnel.flink.BaseFlinkSink;
import org.apache.seatunnel.flink.FlinkEnvironment;
import org.apache.seatunnel.flink.batch.FlinkBatchSink;
import org.apache.seatunnel.flink.stream.FlinkStreamSink;

import org.apache.seatunnel.shade.com.typesafe.config.Config;

import com.google.auto.service.AutoService;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.operators.DataSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.BatchTableEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.apache.flink.util.Preconditions;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

@AutoService(BaseFlinkSink.class)
public class DorisSink implements FlinkStreamSink, FlinkBatchSink {

    private static final long serialVersionUID = 4747849769146047770L;
    private static final int DEFAULT_BATCH_SIZE = 5000;
    private static final long DEFAULT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(5);
    private static final String PARALLELISM = "parallelism";

    private Config config;
    private String fenodes;
    private String username;
    private String password;
    private String tableName;
    private String dbName;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private long batchIntervalMs = DEFAULT_INTERVAL_MS;
    private int maxRetries = 1;
    private final Properties streamLoadProp = new Properties();

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public CheckResult checkConfig() {
        return CheckConfigUtil.checkAllExists(config, "fenodes", "user", "password", "table", "database");
    }

    @Override
    public void prepare(FlinkEnvironment prepareEnv) {
        fenodes = config.getString("fenodes");
        username = config.getString("user");
        tableName = config.getString("table");
        password = config.getString("password");
        dbName = config.getString("database");
        if (config.hasPath("batch_size")) {
            batchSize = config.getInt("batch_size");
            Preconditions.checkArgument(batchSize > 0, "batch_size must be greater than 0");
        }
        if (config.hasPath("interval")) {
            batchIntervalMs = config.getInt("interval");
            Preconditions.checkArgument(batchIntervalMs > 0, "interval must be greater than 0");
        }
        if (config.hasPath("max_retries")) {
            maxRetries = config.getInt("max_retries");
            Preconditions.checkArgument(maxRetries > 0, "max_retries must be greater than 0");
        }

        String producerPrefix = "doris.";
        PropertiesUtil.setProperties(config, streamLoadProp, producerPrefix, false);
    }

    @Override
    public String getPluginName() {
        return "DorisSink";
    }

    @Override
    public void outputBatch(FlinkEnvironment env, DataSet<Row> dataSet) {
        batchIntervalMs = 0;
        BatchTableEnvironment tableEnvironment = env.getBatchTableEnvironment();
        Table table = tableEnvironment.fromDataSet(dataSet);
        String[] fieldNames = table.getSchema().getFieldNames();

        DorisStreamLoad dorisStreamLoad = new DorisStreamLoad(fenodes, dbName, tableName, username, password, streamLoadProp);
        DataSink<Row> rowDataSink = dataSet.output(new DorisOutputFormat<>(dorisStreamLoad, fieldNames, batchSize, batchIntervalMs, maxRetries));
        if (config.hasPath(PARALLELISM)) {
            int parallelism = config.getInt(PARALLELISM);
            rowDataSink.setParallelism(parallelism);
        }
    }

    @Override
    public void outputStream(FlinkEnvironment env, DataStream<Row> dataStream) {
        StreamTableEnvironment tableEnvironment = env.getStreamTableEnvironment();
        Table table = tableEnvironment.fromDataStream(dataStream);
        String[] fieldNames = table.getSchema().getFieldNames();

        DorisStreamLoad dorisStreamLoad = new DorisStreamLoad(fenodes, dbName, tableName, username, password, streamLoadProp);
        DataStreamSink<Row> rowDataStreamSink = dataStream.addSink(new DorisSinkFunction<>(new DorisOutputFormat<>(dorisStreamLoad, fieldNames, batchSize, batchIntervalMs, maxRetries)));
        if (config.hasPath(PARALLELISM)) {
            int parallelism = config.getInt(PARALLELISM);
            rowDataStreamSink.setParallelism(parallelism);
        }
    }
}
