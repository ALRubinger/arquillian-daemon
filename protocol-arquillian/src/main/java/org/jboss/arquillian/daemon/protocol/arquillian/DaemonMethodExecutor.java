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

import java.util.logging.Logger;

import org.jboss.arquillian.container.test.spi.ContainerMethodExecutor;
import org.jboss.arquillian.test.spi.TestMethodExecutor;
import org.jboss.arquillian.test.spi.TestResult;

/**
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public class DaemonMethodExecutor implements ContainerMethodExecutor {

    private static final Logger log = Logger.getLogger(DaemonMethodExecutor.class.getName());

    private final DeploymentContext context;

    DaemonMethodExecutor(final DeploymentContext context) {
        if (context == null) {
            throw new IllegalArgumentException("deployment context must be specified");
        }
        this.context = context;
    }

    /**
     * {@inheritDoc}
     *
     * @see org.jboss.arquillian.container.test.spi.ContainerMethodExecutor#invoke(org.jboss.arquillian.test.spi.TestMethodExecutor)
     */
    @Override
    public TestResult invoke(final TestMethodExecutor testMethodExecutor) {

        assert testMethodExecutor != null : "Test method executor is required";
        final TestResult testResult = new TestResult();

        // Invoke
        Throwable throwable = null;
        final long startTime = System.currentTimeMillis();
        final long endTime;
        try {
            // TODO This is going local, need to bridge to the server
            testMethodExecutor.invoke();
        } catch (final Throwable t) {
            throwable = t;
        } finally {
            endTime = System.currentTimeMillis();
        }

        // Populate the value object
        final TestResult.Status status = throwable == null ? TestResult.Status.PASSED : TestResult.Status.FAILED;
        testResult.setStatus(status);
        testResult.setStart(startTime);
        testResult.setEnd(endTime);
        testResult.setThrowable(throwable);

        // Return
        return testResult;
    }

}
