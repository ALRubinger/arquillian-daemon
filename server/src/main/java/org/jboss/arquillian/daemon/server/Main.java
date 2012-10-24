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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.jar.JarFile;
import java.util.logging.Logger;

import org.jboss.arquillian.daemon.server.jbossmodules.HackJarModuleLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;

/**
 * Standalone process entry point for the Arquillian Server Daemon
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public class Main {

    private static final Logger log = Logger.getLogger(Main.class.getName());
    private static final String LOCATION_MODULES = "META-INF/modules";
    private static final String SYSPROP_NAME_BIND_NAME = "arquillian.daemon.bind.name";
    private static final String SYSPROP_NAME_BIND_PORT = "arquillian.daemon.bind.port";

    /**
     * @param args
     */
    public static void main(final String[] args) {

        final ProtectionDomain domain = getProtectionDomain();
        final URL thisJar = domain.getCodeSource().getLocation();
        JarFile jar = null;
        try {
            try {
                jar = new JarFile(new File(thisJar.toURI()));
            } catch (final IOException ioe) {
                throw new RuntimeException("Could not obtain current JAR file: " + thisJar.toExternalForm());
            } catch (final URISyntaxException e) {
                throw new RuntimeException("Incorrectly-formatted URI to JAR: " + thisJar.toExternalForm());
            }

            final HackJarModuleLoader hack = new HackJarModuleLoader(jar, LOCATION_MODULES);

            final ModuleIdentifier nettyId = ModuleIdentifier.create("io.netty");
            final Module netty;
            try {
                netty = hack.loadModule(nettyId);
            } catch (final ModuleLoadException mle) {
                throw new RuntimeException("Could not load Netty", mle);
            }
            System.out.println("Netty loaded: " + netty);

            final Class<?> c;
            try {
                c = netty.getClassLoader().loadClass("io.netty.bootstrap.ServerBootstrap");
            } catch (final ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            System.out.println(c);

        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (final IOException ioe) {
                    // Swallow
                }
            }
        }

        // ModuleLoader loader = null;
        //
        // Server server = NettyServer.create("localhost", 0);
        // try {
        // server.start();
        // } catch (final ServerLifecycleException e) {
        // throw new RuntimeException(e);
        // }
        //
        // if (log.isLoggable(Level.INFO)) {
        // log.info("Arquillian Daemon Started");
        // }
        //
        // try {
        // server.stop();
        // } catch (final ServerLifecycleException e) {
        // throw new RuntimeException(e);
        // }
    }

    private static ProtectionDomain getProtectionDomain() throws SecurityException {
        final Class<Main> mainClass = Main.class;
        if (System.getSecurityManager() == null) {
            return mainClass.getProtectionDomain();
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<ProtectionDomain>() {
                @Override
                public ProtectionDomain run() {
                    return mainClass.getProtectionDomain();
                }
            });
        }
    }

}
