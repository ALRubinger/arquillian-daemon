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

import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.arquillian.container.test.spi.TestDeployment;
import org.jboss.arquillian.container.test.spi.client.deployment.DeploymentPackager;
import org.jboss.arquillian.container.test.spi.client.deployment.ProtocolArchiveProcessor;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

/**
 * {@link DeploymentPackager} to merge auxiliar archive contents with the archive provided by the user
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public enum DaemonDeploymentPackager implements DeploymentPackager {

    INSTANCE;

    private static final Logger log = Logger.getLogger(DaemonDeploymentPackager.class.getName());

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.container.test.spi.client.deployment.DeploymentPackager#generateDeployment(org.jboss.arquillian
     *      .container.test.spi.TestDeployment, java.util.Collection)
     */
    @Override
    public Archive<?> generateDeployment(final TestDeployment testDeployment,
        final Collection<ProtocolArchiveProcessor> processors) {
        // Merge auxiliary archives with the declared for ARQ and testrunner support
        final JavaArchive archive = testDeployment.getApplicationArchive().as(JavaArchive.class);
        if (log.isLoggable(Level.FINEST)) {
            log.finest("Archive before additional packaging: " + archive.toString(true));
        }
        for (final Archive<?> auxArchive : testDeployment.getAuxiliaryArchives()) {

            // TODO THIS IS BROKEN IN SHRINKWRAP, FIX IT SHRINKWRAP-431
            // archive.merge(auxArchive);

            // TODO This hack replaces the above.
            final Map<ArchivePath, Node> content = auxArchive.getContent();
            final Collection<ArchivePath> paths = content.keySet();
            for (final ArchivePath path : paths) {
                final Node current = archive.get(path);
                final Node aux = content.get(path);
                if (current != null) {
                    if (current.getAsset() == null && aux.getAsset() == null) {
                    } else if (current.getAsset() == null && aux.getAsset() != null) {
                        throw new IllegalStateException("Current archive has dir and aux has " + aux.getAsset()
                            + " at " + path);
                    } else if (current.getAsset() != null && aux.getAsset() == null) {
                        throw new IllegalStateException("Current archive has " + current.getAsset()
                            + " and aux has dir at " + path);
                    } else {
                        archive.add(aux.getAsset(), path);
                    }
                } else {
                    if (aux.getAsset() != null) {
                        archive.add(aux.getAsset(), path);
                    }
                }
            }
        }
        if (log.isLoggable(Level.FINEST)) {
            log.finest("Archive after additional packaging: " + archive.toString(true));
        }

        return archive;

    }
}
