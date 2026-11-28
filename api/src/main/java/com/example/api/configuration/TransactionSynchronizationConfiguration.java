package com.example.api.configuration;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class TransactionSynchronizationConfiguration {
    @Bean
    static BeanPostProcessor synchronizeActualTransactions() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String name) {
                if (bean instanceof AbstractPlatformTransactionManager manager) {
                    // NOT_SUPPORTED must not retain the pre-check's MyBatis/JDBC connection.
                    // Otherwise every request holds one connection while REQUIRES_NEW waits
                    // for a second, exhausting the pool. Real transactions still synchronize
                    // rollback compensation, notifications and cache invalidation normally.
                    manager.setTransactionSynchronization(
                        AbstractPlatformTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION);
                }
                return bean;
            }
        };
    }
}
