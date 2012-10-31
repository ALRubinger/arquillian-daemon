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
package org.jboss.arquillian.daemon.protocol.wire;

/**
 * Defines the wire protocol for the Arquillian Server Daemon.
 *
 * To stop:
 * <code>CMD stop<<EOF</code>
 * To deploy:
 * <code>DPL ${zip-formatted contents}<<EOF</code>
 * To undeploy:
 * <code>CMD undeploy ${deploymentName}<<EOF</code>
 *
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public interface WireProtocol {

    /**
     * Charset used in codec of String/bytes
     */
    String CHARSET = "UTF-8";

    String PREFIX_STRING_COMMAND = "CMD ";
    String PREFIX_DEPLOY_COMMAND = "DPL ";

    String COMMAND_STOP = PREFIX_STRING_COMMAND + "stop";

    /**
     * To be prepended with the byte contents of a ZIP-formatted stream
     */
    String COMMAND_DEPLOY_PREFIX = PREFIX_DEPLOY_COMMAND;

    /**
     * Marks the end of a command
     */
    String COMMAND_EOF_DELIMITER = "<<EOF";

    /**
     * To be prepended to a valid current deployment name
     */
    String COMMAND_UNDEPLOY_PREFIX = PREFIX_STRING_COMMAND + "undeploy ";

    String RESPONSE_OK_PREFIX = "OK ";
    String RESPONSE_ERROR_PREFIX = "ERR ";

}
