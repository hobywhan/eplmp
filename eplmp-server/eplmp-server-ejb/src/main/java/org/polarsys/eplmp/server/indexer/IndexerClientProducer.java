/*******************************************************************************
  * Copyright (c) 2017 DocDoku.
  * All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Eclipse Public License v1.0
  * which accompanies this distribution, and is available at
  * http://www.eclipse.org/legal/epl-v10.html
  *
  * Contributors:
  *    DocDoku - initial API and implementation
  *******************************************************************************/
package org.polarsys.eplmp.server.indexer;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import vc.inreach.aws.request.AWSSigner;
import vc.inreach.aws.request.AWSSigningRequestInterceptor;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton(name = "IndexerClientProducer")
public class IndexerClientProducer {

    private static final Logger LOGGER = Logger.getLogger(IndexerClientProducer.class.getName());

    private JestClient client;

    @Inject
    private IndexerConfigManager config;

    @PostConstruct
    public void open() {
        LOGGER.log(Level.INFO, "Create Elasticsearch client");

        String serverUri = config.getServerUri();
        String username = config.getUserName();
        String password = config.getPassword();
        String awsService = config.getAWSService();
        String awsRegion = config.getAWSRegion();
        String awsAccessKey = config.getAWSAccessKey();
        String awsSecretKey = config.getAWSSecretKey();

        HttpClientConfig.Builder httpConfigBuilder = new HttpClientConfig.Builder(serverUri)
                .multiThreaded(true)
                .defaultMaxTotalConnectionPerRoute(8);

        if (username != null && password != null)
            httpConfigBuilder.defaultCredentials(username, password);

        JestClientFactory factory;
        if (awsService != null && awsRegion != null && awsAccessKey != null && awsSecretKey != null) {

            final StaticCredentialsProvider staticCredentialsProvider = new StaticCredentialsProvider(new BasicAWSCredentials(awsAccessKey, awsSecretKey));
            final AWSSigner awsSigner = new AWSSigner(staticCredentialsProvider, awsRegion, awsService, () -> LocalDateTime.now(ZoneOffset.UTC));
            final AWSSigningRequestInterceptor requestInterceptor = new AWSSigningRequestInterceptor(awsSigner);

            factory = new JestClientFactory() {
                @Override
                protected HttpClientBuilder configureHttpClient(HttpClientBuilder builder) {
                    builder.addInterceptorLast(requestInterceptor);
                    return builder;
                }

                @Override
                protected HttpAsyncClientBuilder configureHttpClient(HttpAsyncClientBuilder builder) {
                    builder.addInterceptorLast(requestInterceptor);
                    return builder;
                }
            };
        } else {
            factory = new JestClientFactory();
        }
        factory.setHttpClientConfig(httpConfigBuilder.build());
        client = factory.getObject();

    }

    @PreDestroy
    public void close() {
        client.shutdownClient();
    }

    @Lock(LockType.READ)
    @Produces
    @ApplicationScoped
    public JestClient produce() {
        LOGGER.log(Level.INFO, "Producing Elasticsearch rest client");
        return client;
    }

}
