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

package org.apache.seatunnel.spark.tidb.source

import org.apache.seatunnel.common.config.CheckConfigUtil.checkAllExists
import org.apache.seatunnel.common.config.CheckResult
import org.apache.seatunnel.spark.SparkEnvironment
import org.apache.seatunnel.spark.batch.SparkBatchSource
import org.apache.seatunnel.spark.tidb.Config.{DATABASE, PRE_SQL}
import org.apache.spark.sql.{Dataset, Row}

class Tidb extends SparkBatchSource {

  override def prepare(env: SparkEnvironment): Unit = {}

  override def checkConfig(): CheckResult = {
    checkAllExists(config, PRE_SQL, DATABASE)
  }

  override def getData(env: SparkEnvironment): Dataset[Row] = {
    val spark = env.getSparkSession
    spark.sql("use " + config.getString(DATABASE))
    spark.sql(config.getString(PRE_SQL))
  }

  override def getPluginName: String = "TiDB"
}
