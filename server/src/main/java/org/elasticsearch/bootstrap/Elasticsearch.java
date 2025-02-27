/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.bootstrap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.lucene.util.Constants;
import org.apache.lucene.util.StringHelper;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.Version;
import org.elasticsearch.common.ReferenceDocs;
import org.elasticsearch.common.filesystem.FileSystemNatives;
import org.elasticsearch.common.io.stream.InputStreamStreamInput;
import org.elasticsearch.common.logging.LogConfigurator;
import org.elasticsearch.common.network.IfConfig;
import org.elasticsearch.common.settings.SecureSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.BoundTransportAddress;
import org.elasticsearch.core.IOUtils;
import org.elasticsearch.core.SuppressForbidden;
import org.elasticsearch.env.Environment;
import org.elasticsearch.jdk.JarHell;
import org.elasticsearch.monitor.jvm.HotThreads;
import org.elasticsearch.monitor.jvm.JvmInfo;
import org.elasticsearch.monitor.os.OsProbe;
import org.elasticsearch.monitor.process.ProcessProbe;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Permission;
import java.security.Security;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.elasticsearch.bootstrap.BootstrapSettings.SECURITY_FILTER_BAD_DEFAULTS_SETTING;

/**
 * elasticsearch 服务开启从这个类开始
 */
class Elasticsearch {

    /**
     * elasticsearch 服务启动的入口方法
     */
    public static void main(final String[] args) {

        //初始化准备第一步
        Bootstrap bootstrap = initPhase1();
        assert bootstrap != null;

        try {
            //初始化准备第二步
            initPhase2(bootstrap);
            //初始化准备第三步
            initPhase3(bootstrap);
        } catch (NodeValidationException e) {
            bootstrap.exitWithNodeValidationException(e);
        } catch (Throwable t) {
            bootstrap.exitWithUnknownException(t);
        }
    }

    // 和 getStdout 方法一样
    @SuppressForbidden(reason = "grab stderr for communication with server-cli")
    private static PrintStream getStderr() {
        return System.err;
    }

    // 这东西是为了Debug 用的。放到后边再看，估计得返回去看 CliToolLauncher 类的内容
    @SuppressForbidden(reason = "grab stdout for communication with server-cli")
    private static PrintStream getStdout() {
        return System.out;
    }

    /**
     * 服务初始化的第一阶段
     *
     * 阶段1包括一些静态初始化、从CLI进程读取参数和
     * 最终初始化日志。在这个阶段应该做的尽可能少，因为
     * 初始化日志是最后一步
     */
    private static Bootstrap initPhase1() {
        // 获取与server-cli通信的标准输出
        final PrintStream out = getStdout();
        // 获取与server-cli通信的标准错误
        final PrintStream err = getStderr();
        final ServerArgs args;
        try {
             // 初始化安全配置，其实就是加载了两个系统配置
            initSecurityProperties();

            /*
             * 我们希望JVM认为已经安装了一个安全管理器，这样如果内部策略决策是基于安全管理器的存在或不存在，那么就像存在一个安全管理器一样(例如，DNS缓存策略)。这迫使这些政策立即生效。
             * 这是本身在这的注释
             */
            org.elasticsearch.bootstrap.Security.setSecurityManager(new SecurityManager() {
                @Override
                public void checkPermission(Permission perm) {
                    // 授予所有权限，以便以后可以将安全管理器设置为所需的安全管理器
                }
            });
            LogConfigurator.registerErrorListener();

            BootstrapInfo.init();

            // 请注意，读取服务器参数不会关闭System.in，因为稍后将从中读取该参数以发出关闭通知
            var in = new InputStreamStreamInput(System.in);
            args = new ServerArgs(in);

            // 环境变量配置
            Environment nodeEnv = new Environment(args.nodeSettings(), args.configDir());

            BootstrapInfo.setConsole(ConsoleLoader.loadConsole(nodeEnv));

            // DO NOT MOVE THIS
            // Logging must remain the last step of phase 1. Anything init steps needing logging should be in phase 2.
            // 记录必须仍然是阶段1的最后一步。任何需要日志记录的初始化步骤都应该在第2阶段。
            LogConfigurator.setNodeName(Node.NODE_NAME_SETTING.get(args.nodeSettings()));
            LogConfigurator.configure(nodeEnv, args.quiet() == false);
        } catch (Throwable t) {
            // any exception this early needs to be fully printed and fail startup
            t.printStackTrace(err);
            err.flush();
            Bootstrap.exit(1); // mimic JDK exit code on exception
            return null; // unreachable, to satisfy compiler
        }

        return new Bootstrap(out, err, args);
    }

