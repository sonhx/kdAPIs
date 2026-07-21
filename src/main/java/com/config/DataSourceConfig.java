package com.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DataSourceConfig {

    // 1. Core DataSource using Hikari
    @Primary
    @Bean(name = "dataSource")
    @ConfigurationProperties(prefix = "spring.datasource.core.hikari")
    public HikariDataSource dataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    // 2. Evidence DataSource using Hikari
    @Bean(name = "evidenceDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.evidence.hikari")
    public HikariDataSource evidenceDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
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
}