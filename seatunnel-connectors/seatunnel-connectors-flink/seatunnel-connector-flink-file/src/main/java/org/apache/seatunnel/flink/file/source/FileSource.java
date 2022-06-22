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

package org.apache.seatunnel.flink.file.source;

import org.apache.seatunnel.common.config.CheckConfigUtil;
import org.apache.seatunnel.common.config.CheckResult;
import org.apache.seatunnel.common.utils.JsonUtils;
import org.apache.seatunnel.flink.BaseFlinkSource;
import org.apache.seatunnel.flink.FlinkEnvironment;
import org.apache.seatunnel.flink.batch.FlinkBatchSource;
import org.apache.seatunnel.flink.enums.FormatType;
import org.apache.seatunnel.flink.util.SchemaUtil;

import org.apache.seatunnel.shade.com.typesafe.config.Config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auto.service.AutoService;
import org.apache.avro.Schema;
import org.apache.flink.api.common.io.InputFormat;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.io.RowCsvInputFormat;
import org.apache.flink.api.java.operators.DataSource;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.parquet.ParquetRowInputFormat;
import org.apache.flink.orc.OrcRowInputFormat;
import org.apache.flink.types.Row;
import org.apache.parquet.avro.AvroSchemaConverter;
import org.apache.parquet.schema.MessageType;

import java.util.List;
import java.util.Map;

@AutoService(BaseFlinkSource.class)
public class FileSource implements FlinkBatchSource {

    private static final long serialVersionUID = -5206798549756998426L;
    private static final int DEFAULT_BATCH_SIZE = 1000;

    private Config config;

    private InputFormat<Row, ?> inputFormat;

    private static final String PATH = "path";
    private static final String SOURCE_FORMAT = "format.type";
    private static final String SCHEMA = "schema";
    private static final String PARALLELISM = "parallelism";

    @Override
    public DataSet<Row> getData(FlinkEnvironment env) {
        DataSource<Row> dataSource = env.getBatchEnvironment().createInput(inputFormat);
        if (config.hasPath(PARALLELISM)) {
            int parallelism = config.getInt(PARALLELISM);
            return dataSource.setParallelism(parallelism);
        }
        return dataSource;
    }

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
        return CheckConfigUtil.checkAllExists(config, PATH, SOURCE_FORMAT, SCHEMA);
    }

    @Override
    public void prepare(FlinkEnvironment env) {
        String path = config.getString(PATH);
        FormatType format = FormatType.from(config.getString(SOURCE_FORMAT).trim().toLowerCase());
        Path filePath = new Path(path);
        switch (format) {
            case JSON:
                ObjectNode jsonSchemaInfo = JsonUtils.parseObject(config.getString(SCHEMA));
                RowTypeInfo jsonInfo = SchemaUtil.getTypeInformation(jsonSchemaInfo);
                inputFormat = new JsonRowInputFormat(filePath, null, jsonInfo);
                break;
            case PARQUET:
                final Schema parse = new Schema.Parser().parse(config.getString(SCHEMA));
                final MessageType messageType = new AvroSchemaConverter().convert(parse);
                inputFormat = new ParquetRowInputFormat(filePath, messageType);
                break;
            case ORC:
                this.inputFormat = new OrcRowInputFormat(path, config.getString(SCHEMA), null, DEFAULT_BATCH_SIZE);
                break;
            case CSV:
                List<Map<String, String>> csvSchemaInfo = JsonUtils.parseObject(config.getString(SCHEMA),
                        new TypeReference<List<Map<String, String>>>() {
                        });
                TypeInformation<?>[] csvType = SchemaUtil.getCsvType(csvSchemaInfo);
                this.inputFormat = new RowCsvInputFormat(filePath, csvType, true);
                break;
            case TEXT:
                inputFormat = new TextRowInputFormat(filePath);
                break;
            default:
                throw new RuntimeException("Format '" + format + "' is not supported");
        }

    }

    @Override
    public String getPluginName() {
        return "FileSource";
    }
}
