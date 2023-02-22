# Testing

## Creating packages

创建 包

To build a distribution for your local OS and print its output location upon completion, run:

要为您的本地操作系统构建一个发行版，并在完成后打印其输出位置，运行:

```
./gradlew localDistro
```

To create a platform-specific build, use the following depending on your operating system:

要创建特定于平台的构建，请根据您的操作系统使用以下方法:

```
./gradlew :distribution:archives:linux-tar:assemble
./gradlew :distribution:archives:darwin(-aarch64)-tar:assemble
./gradlew :distribution:archives:windows-zip:assemble
```

You can build a Docker image with:

您可以使用以下命令构建Docker映像:

```
./gradlew build(Aarch64)DockerImage
```

Note: you almost certainly don’t want to run `./gradlew assemble` as this will attempt build every single Elasticsearch distribution.

注意:你几乎肯定不想跑。/gradlew assemble '，因为它将尝试构建每个Elasticsearch发行版。

### Running Elasticsearch from a checkout

从checkout运行Elasticsearch

In order to run Elasticsearch from source without building a package, you can run it using Gradle:

为了在不构建包的情况下从源代码运行Elasticsearch，您可以使用Gradle运行它:

```
./gradlew run
```

#### Launching and debugging from an IDE

从IDE启动和调试

If you want to run Elasticsearch from your IDE, the `./gradlew run` task supports a remote debugging option. Run the following from your terminal:

如果您想从您的IDE运行Elasticsearch， `./gradlew run`任务支持远程调试选项。从终端运行以下命令:

```
./gradlew run --debug-jvm
```

Next start the "Debug Elasticsearch" run configuration in IntelliJ. This will enable the IDE to connect to the process and allow debug functionality.

接下来在IntelliJ中启动“Debug Elasticsearch”运行配置。这将使IDE能够连接到流程并允许调试功能。

As such the IDE needs to be instructed to listen for connections on this port. Since we might run multiple JVMs as part of configuring and starting the cluster it’s recommended to configure the IDE to initiate multiple listening attempts. In case of IntelliJ, this option is called "Auto restart" and needs to be checked.

因此，需要指示IDE监听该端口上的连接。由于在配置和启动集群时可能会运行多个jvm，因此建议将IDE配置为发起多次侦听尝试。对于IntelliJ，该选项称为“自动重启”，需要勾选。

