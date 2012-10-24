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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.arquillian.daemon.server.Server;
import org.jboss.arquillian.daemon.server.ServerLifecycleException;

/**
 * Netty-based implementation of a {@link Server}; not thread-safe.
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public class NettyServer implements Server {

    private static final Logger log = Logger.getLogger(NettyServer.class.getName());
    public static final int MAX_PORT = 65535;

    private final ServerBootstrap bootstrap;
    private boolean running;

    private NettyServer(final InetSocketAddress bindAddress) {
        // Precondition checks
        assert bindAddress != null : "Bind address must be specified";

        // Set up Netty Boostrap
        final ServerBootstrap bootstrap = new ServerBootstrap().group(new NioEventLoopGroup(), new NioEventLoopGroup())
            .channel(NioServerSocketChannel.class).localAddress(bindAddress)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(final SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new DiscardHandler());
                }
            }).childOption(ChannelOption.TCP_NODELAY, true).childOption(ChannelOption.SO_KEEPALIVE, true);

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

        // Start 'er up
        final ChannelFuture openChannel;
        try {
            openChannel = bootstrap.bind().sync();
        } catch (final InterruptedException ie) {
            Thread.interrupted();
            throw new ServerLifecycleException("Interrupted while awaiting server start", ie);
        } catch (final RuntimeException re) {
            // Exception xlate
            throw new ServerLifecycleException("Encountered error in binding; could not start server.", re);
        }
        if (log.isLoggable(Level.INFO)) {
            final InetSocketAddress boundAddress = ((InetSocketAddress) openChannel.channel().localAddress());
            log.info("Server started on " + boundAddress.getHostName() + ":" + boundAddress.getPort());
        }
        // Running
        running = true;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.daemon.server.Server#isRunning()
     */
    @Override
    public boolean isRunning() {
        return running;

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

        // Shutdown
        bootstrap.shutdown();

        // Not running
        running = false;

        if (log.isLoggable(Level.INFO)) {
            log.info("Server shutdown.");
        }
    }

    private static class DiscardHandler extends ChannelInboundByteHandlerAdapter {

        @Override
        public void inboundBufferUpdated(final ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            in.clear();
        }

    }

}
