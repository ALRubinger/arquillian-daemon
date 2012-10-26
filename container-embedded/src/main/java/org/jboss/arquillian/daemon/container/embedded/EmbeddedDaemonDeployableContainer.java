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
package org.jboss.arquillian.daemon.container.embedded;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.daemon.main.Main;
import org.jboss.arquillian.daemon.protocol.DaemonProtocol;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

/**
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public class EmbeddedDaemonDeployableContainer implements DeployableContainer<EmbeddedDaemonContainerConfiguration> {

    private static final String ERROR_MESSAGE_DESCRIPTORS_UNSUPPORTED = "Descriptor deployment not supported";

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.container.spi.client.container.DeployableContainer#getConfigurationClass()
     */
    @Override
    public Class<EmbeddedDaemonContainerConfiguration> getConfigurationClass() {
        return EmbeddedDaemonContainerConfiguration.class;
    }

    @Override
    public void setup(final EmbeddedDaemonContainerConfiguration configuration) {
        // TODO Auto-generated method stub

    }

    @Override
    public void start() throws LifecycleException {
        Main.main(new String[] { "localhost", "12345" });
    }

    @Override
    public void stop() throws LifecycleException {
        // TODO Auto-generated method stub

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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void undeploy(final Archive<?> archive) throws DeploymentException {
        // TODO Auto-generated method stub

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
