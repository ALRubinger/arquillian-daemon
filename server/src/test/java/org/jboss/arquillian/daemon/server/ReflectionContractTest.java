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

import java.lang.reflect.Method;

import junit.framework.Assert;

import org.junit.Test;

/**
 * Tests to validate the reflection contracts of {@link Server} and {@link Servers}
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public class ReflectionContractTest {

    @Test
    public void startMethod() throws NoSuchMethodException {
        final Method method = Server.class.getMethod(Server.METHOD_NAME_START, Server.METHOD_PARAMS_START);
        Assert.assertNotNull(method);
    }

    @Test
    public void stopMethod() throws NoSuchMethodException {
        final Method method = Server.class.getMethod(Server.METHOD_NAME_STOP, Server.METHOD_PARAMS_STOP);
        Assert.assertNotNull(method);
    }

    @Test
    public void createMethod() throws NoSuchMethodException {
        final Method method = Servers.class.getMethod(Servers.METHOD_NAME_CREATE, Servers.METHOD_PARAMS_CREATE);
        Assert.assertNotNull(method);
    }

}
