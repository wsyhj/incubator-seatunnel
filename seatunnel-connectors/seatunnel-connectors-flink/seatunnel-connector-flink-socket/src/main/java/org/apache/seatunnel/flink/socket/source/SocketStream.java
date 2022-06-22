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

package org.apache.seatunnel.flink.socket.source;

import org.apache.seatunnel.flink.BaseFlinkSource;
import org.apache.seatunnel.flink.FlinkEnvironment;
import org.apache.seatunnel.flink.stream.FlinkStreamSource;

import org.apache.seatunnel.shade.com.typesafe.config.Config;

import com.google.auto.service.AutoService;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.api.scala.typeutils.Types;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.types.Row;

@AutoService(BaseFlinkSource.class)
public class SocketStream implements FlinkStreamSource {

    private static final long serialVersionUID = 986629276153771291L;
    private Config config;

    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final int DEFAULT_PORT = 9999;

    private String host = "localhost";
    private int port = DEFAULT_PORT;

    @Override
    public DataStream<Row> getData(FlinkEnvironment env) {
        final StreamExecutionEnvironment environment = env.getStreamExecutionEnvironment();
        return environment.socketTextStream(host, port)
                .map((MapFunction<String, Row>) value -> {
                    Row row = new Row(1);
                    row.setField(0, value);
                    return row;
                }).returns(new RowTypeInfo(Types.STRING()));
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
    public void prepare(FlinkEnvironment prepareEnv) {
        if (config.hasPath(HOST)) {
            host = config.getString(HOST);
        }
        if (config.hasPath(PORT)) {
            port = config.getInt(PORT);
        }
    }

    @Override
    public String getPluginName() {
        return "SocketStream";
    }
}