    /**
     * 进程初始化的第二阶段。
     *
     * Phase 2 consists of everything that must occur up to and including security manager initialization.
     * 阶段2包括安全管理器初始化之前必须发生的所有事情。
     */
    private static void initPhase2(Bootstrap bootstrap) throws IOException {
        final ServerArgs args = bootstrap.args();
        final SecureSettings secrets = args.secrets();
        bootstrap.setSecureSettings(secrets);
        Environment nodeEnv = createEnvironment(args.configDir(), args.nodeSettings(), secrets);
        bootstrap.setEnvironment(nodeEnv);

        initPidFile(args.pidFile());

        // install the default uncaught exception handler; must be done before security is
        // initialized as we do not want to grant the runtime permission
        // setDefaultUncaughtExceptionHandler
        Thread.setDefaultUncaughtExceptionHandler(new ElasticsearchUncaughtExceptionHandler());

        bootstrap.spawner().spawnNativeControllers(nodeEnv);

        nodeEnv.validateNativesConfig(); // temporary directories are important for JNA
        initializeNatives(
            nodeEnv.tmpFile(),
            BootstrapSettings.MEMORY_LOCK_SETTING.get(args.nodeSettings()),
            true, // always install system call filters, not user-configurable since 8.0.0
            BootstrapSettings.CTRLHANDLER_SETTING.get(args.nodeSettings())
        );

        // initialize probes before the security manager is installed
        initializeProbes();

        Runtime.getRuntime().addShutdownHook(new Thread(Elasticsearch::shutdown));

        // look for jar hell
        final Logger logger = LogManager.getLogger(JarHell.class);
        JarHell.checkJarHell(logger::debug);

        // Log ifconfig output before SecurityManager is installed
        IfConfig.logIfNecessary();

        try {
            // ReferenceDocs class does nontrivial static initialization which should always succeed but load it now (before SM) to be sure
            MethodHandles.publicLookup().ensureInitialized(ReferenceDocs.class);
        } catch (IllegalAccessException unexpected) {
            throw new AssertionError(unexpected);
        }

        // install SM after natives, shutdown hooks, etc.
        org.elasticsearch.bootstrap.Security.configure(
            nodeEnv,
            SECURITY_FILTER_BAD_DEFAULTS_SETTING.get(args.nodeSettings()),
            args.pidFile()
        );
    }

    /**
     * Third phase of initialization.
     * 初始化的第三阶段。
     *
     * Phase 3 consists of everything after security manager is initialized. Up until now, the system has been single
     * threaded. This phase can spawn threads, write to the log, and is subject ot the security manager policy.
     * 阶段3包括初始化安全管理器后的所有内容。到目前为止，该系统一直是单线程的。此阶段可以生成线程，写入日志，并受安全管理器策略的约束。
     *
     * At the end of phase 3 the system is ready to accept requests and the main thread is ready to terminate. This means:
     * 在阶段3结束时，系统准备好接受请求，主线程准备好终止。这意味着：
     *     The node components have been constructed and started
     *     节点组件已构建并启动
     *     Cleanup has been done (eg secure settings are closed)
     *     已完成清理（如安全设置已关闭）
     *     At least one thread other than the main thread is alive and will stay alive after the main thread terminates
     *     除主线程外，至少有一个线程处于活动状态，并且在主线程终止后将保持活动状态
     *     The parent CLI process has been notified the system is ready
     *     已通知父CLI进程系统已就绪
     *
     * @param bootstrap the bootstrap state
     * @throws IOException if a problem with filesystem or network occurs
     * @throws NodeValidationException if the node cannot start due to a node configuration issue
     */
    private static void initPhase3(Bootstrap bootstrap) throws IOException, NodeValidationException {
        checkLucene();

        Node node = new Node(bootstrap.environment()) {
            @Override
            protected void validateNodeBeforeAcceptingRequests(
                final BootstrapContext context,
                final BoundTransportAddress boundTransportAddress,
                List<BootstrapCheck> checks
            ) throws NodeValidationException {
                BootstrapChecks.check(context, boundTransportAddress, checks);
            }
        };
        // new 一个elasticsearch节点实例
        INSTANCE = new Elasticsearch(bootstrap.spawner(), node);

        // any secure settings must be read during node construction
        IOUtils.close(bootstrap.secureSettings());

        // 启动 实例
        INSTANCE.start();

        if (bootstrap.args().daemonize()) {
            LogConfigurator.removeConsoleAppender();
        }

        // DO NOT MOVE THIS
        // Signaling readiness to accept requests must remain the last step of initialization. Note that it is extremely
        // important closing the err stream to the CLI when daemonizing is the last statement since that is the only
        // way to pass errors to the CLI
        bootstrap.sendCliMarker(BootstrapInfo.SERVER_READY_MARKER);
        if (bootstrap.args().daemonize()) {
            bootstrap.closeStreams();
        } else {
            startCliMonitorThread(System.in);
        }
    }

