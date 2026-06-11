# Quick Checklist: Fix Database Connection Profile

## 🔧 Configuration Changes (DONE ✅)
- [x] Updated `.project` file with faceted nature
- [x] Updated `pom.xml` - mssql-jdbc now in compile scope
- [ ] **ACTION NEEDED**: Restart Eclipse IDE completely

## 🔌 Eclipse Setup (TO DO)
- [ ] Step 1: Maven → Update Project (Force Update)
- [ ] Step 2: Install Database Tools Platform
  - Help → Install New Software
  - Search "Data Tools Platform"
  
- [ ] Step 3: Register SQL Server Driver
  - Window → Preferences
  - Data Management → Connectivity → Driver Definitions
  - New → Select "Microsoft SQL Server"
  - Verify mssql-jdbc JAR is detected
  
- [ ] Step 4: Show Data Source Explorer
  - Window → Show View → Other
  - Search "Data Source Explorer"
  
- [ ] Step 5: Create Database Connection
  - Right-click Database Connections → New
  - Server: localhost
  - Port: 1433
  - Database: kiemdinh
  - User: sa1
  - Password: Cdit@mothai34nam
  
- [ ] Step 6: Test Connection
  - Click "Test Connection" button
  - Should see "Connection successful" message

## 🐛 If Still Not Working
1. [ ] Check Error Log (Window → Show View → Error Log)
2. [ ] Test SQL Server access:
   ```
   sqlcmd -S localhost,1433 -U sa1 -P "Cdit@mothai34nam" -Q "SELECT @@VERSION"
   ```
3. [ ] Check if SQL Server is running on port 1433
4. [ ] Verify firewall allows connections
5. [ ] Review DATABASE_CONNECTION_ISSUES.md for detailed info

## 📋 Key Files
- `.project` - Eclipse project configuration (UPDATED ✅)
- `pom.xml` - Maven dependencies (UPDATED ✅)
- `application.properties` - Spring Boot DB config (OK ✅)
- `src/main/java/com/db/IOCdbconnect.java` - Connection pooling class (OK ✅)

## 🎯 Expected Result
After completing all steps, you should be able to:
1. See "Database Connections" in Data Source Explorer
2. Expand connection and view tables in kiemdinh database
3. Browse schema objects
4. Write and test SQL queries for development

---
📌 See ECLIPSE_SETUP_GUIDE.md for detailed step-by-step instructions
📌 See DATABASE_CONNECTION_ISSUES.md for technical details
