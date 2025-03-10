/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.http.transport.passthrough;

import io.ballerina.stdlib.http.transport.contract.Constants;
import io.ballerina.stdlib.http.transport.contract.HttpClientConnector;
import io.ballerina.stdlib.http.transport.contract.HttpConnectorListener;
import io.ballerina.stdlib.http.transport.contract.HttpResponseFuture;
import io.ballerina.stdlib.http.transport.contract.HttpWsConnectorFactory;
import io.ballerina.stdlib.http.transport.contract.config.SenderConfiguration;
import io.ballerina.stdlib.http.transport.contract.exceptions.ServerConnectorException;
import io.ballerina.stdlib.http.transport.contractimpl.DefaultHttpWsConnectorFactory;
import io.ballerina.stdlib.http.transport.contractimpl.sender.channel.pool.ConnectionManager;
import io.ballerina.stdlib.http.transport.message.HttpCarbonMessage;
import io.ballerina.stdlib.http.transport.util.TestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

/**
 * A class for https pass-through message processor.
 */
public class PassthroughHttpsMessageProcessorListener implements HttpConnectorListener {
    private static final Logger LOG = LoggerFactory.getLogger(PassthroughHttpsMessageProcessorListener.class);
    private HttpClientConnector clientConnector;
    private HttpWsConnectorFactory httpWsConnectorFactory;
    private SenderConfiguration senderConfiguration;
    private static final String testValue = "Test Message";
    private boolean shareConnectionPool;
    private ConnectionManager connectionManager;

    PassthroughHttpsMessageProcessorListener(SenderConfiguration senderConfiguration) {
        this.httpWsConnectorFactory = new DefaultHttpWsConnectorFactory();
        this.senderConfiguration = senderConfiguration;
    }

    public PassthroughHttpsMessageProcessorListener(SenderConfiguration senderConfiguration,
                                                    boolean shareConnectionPool) {
        this.httpWsConnectorFactory = new DefaultHttpWsConnectorFactory();
        this.senderConfiguration = senderConfiguration;
        this.shareConnectionPool = shareConnectionPool;
        if (shareConnectionPool) {
            connectionManager = new ConnectionManager(senderConfiguration.getPoolConfiguration());
        }
    }

    @Override
    public void onMessage(HttpCarbonMessage httpRequestMessage) {
        Thread.startVirtualThread(() -> {
            HttpCarbonMessage outboundRequest = TestUtil.createHttpsPostReq(TestUtil.HTTP_SERVER_PORT, testValue, "");
            outboundRequest.setProperty(Constants.SRC_HANDLER, httpRequestMessage.getProperty(Constants.SRC_HANDLER));
            try {
                if (shareConnectionPool && connectionManager != null) {
                    clientConnector = httpWsConnectorFactory
                        .createHttpsClientConnector(new HashMap<>(), senderConfiguration, connectionManager);
                } else {
                    clientConnector = httpWsConnectorFactory
                        .createHttpsClientConnector(new HashMap<>(), senderConfiguration);
                }
                HttpResponseFuture future = clientConnector.send(outboundRequest);
                future.setHttpConnectorListener(new HttpConnectorListener() {
                    @Override
                    public void onMessage(HttpCarbonMessage httpResponse) {
                        Thread.startVirtualThread(() -> {
                            try {
                                httpRequestMessage.respond(httpResponse);
                            } catch (ServerConnectorException e) {
                                LOG.error("Error occurred during message notification: " + e.getMessage());
                            }
                        });
                    }

                    // Did not implement onError since this is a test case.
                    @Override
                    public void onError(Throwable throwable) {
                    }
                });
            } catch (Exception e) {
                LOG.error("Error occurred during message processing: ", e);
            }
        });
    }

    @Override
    public void onError(Throwable throwable) {
    }
}
