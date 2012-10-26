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
package org.jboss.arquillian.daemon.main;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.logging.Logger;

import org.jboss.arquillian.daemon.server.Server;
import org.jboss.arquillian.daemon.server.Servers;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;

/**
 * Standalone process entry point for the Arquillian Server Daemon. Accepts two (optional) arguments:
 *
 * <ol>
 * <li>hostname - The name which the server should bind to. If left unspecified, the server will bind to the wildcard
 * address.</li>
 * <li>port - An integer between 0 and {@link Servers#MAX_PORT}; if unspecified or 0 the system will choose a port.</li>
 * </ol>
 *
 * These arguments may be specified as system properties {@link Main#SYSPROP_NAME_BIND_NAME} and
 * {@link Main#SYSPROP_NAME_BIND_PORT}, which will take precedence over command-line arguments.
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public class Main {

    private static final Logger log = Logger.getLogger(Main.class.getName());
    private static final String LOCATION_MODULES = "META-INF/modules";
    private static final String NAME_MODULE_ARQUILLIAN_DAEMON_SERVER = "org.jboss.arquillian.daemon";
    private static final String SYSPROP_NAME_BIND_NAME = "arquillian.daemon.bind.name";
    private static final String SYSPROP_NAME_BIND_PORT = "arquillian.daemon.bind.port";

    /**
     * Entry point; arguments as documented at the class level
     *
     * @param args
     */
    public static void main(final String[] args) {

        /*
         * Note: This entry point depends on the Executable JAR structure as assembled by the build; executing directly
         * from a .class file or via the IDE will fail.
         */

        // Get a reference to this JAR, and create a ModuleLoader pointing to its modules dir
        final ProtectionDomain domain = getProtectionDomain();
        final URL thisJar = domain.getCodeSource().getLocation();
        ModuleLoader loader = null;
        JarFile jar = null;
        try {
            try {
                jar = new JarFile(new File(thisJar.toURI()));
            } catch (final IOException ioe) {
                throw new RuntimeException("Could not obtain current JAR file: " + thisJar.toExternalForm());
            } catch (final URISyntaxException e) {
                throw new RuntimeException("Incorrectly-formatted URI to JAR: " + thisJar.toExternalForm());
            }

            // Create a module loader to load from this JAR
            loader = new HackJarModuleLoader(jar, LOCATION_MODULES);
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (final IOException ioe) {
                    // Swallow
                }
            }
        }

        // Load the Arquillian Daemon Module
        final ModuleIdentifier arquillianDaemonServerId = ModuleIdentifier.create(NAME_MODULE_ARQUILLIAN_DAEMON_SERVER);
        final Module arquillianDaemonModule;
        try {
            arquillianDaemonModule = loader.loadModule(arquillianDaemonServerId);
        } catch (final ModuleLoadException mle) {
            throw new RuntimeException("Could not load Arquillian Daemon module", mle);
        }

        // Use reflection to get the server factory class to avoid CCE
        final Class<?> serverFactoryClass;
        try {
            serverFactoryClass = arquillianDaemonModule.getClassLoader().loadClass(Servers.class.getName());
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        final Method createServerMethod = getMethod(serverFactoryClass, Servers.METHOD_NAME_CREATE,
            Servers.METHOD_PARAMS_CREATE);

        // Get the bind name
        String hostname = null;
        if (args.length >= 1) {
            hostname = args[0];
        }
        hostname = getDefaultValue(SYSPROP_NAME_BIND_NAME, hostname);

        // Get the bind port
        String port = null;
        if (args.length >= 2) {
            port = args[1];
        }
        port = getDefaultValue(SYSPROP_NAME_BIND_PORT, port);
        if (port == null || port.length() == 0) {
            port = "0"; // Let the system select
        }

        // Make a server from the factory
        final Object server;
        try {
            server = createServerMethod.invoke(null, hostname, Integer.parseInt(port));
        } catch (final Exception e) {
            throw new RuntimeException("Could not create a new server instance", e);
        }

        // Start
        final Class<?> serverClass;
        try {
            serverClass = arquillianDaemonModule.getClassLoader().loadClass(Server.class.getName());
        } catch (final ClassNotFoundException cnfe) {
            throw new RuntimeException("Could not get server interface class", cnfe);
        }
        final Method startMethod = getMethod(serverClass, Server.METHOD_NAME_START, Server.METHOD_PARAMS_START);
        try {
            startMethod.invoke(server);
        } catch (final Exception e) {
            throw new RuntimeException("Could not start server", e);
        }

        // Gracefully shut down the server when we quit
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                log.info("Caught SIGTERM, shutting down...");
                final Method stopMethod = getMethod(serverClass, Server.METHOD_NAME_STOP, Server.METHOD_PARAMS_STOP);
                try {
                    stopMethod.invoke(server);
                } catch (final Exception e) {
                    throw new RuntimeException("Could not stop server", e);
                }
            }
        }));
    }

    private static final String getDefaultValue(final String sysProp, final String suppliedValue) {
        final String fromSysProp = SecurityActions.getSystemProperty(sysProp);
        return fromSysProp != null ? fromSysProp : suppliedValue;
    }

    private static Method getMethod(final Class<?> clazz, final String methodName, final Class<?>[] paramTypes)
        throws SecurityException {
        assert clazz != null;
        assert methodName != null && methodName.length() > 0;
        assert paramTypes != null;
        try {
            if (System.getSecurityManager() == null) {
                return clazz.getMethod(methodName, paramTypes);
            } else {
                return AccessController.doPrivileged(new PrivilegedAction<Method>() {
                    @Override
                    public Method run() {
                        try {
                            return clazz.getMethod(methodName, paramTypes);
                        } catch (final NoSuchMethodException nsme) {
                            throw new RuntimeException("Could not get method " + methodName + " with types "
                                + Arrays.asList(paramTypes) + " from " + clazz, nsme);
                        }
                    }
                });
            }
        } catch (final NoSuchMethodException nsme) {
            throw new RuntimeException("Could not get method " + methodName + " with types "
                + Arrays.asList(paramTypes) + " from " + clazz, nsme);
        }
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
