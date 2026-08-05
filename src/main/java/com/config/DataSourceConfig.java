package com.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DataSourceConfig {

    // 1. Core DataSource using Hikari
    @Primary
    @Bean(name = "dataSource")
    @ConfigurationProperties(prefix = "spring.datasource.core.hikari")
    public HikariDataSource dataSource() {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
        ds.setPoolName("HikariPool-Core");
        ds.setMaxLifetime(120000);   // 2 minutes (120,000 ms)
        ds.setIdleTimeout(60000);     // 1 minute (60,000 ms)
        ds.setKeepaliveTime(30000);   // 30 seconds (30,000 ms keepalive ping)
        ds.setValidationTimeout(3000);// 3 seconds
        ds.setConnectionTestQuery("SELECT 1");
        return ds;
    }

    // 2. Evidence DataSource using Hikari
    @Bean(name = "evidenceDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.evidence.hikari")
    public HikariDataSource evidenceDataSource() {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
        ds.setPoolName("HikariPool-Evidence");
        ds.setMaxLifetime(120000);   // 2 minutes (120,000 ms)
        ds.setIdleTimeout(60000);     // 1 minute (60,000 ms)
        ds.setKeepaliveTime(30000);   // 30 seconds (30,000 ms keepalive ping)
        ds.setValidationTimeout(3000);// 3 seconds
        ds.setConnectionTestQuery("SELECT 1");
        return ds;
    }

    @Primary
    @Bean(name = "jdbcTemplate")
    public JdbcTemplate coreJdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "evidenceJdbcTemplate")
    public JdbcTemplate evidenceJdbcTemplate(@Qualifier("evidenceDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Primary
    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(@Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "evidenceTransactionManager")
    public PlatformTransactionManager evidenceTransactionManager(@Qualifier("evidenceDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}