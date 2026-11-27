package com.dormrepair.v2.identity;
import java.lang.reflect.*;
import java.sql.*;
import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.config.BeanPostProcessor;
@TestConfiguration(proxyBeanMethods=false)
public class CatalogSqlCounterConfiguration {
 static final AtomicLong COUNT=new AtomicLong();
 @Bean static BeanPostProcessor countedDatasource(){return new BeanPostProcessor(){public Object postProcessAfterInitialization(Object bean,String name){if(!(bean instanceof DataSource original))return bean;return new org.springframework.jdbc.datasource.AbstractDataSource(){public Connection getConnection()throws SQLException{return wrap(original.getConnection());}public Connection getConnection(String u,String p)throws SQLException{return wrap(original.getConnection(u,p));}};}};}
 static Object invoke(Object target,Method m,Object[] args)throws Throwable{try{return m.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
 static Connection wrap(Connection c){return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(proxy,m,args)->{Object result=invoke(c,m,args);if(result instanceof Statement statement){String prepared=result instanceof PreparedStatement?(String)args[0]:null;Class<?> api=result instanceof PreparedStatement?PreparedStatement.class:Statement.class;return Proxy.newProxyInstance(api.getClassLoader(),new Class[]{api},(p,method,a)->{String sql=prepared!=null?prepared:(a!=null&&a.length>0&&a[0] instanceof String?(String)a[0]:"");if(method.getName().startsWith("execute")&&sql.stripLeading().toLowerCase(java.util.Locale.ROOT).matches("(?s)(select|with).*"))COUNT.incrementAndGet();return invoke(statement,method,a);});}return result;});}
}
