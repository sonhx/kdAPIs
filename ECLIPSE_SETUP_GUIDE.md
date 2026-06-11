# Step-by-Step Guide: Fix Eclipse Database Connection Profile

## Changes Already Made ✅
1. ✅ Updated `.project` file - Added `org.eclipse.wst.common.project.facet.core.nature`
2. ✅ Updated `pom.xml` - Changed mssql-jdbc scope to `compile`

## Next Steps (Required in Eclipse)

### Step 1: Refresh Eclipse Project
1. In Eclipse, right-click on **kdAPIs** project
2. Select **Maven → Update Project...**
3. Check **Force Update of Snapshots/Releases**
4. Click **OK**
5. Wait for Maven to download dependencies

### Step 2: Install Eclipse Data Tools (if not already installed)
1. Go to **Help → Install New Software**
2. Work with: Select your Eclipse repository URL
3. Search for "Data Tools Platform" or "Eclipse Data Tools"
4. Check **Eclipse Data Tools Platform (DTP)** - Runtime
5. Click **Next** and **Finish**
6. Accept licenses and restart Eclipse when prompted

### Step 3: Register SQL Server Driver in Eclipse
1. Go to **Window → Preferences**
2. Navigate to **Data Management → Connectivity → Driver Definitions**
3. Click **New** button at bottom right
4. Select **Microsoft SQL Server** (choose 2019 or latest available)
5. Driver Name: Keep default or rename to `SQL Server (kdAPIs)`
6. Under "JAR List" tab:
   - Eclipse should auto-detect the mssql-jdbc JAR from Maven
   - If not, click **Add JARs** and navigate to:
     ```
     C:\Users\[YourUsername]\.m2\repository\com\microsoft\sqlserver\mssql-jdbc\[version]\mssql-jdbc-[version].jar
     ```
7. Click **OK** to save the driver definition

### Step 4: Show Data Source Explorer View
1. Go to **Window → Show View → Other**
2. Search for "Data Source Explorer"
3. Select it and click **Open**
4. A new view panel will appear at the bottom or side

### Step 5: Create New Database Connection
1. In the **Data Source Explorer** view, right-click **Database Connections**
2. Select **New**
3. Choose your SQL Server driver from the list
4. Click **Next**
5. Fill in connection properties:
   - **Database name**: kiemdinh
   - **Server name** (hostname): localhost
   - **Port number**: 1433
   - **User name**: sa1
   - **Password**: Cdit@mothai34nam
   - Check **"Save password"** if desired
6. Click **Test Connection** to verify
7. Click **Finish**

### Step 6: Verify Connection
After successful creation, you should see:
- **Database Connections → SQL Server - kiemdinh** in the explorer
- Can expand and see tables, views, procedures, etc.

## If Properties Still Appear Blank

### Troubleshooting:
1. **Restart Eclipse completely** (File → Exit, then relaunch)
2. **Clean binaries**:
   - Right-click project → Maven → Clean
   - Right-click project → Project → Clean
3. **Check Error Log**:
   - Window → Show View → Other → Error Log
   - Look for messages related to database connectivity
4. **Verify SQL Server is Running**:
   - Open Command Prompt and test connection:
     ```
     sqlcmd -S localhost,1433 -U sa1 -P "Cdit@mothai34nam" -Q "SELECT @@VERSION"
     ```
   - If this fails, SQL Server is not accessible

## Alternative: Use Spring Boot Configuration Only

If you cannot get Eclipse tools to work, you can still use the connection through:
- Application code via `IOCdbconnect.getConnection()`
- Spring Data JPA repositories
- The application will run fine on port 8085 with your configured connection

## Verification Commands (Command Prompt)

Test SQL Server connectivity:
```cmd
sqlcmd -S localhost,1433 -U sa1 -P "Cdit@mothai34nam" -d kiemdinh -Q "SELECT 1"
```

Test Maven build:
```cmd
cd E:\eclipse-workspace\kdAPIs
mvn clean compile
```

## Security Notes
⚠️ **Important**: Your database credentials (sa1 / Cdit@mothai34nam) are visible in:
- `application.properties`
- `dbconnect.java`
- `IOCdbconnect.java`

**Recommended**: Use environment variables instead:
```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=kiemdinh;encrypt=true;trustServerCertificate=true;
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASS}
```

Then set environment variables before running the app.
