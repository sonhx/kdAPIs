-- ============================================================
-- QA-EX Module — User Roles & Role Definitions Database Schema
-- SQL Server / T-SQL Compatible
-- Database: Evidence / IQA Database
-- References: personnel.id (User ID from personnel table)
-- ============================================================

-- ------------------------------------------------------------
-- 1. Role Definitions Table (dbo.qa_ex_roles)
-- ------------------------------------------------------------
IF NOT EXISTS (
    SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.qa_ex_roles') AND type = 'U'
)
BEGIN
    CREATE TABLE dbo.qa_ex_roles (
        role_id             INT IDENTITY(1,1) PRIMARY KEY,
        role_code           VARCHAR(50)   NOT NULL,         -- Unique role identifier (e.g., 'EX_ADMIN', 'EX_EVALUATOR')
        role_name           NVARCHAR(150) NOT NULL,         -- Display role name in Vietnamese (e.g., N'Trưởng đoàn ĐGN')
        description         NVARCHAR(500) NULL,             -- Detail description of permissions / scope
        is_active           BIT           NOT NULL DEFAULT 1,
        created_at          DATETIME2     NOT NULL DEFAULT GETDATE(),
        updated_at          DATETIME2     NOT NULL DEFAULT GETDATE()
    );

    CREATE UNIQUE NONCLUSTERED INDEX UQ_qa_ex_roles_code ON dbo.qa_ex_roles (role_code);

    PRINT 'Table dbo.qa_ex_roles created successfully.';
END
ELSE
BEGIN
    PRINT 'Table dbo.qa_ex_roles already exists.';
END
GO

-- ------------------------------------------------------------
-- 2. User Roles Assignment Table (dbo.qa_ex_user_roles)
-- References: user_id (matches personnel.id from dbo.personnel)
-- ------------------------------------------------------------
IF NOT EXISTS (
    SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.qa_ex_user_roles') AND type = 'U'
)
BEGIN
    CREATE TABLE dbo.qa_ex_user_roles (
        user_role_id        INT IDENTITY(1,1) PRIMARY KEY,
        user_id             VARCHAR(100)  NOT NULL,         -- Maps to personnel.id from dbo.personnel
        role_id             INT           NOT NULL,         -- Foreign Key referencing dbo.qa_ex_roles(role_id)
        is_active           BIT           NOT NULL DEFAULT 1,
        assigned_at         DATETIME2     NOT NULL DEFAULT GETDATE(),
        assigned_by         VARCHAR(100)  NULL,             -- User ID of administrator who assigned the role
        
        CONSTRAINT UQ_qa_ex_user_roles_user_role UNIQUE (user_id, role_id),
        CONSTRAINT FK_qa_ex_user_roles_role FOREIGN KEY (role_id) 
            REFERENCES dbo.qa_ex_roles (role_id) ON DELETE CASCADE
    );

    -- Index for fast user role lookups by personnel.id
    CREATE NONCLUSTERED INDEX IX_qa_ex_user_roles_user_id ON dbo.qa_ex_user_roles (user_id) WHERE is_active = 1;
    CREATE NONCLUSTERED INDEX IX_qa_ex_user_roles_role_id ON dbo.qa_ex_user_roles (role_id);

    PRINT 'Table dbo.qa_ex_user_roles created successfully.';
END
ELSE
BEGIN
    PRINT 'Table dbo.qa_ex_user_roles already exists.';
END
GO

-- ------------------------------------------------------------
-- 3. Initial Default Roles Seed Data
-- ------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM dbo.qa_ex_roles WHERE role_code = 'EX_ADMIN')
BEGIN
    INSERT INTO dbo.qa_ex_roles (role_code, role_name, description)
    VALUES
    ('EX_ADMIN',     N'Quản trị viên QA-EX',        N'Toàn quyền quản trị hệ thống Đánh giá ngoài, phân quyền và cấu hình tiêu chuẩn.'),
    ('EX_LEADER',    N'Trưởng đoàn ĐGN',           N'Phụ trách chỉ đạo đoàn Đánh giá ngoài, phê duyệt báo cáo và quyết định cổng sẵn sàng.'),
    ('EX_EVALUATOR', N'Thành viên Đoàn ĐGN',       N'Thực hiện thẩm định minh chứng, chấm điểm tiêu chuẩn và tham gia phỏng vấn.'),
    ('EX_SECRETARY', N'Thư ký Đoàn ĐGN',           N'Ghi nhận nhật ký vận hành, tổng hợp báo cáo DSR và theo dõi yêu cầu bằng chứng.'),
    ('EX_MONITOR',   N'Cán bộ Giám sát E-IQA',     N'Theo dõi tiến độ vận hành Onsite, bảng điều hành OT-02 và cảnh báo leo thang.');

    PRINT 'Default QA-EX role definitions seeded successfully.';
END
GO
