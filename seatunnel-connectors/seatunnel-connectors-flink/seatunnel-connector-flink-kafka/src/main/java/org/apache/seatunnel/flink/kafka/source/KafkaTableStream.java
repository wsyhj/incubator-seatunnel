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

package org.apache.seatunnel.flink.kafka.source;

import org.apache.seatunnel.common.PropertiesUtil;
import org.apache.seatunnel.common.config.CheckConfigUtil;
import org.apache.seatunnel.common.config.CheckResult;
import org.apache.seatunnel.common.config.TypesafeConfigUtils;
import org.apache.seatunnel.common.utils.JsonUtils;
import org.apache.seatunnel.flink.BaseFlinkSource;
import org.apache.seatunnel.flink.FlinkEnvironment;
import org.apache.seatunnel.flink.enums.FormatType;
import org.apache.seatunnel.flink.stream.FlinkStreamSource;
import org.apache.seatunnel.flink.util.SchemaUtil;
import org.apache.seatunnel.flink.util.TableUtil;

import org.apache.seatunnel.shade.com.typesafe.config.Config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.auto.service.AutoService;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.descriptors.FormatDescriptor;
import org.apache.flink.table.descriptors.Kafka;
import org.apache.flink.table.descriptors.Rowtime;
import org.apache.flink.table.descriptors.Schema;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;

@AutoService(BaseFlinkSource.class)
public class KafkaTableStream implements FlinkStreamSource {

    private static final long serialVersionUID = 5287018194573371428L;
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaTableStream.class);

    private Config config;

    private final Properties kafkaParams = new Properties();
    private String topic;
    private Object schemaInfo;
    private String rowTimeField;
    private String tableName;
    private final String consumerPrefix = "consumer.";
    private long watermark;
    private FormatType format;

    private static final String TOPICS = "topics";
    private static final String ROWTIME_FIELD = "rowtime.field";
    private static final String WATERMARK_VAL = "watermark";
    private static final String SCHEMA = "schema";
    private static final String SOURCE_FORMAT = "format.type";
    private static final String GROUP_ID = "group.id";
    private static final String BOOTSTRAP_SERVERS = "bootstrap.servers";
    private static final String OFFSET_RESET = "offset.reset";
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    @Override
    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public CheckResult checkConfig() {

        CheckResult result = CheckConfigUtil.checkAllExists(config, TOPICS, SCHEMA, SOURCE_FORMAT, RESULT_TABLE_NAME);

        if (result.isSuccess()) {
            Config consumerConfig = TypesafeConfigUtils.extractSubConfig(config, consumerPrefix, false);
            return CheckConfigUtil.checkAllExists(consumerConfig, BOOTSTRAP_SERVERS, GROUP_ID);
        }

        return result;
    }

    @Override
    public void prepare(FlinkEnvironment env) {
        topic = config.getString(TOPICS);
        PropertiesUtil.setProperties(config, kafkaParams, consumerPrefix, false);
        tableName = config.getString(RESULT_TABLE_NAME);
        if (config.hasPath(ROWTIME_FIELD)) {
            rowTimeField = config.getString(ROWTIME_FIELD);
            if (config.hasPath(WATERMARK_VAL)) {
                watermark = config.getLong(WATERMARK_VAL);
            }
        }
        String schemaContent = config.getString(SCHEMA);
        format = FormatType.from(config.getString(SOURCE_FORMAT).trim().toLowerCase());
        schemaInfo = JsonUtils.parseObject(schemaContent);
    }

    @Override
    public String getPluginName() {
        return "KafkaTableStream";
    }

    @Override
    public DataStream<Row> getData(FlinkEnvironment env) {
        StreamTableEnvironment tableEnvironment = env.getStreamTableEnvironment();
        tableEnvironment
                .connect(getKafkaConnect())
                .withFormat(setFormat())
                .withSchema(getSchema())
                .inAppendMode()
                .createTemporaryTable(tableName);
        Table table = tableEnvironment.scan(tableName);
        return TableUtil.tableToDataStream(tableEnvironment, table, true);
    }

    private Schema getSchema() {
        Schema schema = new Schema();
        SchemaUtil.setSchema(schema, schemaInfo, format);
        if (StringUtils.isNotBlank(rowTimeField)) {
            Rowtime rowtime = new Rowtime();
            rowtime.timestampsFromField(rowTimeField);
            rowtime.watermarksPeriodicBounded(watermark);
            schema.rowtime(rowtime);
        }
        return schema;
    }

    private Kafka getKafkaConnect() {
        Kafka kafka = new Kafka().version("universal");
        kafka.topic(topic);
        kafka.properties(kafkaParams);
        if (config.hasPath(OFFSET_RESET)) {
            String reset = config.getString(OFFSET_RESET);
            switch (reset) {
                case "latest":
                    kafka.startFromLatest();
                    break;
                case "earliest":
                    kafka.startFromEarliest();
                    break;
                case "specific":
                    //todo Is json format?
                    String offset = config.getString("offset.reset.specific");
                    Map<Integer, Long> map = JsonUtils.parseObject(offset, new TypeReference<Map<Integer, Long>>() {
                    });
                    kafka.startFromSpecificOffsets(map);
                    break;
                default:
                    break;
            }
        }
        return kafka;
    }

    private FormatDescriptor setFormat() {
        try {
            return SchemaUtil.setFormat(format, config);
        } catch (Exception e) {
            LOGGER.warn("set format exception", e);
        }
        throw new RuntimeException("format config error");
    }

}