| Note | If you have imported the project into IntelliJ according to the instructions in [CONTRIBUTING.md](https://github.com/elastic/elasticsearch/blob/main/CONTRIBUTING.md#importing-the-project-into-intellij-idea) then a debug run configuration named "Debug Elasticsearch" will be created for you and configured appropriately. |
| ---- | ------------------------------------------------------------ |
|      | 如果您已经根据[contribution .md](https://github.com/elastic/elasticsearch/blob/main/CONTRIBUTING.md#importing-the-project-into-intellij-idea)中的说明将项目导入IntelliJ，那么将为您创建一个名为“debug Elasticsearch”的调试运行配置并进行适当配置。 |

#### Disabling assertions

禁用断言

When running Elasticsearch with `./gradlew run`, assertions are enabled by default. To disable them, add the following command line option:

在运行Elasticsearch时使用'。/gradlew run`时，断言默认是启用的。要禁用它们，添加以下命令行选项:

```
-Dtests.jvm.argline="-da"
```

#### Distribution

分布式

By default a node is started with the zip distribution. In order to start with a different distribution use the `-Drun.distribution` argument.

To for example start the open source distribution:

默认情况下，节点以zip版本启动。为了从不同的分布开始，请使用'-Drun.distribution'的参数。



例如，启动开源分发:

```
./gradlew run -Drun.distribution=oss
```

#### License type

许可类型

By default a node is started with the `basic` license type. In order to start with a different license type use the `-Drun.license_type` argument.

In order to start a node with a trial license execute the following command:

默认情况下，节点以' basic '许可证类型启动。为了从不同的许可证类型开始，请使用'`-Drun.license_type`'参数。



为了启动一个具有试用许可证的节点，执行以下命令:

```
./gradlew run -Drun.license_type=trial
```

This enables security and other paid features and adds a superuser with the username: `elastic-admin` and password: `elastic-password`.

这将启用安全性和其他付费功能，并添加一个超级用户，用户名:' elastic-admin '，密码:' elastic-password '。

#### Other useful arguments

- In order to start a node with a different max heap space add: `-Dtests.heap.size=4G`
- In order to use a custom data directory: `--data-dir=/tmp/foo`
- In order to preserve data in between executions: `--preserve-data`
- In order to remotely attach a debugger to the process: `--debug-jvm`
- In order to set a different keystore password: `--keystore-password`
- In order to set an Elasticsearch setting, provide a setting with the following prefix: `-Dtests.es.`
- In order to pass a JVM setting, e.g. to disable assertions: `-Dtests.jvm.argline="-da"`
- In order to use HTTPS: ./gradlew run --https

#### Customizing the test cluster for ./gradlew run

You may need to customize the cluster configuration for the ./gradlew run task. The settings can be set via the command line, but other options require updating the task itself. You can simply find the task in the source code and configure it there. (The task is currently defined in build-tools-internal/src/main/groovy/elasticsearch.run.gradle) However, this requires modifying a source controlled file and is subject to accidental commits. Alternatively, you can use a Gradle init script to inject custom build logic with the -I flag to configure this task locally.

For example:

To use a custom certificate for HTTPS with `./gradlew run`, you can do the following. Create a file (for example ~/custom-run.gradle) with the following contents:

```
rootProject {
  if(project.name == 'elasticsearch') {
    afterEvaluate {
        testClusters.matching { it.name == "runTask"}.configureEach {
          extraConfigFile 'http.p12', file("<path/to/file>/http.p12")
        }
    }
  }
}
```

Now tell Gradle to use this init script:

```
./gradlew run -I ~/custom-run.gradle \
-Dtests.es.xpack.security.http.ssl.enabled=true \
-Dtests.es.xpack.security.http.ssl.keystore.path=http.p12
```

Now the http.p12 file will be placed in the config directory of the running cluster and available for use. Assuming you have the http.ssl.keystore setup correctly, you can now use HTTPS with ./gradlew run without the risk of accidentally committing your local configurations.

#### Multiple nodes in the test cluster for ./gradlew run

Another desired customization for ./gradlew run might be to run multiple nodes with different setting for each node. For example, you may want to debug a coordinating only node that fans out to one or more data nodes. To do this, increase the numberOfNodes and add specific configuration for each of the nodes. For example, the following will instruct the first node (:9200) to be a coordinating only node, and all other nodes to be master, data_hot, data_content nodes.

```
testClusters.register("runTask") {
     ...
    numberOfNodes = 2
    def cluster = testClusters.named("runTask").get()
    cluster.getNodes().each { node ->
      node.setting('cluster.initial_master_nodes', cluster.getLastNode().getName())
      node.setting('node.roles', '[master,data_hot,data_content]')
    }
    cluster.getFirstNode().setting('node.roles', '[]')
   ...
}
```

You can also place this config in custom init script (see above) to avoid accidental commits. If you are passing in the --debug-jvm flag with multiple nodes, you will need multiple remote debuggers running. One for each node listening at port 5007, 5008, 5009, and so on. Ensure that each remote debugger has auto restart enabled.

#### Manually testing cross cluster search

Use ./gradlew run-ccs to launch 2 clusters wired together for the purposes of cross cluster search. For example send a search request "my_remote_cluster:*/_search" to the querying cluster (:9200) to query data in the fulfilling cluster.

If you are passing in the --debug-jvm flag, you will need two remote debuggers running. One at port 5007 and another one at port 5008. Ensure that each remote debugger has auto restart enabled.

### Test case filtering.

You can run a single test, provided that you specify the Gradle project. See the documentation on [simple name pattern filtering](https://docs.gradle.org/current/userguide/userguide_single.html#simple_name_pattern).

Run a single test case in the `server` project:

```
./gradlew :server:test --tests org.elasticsearch.package.ClassName
```

Run all tests in a package and its sub-packages:

```
./gradlew :server:test --tests 'org.elasticsearch.package.*'
```

Run all tests that are waiting for a bugfix (disabled by default)

```
./gradlew test -Dtests.filter=@awaitsfix
```

### Seed and repetitions.

Run with a given seed (seed is a hex-encoded long).

```
./gradlew test -Dtests.seed=DEADBEEF
```

### Repeats *all* tests of ClassName N times.

Every test repetition will have a different method seed (derived from a single random master seed).

```
./gradlew :server:test -Dtests.iters=N --tests org.elasticsearch.package.ClassName
```

### Repeats *all* tests of ClassName N times.

Every test repetition will have exactly the same master (0xdead) and method-level (0xbeef) seed.

```
./gradlew :server:test -Dtests.iters=N -Dtests.seed=DEAD:BEEF --tests org.elasticsearch.package.ClassName
```

### Repeats a given test N times

(note the filters - individual test repetitions are given suffixes, ie: testFoo[0], testFoo[1], etc… so using testmethod or tests.method ending in a glob is necessary to ensure iterations are run).

```
./gradlew :server:test -Dtests.iters=N --tests org.elasticsearch.package.ClassName.methodName
```

Repeats N times but skips any tests after the first failure or M initial failures.

```
./gradlew test -Dtests.iters=N -Dtests.failfast=true ...
./gradlew test -Dtests.iters=N -Dtests.maxfailures=M ...
```

### Test groups.

Test groups can be enabled or disabled (true/false).

Default value provided below in [brackets].

```
./gradlew test -Dtests.awaitsfix=[false] - known issue (@AwaitsFix)
```

### Load balancing and caches.

By default the tests run on multiple processes using all the available cores on all available CPUs. Not including hyper-threading. If you want to explicitly specify the number of JVMs you can do so on the command line:

```
./gradlew test -Dtests.jvms=8
```

Or in `~/.gradle/gradle.properties`:

```
systemProp.tests.jvms=8
```

It’s difficult to pick the "right" number here. Hypercores don’t count for CPU intensive tests and you should leave some slack for JVM-internal threads like the garbage collector. And you have to have enough RAM to handle each JVM.

### Test compatibility.

It is possible to provide a version that allows to adapt the tests behaviour to older features or bugs that have been changed or fixed in the meantime.

```
./gradlew test -Dtests.compatibility=1.0.0
```

### Miscellaneous.

Run all tests without stopping on errors (inspect log files).

```
./gradlew test -Dtests.haltonfailure=false
```

Run more verbose output (slave JVM parameters, etc.).

```
./gradlew test -verbose
```

Change the default suite timeout to 5 seconds for all tests (note the exclamation mark).

```
./gradlew test -Dtests.timeoutSuite=5000! ...
```

Change the logging level of ES (not Gradle)

```
./gradlew test -Dtests.es.logger.level=DEBUG
```

Print all the logging output from the test runs to the commandline even if tests are passing.

```
./gradlew test -Dtests.output=always
```

Configure the heap size.

```
./gradlew test -Dtests.heap.size=512m
```

Pass arbitrary jvm arguments.

```
# specify heap dump path
./gradlew test -Dtests.jvm.argline="-XX:HeapDumpPath=/path/to/heapdumps"
# enable gc logging
./gradlew test -Dtests.jvm.argline="-verbose:gc"
# enable security debugging
./gradlew test -Dtests.jvm.argline="-Djava.security.debug=access,failure"
```

Pass build arguments.

```
# Run tests against a release build. License key must be provided, but usually can be anything.
./gradlew test -Dbuild.snapshot=false -Dlicense.key="x-pack/license-tools/src/test/resources/public.key"
```

## Running verification tasks

To run all verification tasks, including static checks, unit tests, and integration tests:

```
./gradlew check
```

Note that this will also run the unit tests and precommit tasks first. If you want to just run the in memory cluster integration tests (because you are debugging them):

```
./gradlew internalClusterTest
```

If you want to just run the precommit checks:

```
./gradlew precommit
```

Some of these checks will require `docker-compose` installed for bringing up test fixtures. If it’s not present those checks will be skipped automatically. The host running Docker (or VM if you’re using Docker Desktop) needs 4GB of memory or some of the containers will fail to start. You can tell that you are short of memory if containers are exiting quickly after starting with code 137 (128 + 9, where 9 means SIGKILL).

## Debugging tests

If you would like to debug your tests themselves, simply pass the `--debug-jvm` flag to the testing task and connect a debugger on the default port of `5005`.

```
./gradlew :server:test --debug-jvm
```

For REST tests, if you’d like to debug the Elasticsearch server itself, and not your test code, use the `--debug-server-jvm` flag and use the "Debug Elasticsearch" run configuration in IntelliJ to listen on the default port of `5007`.

```
./gradlew :rest-api-spec:yamlRestTest --debug-server-jvm
```

| Note | In the case of test clusters using multiple nodes, multiple debuggers will need to be attached on incrementing ports. For example, for a 3 node cluster ports `5007`, `5008`, and `5009` will attempt to attach to a listening debugger. |
| ---- | ------------------------------------------------------------ |
|      |                                                              |

You can also use a combination of both flags to debug both tests and server. This is only applicable to Java REST tests.

```
./gradlew :modules:kibana:javaRestTest --debug-jvm --debug-server-jvm
```

## Testing the REST layer

The REST layer is tested through specific tests that are executed against a cluster that is configured and initialized via Gradle. The tests themselves can be written in either Java or with a YAML based DSL.

YAML based REST tests should be preferred since these are shared between all the elasticsearch official clients. The YAML based tests describe the operations to be executed and the obtained results that need to be tested.

The YAML tests support various operators defined in the [rest-api-spec](https://github.com/elastic/elasticsearch/blob/main/rest-api-spec/src/yamlRestTest/resources/rest-api-spec/test/README.asciidoc) and adhere to the [Elasticsearch REST API JSON specification](https://github.com/elastic/elasticsearch/blob/main/rest-api-spec/README.markdown) In order to run the YAML tests, the relevant API specification needs to be on the test classpath. Any gradle project that has support for REST tests will get the primary API on it’s class path. However, to better support Gradle incremental builds, it is recommended to explicitly declare which parts of the API the tests depend upon.

For example:

```
restResources {
  restApi {
    includeCore '_common', 'indices', 'index', 'cluster', 'nodes', 'get', 'ingest'
  }
}
```

YAML REST tests that include x-pack specific APIs need to explicitly declare which APIs are required through a similar `includeXpack` configuration.

The REST tests are run automatically when executing the "./gradlew check" command. To run only the YAML REST tests use the following command (modules and plugins may also include YAML REST tests):

```
./gradlew :rest-api-spec:yamlRestTest
```

A specific test case can be run with the following command:

```
./gradlew ':rest-api-spec:yamlRestTest' \
  --tests "org.elasticsearch.test.rest.ClientYamlTestSuiteIT" \
  -Dtests.method="test {yaml=cat.segments/10_basic/Help}"
```

The YAML REST tests support all the options provided by the randomized runner, plus the following:

- `tests.rest.suite`: comma separated paths of the test suites to be run (by default loaded from /rest-api-spec/test). It is possible to run only a subset of the tests providing a sub-folder or even a single yaml file (the default /rest-api-spec/test prefix is optional when files are loaded from classpath) e.g. -Dtests.rest.suite=index,get,create/10_with_id
- `tests.rest.blacklist`: comma separated globs that identify tests that are blacklisted and need to be skipped e.g. -Dtests.rest.blacklist=index/**/Index document,get/10_basic/**

Java REST tests can be run with the "javaRestTest" task.

For example :

```
./gradlew :modules:mapper-extras:javaRestTest
```

A specific test case can be run with the following syntax (fqn.test {params}):

```
./gradlew ':modules:mapper-extras:javaRestTest' \
  --tests "org.elasticsearch.index.mapper.TokenCountFieldMapperIntegrationIT.testSearchByTokenCount {storeCountedFields=true loadCountedFields=false}"
```

yamlRestTest’s and javaRestTest’s are easy to identify, since they are found in a respective source directory. However, there are some more specialized REST tests that use custom task names. These are usually found in "qa" projects commonly use the "integTest" task.

If in doubt about which command to use, simply run <gradle path>:check

## Testing packaging

The packaging tests use Vagrant virtual machines or cloud instances to verify that installing and running Elasticsearch distributions works correctly on supported operating systems. These tests should really only be run on ephemeral systems because they’re destructive; that is, these tests install and remove packages and freely modify system settings, so you will probably regret it if you execute them on your development machine.

When you run a packaging test, Gradle will set up the target VM and mount your repository directory in the VM. Once this is done, a Gradle task will issue a Vagrant command to run a **nested** Gradle task on the VM. This nested Gradle runs the actual "destructive" test classes.

1. Install Virtual Box and Vagrant.

2. (Optional) Install [vagrant-cachier](https://github.com/fgrehm/vagrant-cachier) to squeeze a bit more performance out of the process:

   ```
   vagrant plugin install vagrant-cachier
   ```

3. You can run all of the OS packaging tests with `./gradlew packagingTest`. This task includes our legacy `bats` tests. To run only the OS tests that are written in Java, run `.gradlew distroTest`, will cause Gradle to build the tar, zip, and deb packages and all the plugins. It will then run the tests on every available system. This will take a very long time.

   Fortunately, the various systems under test have their own Gradle tasks under `qa/os`. To find the systems tested, do a listing of the `qa/os` directory. To find out what packaging combinations can be tested on a system, run the `tasks` task. For example:

   ```
   ./gradlew :qa:os:ubuntu-1804:tasks
   ```

   If you want a quick test of the tarball and RPM packagings for Centos 7, you would run:

   ```
   ./gradlew :qa:os:centos-7:distroTest.default-rpm :qa:os:centos-7:distroTest.default-linux-archive
   ```

Note that if you interrupt Gradle in the middle of running these tasks, any boxes started will remain running and you’ll have to stop them manually with `./gradlew --stop` or `vagrant halt`.

All the regular vagrant commands should just work so you can get a shell in a VM running trusty by running `vagrant up ubuntu-1804 --provider virtualbox && vagrant ssh ubuntu-1804`.

### Testing packaging on Windows

The packaging tests also support Windows Server 2012R2 and Windows Server 2016. Unfortunately we’re not able to provide boxes for them in open source use because of licensing issues. Any Virtualbox image that has WinRM and Powershell enabled for remote users should work.

Specify the image IDs of the Windows boxes to gradle with the following project properties. They can be set in `~/.gradle/gradle.properties` like

```
vagrant.windows-2012r2.id=my-image-id
vagrant.windows-2016.id=another-image-id
```

or passed on the command line like `-Pvagrant.windows-2012r2.id=my-image-id` `-Pvagrant.windows-2016=another-image-id`

These properties are required for Windows support in all gradle tasks that handle packaging tests. Either or both may be specified.

If you’re running vagrant commands outside of gradle, specify the Windows boxes with the environment variables

- `VAGRANT_WINDOWS_2012R2_BOX`
- `VAGRANT_WINDOWS_2016_BOX`

### Testing VMs are disposable

It’s important to think of VMs like cattle. If they become lame you just shoot them and let vagrant reprovision them. Say you’ve hosed your precise VM:

```
vagrant ssh ubuntu-1604 -c 'sudo rm -rf /bin'; echo oops
```

All you’ve got to do to get another one is

```
vagrant destroy -f ubuntu-1604 && vagrant up ubuntu-1604 --provider virtualbox
```

The whole process takes a minute and a half on a modern laptop, two and a half without vagrant-cachier.

It’s possible that some downloads will fail and it’ll be impossible to restart them. This is a bug in vagrant. See the instructions here for how to work around it: [hashicorp/vagrant#4479](https://github.com/hashicorp/vagrant/issues/4479)

Some vagrant commands will work on all VMs at once:

```
vagrant halt
vagrant destroy -f
```

`vagrant up` would normally start all the VMs but we’ve prevented that because that’d consume a ton of ram.

### Iterating on packaging tests

Because our packaging tests are capable of testing many combinations of OS (e.g., Windows, Linux, etc.), package type (e.g., zip file, RPM, etc.), Elasticsearch distribution type (e.g., default or OSS), and so forth, it’s faster to develop against smaller subsets of the tests. For example, to run tests for the default archive distribution on Fedora 28:

```
./gradlew :qa:os:fedora-28:distroTest.default-linux-archive
```

These test tasks can use the `--tests`, `--info`, and `--debug` parameters just like non-OS tests can. For example:

```
./gradlew :qa:os:fedora-28:distroTest.default-linux-archive \
  --tests "com.elasticsearch.packaging.test.ArchiveTests"
```

## Testing backwards compatibility

Backwards compatibility tests exist to test upgrading from each supported version to the current version. To run them all use:

```
./gradlew bwcTest
```

A specific version can be tested as well. For example, to test bwc with version 5.3.2 run:

```
./gradlew v5.3.2#bwcTest
```

Use -Dtests.class and -Dtests.method to run a specific bwcTest test. For example to run a specific tests from the x-pack rolling upgrade from 7.7.0:

```
./gradlew :x-pack:qa:rolling-upgrade:v7.7.0#bwcTest \
 -Dtests.class=org.elasticsearch.upgrades.UpgradeClusterClientYamlTestSuiteIT \
 -Dtests.method="test {p0=*/40_ml_datafeed_crud/*}"
```

Tests are ran for versions that are not yet released but with which the current version will be compatible with. These are automatically checked out and built from source. See [BwcVersions](https://github.com/elastic/elasticsearch/blob/main/build-tools-internal/src/main/java/org/elasticsearch/gradle/BwcVersions.java) and [distribution/bwc/build.gradle](https://github.com/elastic/elasticsearch/blob/main/distribution/bwc/build.gradle) for more information.

When running `./gradlew check`, minimal bwc checks are also run against compatible versions that are not yet released.

#### BWC Testing against a specific remote/branch

Sometimes a backward compatibility change spans two versions. A common case is a new functionality that needs a BWC bridge in an unreleased versioned of a release branch (for example, 5.x). To test the changes, you can instruct Gradle to build the BWC version from another remote/branch combination instead of pulling the release branch from GitHub. You do so using the `bwc.remote` and `bwc.refspec.BRANCH` system properties:

```
./gradlew check -Dbwc.remote=${remote} -Dbwc.refspec.5.x=index_req_bwc_5.x
```

The branch needs to be available on the remote that the BWC makes of the repository you run the tests from. Using the remote is a handy trick to make sure that a branch is available and is up to date in the case of multiple runs.

Example:

Say you need to make a change to `main` and have a BWC layer in `5.x`. You will need to: . Create a branch called `index_req_change` off your remote `${remote}`. This will contain your change. . Create a branch called `index_req_bwc_5.x` off `5.x`. This will contain your bwc layer. . Push both branches to your remote repository. . Run the tests with `./gradlew check -Dbwc.remote=${remote} -Dbwc.refspec.5.x=index_req_bwc_5.x`.

#### Skip fetching latest

For some BWC testing scenarios, you want to use the local clone of the repository without fetching latest. For these use cases, you can set the system property `tests.bwc.git_fetch_latest` to `false` and the BWC builds will skip fetching the latest from the remote.

## Testing in FIPS 140-2 mode

We have a CI matrix job that periodically runs all our tests with the JVM configured to be FIPS 140-2 compliant with the use of the BouncyCastle FIPS approved Security Provider. FIPS 140-2 imposes certain requirements that affect how our tests should be set up or what can be tested. This section summarizes what one needs to take into consideration so that tests won’t fail when run in fips mode.

### Muting tests in FIPS 140-2 mode

If the following limitations cannot be observed, or there is a need to actually test some use case that is not available/allowed in fips mode, the test can be muted. For unit tests or Java rest tests one can use

```
assumeFalse("Justification why this cannot be run in FIPS mode", inFipsJvm());
```

For specific YAML rest tests one can use

```
- skip:
    features: fips_140
    reason: "Justification why this cannot be run in FIPS mode"
```

For disabling entire types of tests for subprojects, one can use for example:

```
if (BuildParams.inFipsJvm){
  // This test cluster is using a BASIC license and FIPS 140 mode is not supported in BASIC
  tasks.named("javaRestTest").configure{enabled = false }
}
```

in `build.gradle`.

### Limitations

The following should be taken into consideration when writing new tests or adjusting existing ones:

#### TLS

`JKS` and `PKCS#12` keystores cannot be used in FIPS mode. If the test depends on being able to use a keystore, it can be muted when needed ( see `ESTestCase#inFipsJvm` ). Alternatively, one can use PEM encoded files for keys and certificates for the tests or for setting up TLS in a test cluster. Also, when in FIPS 140 mode, hostname verification for TLS cannot be turned off so if you are using `*.verification_mode: none` , you’d need to mute the test in fips mode.

When using TLS, ensure that private keys used are longer than 2048 bits, or mute the test in fips mode.

#### Password hashing algorithm

Test clusters are configured with `xpack.security.fips_mode.enabled` set to true. This means that FIPS 140-2 related bootstrap checks are enabled and the test cluster will fail to form if the password hashing algorithm is set to something else than a PBKDF2 based one. You can delegate the choice of algorithm to i.e. `SecurityIntegTestCase#getFastStoredHashAlgoForTests` if you don’t mind the actual algorithm used, or depend on default values for the test cluster nodes.

#### Password length

While using `pbkdf2` as the password hashing algorithm, FIPS 140-2 imposes a requirement that passwords are longer than 14 characters. You can either ensure that all test user passwords in your test are longer than 14 characters and use i.e. `SecurityIntegTestCase#getFastStoredHashAlgoForTests` to randomly select a hashing algorithm, or use `pbkdf2_stretch` that doesn’t have the same limitation.

#### Keystore Password

In FIPS 140-2 mode, the elasticsearch keystore needs to be password protected with a password of appropriate length. This is handled automatically in `fips.gradle` and the keystore is unlocked on startup by the test clusters tooling in order to have secure settings available. However, you might need to take into consideration that the keystore is password-protected with `keystore-password` if you need to interact with it in a test.

## How to write good tests?

### Base classes for test cases

There are multiple base classes for tests:

- **`ESTestCase`**: The base class of all tests. It is typically extended directly by unit tests.
- **`ESSingleNodeTestCase`**: This test case sets up a cluster that has a single node.
- **`ESIntegTestCase`**: An integration test case that creates a cluster that might have multiple nodes.
- **`ESRestTestCase`**: An integration tests that interacts with an external cluster via the REST API. This is used for Java based REST tests.
- **`ESClientYamlSuiteTestCase`** : A subclass of `ESRestTestCase` used to run YAML based REST tests.

### Good practices

#### What kind of tests should I write?

Unit tests are the preferred way to test some functionality: most of the time they are simpler to understand, more likely to reproduce, and unlikely to be affected by changes that are unrelated to the piece of functionality that is being tested.

The reason why `ESSingleNodeTestCase` exists is that all our components used to be very hard to set up in isolation, which had led us to having a number of integration tests but close to no unit tests. `ESSingleNodeTestCase` is a workaround for this issue which provides an easy way to spin up a node and get access to components that are hard to instantiate like `IndicesService`. Whenever practical, you should prefer unit tests.

Many tests extend `ESIntegTestCase`, mostly because this is how most tests used to work in the early days of Elasticsearch. However the complexity of these tests tends to make them hard to debug. Whenever the functionality that is being tested isn’t intimately dependent on how Elasticsearch behaves as a cluster, it is recommended to write unit tests or REST tests instead.

In short, most new functionality should come with unit tests, and optionally REST tests to test integration.

#### Refactor code to make it easier to test

Unfortunately, a large part of our code base is still hard to unit test. Sometimes because some classes have lots of dependencies that make them hard to instantiate. Sometimes because API contracts make tests hard to write. Code refactors that make functionality easier to unit test are encouraged. If this sounds very abstract to you, you can have a look at [this pull request](https://github.com/elastic/elasticsearch/pull/16610) for instance, which is a good example. It refactors `IndicesRequestCache` in such a way that: - it no longer depends on objects that are hard to instantiate such as `IndexShard` or `SearchContext`, - time-based eviction is applied on top of the cache rather than internally, which makes it easier to assert on what the cache is expected to contain at a given time.

### Bad practices

#### Use randomized-testing for coverage

In general, randomization should be used for parameters that are not expected to affect the behavior of the functionality that is being tested. For instance the number of shards should not impact `date_histogram` aggregations, and the choice of the `store` type (`niofs` vs `mmapfs`) does not affect the results of a query. Such randomization helps improve confidence that we are not relying on implementation details of one component or specifics of some setup.

However it should not be used for coverage. For instance if you are testing a piece of functionality that enters different code paths depending on whether the index has 1 shards or 2+ shards, then we shouldn’t just test against an index with a random number of shards: there should be one test for the 1-shard case, and another test for the 2+ shards case.

#### Abuse randomization in multi-threaded tests

Multi-threaded tests are often not reproducible due to the fact that there is no guarantee on the order in which operations occur across threads. Adding randomization to the mix usually makes things worse and should be done with care.

## Test coverage analysis

Generating test coverage reports for Elasticsearch is currently not possible through Gradle. However, it *is* possible to gain insight in code coverage using IntelliJ’s built-in coverage analysis tool that can measure coverage upon executing specific tests.

Test coverage reporting used to be possible with JaCoCo when Elasticsearch was using Maven as its build system. Since the switch to Gradle though, this is no longer possible, seeing as the code currently used to build Elasticsearch does not allow JaCoCo to recognize its tests. For more information on this, see the discussion in [issue #28867](https://github.com/elastic/elasticsearch/issues/28867).

## Building with extra plugins

Additional plugins may be built alongside elasticsearch, where their dependency on elasticsearch will be substituted with the local elasticsearch build. To add your plugin, create a directory called elasticsearch-extra as a sibling of elasticsearch. Checkout your plugin underneath elasticsearch-extra and the build will automatically pick it up. You can verify the plugin is included as part of the build by checking the projects of the build.

```
./gradlew projects
```

## Environment misc

There is a known issue with macOS localhost resolve strategy that can cause some integration tests to fail. This is because integration tests have timings for cluster formation, discovery, etc. that can be exceeded if name resolution takes a long time. To fix this, make sure you have your computer name (as returned by `hostname`) inside `/etc/hosts`, e.g.:

```
127.0.0.1       localhost ElasticMBP.local
255.255.255.255 broadcasthost
::1             localhost ElasticMBP.local`
```

## Benchmarking

For changes that might affect the performance characteristics of Elasticsearch you should also run macrobenchmarks. We maintain a macrobenchmarking tool called [Rally](https://github.com/elastic/rally) which you can use to measure the performance impact. It comes with a set of default benchmarks that we also [run every night](https://elasticsearch-benchmarks.elastic.co/). To get started, please see [Rally’s documentation](https://esrally.readthedocs.io/en/stable/).

## Test doc builds

The Elasticsearch docs are in AsciiDoc format. You can test and build the docs locally using the Elasticsearch documentation build process. See https://github.com/elastic/docs.