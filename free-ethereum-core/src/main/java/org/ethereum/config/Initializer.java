/*
 * The MIT License (MIT)
 *
 * Copyright 2017 Alexander Orlov <alexander.orlov@loxal.net>. All rights reserved.
 * Copyright (c) [2016] [ <ether.camp> ]
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package org.ethereum.config;

import org.ethereum.net.eth.EthVersion;
import org.ethereum.net.shh.ShhHandler;
import org.ethereum.net.swarm.bzz.BzzHandler;
import org.ethereum.util.BuildInfo;
import org.ethereum.util.FileUtil;
import org.ethereum.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.io.*;
import java.util.Properties;

class Initializer implements BeanPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger("general");

    // Util to ensure database directory is compatible with code
    private final DatabaseVersionHandler databaseVersionHandler = new DatabaseVersionHandler();

    /**
     * Method to be called right after the config is instantiated.
     * Effectively is called before any other bean is initialized
     */
    private void initConfig(final SystemProperties config) {
        logger.info("Running {},  core version: {}-{}", config.genesisInfo(), config.projectVersion(), config.projectVersionModifier());
        BuildInfo.printInfo();

        databaseVersionHandler.process(config);

        if (logger.isInfoEnabled()) {
            final StringBuilder versions = new StringBuilder();
            for (final EthVersion v : EthVersion.Companion.supported()) {
                versions.append(v.getCode()).append(", ");
            }
            versions.delete(versions.length() - 2, versions.length());
            logger.info("capability eth version: [{}]", versions);
        }
        logger.info("capability shh version: [{}]", ShhHandler.Companion.getVERSION());
        logger.info("capability bzz version: [{}]", BzzHandler.VERSION);

        // forcing loading blockchain config
        config.getBlockchainConfig();

        // forcing loading genesis to fail fast in case of error
        config.getGenesis();

        // forcing reading private key or generating it in database directory
        config.nodeId();

        if (logger.isDebugEnabled()) {
            logger.debug("Blockchain config {}", config.getBlockchainConfig().toString());
        }
    }

    @Override
    public Object postProcessBeforeInitialization(final Object bean, final String beanName) throws BeansException {
        if (bean instanceof SystemProperties) {
            initConfig((SystemProperties) bean);
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
        return bean;
    }

    /**
     * We need to persist the DB version, so after core upgrade we can either reset older incompatible db
     * or make a warning and let the user reset DB manually.
     * Database version is stored in ${database}/version.properties
     * Logic will assume that database has version 1 if file with version is absent.
     */
    public static class DatabaseVersionHandler {

        public void process(final SystemProperties config) {
            if (config.databaseReset() && config.databaseResetBlock() == 0){
                FileUtil.recursiveDelete(config.databaseDir());
                putDatabaseVersion(config, config.databaseVersion());
                logger.info("Database reset done");
                System.out.println("Database reset done");
            }

            final File versionFile = getDatabaseVersionFile(config);
            final Behavior behavior = Behavior.valueOf(
                    config.getProperty("database.incompatibleDatabaseBehavior", Behavior.EXIT.toString()).toUpperCase());


            // Detect database version
            final Integer expectedVersion = config.databaseVersion();
            if (isDatabaseDirectoryExists(config)) {
                final Integer actualVersionRaw = getDatabaseVersion(versionFile);
                final boolean isVersionFileNotFound = actualVersionRaw.equals(-1);
                final Integer actualVersion = isVersionFileNotFound ? 1 : actualVersionRaw;

                if (actualVersionRaw.equals(-1)) {
                    putDatabaseVersion(config, actualVersion);
                }

                if (actualVersion.equals(expectedVersion) || (isVersionFileNotFound && expectedVersion.equals(1))) {
                    logger.info("Database directory location: '{}', version: {}", config.databaseDir(), actualVersion);
                } else {
                    logger.warn("Detected incompatible database version. Detected:{}, required:{}", actualVersion, expectedVersion);
                    if (behavior == Behavior.EXIT) {
                        Utils.showErrorAndExit(
                                "Incompatible database version " + actualVersion,
                                "Please remove database directory manually or set `database.incompatibleDatabaseBehavior` to `RESET`",
                                "Database directory location is " + config.databaseDir()
                        );
                    } else if (behavior == Behavior.RESET) {
                        final boolean res = FileUtil.recursiveDelete(config.databaseDir());
                        if (!res) {
                            throw new RuntimeException("Couldn't delete database dir: " + config.databaseDir());
                        }
                        putDatabaseVersion(config, config.databaseVersion());
                        logger.warn("Auto reset database directory according to flag");
                    } else {
                        // IGNORE
                        logger.info("Continue working according to flag");
                    }
                }
            } else {
                putDatabaseVersion(config, config.databaseVersion());
                logger.info("Created database version file");
            }
        }

        public boolean isDatabaseDirectoryExists(final SystemProperties config) {
            final File databaseFile = new File(config.databaseDir());
            return databaseFile.exists() && databaseFile.isDirectory() && databaseFile.list().length > 0;
        }

        /**
         * @return database version stored in specific location in database dir
         *         or -1 if can't detect version due to error
         */
        public Integer getDatabaseVersion(final File file) {
            if (!file.exists()) {
                return -1;
            }

            try (Reader reader = new FileReader(file)) {
                final Properties prop = new Properties();
                prop.load(reader);
                return Integer.valueOf(prop.getProperty("databaseVersion"));
            } catch (final Exception e) {
                logger.error("Problem reading current database version.", e);
                return -1;
            }
        }

        public void putDatabaseVersion(final SystemProperties config, final Integer version) {
            final File versionFile = getDatabaseVersionFile(config);
            versionFile.getParentFile().mkdirs();
            try (Writer writer = new FileWriter(versionFile)) {
                final Properties prop = new Properties();
                prop.setProperty("databaseVersion", version.toString());
                prop.store(writer, "Generated database version");
            } catch (final Exception e) {
                throw new Error("Problem writing current database version ", e);
            }
        }

        private File getDatabaseVersionFile(final SystemProperties config) {
            return new File(config.databaseDir() + "/version.properties");
        }

        public enum Behavior {
            EXIT, RESET, IGNORE
        }
    }
}
