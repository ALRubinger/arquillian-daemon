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

/**
 * Netty-based implementation of a {@link Server}; not thread-safe.
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
class NettyServer implements Server {

    private static final Logger log = Logger.getLogger(NettyServer.class.getName());

    private final ServerBootstrap bootstrap;
    private boolean running;
    private InetSocketAddress boundAddress;

    NettyServer(final InetSocketAddress bindAddress) {
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
        // Set bound address
        boundAddress = ((InetSocketAddress) openChannel.channel().localAddress());
        if (log.isLoggable(Level.INFO)) {
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

        if (log.isLoggable(Level.INFO)) {
            log.info("Requesting shutdown");
        }

        // Shutdown
        bootstrap.shutdown();

        // Not running
        running = false;
        boundAddress = null;

        if (log.isLoggable(Level.INFO)) {
            log.info("Server shutdown.");
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.daemon.server.Server#getBindAddress()
     */
    @Override
    public InetSocketAddress getBindAddress() throws IllegalStateException {
        if (!this.isRunning()) {
            throw new IllegalStateException("Server is not running");
        }
        return this.boundAddress;
    }

    private static class DiscardHandler extends ChannelInboundByteHandlerAdapter {

        @Override
        public void inboundBufferUpdated(final ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            in.clear();
        }

    }

}
