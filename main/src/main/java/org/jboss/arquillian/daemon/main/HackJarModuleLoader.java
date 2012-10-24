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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Enumeration;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.jboss.modules.LocalModuleLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;

/**
 * Hack implmentation of a {@link ModuleLoader} capable of loading modules contained in a JAR under a known module root.
 * Explodes the module root into a temporary directory on the filesystem, by which a delegate {@link LocalModuleLoader}
 * may then load the modules. Temporarily necessary due to API restrictions in jboss-modules whereby the JarModuleLoader
 * is not accessible, nor is parsing a {@link ModuleSpec} from a <code>module.xml</code> file.
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 * @deprecated In place until we can work out proper support for this either in jboss-modules or via a new jboss-modules
 *             API which lets us load from a JAR
 */
@Deprecated
final class HackJarModuleLoader extends ModuleLoader {

    private static final String SYSPROP_NAME_TMP_DIR = "java.io.tmpdir";
    private static final String PREFIX_MODULES_DIR = "modules-";

    /**
     * Local delegate for loading modules once unpacked
     */
    private final ModuleLoader delegate;

    /**
     * Target location the modules will be unpacked to
     */
    private final File localModulesLocation;

    /**
     * Creates a new {@link ModuleLoader} instance for the specified JAR file, where modules are located in a root
     * denoted by the specified <code>moduleRoot</code> parameter (which is relative to the root of the JAR).
     *
     * @param jar
     * @param moduleRoot
     * @throws IllegalArgumentException
     *             If either argument is not specified
     */
    public HackJarModuleLoader(final JarFile jar, final String moduleRoot) throws IllegalArgumentException {

        // Precondition checks
        if (jar == null) {
            throw new IllegalArgumentException("JAR file must be specified");
        }
        if (moduleRoot == null || moduleRoot.length() == 0) {
            throw new IllegalArgumentException("Module root within the JAR must be specified");
        }

        // Set up the local temp modules directory
        final String tempDirName = SecurityActions.getSystemProperty(SYSPROP_NAME_TMP_DIR);
        final File tempDir = new File(tempDirName);
        final File modulesDir = new File(tempDir, PREFIX_MODULES_DIR + UUID.randomUUID().toString());
        if (!modulesDir.mkdir()) {
            throw new IllegalStateException("Could not create modules directory: " + modulesDir.getAbsolutePath());
        }

        // Explode
        final Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            final String name = entry.getName();
            if (name.startsWith(moduleRoot)) {
                final String parsedFullFileName = name.substring(moduleRoot.length() + 1);
                final int lastDirIndex = parsedFullFileName.lastIndexOf('/');
                if (lastDirIndex > 0) {
                    final String targetRelativeDirName = parsedFullFileName.substring(0, lastDirIndex);
                    final File targetDir = new File(modulesDir, targetRelativeDirName);
                    if (!targetDir.exists()) {
                        final boolean created = targetDir.mkdirs();
                        if (!created) {
                            throw new IllegalStateException("Could not create target directory: " + targetDir);
                        }
                    }
                    final String fileName = parsedFullFileName.substring(lastDirIndex);
                    final File targetFile = new File(targetDir, fileName);
                    registerRecursiveDeleteOnExit(targetFile, modulesDir);
                    InputStream in = null;
                    OutputStream out = null;
                    try {
                        in = jar.getInputStream(entry);
                        out = new FileOutputStream(targetFile);
                        final byte[] buffer = new byte[4096];
                        int read = 0;
                        while ((read = in.read(buffer, 0, buffer.length)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    } catch (final IOException e) {
                        throw new RuntimeException("Could not write " + entry.getName() + " to "
                            + targetFile.getAbsolutePath(), e);
                    } finally {
                        if (in != null) {
                            try {
                                in.close();
                            } catch (final IOException ioe) {
                                // Swallow
                            }
                        }
                        if (out != null) {
                            try {
                                out.close();
                            } catch (final IOException ioe) {
                                // Swallow
                            }
                        }
                    }
                }
            }
        }

        // Set
        this.delegate = new LocalModuleLoader(new File[] { modulesDir });
        this.localModulesLocation = modulesDir;

    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.modules.ModuleLoader#preloadModule(org.jboss.modules.ModuleIdentifier)
     */
    @Override
    protected Module preloadModule(final ModuleIdentifier identifier) throws ModuleLoadException {
        assert identifier != null;
        return ModuleLoader.preloadModule(identifier, delegate);
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.modules.ModuleLoader#toString()
     */
    @Override
    public String toString() {
        return HackJarModuleLoader.class.getSimpleName() + " delegating to modules in "
            + localModulesLocation.getAbsolutePath();
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.modules.ModuleLoader#findModule(org.jboss.modules.ModuleIdentifier)
     */
    @Override
    protected ModuleSpec findModule(final ModuleIdentifier moduleIdentifier) throws ModuleLoadException {
        // Due to incompatible API
        throw new UnsupportedOperationException("All loading should be done via the delegate in preoadModule");
    }

    private static void registerRecursiveDeleteOnExit(final File child, final File root) {
        if (System.getSecurityManager() == null) {
            child.deleteOnExit();
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    child.deleteOnExit();
                    return null;
                }
            });
        }
        final File parent = child.getParentFile();
        if (!child.equals(root)) {
            registerRecursiveDeleteOnExit(parent, root);
        }
    }

}
