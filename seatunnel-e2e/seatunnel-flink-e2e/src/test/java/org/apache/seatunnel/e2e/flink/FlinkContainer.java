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

package org.apache.seatunnel.e2e.flink;

import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * This class is the base class of FlinkEnvironment test.
 * The before method will create a Flink cluster, and after method will close the Flink cluster.
 * You can use {@link FlinkContainer#executeSeaTunnelFlinkJob} to submit a seatunnel config and run a seatunnel job.
 */
public abstract class FlinkContainer {

    private static final Logger LOG = LoggerFactory.getLogger(FlinkContainer.class);

    private static final String FLINK_DOCKER_IMAGE = "flink:1.13.6-scala_2.11";
    protected static final Network NETWORK = Network.newNetwork();

    protected GenericContainer<?> jobManager;
    protected GenericContainer<?> taskManager;
    private static final Path PROJECT_ROOT_PATH = Paths.get(System.getProperty("user.dir")).getParent().getParent();
    private static final String SEATUNNEL_FLINK_BIN = "start-seatunnel-flink.sh";
    private static final String SEATUNNEL_FLINK_JAR = "seatunnel-core-flink.jar";
    private static final String PLUGIN_MAPPING_FILE = "plugin-mapping.properties";
    private static final String SEATUNNEL_HOME = "/tmp/flink/seatunnel";
    private static final String SEATUNNEL_BIN = Paths.get(SEATUNNEL_HOME, "bin").toString();
    private static final String SEATUNNEL_LIB = Paths.get(SEATUNNEL_HOME, "lib").toString();
    private static final String SEATUNNEL_CONNECTORS = Paths.get(SEATUNNEL_HOME, "connectors").toString();

    private static final int WAIT_FLINK_JOB_SUBMIT = 5000;

    private static final String FLINK_PROPERTIES = String.join(
        "\n",
        Arrays.asList(
            "jobmanager.rpc.address: jobmanager",
            "taskmanager.numberOfTaskSlots: 10",
            "parallelism.default: 4",
            "env.java.opts: -Doracle.jdbc.timezoneAsRegion=false"));

    @Before
    public void before() {
        jobManager = new GenericContainer<>(FLINK_DOCKER_IMAGE)
                .withCommand("jobmanager")
                .withNetwork(NETWORK)
                .withNetworkAliases("jobmanager")
                .withExposedPorts()
                .withEnv("FLINK_PROPERTIES", FLINK_PROPERTIES)
                .withLogConsumer(new Slf4jLogConsumer(LOG));

        taskManager =
                new GenericContainer<>(FLINK_DOCKER_IMAGE)
                        .withCommand("taskmanager")
                        .withNetwork(NETWORK)
                        .withNetworkAliases("taskmanager")
                        .withEnv("FLINK_PROPERTIES", FLINK_PROPERTIES)
                        .dependsOn(jobManager)
                        .withLogConsumer(new Slf4jLogConsumer(LOG));

        Startables.deepStart(Stream.of(jobManager)).join();
        Startables.deepStart(Stream.of(taskManager)).join();
        copySeaTunnelFlinkFile();
        LOG.info("Flink containers are started.");
    }

    @After
    public void close() {
        if (taskManager != null) {
            taskManager.stop();
        }
        if (jobManager != null) {
            jobManager.stop();
        }
    }

    public Container.ExecResult executeSeaTunnelFlinkJob(String confFile) throws IOException, InterruptedException {
        final String confPath = getResource(confFile);
        if (!new File(confPath).exists()) {
            throw new IllegalArgumentException(confFile + " doesn't exist");
        }
        final String targetConfInContainer = Paths.get("/tmp", confFile).toString();
        jobManager.copyFileToContainer(MountableFile.forHostPath(confPath), targetConfInContainer);

        // Running IT use cases under Windows requires replacing \ with /
        String conf = targetConfInContainer.replaceAll("\\\\", "/");
        final List<String> command = new ArrayList<>();
        command.add(Paths.get(SEATUNNEL_HOME, "bin/start-seatunnel-flink.sh").toString());
        command.add("--config " + conf);

        Container.ExecResult execResult = jobManager.execInContainer("bash", "-c", String.join(" ", command));
        LOG.info(execResult.getStdout());
        LOG.error(execResult.getStderr());
        // wait job start
        Thread.sleep(WAIT_FLINK_JOB_SUBMIT);
        return execResult;
    }

    protected void copySeaTunnelFlinkFile() {
        // copy lib
        String seatunnelCoreFlinkJarPath = PROJECT_ROOT_PATH
            + "/seatunnel-core/seatunnel-core-flink/target/seatunnel-core-flink.jar";
        jobManager.copyFileToContainer(
            MountableFile.forHostPath(seatunnelCoreFlinkJarPath),
            Paths.get(SEATUNNEL_LIB, SEATUNNEL_FLINK_JAR).toString());

        // copy bin
        String seatunnelFlinkBinPath = PROJECT_ROOT_PATH + "/seatunnel-core/seatunnel-core-flink/src/main/bin/start-seatunnel-flink.sh";
        jobManager.copyFileToContainer(
            MountableFile.forHostPath(seatunnelFlinkBinPath),
            Paths.get(SEATUNNEL_BIN, SEATUNNEL_FLINK_BIN).toString());

        // copy connectors
        File jars = new File(PROJECT_ROOT_PATH +
            "/seatunnel-connectors/seatunnel-connectors-flink-dist/target/lib");
        Arrays.stream(Objects.requireNonNull(jars.listFiles(f -> f.getName().startsWith("seatunnel-connector-flink"))))
            .forEach(jar ->
                jobManager.copyFileToContainer(
                    MountableFile.forHostPath(jar.getAbsolutePath()),
                    getConnectorPath(jar.getName())));

        // copy plugin-mapping.properties
        jobManager.copyFileToContainer(
            MountableFile.forHostPath(PROJECT_ROOT_PATH + "/seatunnel-connectors/plugin-mapping.properties"),
            Paths.get(SEATUNNEL_CONNECTORS, PLUGIN_MAPPING_FILE).toString());
    }

    private String getResource(String confFile) {
        return System.getProperty("user.dir") + "/src/test/resources" + confFile;
    }

    private String getConnectorPath(String fileName) {
        return Paths.get(SEATUNNEL_CONNECTORS, "flink", fileName).toString();
    }

}
