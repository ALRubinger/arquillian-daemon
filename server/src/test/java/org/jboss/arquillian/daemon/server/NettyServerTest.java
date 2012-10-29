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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

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
    public void testForNorman() throws ServerLifecycleException {
        final Server server = Servers.create(null, 12345);
        server.start();

        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "myarchive.jar").addClass(this.getClass());

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
            writer.flush();
            while ((read = archiveInstream.read(buffer, 0, buffer.length)) != -1) {
                socketOutstream.write(buffer, 0, read);
            }
            // Terminate the command
            writer.println();
            //
            //
            // final InputStream responseStream = socket.getInputStream();
            // reader = new BufferedReader(new InputStreamReader(responseStream));
            // System.out.println("Got response from deployment: " + reader.readLine());
            // System.out.println("Got response from deployment 2: " + reader.readLine());

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

        server.stop();
    }
}
