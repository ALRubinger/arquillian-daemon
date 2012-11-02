/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.daemon.server;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.jboss.shrinkwrap.api.ConfigurationBuilder;
import org.jboss.shrinkwrap.api.Domain;
import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.classloader.ShrinkWrapClassLoader;

/**
 * Base support for {@link Server} implementations
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public abstract class ServerBase implements Server {

    private static final Logger log = Logger.getLogger(ServerBase.class.getName());

    private static final String CLASS_NAME_ARQ_TEST_RUNNERS = "org.jboss.arquillian.container.test.spi.util.TestRunners";
    private static final String METHOD_NAME_GET_TEST_RUNNER = "getTestRunner";
    private static final String METHOD_NAME_EXECUTE = "execute";

    private ExecutorService shutdownService;
    private boolean running;
    private InetSocketAddress boundAddress;
    private final InetSocketAddress bindAddress;
    private final ConcurrentMap<String, GenericArchive> deployedArchives;
    private final Domain shrinkwrapDomain;

    /**
     * Creates a new instance, to be bound on start at the specified, required {@link InetSocketAddress}
     *
     * @param bindAddress
     */
    public ServerBase(final InetSocketAddress bindAddress) {
        // Precondition checks
        assert bindAddress != null : "Bind address must be specified";

        // Determine the ClassLoader to use in creating the SW Domain
        final ClassLoader thisCl = NettyServer.class.getClassLoader();
        final Set<ClassLoader> classloaders = new HashSet<ClassLoader>(1);
        classloaders.add(thisCl);
        if (log.isLoggable(Level.FINEST)) {
            log.finest("Using ClassLoader for ShrinkWrap Domain: " + thisCl);
        }
        final Domain shrinkwrapDomain = ShrinkWrap.createDomain(new ConfigurationBuilder().classLoaders(classloaders));

        // Set
        this.bindAddress = bindAddress;
        this.deployedArchives = new ConcurrentHashMap<String, GenericArchive>();
        this.shrinkwrapDomain = shrinkwrapDomain;
    }

    /**
     * Starts the backend engine powering this {@link Server}. Must call upon
     * {@link ServerBase#setBoundAddress(InetSocketAddress)} to set the bound address.
     *
     * @throws ServerLifecycleException
     * @throws IllegalStateException
     */
    protected abstract void startInternal() throws ServerLifecycleException, IllegalStateException;

    /**
     * Stops the backend engine powering this {@link Server}
     *
     * @throws ServerLifecycleException
     * @throws IllegalStateException
     */
    protected abstract void stopInternal() throws ServerLifecycleException, IllegalStateException;

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.daemon.server.Server#start()
     */
    @Override
    public final void start() throws ServerLifecycleException, IllegalStateException {

        // Precondition checks
        if (this.isRunning()) {
            throw new IllegalStateException("Already running");
        }

        // Forward to engine impl
        startInternal();

        // Running
        running = true;
        // Create the shutdown service
        this.shutdownService = Executors.newSingleThreadExecutor();

        if (log.isLoggable(Level.INFO)) {
            log.info("Server started on " + boundAddress.getHostName() + ":" + boundAddress.getPort());
        }

    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.daemon.server.Server#stop()
     */
    @Override
    public final void stop() throws ServerLifecycleException, IllegalStateException {

        // Use an anonymous logger because the JUL LogManager will not log after process shutdown has been received
        final Logger log = Logger.getAnonymousLogger();
        log.addHandler(new Handler() {

            private final String PREFIX = "[" + NettyServer.class.getSimpleName() + "] ";

            @Override
            public void publish(final LogRecord record) {
                System.out.println(PREFIX + record.getMessage());

            }

            @Override
            public void flush() {

            }

            @Override
            public void close() throws SecurityException {
            }
        });

        if (!this.isRunning()) {
            throw new IllegalStateException("Server is not running");
        }

        if (log.isLoggable(Level.INFO)) {
            log.info("Requesting shutdown...");
        }

        // Signal engine to shut down
        stopInternal();

        // Kill the shutdown service
        shutdownService.shutdownNow();
        shutdownService = null;

        // Not running
        running = false;
        boundAddress = null;

        if (log.isLoggable(Level.INFO)) {
            log.info("Server shutdown.");
        }
    }

    @Override
    public final boolean isRunning() {
        return running;
    }

    /**
     * The address configured to which we should bind
     *
     * @return
     */
    protected final InetSocketAddress getBindAddress() {
        return this.bindAddress;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.daemon.server.Server#getBoundAddress()
     */
    @Override
    public final InetSocketAddress getBoundAddress() throws IllegalStateException {
        if (!this.isRunning()) {
            throw new IllegalStateException("Server is not running");
        }
        return boundAddress;
    }

    /**
     * @param boundAddress
     *            the boundAddress to set
     */
    protected final void setBoundAddress(final InetSocketAddress boundAddress) {
        this.boundAddress = boundAddress;
    }

    /**
     * @return the deployedArchives
     */
    protected final ConcurrentMap<String, GenericArchive> getDeployedArchives() {
        return deployedArchives;
    }

    /**
     * @return the shrinkwrapDomain
     */
    protected final Domain getShrinkwrapDomain() {
        return shrinkwrapDomain;
    }

    /**
     * Executes the specified method name on the specified test class upon the archive with the specified archive ID in
     * an isolated ClassLoader containing only the archive's contents and the bootstrap {@link ClassLoader}. Note that
     * the system --classpath {@link ClassLoader} will not be visible to the test.
     *
     * @param archiveId
     * @param testClassName
     * @param methodName
     * @return
     * @throws IllegalStateException
     */
    protected final Serializable executeTest(final String archiveId, final String testClassName, final String methodName)
        throws IllegalStateException {
        final GenericArchive archive = this.getDeployedArchives().get(archiveId);
        if (archive == null) {
            throw new IllegalStateException("Archive with ID " + archiveId + " is not deployed");
        }

        // Use a ClassLoader with explicitly null parent to achieve isolation from --classpath
        final ShrinkWrapClassLoader isolatedArchiveCL = new ShrinkWrapClassLoader((ClassLoader) null, archive);

        final ClassLoader oldCl = SecurityActions.getTccl();
        ObjectOutputStream objectOutstream = null;
        try {
            // We have to set the TCCL here due to ARQ-1181; if that's resolved we can remove all TCCL mucking
            SecurityActions.setTccl(isolatedArchiveCL);
            /*
             * All reflection in this section is to avoid CCE when we load these classes from the isolated ClassLoader,
             * we can't have them assignable to the ClassLoader which loaded this class.
             */
            final Class<?> testClass;
            try {
                testClass = isolatedArchiveCL.loadClass(testClassName);
            } catch (final ClassNotFoundException cnfe) {
                throw new IllegalStateException("Could not load class " + testClassName + " from deployed archive: "
                    + archive.toString());
            }
            final Class<?> testRunnersClass;
            try {
                testRunnersClass = isolatedArchiveCL.loadClass(CLASS_NAME_ARQ_TEST_RUNNERS);
            } catch (final ClassNotFoundException cnfe) {
                throw new IllegalStateException("Could not load class " + CLASS_NAME_ARQ_TEST_RUNNERS
                    + " from deployed archive: " + archive.toString());
            }
            final Method getTestRunnerMethod = testRunnersClass.getMethod(METHOD_NAME_GET_TEST_RUNNER,
                ClassLoader.class);
            final Object testRunner = getTestRunnerMethod.invoke(null, isolatedArchiveCL);
            final Class<?> testRunnerClass = testRunner.getClass();
            final Method executeMethod = testRunnerClass.getMethod(METHOD_NAME_EXECUTE, Class.class, String.class);
            final Serializable testResult = (Serializable) executeMethod.invoke(testRunner, testClass, methodName);
            return testResult;
        } catch (final IllegalAccessException iae) {
            throw new RuntimeException(iae);
        } catch (final InvocationTargetException ite) {
            throw new RuntimeException(ite);
        } catch (final NoSuchMethodException nsme) {
            throw new RuntimeException(nsme);
        } finally {
            SecurityActions.setTccl(oldCl);
            try {
                isolatedArchiveCL.close();
            } catch (final IOException ignore) {
            }
            if (objectOutstream != null) {
                try {
                    objectOutstream.close();
                } catch (final IOException ignore) {
                }
            }
        }
    }

    /**
     * Asynchronously calls upon {@link Server#stop()}
     */
    protected final void stopAsync() {

        shutdownService.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                ServerBase.this.stop();
                return null;
            }
        });
    }

    /**
     * Internal secured actions not to leak out of this class/package
     *
     * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
     */
    private static final class SecurityActions {
        private SecurityActions() {
            throw new UnsupportedOperationException("No instances");
        }

        private enum GetTcclAction implements PrivilegedAction<ClassLoader> {
            INSTANCE;

            @Override
            public ClassLoader run() {
                return Thread.currentThread().getContextClassLoader();
            }
        }

        static ClassLoader getTccl() {
            if (System.getSecurityManager() == null) {
                return Thread.currentThread().getContextClassLoader();
            }
            return AccessController.doPrivileged(GetTcclAction.INSTANCE);
        }

        static void setTccl(final ClassLoader cl) {
            assert cl != null : "ClassLoader must be specified";
            if (System.getSecurityManager() == null) {
                Thread.currentThread().setContextClassLoader(cl);
            }
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    Thread.currentThread().setContextClassLoader(cl);
                    return null;
                }
            });
        }
    }
}
