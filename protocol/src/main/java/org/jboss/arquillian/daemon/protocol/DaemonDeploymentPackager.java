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
package org.jboss.arquillian.daemon.protocol;

import java.util.Collection;

import org.jboss.arquillian.container.test.spi.TestDeployment;
import org.jboss.arquillian.container.test.spi.client.deployment.DeploymentPackager;
import org.jboss.arquillian.container.test.spi.client.deployment.ProtocolArchiveProcessor;
import org.jboss.shrinkwrap.api.Archive;

/**
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public enum DaemonDeploymentPackager implements DeploymentPackager {

    INSTANCE;

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.container.test.spi.client.deployment.DeploymentPackager#generateDeployment(org.jboss.arquillian
     *      .container.test.spi.TestDeployment, java.util.Collection)
     */
    @Override
    public Archive<?> generateDeployment(final TestDeployment testDeployment,
        final Collection<ProtocolArchiveProcessor> processors) {
        // We only deploy the archive as specified by the user
        return testDeployment.getApplicationArchive();
    }

}
