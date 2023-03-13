/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.bootstrap;

import org.elasticsearch.common.settings.KeyStoreWrapper;
import org.elasticsearch.common.settings.SecureSettings;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.env.Environment;

/**
 * Utilities for use during bootstrap. This is public so that tests may use these methods.
 * 引导期间使用的实用程序。这是公开的，以便测试可以使用这些方法。
 */
public class BootstrapUtil {

    // no construction
    private BootstrapUtil() {}

    public static SecureSettings loadSecureSettings(Environment initialEnv, SecureString keystorePassword) throws BootstrapException {
        try {
            return KeyStoreWrapper.bootstrap(initialEnv.configFile(), () -> keystorePassword);
        } catch (Exception e) {
            throw new BootstrapException(e);
        }
    }
}