    /**
     * Initialize native resources.
     *
     * @param tmpFile          the temp directory
     * @param mlockAll         whether or not to lock memory
     * @param systemCallFilter whether or not to install system call filters
     * @param ctrlHandler      whether or not to install the ctrl-c handler (applies to Windows only)
     */
    static void initializeNatives(final Path tmpFile, final boolean mlockAll, final boolean systemCallFilter, final boolean ctrlHandler) {
        final Logger logger = LogManager.getLogger(Elasticsearch.class);

        // check if the user is running as root, and bail
        if (Natives.definitelyRunningAsRoot()) {
            throw new RuntimeException("can not run elasticsearch as root");
        }

        if (systemCallFilter) {
            /*
             * Try to install system call filters; if they fail to install; a bootstrap check will fail startup in production mode.
             *
             * TODO: should we fail hard here if system call filters fail to install, or remain lenient in non-production environments?
             */
            Natives.tryInstallSystemCallFilter(tmpFile);
        }

        // mlockall if requested
        if (mlockAll) {
            if (Constants.WINDOWS) {
                Natives.tryVirtualLock();
            } else {
                Natives.tryMlockall();
            }
        }

        // listener for windows close event
        if (ctrlHandler) {
            Natives.addConsoleCtrlHandler(new ConsoleCtrlHandler() {
                @Override
                public boolean handle(int code) {
                    if (CTRL_CLOSE_EVENT == code) {
                        logger.info("running graceful exit on windows");
                        shutdown();
                        return true;
                    }
                    return false;
                }
            });
        }

        // force remainder of JNA to be loaded (if available).
        try {
            JNAKernel32Library.getInstance();
        } catch (Exception ignored) {
            // we've already logged this.
        }

        Natives.trySetMaxNumberOfThreads();
        Natives.trySetMaxSizeVirtualMemory();
        Natives.trySetMaxFileSize();

        // init lucene random seed. it will use /dev/urandom where available:
        StringHelper.randomId();

        // init filesystem natives
        FileSystemNatives.init();
    }

    static void initializeProbes() {
        // Force probes to be loaded
        ProcessProbe.getInstance();
        OsProbe.getInstance();
        JvmInfo.jvmInfo();
        HotThreads.initializeRuntimeMonitoring();
    }

    static void checkLucene() {
        if (Version.CURRENT.luceneVersion.equals(org.apache.lucene.util.Version.LATEST) == false) {
            throw new AssertionError(
                "Lucene version mismatch this version of Elasticsearch requires lucene version ["
                    + Version.CURRENT.luceneVersion
                    + "]  but the current lucene version is ["
                    + org.apache.lucene.util.Version.LATEST
                    + "]"
            );
        }
    }

    /**
     * Starts a thread that monitors stdin for a shutdown signal.
     *
     * If the shutdown signal is received, Elasticsearch exits with status code 0.
     * If the pipe is broken, Elasticsearch exits with status code 1.
     *
     * @param stdin Standard input for this process
     */
    private static void startCliMonitorThread(InputStream stdin) {
        new Thread(() -> {
            int msg = -1;
            try {
                msg = stdin.read();
            } catch (IOException e) {
                // ignore, whether we cleanly got end of stream (-1) or an error, we will shut down below
            } finally {
                if (msg == BootstrapInfo.SERVER_SHUTDOWN_MARKER) {
                    Bootstrap.exit(0);
                } else {
                    // parent process died or there was an error reading from it
                    Bootstrap.exit(1);
                }
            }
        }).start();
    }

