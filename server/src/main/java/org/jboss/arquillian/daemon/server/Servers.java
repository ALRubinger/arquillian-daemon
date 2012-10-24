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

/**
 * Factory for creating {@link Server} instances
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public final class Servers {

    public static final int MAX_PORT = 65535;

    // These exposed as part of the API to help callers using reflection (for instance to avoid CCE if accessing the
    // server from a different ClassLoader)
    public static String METHOD_NAME_CREATE = "create";
    public static Class<?>[] METHOD_PARAMS_CREATE = new Class<?>[] { String.class, int.class };

    /**
     * No instances
     */
    private Servers() {
        throw new UnsupportedOperationException("No instances permitted");
    }

    /**
     * Creates a {@link Server} instance using the specified bind address and bind port. If no bind address is
     * specified, the server will bind on all available addresses. The port value must be between 0 and
     * {@link Servers#MAX_PORT}; if a value of 0 is selected, the system will choose a port.
     *
     * @param bindAddress
     * @param bindPort
     * @return
     * @throws IllegalArgumentException
     */
    public static Server create(final String bindAddress, final int bindPort) throws IllegalArgumentException {

        // Precondition checks
        if (bindPort < 0 || bindPort > MAX_PORT) {
            throw new IllegalArgumentException("Bind port must be between 0 and " + MAX_PORT);
        }

        // Create the inetaddress and ensure it's resolved
        final InetSocketAddress resolvedInetAddress = bindAddress == null ? new InetSocketAddress(bindPort)
            : new InetSocketAddress(bindAddress, bindPort);
        if (resolvedInetAddress.isUnresolved()) {
            throw new IllegalArgumentException("Address \"" + bindAddress + "\" could not be resolved");
        }

        // Create and return a new server instance
        return new NettyServer(resolvedInetAddress);
    }
}
