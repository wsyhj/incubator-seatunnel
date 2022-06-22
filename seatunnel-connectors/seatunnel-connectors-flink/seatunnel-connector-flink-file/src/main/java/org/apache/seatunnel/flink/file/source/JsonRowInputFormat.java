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

import org.apache.seatunnel.common.utils.JsonUtils;

import org.apache.flink.api.common.io.DelimitedInputFormat;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.ObjectArrayTypeInfo;
import org.apache.flink.api.java.typeutils.ResultTypeQueryable;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.Path;
import org.apache.flink.types.Row;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class JsonRowInputFormat extends DelimitedInputFormat<Row> implements ResultTypeQueryable<Row> {

    private static final long serialVersionUID = 3256896054712026638L;
    private RowTypeInfo rowTypeInfo;

    private static final byte CARRIAGE_RETURN = (byte) '\r';

    private static final byte NEW_LINE = (byte) '\n';

    private String charsetName = "UTF-8";

    public JsonRowInputFormat(Path filePath, Configuration configuration, RowTypeInfo rowTypeInfo) {
        super(filePath, configuration);
        this.rowTypeInfo = rowTypeInfo;
    }

    @Override
    public Row readRecord(Row reuse, byte[] bytes, int offset, int numBytes) throws IOException {
        if (this.getDelimiter() != null
                && this.getDelimiter().length == 1
                && this.getDelimiter()[0] == NEW_LINE
                && offset + numBytes >= 1
                && bytes[offset + numBytes - 1] == CARRIAGE_RETURN) {
            numBytes -= 1;
        }

        String str = new String(bytes, offset, numBytes, this.charsetName);
        Map<String, Object> json = JsonUtils.toMap(JsonUtils.stringToJsonNode(str));
        Row reuseRow;
        if (reuse == null) {
            reuseRow = new Row(rowTypeInfo.getArity());
        } else {
            reuseRow = reuse;
        }
        setJsonRow(reuseRow, json, rowTypeInfo);
        return reuseRow;
    }

    private void setJsonRow(Row row, Map<String, Object> json, RowTypeInfo rowTypeInfo) {
        String[] fieldNames = rowTypeInfo.getFieldNames();
        int i = 0;
        for (String name : fieldNames) {
            Object value = json.get(name);
            if (value instanceof Map) {
                TypeInformation<?> information = rowTypeInfo.getTypeAt(name);
                Row r = new Row(information.getArity());
                setJsonRow(r, (Map<String, Object>) value, (RowTypeInfo) information);
                row.setField(i++, r);
            } else if (value instanceof List) {
                ObjectArrayTypeInfo<?, ?> information =
                        (ObjectArrayTypeInfo<?, ?>) rowTypeInfo.getTypeAt(name);
                List<?> array = (List<?>) value;
                Object[] objects = new Object[array.size()];
                int j = 0;
                for (Object o : array) {
                    if (o instanceof Map) {
                        TypeInformation<?> componentInfo = information.getComponentInfo();
                        Row r = new Row(componentInfo.getArity());
                        setJsonRow(r, (Map<String, Object>) o, (RowTypeInfo) componentInfo);
                        objects[j++] = r;
                    } else {
                        objects[j++] = o;
                    }
                }
                row.setField(i++, objects);
                i++;
            } else {
                row.setField(i++, value);
            }
        }
    }

    @Override
    public TypeInformation<Row> getProducedType() {
        return rowTypeInfo;
    }

    public void setCharsetName(String charsetName) {
        this.charsetName = charsetName;
    }

}
