# Eclipse Database Connection Profile Issues - Analysis & Solutions

## Problem Summary
You're unable to create a database connection profile in Eclipse because the properties appear blank in the Data Source Explorer.

## Root Causes Identified

### 1. **Missing Eclipse Project Facets** ❌
The `.project` file is missing the required faceted nature:
- **Current**: Only has `jdt`, `m2e`, and Spring Boot validation natures
- **Missing**: `org.eclipse.wst.common.project.facet.core.nature` (Web Tools Platform)
- **Impact**: Eclipse's Data Source Explorer cannot properly recognize this project

### 2. **Maven Dependency Configuration Issue** ⚠️
In `pom.xml` (line 80-82):
```xml
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <scope>runtime</scope>  <!-- PROBLEM: Only available at runtime -->
</dependency>
```
- **Issue**: The `<scope>runtime</scope>` means the driver is NOT available at compile time or for Eclipse tooling
- **Impact**: Eclipse's database tools cannot find the driver properties

### 3. **Hardcoded DB Credentials**
- Database properties are hardcoded in Java files and `application.properties`
- Credentials are exposed (sa1 / Cdit@mothai34nam)

## Solutions

### Solution 1: Update Project Facets (IMMEDIATE FIX)
Add faceted project nature to `.project` file:
```xml
<nature>org.eclipse.wst.common.project.facet.core.nature</nature>
```

### Solution 2: Fix Maven Dependency Scope
Change the mssql-jdbc dependency scope from `runtime` to `compile`:
```xml
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <!-- Remove scope or set to compile for Eclipse tools -->
</dependency>
```

### Solution 3: Install Eclipse Database Tools (if not already installed)
If Eclipse Data Source Explorer is not available:
1. Go to **Help → Install New Software**
2. Work with: Select your Eclipse repository
3. Search for "Data Tools Platform"
4. Install "Eclipse Data Tools Platform"
5. Restart Eclipse

### Solution 4: Register SQL Server Driver in Eclipse
After installing Data Tools:
1. **Window → Preferences → Data Management → Connectivity → Driver Definitions**
2. Click **New** button
3. Select **Microsoft SQL Server 2019** (or latest available)
4. Fill in the JAR locations (should auto-detect from Maven)
5. Click **OK**

### Solution 5: Create Database Connection Profile
Once driver is registered:
1. **Window → Show View → Data Source Explorer**
2. Right-click on **Database Connections**
3. Select **New**
4. Choose **SQL Server 2019** (or your version)
5. Fill in:
   - **Database name**: kiemdinh
   - **Server name**: localhost
   - **Port**: 1433
   - **User name**: sa1
   - **Password**: Cdit@mothai34nam
6. Click **Finish**

## Additional Recommendations

### Security Improvements Needed:
1. Move credentials to environment variables or properties file (not in pom.xml)
2. Use Spring Boot's externalized configuration
3. Never commit sensitive credentials to version control

Example .env approach:
```properties
# In application-dev.properties or environment variables
spring.datasource.url=${DB_URL:jdbc:sqlserver://localhost:1433;databaseName=kiemdinh}
spring.datasource.username=${DB_USER:sa1}
spring.datasource.password=${DB_PASS}
```

## Files That Need Changes
- ✅ `.project` - Add faceted nature
- ✅ `pom.xml` - Change mssql-jdbc scope
- ✅ Eclipse settings - Configure driver definitions

## Current Database Configuration ✓
The following is already correctly configured:
- `application.properties` has valid SQL Server connection string
- `IOCdbconnect.java` has proper connection pooling logic
- Spring Boot Data JPA is properly configured
- SQL Server JDBC driver is in dependencies
