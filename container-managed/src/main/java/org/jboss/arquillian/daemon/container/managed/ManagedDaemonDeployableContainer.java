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
package org.jboss.arquillian.daemon.container.managed;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.net.InetSocketAddress;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.daemon.container.common.DaemonDeployableContainerBase;
import org.jboss.arquillian.daemon.protocol.wire.WireProtocol;

/**
 * {@link DeployableContainer} implementation for Managed Arquillian Server Daemon (handles start/stop of the server as
 * part of lifecycle).
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public class ManagedDaemonDeployableContainer extends
    DaemonDeployableContainerBase<ManagedDaemonContainerConfiguration> implements
    DeployableContainer<ManagedDaemonContainerConfiguration> {

    private static final Logger log = Logger.getLogger(ManagedDaemonDeployableContainer.class.getName());
    private static final String SYSPROP_KEY_JAVA_HOME = "java.home";

    private Thread shutdownHookThread;
    private File serverjarFile;
    private Process remoteProcess;

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.container.spi.client.container.DeployableContainer#getConfigurationClass()
     */
    @Override
    public Class<ManagedDaemonContainerConfiguration> getConfigurationClass() {
        return ManagedDaemonContainerConfiguration.class;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.daemon.container.common.DaemonDeployableContainerBase#setup(org.jboss.arquillian.daemon.container.common.DaemonContainerConfigurationBase)
     */
    @Override
    public void setup(final ManagedDaemonContainerConfiguration configuration) {
        super.setup(configuration);
        serverjarFile = new File(configuration.getServerJarFile());
    }

    /**
     * Starts the process, then forwards control to {@link DaemonDeployableContainerBase#start()} to connect.
     *
     * @see org.jboss.arquillian.daemon.container.common.DaemonDeployableContainerBase#start()
     */
    @Override
    public void start() throws LifecycleException {

        // Build the launch command
        final File javaHome = new File(SecurityActions.getSystemProperty(SYSPROP_KEY_JAVA_HOME));
        final List<String> command = new ArrayList<String>(10);
        command.add(javaHome.getAbsolutePath() + "/bin/java");
        command.add("-jar");
        command.add(serverjarFile.getAbsolutePath());
        final InetSocketAddress remoteAddress = this.getRemoteAddress();
        command.add(remoteAddress.getHostString());
        command.add(Integer.toString(remoteAddress.getPort()));

        // Launch the process
        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(Redirect.INHERIT);
        final Process process;
        try {
            process = processBuilder.start();
            this.remoteProcess = process;
        } catch (final IOException e) {
            throw new LifecycleException("Could not start container", e);
        }

        // Add a shutdown hook for when this current process terminates to kill the one we've launched
        final Runnable shutdownServerRunnable = new Runnable() {
            @Override
            public void run() {
                if (process != null) {
                    process.destroy();
                    try {
                        process.waitFor();
                    } catch (final InterruptedException e) {
                        Thread.interrupted();
                        throw new RuntimeException("Interrupted while awaiting server daemon process termination", e);
                    }
                }
            }
        };
        shutdownHookThread = new Thread(shutdownServerRunnable);
        Runtime.getRuntime().addShutdownHook(shutdownHookThread);

        // Call the super implementation (to handle connect)
        super.start();
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.daemon.container.common.DaemonDeployableContainerBase#stop()
     */
    @Override
    public void stop() throws LifecycleException {

        try {
            // Write the stop command
            this.getWriter().print(WireProtocol.COMMAND_STOP);
            // Terminate the command and flush
            this.getWriter().print(WireProtocol.COMMAND_EOF_DELIMITER);
            this.getWriter().flush();

            // Block until we get "OK" response
            final String response = this.getReader().readLine();
            if (log.isLoggable(Level.FINEST)) {
                log.finest("Response from stop: " + response);
            }

            log.info("Response from stop: " + response);

        } catch (final IOException ioe) {
            throw new LifecycleException(
                "Unexpected problem encountered during read of the response from stop request", ioe);
        } catch (final RuntimeException re) {
            throw new LifecycleException("Unexpected problem encountered during stop", re);
        } finally {
            // Call super implementation to close up resources
            super.stop();
        }

        // Block until the process is killed
        try {
            remoteProcess.waitFor();
        } catch (final InterruptedException ie) {
            Thread.interrupted();
        }
        // Null out
        remoteProcess = null;
    }

    private static final class SecurityActions {
        private SecurityActions() {
            throw new UnsupportedOperationException("No instance permitted");
        }

        static String getSystemProperty(final String key) {
            assert key != null && key.length() > 0 : "key must be specified";
            if (System.getSecurityManager() == null) {
                return System.getProperty(key);
            }
            return AccessController.doPrivileged(new PrivilegedAction<String>() {
                @Override
                public String run() {
                    return System.getProperty(key);
                }
            });
        }
    }
}
