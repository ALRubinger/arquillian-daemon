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

import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases to ensure the {@link NettyServer} is working as contracted
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public class NettyServerTest {

    @Test
    public void startProhibitedIfRunning() throws ServerLifecycleException {
        final Server server = Servers.create(null, 0);
        server.start();
        boolean gotExpectedException = false;
        try {
            try {
                server.start();
            } catch (final IllegalStateException ise) {
                gotExpectedException = true;
            }
            Assert.assertTrue("Server should not be able to be started while running", gotExpectedException);
        } finally {
            server.stop();
        }
    }

    @Test
    public void stopProhibitedIfNotRunning() throws ServerLifecycleException {
        final Server server = Servers.create(null, 0);
        boolean gotExpectedException = false;
        try {
            server.stop();
        } catch (final IllegalStateException ise) {
            gotExpectedException = true;
        }
        Assert.assertTrue("Server should not be able to be stopped if not running", gotExpectedException);
    }

}
