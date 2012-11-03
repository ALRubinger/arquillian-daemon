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
package org.jboss.arquillian.daemon.container.managed;

import java.io.File;

import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;
import org.jboss.arquillian.daemon.container.common.DaemonContainerConfigurationBase;

/**
 * {@link ContainerConfiguration} implementation for Managed Containers
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public class ManagedDaemonContainerConfiguration extends DaemonContainerConfigurationBase implements
    ContainerConfiguration {

    private String serverJarFile;

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.container.spi.client.container.ContainerConfiguration#validate()
     */
    @Override
    public void validate() throws ConfigurationException {
        super.validate();
        if (serverJarFile == null || serverJarFile.length() == 0) {
            throw new ConfigurationException("\"serverJarFile\" must be specified");
        }
        final File file = new File(serverJarFile);
        if (!file.exists() || file.isDirectory()) {
            throw new ConfigurationException("Server JAR file must exist and not be a directory: "
                + file.getAbsolutePath());
        }
    }

    /**
     * @return the serverJarFile
     */
    public String getServerJarFile() {
        return serverJarFile;
    }

    /**
     * @param serverJarFile
     *            the serverJarFile to set
     */
    public void setServerJarFile(final String serverJarFile) {
        this.serverJarFile = serverJarFile;
    }

}
