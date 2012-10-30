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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Logger;

import org.jboss.arquillian.daemon.protocol.wire.WireProtocol;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases to ensure the {@link NettyServer} is working as contracted
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public class NettyServerTest {

    private static final Logger log = Logger.getLogger(NettyServerTest.class.getName());

    @Test
    public void isNotRunningAfterCreate() throws ServerLifecycleException {
        final Server server = Servers.create(null, 0);
        Assert.assertFalse(server.isRunning());
    }

    @Test
    public void start() throws ServerLifecycleException {
        final Server server = Servers.create(null, 0);
        server.start();
        Assert.assertTrue(server.isRunning());
        server.stop();
    }

    @Test
    public void stop() throws ServerLifecycleException {
        final Server server = Servers.create(null, 0);
        server.start();
        server.stop();
        Assert.assertFalse(server.isRunning());
    }

    @Test
    public void restart() throws ServerLifecycleException {
        final Server server = Servers.create(null, 0);
        try {
            server.start();
            server.stop();
            server.start();
            Assert.assertTrue(server.isRunning());
        } finally {
            if (server.isRunning()) {
                server.stop();
            }
        }
    }

    @Test
    public void startProhibitedIfRunning() throws ServerLifecycleException {
        final Server server = Servers.create(null, 0);
        server.start();
        boolean gotExpectedException = false;
        try {
            try {
                server.start();
            } catch (final IllegalStateException ise) {
                gotExpectedException = true;
            }
            Assert.assertTrue("Server should not be able to be started while running", gotExpectedException);
        } finally {
            server.stop();
        }
    }

    @Test
    public void stopProhibitedIfNotRunning() throws ServerLifecycleException {
        final Server server = Servers.create(null, 0);
        boolean gotExpectedException = false;
        try {
            server.stop();
        } catch (final IllegalStateException ise) {
            gotExpectedException = true;
        }
        Assert.assertTrue("Server should not be able to be stopped if not running", gotExpectedException);
    }

    @Test
    public void deploy() throws Exception {

        // Create the server
        final Server server = Servers.create(null, 12345);
        server.start();

        // Make an archive
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "myarchive.jar").addClass(this.getClass());

        Socket socket = null;
        BufferedReader reader = null;
        try {
            socket = new Socket("localhost", 12345);
            final OutputStream socketOutstream = socket.getOutputStream();
            final PrintWriter writer = new PrintWriter(new OutputStreamWriter(socketOutstream, WireProtocol.CHARSET),
                true);

            // Write the deploy command prefix and flush it
            writer.print(WireProtocol.COMMAND_DEPLOY);
            writer.flush();
            // Now write the archive
            archive.as(ZipExporter.class).exportTo(socketOutstream);
            socketOutstream.flush();
            // Terminate the command
            writer.write(WireProtocol.COMMAND_EOF_DELIMITER);
            writer.flush();
            // Read and check the response
            final InputStream responseStream = socket.getInputStream();
            reader = new BufferedReader(new InputStreamReader(responseStream));
            final String response = reader.readLine();
            log.info("Got response: " + response);
            Assert.assertTrue(response.startsWith(WireProtocol.RESPONSE_OK_PREFIX));
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

            // Stop
            server.stop();
        }

    }

    @Test
    public void stopOverWireProtocol() throws Exception {

        // Create the server
        final Server server = Servers.create(null, 12345);
        server.start();

        Socket socket = null;
        BufferedReader reader = null;
        try {
            socket = new Socket("localhost", 12345);
            final OutputStream socketOutstream = socket.getOutputStream();
            final PrintWriter writer = new PrintWriter(new OutputStreamWriter(socketOutstream, WireProtocol.CHARSET),
                true);

            // Write the deploy command prefix and flush it
            writer.print(WireProtocol.COMMAND_STOP);
            writer.print(WireProtocol.COMMAND_EOF_DELIMITER);
            writer.flush();
            // Read and check the response
            final InputStream responseStream = socket.getInputStream();
            reader = new BufferedReader(new InputStreamReader(responseStream));
            final String response = reader.readLine();
            log.info("Got response: " + response);
            Assert.assertTrue(response.startsWith(WireProtocol.RESPONSE_OK_PREFIX));

            final long current = System.currentTimeMillis();
            // Try for 10s
            while (current < current + (10 * 1000)) {
                if (!server.isRunning()) {
                    log.info("Server shutdown over wire protocol");
                    return;
                }
                Thread.sleep(300);
            }
            Assert.fail("Server did not shut down via wire protocol request in the alloted time");

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

            // Stop
            if (server.isRunning()) {
                server.stop();
            }
        }

    }
}
