/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.bootstrap;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.settings.SecureSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.PathUtils;
import org.elasticsearch.core.SuppressForbidden;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

/**
 * Arguments for running Elasticsearch.
 *
 * @param daemonize {@code true} if Elasticsearch should run as a daemon process, or {@code false} otherwise
 * @param quiet {@code false} if Elasticsearch should print log output to the console, {@code true} otherwise
 * @param pidFile absolute path to a file Elasticsearch should write its process id to, or {@code null} if no pid file should be written
 * @param secrets the provided secure settings implementation
 * @param nodeSettings the node settings read from {@code elasticsearch.yml}, the cli and the process environment
 * @param configDir the directory where {@code elasticsearch.yml} and other config exists
 */
public record ServerArgs(boolean daemonize, boolean quiet, Path pidFile, SecureSettings secrets, Settings nodeSettings, Path configDir)
    implements
        Writeable {

    /**
     * 运行Elasticsearch的参数
     *
     * @param daemonize {@code true} Elasticsearch是否作为守护进程运行
     * @param quiet {@code false} 是否在控制台打印输出日志
     * @param pidFile pid文件的绝对路径，写入进城id
     * @param secrets 安全设置
     * @param nodeSettings 从elasticsearch.yml、cli和进程环境中读取的节点设置
     * @param configDir elasticsearch.yml和其他配置所在的目录
     */
    public ServerArgs {
        assert pidFile == null || pidFile.isAbsolute();
        assert secrets != null;
    }

    /**
     * 从二进制流中读取参数的可选构造函数
     */
    public ServerArgs(StreamInput in) throws IOException {
        this(
            in.readBoolean(),
            in.readBoolean(),
            readPidFile(in),
            readSecureSettingsFromStream(in),
            Settings.readSettingsFromStream(in),
            resolvePath(in.readString())
        );
    }

    private static Path readPidFile(StreamInput in) throws IOException {
        String pidFile = in.readOptionalString();
        return pidFile == null ? null : resolvePath(pidFile);
    }

    @SuppressForbidden(reason = "reading local path from stream")
    private static Path resolvePath(String path) {
        return PathUtils.get(path);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeBoolean(daemonize);
        out.writeBoolean(quiet);
        out.writeOptionalString(pidFile == null ? null : pidFile.toString());
        out.writeString(secrets.getClass().getName());
        secrets.writeTo(out);
        nodeSettings.writeTo(out);
        out.writeString(configDir.toString());
    }

    private static SecureSettings readSecureSettingsFromStream(StreamInput in) throws IOException {
        String className = in.readString();
        try {
            return (SecureSettings) Class.forName(className).getConstructor(StreamInput.class).newInstance(in);
        } catch (NoSuchMethodException | ClassNotFoundException | InstantiationException | IllegalAccessException
            | InvocationTargetException cfe) {
            throw new IllegalArgumentException("Invalid secrets implementation [" + className + "]", cfe);
        }
    }
}
