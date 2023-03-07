/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.server.cli;

/**
 * Determines available system memory that could be allocated for Elasticsearch, to include JVM heap and other native processes.
 * The "available system memory" is defined as the total system memory which is visible to the Elasticsearch process. For instances
 * in which Elasticsearch is running in a containerized environment (i.e. Docker) this is expected to be the limits set for the container,
 * not the host system.
 * 确定可以分配给Elasticsearch的可用系统内存，包括JVM堆和其他本机进程。
 * “可用系统内存”定义为Elasticsearch进程可见的总系统内存。为实例
 * 如果Elasticsearch运行在一个容器化的环境中(即Docker)，这将是对容器设置的限制，
 * 不是主机系统。
 */
public interface SystemMemoryInfo {

    /**
     *
     * @return total system memory available to heap or native process allocation in bytes
     * 可用于堆或本机进程分配的总系统内存（字节）
     */
    long availableSystemMemory();
}