    /**
     * Writes the current process id into the given pidfile, if not null. The pidfile is cleaned up on system exit.
     *
     * @param pidFile A path to a file, or null of no pidfile should be written
     */
    private static void initPidFile(Path pidFile) throws IOException {
        if (pidFile == null) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.deleteIfExists(pidFile);
            } catch (IOException e) {
                throw new ElasticsearchException("Failed to delete pid file " + pidFile, e);
            }
        }, "elasticsearch[pidfile-cleanup]"));

        // It has to be an absolute path, otherwise pidFile.getParent() will return null
        assert pidFile.isAbsolute();

        if (Files.exists(pidFile.getParent()) == false) {
            Files.createDirectories(pidFile.getParent());
        }

        Files.writeString(pidFile, Long.toString(ProcessHandle.current().pid()));
    }

    /**
     * 看了一下官方，这俩是jdk 安全管理器的配置，主要是做DNS 缓存的，默认是缓存10秒，这个方法负写了10秒的默认值为60秒。并缓存10秒的负查找。
     * 这个负查找，没想明白是什么意思？
     * 这两个 jdk 的安全管理器参数，可以通过 jvm 的参数进行修改。
     * networkaddress.cache.ttl:
     * networkaddress.cache.negative.ttl:
     */
    private static void initSecurityProperties() {
        for (final String property : new String[] { "networkaddress.cache.ttl", "networkaddress.cache.negative.ttl" }) {
            // 其实我看了一下也不算复写，是写了新的，加了es.这个前缀
            final String overrideProperty = "es." + property;
            // 感觉是 JDK 里边会有带es.前缀的配置，而且配置的是60S，这句就是把60S取出来了。断点看了一下确实是60S
            final String overrideValue = System.getProperty(overrideProperty);
            if (overrideValue != null) {
                try {
                    // 这里是覆盖了旧的 jdk 的 配置
                    Security.setProperty(property, Integer.toString(Integer.valueOf(overrideValue)));
                } catch (final NumberFormatException e) {
                    throw new IllegalArgumentException("failed to parse [" + overrideProperty + "] with value [" + overrideValue + "]", e);
                }
            }
        }

        // 安全性中的策略文件代码库声明。策略依赖于属性扩展，请参阅PolicyUtil.readPolicy
        Security.setProperty("policy.expandProperties", "true");
    }

    private static Environment createEnvironment(Path configDir, Settings initialSettings, SecureSettings secureSettings) {
        Settings.Builder builder = Settings.builder();
        builder.put(initialSettings);
        if (secureSettings != null) {
            builder.setSecureSettings(secureSettings);
        }
        return new Environment(builder.build(), configDir);
    }

    // -- instance

    private static volatile Elasticsearch INSTANCE;

    private final Spawner spawner;
    private final Node node;
    private final CountDownLatch keepAliveLatch = new CountDownLatch(1);
    private final Thread keepAliveThread;

    private Elasticsearch(Spawner spawner, Node node) {
        this.spawner = spawner;
        this.node = node;
        this.keepAliveThread = new Thread(() -> {
            try {
                keepAliveLatch.await();
            } catch (InterruptedException e) {
                // bail out
            }
        }, "elasticsearch[keepAlive/" + Version.CURRENT + "]");
    }

    private void start() throws NodeValidationException {
        node.start();
        keepAliveThread.start();
    }

    private static void shutdown() {
        if (INSTANCE == null) {
            return; // never got far enough
        }
        var es = INSTANCE;
        try {
            IOUtils.close(es.node, es.spawner);
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configurator.shutdown(context);
            if (es.node != null && es.node.awaitClose(10, TimeUnit.SECONDS) == false) {
                throw new IllegalStateException(
                    "Node didn't stop within 10 seconds. " + "Any outstanding requests or tasks might get killed."
                );
            }
        } catch (IOException ex) {
            throw new ElasticsearchException("failed to stop node", ex);
        } catch (InterruptedException e) {
            LogManager.getLogger(Elasticsearch.class).warn("Thread got interrupted while waiting for the node to shutdown.");
            Thread.currentThread().interrupt();
        } finally {
            es.keepAliveLatch.countDown();
        }
    }
}
