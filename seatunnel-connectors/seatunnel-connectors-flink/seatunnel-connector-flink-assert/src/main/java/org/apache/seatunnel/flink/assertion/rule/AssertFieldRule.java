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

package org.apache.seatunnel.flink.assertion.rule;

import lombok.Data;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.Serializable;
import java.util.List;

@Data
public class AssertFieldRule implements Serializable {
    private String fieldName;
    private TypeInformation<?> fieldType;
    private List<AssertValueRule> fieldValueRules;

    @Data
    public static class AssertValueRule implements Serializable {
        private AssertValueRuleType fieldValueRuleType;
        private Double fieldValueRuleValue;
    }

    /**
     * Here is all supported value assert rule type,
     * An exception will be thrown if a field value break the rule
     */
    public enum AssertValueRuleType {
        /**
         * value can't be null
         */
        NOT_NULL,
        /**
         * minimum value of the data
         */
        MIN,
        /**
         * maximum value of the data
         */
        MAX,
        /**
         * minimum string length of a string data
         */
        MIN_LENGTH,
        /**
         * maximum string length of a string data
         */
        MAX_LENGTH
    }
}
