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

import java.net.InetSocketAddress;
import java.util.logging.Logger;

import org.eclipse.jetty.server.ServerConnector;

/**
 * Jetty implementation of a {@link Server}
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
final class JettyServer extends ServerBase implements Server {

    private static final Logger log = Logger.getLogger(JettyServer.class.getName());

    private final org.eclipse.jetty.server.Server jettyServer;

    JettyServer(final InetSocketAddress bindAddress) {
        super(bindAddress);

        // Set up the server
        jettyServer = new org.eclipse.jetty.server.Server();
        final ServerConnector connector = new ServerConnector(jettyServer);
        connector.setPort(bindAddress.getPort());
        connector.setHost(bindAddress.getHostString()); // No reverse IP lookup
    }

    /**
     * {@inheritDoc}
     *
     * @throws ServerLifecycleException
     * @throws IllegalStateException
     */
    @Override
    protected void startInternal() throws ServerLifecycleException, IllegalStateException {
        try {
            jettyServer.start();
        } catch (final Exception e) {
            throw new ServerLifecycleException("Could not start the internal HTTP server", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws ServerLifecycleException
     * @throws IllegalStateException
     */
    @Override
    protected void stopInternal() throws ServerLifecycleException, IllegalStateException {
        try {
            jettyServer.stop();
        } catch (final Exception e) {
            throw new ServerLifecycleException("Could not stop the internal HTTP server", e);
        }
    }
}
