package com.payflow.transactions.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    /** REST client used for the synchronous debit/credit hop to the Accounts service. */
    @Bean
    RestClient accountsRestClient(@Value("${accounts.base-url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Declare the topic so it exists with a known config rather than being auto-created. */
    @Bean
    NewTopic transactionsTopic() {
        return TopicBuilder.name("transactions").partitions(1).replicas(1).build();
    }
}
