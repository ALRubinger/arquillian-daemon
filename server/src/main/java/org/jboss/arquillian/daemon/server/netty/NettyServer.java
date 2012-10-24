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
package org.jboss.arquillian.daemon.server.netty;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.arquillian.daemon.server.Server;
import org.jboss.arquillian.daemon.server.ServerLifecycleException;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

/**
 * Netty-based implementation of a {@link Server}
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public class NettyServer implements Server {

    private static final Logger log = Logger.getLogger(NettyServer.class.getName());
    public static final int MAX_PORT = 65535;
    private static final String OPTION_NAME_TCP_NO_DELAY = "child.tcpNoDelay";
    private static final String OPTION_NAME_KEEP_ALIVE = "child.keepAlive";
    private static final String OPTION_NAME_LOCAL_ADDRESS = "localAddress";

    private final ServerBootstrap bootstrap;
    private Channel openChannel;

    private NettyServer(final InetSocketAddress bindAddress) {
        // Precondition checks
        assert bindAddress != null : "Bind address must be specified";

        // Set up Netty Boostrap
        final ChannelFactory factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(),
            Executors.newCachedThreadPool());
        final ServerBootstrap bootstrap = new ServerBootstrap(factory);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() {
                return Channels.pipeline(new EchoChannelHandler());
            }
        });

        // Set options
        bootstrap.setOption(OPTION_NAME_TCP_NO_DELAY, true);
        bootstrap.setOption(OPTION_NAME_KEEP_ALIVE, true);
        bootstrap.setOption(OPTION_NAME_LOCAL_ADDRESS, bindAddress);

        // Set
        this.bootstrap = bootstrap;
    }

    /**
     * Creates a {@link Server} instance using the specified bind address and bind port. If no bind address is
     * specified, the server will bind on all available addresses. The port value must be between 0 and
     * {@link NettyServer#MAX_PORT}; if a value of 0 is selected, the system will choose a port
     *
     * @param bindAddress
     * @param bindPort
     * @return
     * @throws IllegalArgumentException
     */
    public static Server create(final String bindAddress, final int bindPort) throws IllegalArgumentException {

        if (bindPort < 0 || bindPort > MAX_PORT) {
            throw new IllegalArgumentException("Bind port must be between 0 and " + MAX_PORT);
        }

        final InetSocketAddress resolvedInetAddress = bindAddress == null ? new InetSocketAddress(bindPort)
            : new InetSocketAddress(bindAddress, bindPort);
        if (resolvedInetAddress.isUnresolved()) {
            throw new IllegalArgumentException("Address \"" + bindAddress + "\" could not be resolved");
        }
        return new NettyServer(resolvedInetAddress);

    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.daemon.server.Server#start()
     */
    @Override
    public void start() throws ServerLifecycleException, IllegalStateException {

        // Precondition checks
        if (this.isRunning()) {
            throw new IllegalStateException("Already running");
        }

        try {
            openChannel = bootstrap.bind();
        } catch (final RuntimeException re) {
            // Exception xlate
            throw new ServerLifecycleException("Encountered error in binding; could not start server.", re);
        }

        if (log.isLoggable(Level.INFO)) {
            final InetSocketAddress boundAddress = ((InetSocketAddress) openChannel.getLocalAddress());
            log.info("Server started on " + boundAddress.getHostName() + ":" + boundAddress.getPort());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.daemon.server.Server#isRunning()
     */
    @Override
    public boolean isRunning() {
        return openChannel != null;

    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.daemon.server.Server#stop()
     */
    @Override
    public void stop() throws ServerLifecycleException, IllegalStateException {
        if (!this.isRunning()) {
            throw new IllegalStateException("Server is not running");
        }
        final ChannelFuture closeOp = openChannel.close();
        if (log.isLoggable(Level.FINER)) {
            log.finer("Sent request to close " + openChannel);
        }
        try {
            closeOp.await(10, TimeUnit.SECONDS);
        } catch (final InterruptedException ie) {
            Thread.interrupted();
            log.warning("Interrupted while waiting for channel to close");
        }
        openChannel = null;
        if (log.isLoggable(Level.INFO)) {
            log.info("Server shutdown");
        }
    }

    private static class EchoChannelHandler extends SimpleChannelHandler {

        /*
         * (non-Javadoc)
         *
         * @see
         * org.jboss.netty.channel.SimpleChannelHandler#messageReceived(org.jboss.netty.channel.ChannelHandlerContext,
         * org.jboss.netty.channel.MessageEvent)
         */
        @Override
        public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
            log.info(e.getMessage().toString());
        }

    }

}
