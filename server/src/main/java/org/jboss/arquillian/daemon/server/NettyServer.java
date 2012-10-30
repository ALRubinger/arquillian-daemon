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
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.jboss.arquillian.daemon.protocol.wire.WireProtocol;
import org.jboss.shrinkwrap.api.ConfigurationBuilder;
import org.jboss.shrinkwrap.api.Domain;
import org.jboss.shrinkwrap.api.GenericArchive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.importer.ZipImporter;

/**
 * Netty-based implementation of a {@link Server}; not thread-safe (though invoking operations through its communication
 * channels is).
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
class NettyServer implements Server {

    private static final Logger log = Logger.getLogger(NettyServer.class.getName());

    private static final EofDecoder EOF_DECODER;
    static {
        try {
            EOF_DECODER = new EofDecoder();
        } catch (final UnsupportedEncodingException e) {
            throw new RuntimeException("Could not get encoding: " + WireProtocol.CHARSET, e);
        }
    }

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
                public void initChannel(final SocketChannel channel) throws Exception {
                    final ChannelPipeline pipeline = channel.pipeline();
                    pipeline.addLast(EOF_DECODER, new ActionControllerHandler());
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

    /**
     * Handler for all {@link String}-based commands to the server as specified in {@link WireProtocol}
     *
     * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
     */
    private class StringCommandHandler extends ChannelInboundMessageHandlerAdapter<String> {

        /**
         * {@inheritDoc}
         *
         * @see io.netty.channel.ChannelInboundMessageHandlerAdapter#messageReceived(io.netty.channel.ChannelHandlerContext,
         *      java.lang.Object)
         */
        @Override
        public void messageReceived(final ChannelHandlerContext ctx, final String message) throws Exception {
            if (log.isLoggable(Level.FINEST)) {
                log.finest("Got command: " + message);
            }

            // Read and handle commands
            try {
                if (WireProtocol.COMMAND_STOP.equals(message)) {

                    // Tell the client OK
                    final ByteBuf out = ctx.nextOutboundByteBuffer();
                    NettyServer.sendResponse(ctx, out, WireProtocol.RESPONSE_OK_PREFIX);

                    // Now stop (after we send the response, else we'll prematurely close the connection)
                    NettyServer.this.stop();
                }
                // Unsupported command
                else {
                    throw new UnsupportedOperationException("This server does not support command: " + message);
                }
            } finally {
                final ChannelPipeline pipeline = ctx.pipeline();
                pipeline.addLast(new ActionControllerHandler());
                pipeline.remove(this);
            }

        }

        /**
         * Ignores all exceptions on messages received if the server is not running, else delegates to the super
         * implementation.
         *
         * @see io.netty.channel.ChannelStateHandlerAdapter#exceptionCaught(io.netty.channel.ChannelHandlerContext,
         *      java.lang.Throwable)
         */
        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
            // If the server isn't running, ignore everything
            if (!NettyServer.this.isRunning()) {
                // Ignore, but log if we've got a fine-grained enough level set
                if (log.isLoggable(Level.FINEST)) {
                    log.finest("Got exception while server is not running: " + cause.getMessage());
                }
                ctx.close();
            } else {
                super.exceptionCaught(ctx, cause);
            }
        }

    }

    private static void sendResponse(final ChannelHandlerContext ctx, final ByteBuf out, final String response) {
        out.discardReadBytes();
        try {
            out.writeBytes(response.getBytes(WireProtocol.CHARSET));
            out.writeBytes(Delimiters.lineDelimiter()[0]);
        } catch (final UnsupportedEncodingException uee) {
            throw new RuntimeException("Unsupported encoding", uee);
        }
        ctx.flush();
    }

    private final class DeployHandlerAdapter extends ChannelInboundByteHandlerAdapter {

        @Override
        public void inboundBufferUpdated(final ChannelHandlerContext ctx, final ByteBuf in) throws Exception {
            try {
                log.info("using the " + this.getClass().getSimpleName());
                final InputStream instream = new ByteBufInputStream(in);

                final Set<ClassLoader> classloaders = new HashSet<ClassLoader>(1);
                classloaders.add(NettyServer.class.getClassLoader());
                log.info("Using ClassLoader: " + NettyServer.class.getClassLoader());
                final Domain domain = ShrinkWrap.createDomain(new ConfigurationBuilder().classLoaders(classloaders));
                final GenericArchive archive = domain.getArchiveFactory().create(ZipImporter.class)
                    .importFrom(instream).as(GenericArchive.class);
                instream.close();
                // Tell the client OK
                final ByteBuf out = ctx.nextOutboundByteBuffer();

                log.info("Got archive: " + archive.toString(true));
                NettyServer.sendResponse(ctx, out, WireProtocol.RESPONSE_OK_PREFIX + WireProtocol.COMMAND_DEPLOY);
            } finally {

                final ChannelPipeline pipeline = ctx.pipeline();
                log.info("Pipeline from DeployHandler: " + pipeline);
                pipeline.addLast(EOF_DECODER);
                pipeline.addLast(new ActionControllerHandler());
                pipeline.remove(this);
            }
        }

    }

    /**
     * Determines the type of request and adjusts the pipeline to handle appropriately
     *
     * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
     */
    private final class ActionControllerHandler extends ChannelInboundByteHandlerAdapter {

        @Override
        public void inboundBufferUpdated(final ChannelHandlerContext ctx, final ByteBuf in) throws Exception {

            // We require at least three bytes to determine the action taken
            if (in.readableBytes() < 3) {
                return;
            }

            // Get the pipeline so we can dynamically adjust it and fire events
            final ChannelPipeline pipeline = ctx.pipeline();

            // Pull out the magic header
            int readerIndex = in.readerIndex();
            final int magic1 = in.getUnsignedByte(readerIndex);
            final int magic2 = in.getUnsignedByte(readerIndex + 1);
            final int magic3 = in.getUnsignedByte(readerIndex + 2);

            log.info(new String(new byte[] { (byte) magic1, (byte) magic2, (byte) magic3 }, Charset
                .forName(WireProtocol.CHARSET)));

            // String-based Command?
            if (this.isStringCommand(magic1, magic2, magic3)) {
                // Adjust the pipeline such that we use the command handler
                pipeline.addLast(new DelimiterBasedFrameDecoder(80, Delimiters.lineDelimiter()), new StringDecoder(
                    Charset.forName(WireProtocol.CHARSET)), new StringCommandHandler());
                pipeline.remove(this);
            }
            // Deploy command?
            else if (this.isDeployCommand(magic1, magic2, magic3)) {
                // Set the reader index so we strip out the command portion, leaving only the bytes containing the
                // archive (the frame decoder will strip off the EOF delimiter)
                in.readerIndex(in.readerIndex() + WireProtocol.COMMAND_DEPLOY.length());

                // Adjust the pipeline such that we use the deploy handler only
                pipeline.addLast(new DeployHandlerAdapter());
                pipeline.remove(this);
                pipeline.remove(EOF_DECODER);
                log.info(pipeline.toString());
            } else {
                // Unknown command/protocol
                log.info("UNKNOWN COMMAND");
                in.clear();
                ctx.close();
                return;
            }

            // Write the bytes to the next inbound buffer and re-fire so the updated handlers in the pipeline can have a
            // go at it
            final ByteBuf nextInboundByteBuffer = ctx.nextInboundByteBuffer();
            nextInboundByteBuffer.writeBytes(in);
            pipeline.fireInboundBufferUpdated();

            // Archive deployment?
            // TODO
            // pipeline.addLast(
            // new ObjectDecoder(ClassResolvers.cacheDisabled(NettyServer.class.getClassLoader())),
            // new StringCommandHandler());
            // new StringDecoder()

        }

        /**
         * Determines whether we have a {@link String}-based command
         *
         * @param magic1
         * @param magic2
         * @param magic3
         * @return
         */
        private boolean isStringCommand(final int magic1, final int magic2, final int magic3) {
            // First the bytes matches command prefix?
            return magic1 == WireProtocol.PREFIX_STRING_COMMAND.charAt(0)
                && magic2 == WireProtocol.PREFIX_STRING_COMMAND.charAt(1)
                && magic3 == WireProtocol.PREFIX_STRING_COMMAND.charAt(2);
        }

        /**
         * Determines whether we have a deployment command
         *
         * @param magic1
         * @param magic2
         * @param magic3
         * @return
         */
        private boolean isDeployCommand(final int magic1, final int magic2, final int magic3) {
            return magic1 == WireProtocol.PREFIX_DEPLOY_COMMAND.charAt(0)
                && magic2 == WireProtocol.PREFIX_DEPLOY_COMMAND.charAt(1)
                && magic3 == WireProtocol.PREFIX_DEPLOY_COMMAND.charAt(2);
        }

    }

    /**
     * {@link DelimiterBasedFrameDecoder} implementation to use the {@link WireProtocol#COMMAND_EOF_DELIMITER},
     * stripping it from the buffer. Is {@link Sharable} to allow this to be added/removed more than once.
     *
     * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
     */
    @Sharable
    private static final class EofDecoder extends DelimiterBasedFrameDecoder {
        public EofDecoder() throws UnsupportedEncodingException {
            super(Integer.MAX_VALUE, true, Unpooled.wrappedBuffer(WireProtocol.COMMAND_EOF_DELIMITER
                .getBytes(WireProtocol.CHARSET)));
        }
    }

}
