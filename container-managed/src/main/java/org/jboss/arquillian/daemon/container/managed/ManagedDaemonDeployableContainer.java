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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.ProcessBuilder.Redirect;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.daemon.protocol.arquillian.DaemonProtocol;
import org.jboss.arquillian.daemon.protocol.wire.WireProtocol;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

/**
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public class ManagedDaemonDeployableContainer implements DeployableContainer<ManagedDaemonContainerConfiguration> {

    private static final String ERROR_MESSAGE_DESCRIPTORS_UNSUPPORTED = "Descriptor deployment not supported";

    private Thread shutdownThread;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

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
        final File file = new File("target/arquillian-daemon-main.jar"); // Props
        final File javaHome = new File(System.getProperty("java.home"));
        final List<String> command = new ArrayList<String>(10);
        command.add(javaHome.getAbsolutePath() + "/bin/java");
        command.add("-jar");
        command.add(file.getAbsolutePath());
        command.add("localhost"); // Props
        command.add("12345"); // Props

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

        try {
            Thread.sleep(1000);
        } catch (final InterruptedException e) {
            Thread.interrupted();
        }
    }

    @Override
    public void stop() throws LifecycleException {

        executorService.shutdownNow();

        // TODO
        throw new UnsupportedOperationException("TBD");

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

        // final Bootstrap bootstrap = new Bootstrap();
        //
        // bootstrap.group(new NioEventLoopGroup()).channel(NioSocketChannel.class)
        // .remoteAddress(new InetSocketAddress("localhost", 12345)).handler(new ChannelInitializer<SocketChannel>() {
        // @Override
        // public void initChannel(final SocketChannel channel) throws Exception {
        // final ChannelPipeline pipeline = channel.pipeline();
        // // Decoders
        // // pipeline.addLast("frameDecoderClient",
        // // new DelimiterBasedFrameDecoder(80, Delimiters.nulDelimiter()));
        // // pipeline.addLast("base64DecoderClient", new Base64Decoder());
        //
        // // Encoder
        // // pipeline.addLast("base64EncoderClient", new Base64Encoder());
        // // pipeline.addLast("mine", new ObjectEncoder());
        //
        // }
        //
        // });
        //
        // final Channel channel;
        // try {
        // channel = bootstrap.connect().sync().channel();
        // final ChannelFuture future = channel.write(archive);
        // future.await();
        // } catch (final InterruptedException e) {
        // Thread.interrupted();
        // }

        Socket socket = null;
        BufferedReader reader = null;
        try {
            socket = new Socket("localhost", 12345);
            final OutputStream socketOutstream = socket.getOutputStream();
            final PrintWriter writer = new PrintWriter(new OutputStreamWriter(socketOutstream, WireProtocol.CHARSET),
                true);

            // Now write the archive
            final InputStream archiveInstream = archive.as(ZipExporter.class).exportAsInputStream();
            int read = 0;
            final byte[] buffer = new byte[1024];
            writer.print(WireProtocol.COMMAND_DEPLOY);
            // while ((read = archiveInstream.read(buffer, 0, buffer.length)) != -1) {
            // socketOutstream.write(buffer, 0, read);
            // System.out.println("Wrote: " + read);
            // }
            // Terminate the command
            writer.println();
            socketOutstream.flush();
            System.out.println("all she wrote on the client");

            // writer.println(WireProtocol.COMMAND_STOP);
            // final InputStream in = socket.getInputStream();
            // reader = new BufferedReader(new InputStreamReader(in));
            // final String response = reader.readLine();
            // System.out.println("Client got response: " + response);

        } catch (final UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (final IOException ignore) {
                }
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException ignore) {
                }
            }
        }

        // TODO
        throw new UnsupportedOperationException("TBD");
    }

    @Override
    public void undeploy(final Archive<?> archive) throws DeploymentException {
        // TODO
        throw new UnsupportedOperationException("TBD");
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
}
