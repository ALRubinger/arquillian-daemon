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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.ProcessBuilder.Redirect;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.daemon.protocol.arquillian.DaemonProtocol;
import org.jboss.arquillian.daemon.protocol.arquillian.DeploymentContext;
import org.jboss.arquillian.daemon.protocol.wire.WireProtocol;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

/**
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public class ManagedDaemonDeployableContainer implements DeployableContainer<ManagedDaemonContainerConfiguration> {

    private static final Logger log = Logger.getLogger(ManagedDaemonDeployableContainer.class.getName());
    private static final String ERROR_MESSAGE_DESCRIPTORS_UNSUPPORTED = "Descriptor deployment not supported";

    private Thread shutdownThread;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private InetSocketAddress remoteAddress;
    private Socket socket;
    private OutputStream socketOutstream;
    private InputStream socketInstream;
    private BufferedReader reader;
    private PrintWriter writer;
    private String deploymentName;

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.container.spi.client.container.DeployableContainer#getConfigurationClass()
     */
    @Override
    public Class<ManagedDaemonContainerConfiguration> getConfigurationClass() {
        return ManagedDaemonContainerConfiguration.class;
    }

    @Override
    public void setup(final ManagedDaemonContainerConfiguration configuration) {
        // TODO Auto-generated method stub

    }

    @Override
    public void start() throws LifecycleException {

        // TODO Get from properties
        final String remoteHost = "localhost";
        final String remotePort = "12345";
        final InetSocketAddress address = new InetSocketAddress(remoteHost, Integer.parseInt(remotePort));
        this.remoteAddress = address;

        final File file = new File("target/arquillian-daemon-main.jar"); // TODO Props
        final File javaHome = new File(System.getProperty("java.home")); // TODO Security Action
        final List<String> command = new ArrayList<String>(10);
        command.add(javaHome.getAbsolutePath() + "/bin/java");
        command.add("-jar");
        command.add(file.getAbsolutePath());
        command.add(address.getHostString());
        command.add(Integer.toString(address.getPort()));

        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(Redirect.INHERIT);
        final Process process;
        try {
            process = processBuilder.start();
        } catch (final IOException e) {
            throw new LifecycleException("Could not start container", e);
        }

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

        shutdownThread = new Thread(shutdownServerRunnable);
        Runtime.getRuntime().addShutdownHook(shutdownThread);

        // Open up remote resources
        try {

            final long startTime = System.currentTimeMillis();
            final long acceptableTime = startTime * 1000 * 10; // 10 seconds from now
            Socket socket = null;
            while (true) {
                try {
                    // TODO Security Action
                    socket = new Socket(remoteAddress.getHostString(), remoteAddress.getPort());
                    if (log.isLoggable(Level.FINEST)) {
                        log.finest("Got connection to " + remoteAddress.toString());
                    }
                    break;
                } catch (final ConnectException ce) {
                    if (log.isLoggable(Level.FINEST)) {
                        log.finest("No connection yet available to remote process");
                    }
                    final long currentTime = System.currentTimeMillis();
                    // Time expired?
                    if (currentTime > acceptableTime) {
                        throw ce;
                    }
                    // Sleep and try again
                    try {
                        Thread.sleep(200);
                    } catch (final InterruptedException e) {
                        Thread.interrupted();
                        throw new RuntimeException("No one should be interrupting us waiting to connect", e);
                    }
                }
            }
            assert socket != null : "Socket should have been connected";
            this.socket = socket;
            final OutputStream socketOutstream = socket.getOutputStream();
            this.socketOutstream = socketOutstream;
            final PrintWriter writer = new PrintWriter(new OutputStreamWriter(socketOutstream, WireProtocol.CHARSET),
                true);
            this.writer = writer;
            final InputStream socketInstream = socket.getInputStream();
            this.socketInstream = socketInstream;
            final BufferedReader reader = new BufferedReader(new InputStreamReader(socketInstream));
            this.reader = reader;
        } catch (final IOException ioe) {
            this.closeRemoteResources();
            throw new LifecycleException("Could not open connection to remote process", ioe);
        }

        // TODO Instead do a loop where we check isRunning
        try {
            Thread.sleep(1000);
        } catch (final InterruptedException e) {
            Thread.interrupted();
        }
    }

    @Override
    public void stop() throws LifecycleException {
        this.closeRemoteResources();
        executorService.shutdownNow();
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.container.spi.client.container.DeployableContainer#getDefaultProtocol()
     */
    @Override
    public ProtocolDescription getDefaultProtocol() {
        return DaemonProtocol.DESCRIPTION;
    }

    @Override
    public ProtocolMetaData deploy(final Archive<?> archive) throws DeploymentException {

        final String deploymentName;
        try {

            // Write the deploy command prefix and flush it
            writer.print(WireProtocol.COMMAND_DEPLOY_PREFIX);
            writer.flush();
            // Now write the archive
            archive.as(ZipExporter.class).exportTo(socketOutstream);
            socketOutstream.flush();
            // Terminate the command
            writer.write(WireProtocol.COMMAND_EOF_DELIMITER);
            writer.flush();

            // Block until we get "OK" response
            final String response = reader.readLine();
            if (!response.startsWith(WireProtocol.RESPONSE_OK_PREFIX)) {
                throw new DeploymentException("Did not receive proper response from the server, instead was: "
                    + response);
            }
            if (log.isLoggable(Level.FINEST)) {
                log.finest("Response from deployment: " + response);
            }

            // Set deployment name
            final int startIndex = new String(WireProtocol.RESPONSE_OK_PREFIX + WireProtocol.COMMAND_DEPLOY_PREFIX)
                .length();
            deploymentName = response.substring(startIndex);
            if (log.isLoggable(Level.FINER)) {
                log.finer("Got deployment: " + deploymentName);
            }
            this.deploymentName = deploymentName;

        } catch (final IOException ioe) {
            this.closeRemoteResources();
            throw new DeploymentException("I/O problem encountered during deployment", ioe);
        } catch (final RuntimeException re) {
            this.closeRemoteResources();
            throw new DeploymentException("Unexpected problem encountered during deployment", re);
        }

        // Create and return ProtocolMetaData
        final ProtocolMetaData pmd = new ProtocolMetaData();
        final DeploymentContext socketContext = DeploymentContext.create(deploymentName, remoteAddress);
        pmd.addContext(socketContext);
        return pmd;
    }

    @Override
    public void undeploy(final Archive<?> archive) throws DeploymentException {

        assert deploymentName != null : "Deployment name should be set";

        try {
            // Write the undeploy command prefix and flush it
            writer.print(WireProtocol.COMMAND_UNDEPLOY_PREFIX);
            // Write the deployment name
            writer.print(deploymentName);
            // Terminate the command and flush
            writer.write(WireProtocol.COMMAND_EOF_DELIMITER);
            writer.flush();

            // Block until we get "OK" response
            final String response = reader.readLine();
            if (!response.startsWith(WireProtocol.RESPONSE_OK_PREFIX)) {
                throw new DeploymentException("Did not receive proper response from the server, instead was: "
                    + response);
            }

            if (log.isLoggable(Level.FINEST)) {
                log.finest("Response from undeployment: " + response);
            }

        } catch (final IOException ioe) {
            this.closeRemoteResources();
            throw new DeploymentException("I/O problem encountered during undeployment", ioe);
        } catch (final RuntimeException re) {
            this.closeRemoteResources();
            throw new DeploymentException("Unexpected problem encountered during undeployment", re);
        }
    }

    /**
     * @throws UnsupportedOperationException
     * @see org.jboss.arquillian.container.spi.client.container.DeployableContainer#deploy(org.jboss.shrinkwrap.descriptor.api.Descriptor)
     */
    @Override
    public void deploy(final Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException(ERROR_MESSAGE_DESCRIPTORS_UNSUPPORTED);
    }

    /**
     * @throws UnsupportedOperationException
     * @see org.jboss.arquillian.container.spi.client.container.DeployableContainer#undeploy(org.jboss.shrinkwrap.descriptor.api.Descriptor)
     */
    @Override
    public void undeploy(final Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException(ERROR_MESSAGE_DESCRIPTORS_UNSUPPORTED);

    }

    /**
     * Safely close remote resources
     */
    private void closeRemoteResources() {
        if (reader != null) {
            try {
                reader.close();
            } catch (final IOException ignore) {
            }
        }
        if (writer != null) {
            writer.close();
        }
        if (socketOutstream != null) {
            try {
                socketOutstream.close();
            } catch (final IOException ignore) {
            }
        }
        if (socketInstream != null) {
            try {
                socketInstream.close();
            } catch (final IOException ignore) {
            }
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (final IOException ignore) {
            }
        }

    }
}
