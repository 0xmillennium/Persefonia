package dev.persefonia.app.transaction;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PostCommitTaskExecutorConfiguration {
    @Bean
    PostCommitTaskExecutor postCommitTaskExecutor() {
        return new SpringTransactionSynchronizationPostCommitTaskExecutor();
    }
}
