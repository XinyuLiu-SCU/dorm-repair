package com.dormrepair.config;

import javax.sql.DataSource;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@ConditionalOnProperty(name = "app.workflow.enabled", havingValue = "true")
public class WorkflowTransactionConfig {
    @Bean
    SmartInitializingSingleton verifyWorkflowTransactionResources(ProcessEngine engine, DataSource dataSource,
            JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        return () -> {
            var configuration = (SpringProcessEngineConfiguration) engine.getProcessEngineConfiguration();
            DataSource engineSource = configuration.getDataSource();
            if (engineSource instanceof TransactionAwareDataSourceProxy proxy) engineSource = proxy.getTargetDataSource();
            if (configuration.getTransactionManager() != transactionManager || engineSource != dataSource
                    || jdbc.getDataSource() != dataSource)
                throw new IllegalStateException("Flowable and JDBC must share the DataSource and transaction manager");
        };
    }
}
