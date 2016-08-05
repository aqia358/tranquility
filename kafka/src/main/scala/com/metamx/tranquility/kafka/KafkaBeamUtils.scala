/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.metamx.tranquility.kafka

import com.metamx.common.scala.untyped.Dict
import com.metamx.tranquility.config.DataSourceConfig
import com.metamx.tranquility.druid.DruidBeams
import com.metamx.tranquility.druid.DruidLocation
import com.metamx.tranquility.finagle.FinagleRegistry
import com.metamx.tranquility.kafka.model.PropertiesBasedKafkaConfig
import com.metamx.tranquility.tranquilizer.Tranquilizer
import org.apache.curator.framework.CuratorFramework
import scala.reflect.runtime.universe.typeTag

object KafkaBeamUtils {
  def createTranquilizer(
                          topic: String,
                          config: DataSourceConfig[PropertiesBasedKafkaConfig],
                          curator: CuratorFramework,
                          finagleRegistry: FinagleRegistry
                          ): Tranquilizer[Array[Byte]] = {
    DruidBeams.fromConfig(config, typeTag[Array[Byte]])
      .location(
        DruidLocation.create(
          config.propertiesBasedConfig.druidIndexingServiceName,
          if (config.propertiesBasedConfig.useTopicAsDataSource) topic else config.dataSource
        )
      )
      .curator(curator)
      .finagleRegistry(finagleRegistry)
      .buildTranquilizer(config.tranquilizerBuilder())
  }

  def useInputTopicAsDecodeTopic(topic: String, config: DataSourceConfig[PropertiesBasedKafkaConfig]): DataSourceConfig[PropertiesBasedKafkaConfig] = {
    val dataSchema = config.specMap.get("dataSchema").get.asInstanceOf[Dict]
    val parser = dataSchema.get("parser").get.asInstanceOf[Dict]
    if ("avro_stream".equals(parser.get("type").toString)) {
      val avroBytesDecoder = parser.get("avroBytesDecoder").get.asInstanceOf[Dict]
      val subjectAndIdConverter = avroBytesDecoder.get("subjectAndIdConverter").get.asInstanceOf[Dict]
      val map = config.specMap.updated("dataSchema", dataSchema.updated("parser", parser.updated("avroBytesDecoder", avroBytesDecoder.updated("subjectAndIdConverter", subjectAndIdConverter.updated("topic", topic)))))
      config.copy(config.dataSource, config.propertiesBasedConfig, map)
    } else {
      config
    }
  }
}
