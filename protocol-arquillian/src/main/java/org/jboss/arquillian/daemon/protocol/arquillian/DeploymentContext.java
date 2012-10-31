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
package org.jboss.arquillian.daemon.protocol.arquillian;

import java.net.InetSocketAddress;

import org.jboss.arquillian.container.spi.client.protocol.metadata.NamedContext;

/**
 * Immutable {@link NamedContext} implementation backed by an {@link InetSocketAddress}
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public class DeploymentContext extends NamedContext {

    private final InetSocketAddress address;

    private DeploymentContext(final String deploymentName, final InetSocketAddress address) {
        super(deploymentName);
        this.address = address;
    }

    /**
     * Creates and returns a new {@link DeploymentContext} instance
     *
     * @param deploymentName
     *            Name of the deployment
     * @param address
     * @return
     * @throws IllegalArgumentException
     *             If the deployment name is not specified, or if the address is not specified or is unresolved
     */
    public static DeploymentContext create(final String deploymentName, final InetSocketAddress address)
        throws IllegalArgumentException {
        if (deploymentName == null || deploymentName.length() == 0) {
            throw new IllegalArgumentException("Deployment name must be specified");
        }
        if (address == null) {
            throw new IllegalArgumentException("socket must be specified");
        }
        if (address.isUnresolved()) {
            throw new IllegalArgumentException("address is not resolved: " + address.toString());
        }
        final DeploymentContext context = new DeploymentContext(deploymentName, address);
        return context;
    }

    /**
     * @return the address
     */
    public InetSocketAddress getAddress() {
        return address;
    }

}
