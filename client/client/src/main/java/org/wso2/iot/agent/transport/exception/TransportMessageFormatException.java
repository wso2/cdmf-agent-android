/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.iot.agent.transport.exception;

/**
 * This exception will be thrown when the mqtt message format fails.
 */
public class TransportMessageFormatException extends Exception {

    private String errorMessage;

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public TransportMessageFormatException(String msg, Exception nestedEx) {
        super(msg, nestedEx);
        setErrorMessage(msg);
    }

    public TransportMessageFormatException(String message, Throwable cause) {
        super(message, cause);
        setErrorMessage(message);
    }

    public TransportMessageFormatException(String msg) {
        super(msg);
        setErrorMessage(msg);
    }

    public TransportMessageFormatException() {
        super();
    }

    public TransportMessageFormatException(Throwable cause) {
        super(cause);
    }
}
