package com.dormrepair.v2.core;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.PlatformTransactionManager;
import org.flowable.engine.*;
@Configuration(proxyBeanMethods=false) @ConditionalOnProperty(name="app.v2.core.enabled",havingValue="true") @DependsOn({"v2EnvironmentGuard","flywayInitializer"})
public class CoreEngineConfiguration {
 @Bean(destroyMethod="close") ProcessEngine v2ProcessEngine(DataSource ds,PlatformTransactionManager tx){var c=new org.flowable.spring.SpringProcessEngineConfiguration();c.setDataSource(ds);c.setTransactionManager(tx);c.setDatabaseSchema("public");c.setDatabaseSchemaUpdate("true");c.setAsyncExecutorActivate(false);c.setHistory("full");var engine=c.buildProcessEngine();var repo=engine.getRepositoryService();for(String[] resource:new String[][]{{"dormRepair","processes/dorm-repair.bpmn20.xml"},{"dormRepairV2","v2-processes/dormRepairV2.bpmn20.xml"}}){if(repo.createProcessDefinitionQuery().processDefinitionKey(resource[0]).count()==0)repo.createDeployment().name(resource[0]+" stable definition").addClasspathResource(resource[1]).deploy();}return engine;}
 @Bean RuntimeService coreRuntime(ProcessEngine e){return e.getRuntimeService();}
 @Bean TaskService coreTasks(ProcessEngine e){return e.getTaskService();}
 @Bean RepositoryService coreRepository(ProcessEngine e){return e.getRepositoryService();}
 @Bean HistoryService coreHistory(ProcessEngine e){return e.getHistoryService();}
}
