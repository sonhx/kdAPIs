-- ============================================================
-- CAPA Module — Database Schema (SQL Server / T-SQL)
-- Target Database: Evidence / IQA Database (evidenceJdbcTemplate)
-- Aligned with UI Page: http://localhost:3000/qa-ims/capa
-- ============================================================

-- ------------------------------------------------------------
-- 1. Main CAPA table (dbo.capa)
-- ------------------------------------------------------------
IF NOT EXISTS (
    SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.capa') AND type = 'U'
)
BEGIN
    CREATE TABLE dbo.capa (
        capa_id             INT IDENTITY(1,1) PRIMARY KEY,
        capa_code           NVARCHAR(30)  NOT NULL,         -- e.g. CAPA-26-001
        title               NVARCHAR(500) NOT NULL,         -- Tiêu đề hành động CAPA
        description         NVARCHAR(MAX) NULL,             -- Mô tả chi tiết hành động & giải pháp
        capa_type           NVARCHAR(30)  NOT NULL DEFAULT N'Khắc phục', 
                                                            -- N'Khắc phục' | N'Phòng ngừa'
        status              NVARCHAR(30)  NOT NULL DEFAULT 'processing',
                                                            -- 'processing' (Đang xử lý)
                                                            -- 'pending_closure' (Chờ duyệt đóng)
                                                            -- 'closed' (Đã khép vòng)
                                                            -- 'rejected' (Đã hủy / Yêu cầu sửa đổi)
        department_id       NVARCHAR(50)  NULL,             -- Mã Khoa/Đơn vị
        department_name     NVARCHAR(200) NOT NULL,         -- Tên Khoa/Đơn vị (e.g. 'Khoa Công nghệ thông tin')
        open_date           DATE          NOT NULL DEFAULT CAST(GETDATE() AS DATE), -- Ngày mở
        due_date            DATE          NOT NULL,         -- Hạn hoàn thành
        completed_date      DATE          NULL,             -- Ngày thực tế hoàn thành
        effectiveness       NVARCHAR(50)  NOT NULL DEFAULT N'Đang đánh giá',
                                                            -- N'Đang đánh giá', N'Đạt', N'Chưa đạt'
        feedback            NVARCHAR(MAX) NULL,             -- Ý kiến góp ý cải tiến từ TTKT khi từ chối khép vòng
        
        -- Expanded fields for advanced auditing & details
        priority            NVARCHAR(20)  NOT NULL DEFAULT 'Medium', -- Low, Medium, High, Critical
        source              NVARCHAR(100) NULL,             -- Audit, Survey, Complaint, Inspection...
        source_ref          NVARCHAR(200) NULL,             -- Mã minh chứng / Mã khảo sát liên quan
        root_cause          NVARCHAR(MAX) NULL,             -- Phân tích nguyên nhân gốc rễ (5-Whys/Fishbone)
        action_plan         NVARCHAR(MAX) NULL,             -- Kế hoạch hành động cụ thể
        assigned_to         NVARCHAR(50)  NULL,             -- Cán bộ phụ trách (User ID)
        assigned_to_name    NVARCHAR(200) NULL,             -- Tên cán bộ phụ trách
        created_by          NVARCHAR(50)  NULL,             -- Người khởi tạo
        verified_by         NVARCHAR(50)  NULL,             -- Cán bộ TTKT nghiệm thu/khép vòng
        verified_date       DATE          NULL,             -- Ngày nghiệm thu
        
        is_deleted          BIT           NOT NULL DEFAULT 0,
        created_at          DATETIME2     NOT NULL DEFAULT GETDATE(),
        updated_at          DATETIME2     NOT NULL DEFAULT GETDATE()
    );

    -- Indexes for high-performance filtering & search
    CREATE NONCLUSTERED INDEX IX_capa_status        ON dbo.capa (status)          WHERE is_deleted = 0;
    CREATE NONCLUSTERED INDEX IX_capa_type          ON dbo.capa (capa_type)       WHERE is_deleted = 0;
    CREATE NONCLUSTERED INDEX IX_capa_department    ON dbo.capa (department_name) WHERE is_deleted = 0;
    CREATE NONCLUSTERED INDEX IX_capa_due_date      ON dbo.capa (due_date)        WHERE is_deleted = 0;
    CREATE UNIQUE NONCLUSTERED INDEX IX_capa_code   ON dbo.capa (capa_code)       WHERE is_deleted = 0;

    PRINT 'Table dbo.capa created successfully.';
END
ELSE
BEGIN
    PRINT 'Table dbo.capa already exists.';
END
GO

-- ------------------------------------------------------------
-- 2. CAPA Action Steps table (dbo.capa_actions)
-- ------------------------------------------------------------
IF NOT EXISTS (
    SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.capa_actions') AND type = 'U'
)
BEGIN
    CREATE TABLE dbo.capa_actions (
        action_id           INT IDENTITY(1,1) PRIMARY KEY,
        capa_id             INT           NOT NULL,
        action_description  NVARCHAR(MAX) NOT NULL,
        assigned_to         NVARCHAR(50)  NULL,
        assigned_to_name    NVARCHAR(200) NULL,
        due_date            DATE          NULL,
        completed_date      DATE          NULL,
        status              NVARCHAR(20)  NOT NULL DEFAULT 'Pending', -- Pending | InProgress | Done
        sort_order          INT           NOT NULL DEFAULT 99,
        notes               NVARCHAR(MAX) NULL,
        is_deleted          BIT           NOT NULL DEFAULT 0,
        created_at          DATETIME2     NOT NULL DEFAULT GETDATE(),
        updated_at          DATETIME2     NOT NULL DEFAULT GETDATE(),

        CONSTRAINT FK_capa_actions_capa FOREIGN KEY (capa_id)
            REFERENCES dbo.capa (capa_id) ON DELETE CASCADE
    );

    CREATE NONCLUSTERED INDEX IX_capa_actions_capa_id
        ON dbo.capa_actions (capa_id) WHERE is_deleted = 0;

    PRINT 'Table dbo.capa_actions created successfully.';
END
ELSE
BEGIN
    PRINT 'Table dbo.capa_actions already exists.';
END
GO

-- ------------------------------------------------------------
-- 3. CAPA Audit Trail & Review History (dbo.capa_history)
-- ------------------------------------------------------------
IF NOT EXISTS (
    SELECT 1 FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.capa_history') AND type = 'U'
)
BEGIN
    CREATE TABLE dbo.capa_history (
        history_id          INT IDENTITY(1,1) PRIMARY KEY,
        capa_id             INT           NOT NULL,
        previous_status     NVARCHAR(30)  NULL,
        new_status          NVARCHAR(30)  NOT NULL,
        changed_by          NVARCHAR(50)  NULL,
        feedback_comment    NVARCHAR(MAX) NULL, -- Ý kiến nhận xét / góp ý từ TTKT
        changed_at          DATETIME2     NOT NULL DEFAULT GETDATE(),

        CONSTRAINT FK_capa_history_capa FOREIGN KEY (capa_id)
            REFERENCES dbo.capa (capa_id) ON DELETE CASCADE
    );

    CREATE NONCLUSTERED INDEX IX_capa_history_capa_id
        ON dbo.capa_history (capa_id);

    PRINT 'Table dbo.capa_history created successfully.';
END
ELSE
BEGIN
    PRINT 'Table dbo.capa_history already exists.';
END
GO

-- ------------------------------------------------------------
-- 4. Initial Seed Data (Matches Frontend Mock Data)
-- ------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM dbo.capa WHERE capa_code = 'CAPA-26-001')
BEGIN
    INSERT INTO dbo.capa 
    (capa_code, title, department_name, capa_type, status, open_date, due_date, completed_date, effectiveness, description, feedback)
    VALUES
    (N'CAPA-26-001', N'Khắc phục lỗi nghẽn cổng đăng ký học phần', N'Khoa Công nghệ thông tin', N'Khắc phục', 'processing', '2026-02-15', '2026-03-15', NULL, N'Đang đánh giá', N'Nâng cấp băng thông máy chủ và tối ưu hóa các chỉ mục cơ sở dữ liệu đăng ký môn học trực tuyến.', NULL),
    (N'CAPA-26-002', N'Cập nhật tài liệu thực hành mạng viễn thông thế hệ mới', N'Khoa Viễn thông', N'Phòng ngừa', 'closed', '2026-01-10', '2026-02-28', '2026-02-25', N'Đạt', N'Bổ sung các bài Lab mô phỏng mạng SDN/NFV vào chương trình đào tạo để chuẩn bị cho đợt kiểm định.', NULL),
    (N'CAPA-26-003', N'Rà soát quy trình in sao đề thi hết môn học kỳ 1', N'Phòng Khảo thí', N'Khắc phục', 'pending_closure', '2026-05-12', '2026-06-15', NULL, N'Đang đánh giá', N'Điều chỉnh quy trình giám sát chéo giữa các cán bộ in sao đề thi để tránh sai sót nội dung.', NULL),
    (N'CAPA-26-004', N'Sửa chữa thiết bị đo dao động phòng thí nghiệm tầng 4', N'Khoa Điện tử', N'Khắc phục', 'processing', '2026-03-01', '2026-04-15', NULL, N'Đang đánh giá', N'Hiệu chuẩn lại 5 thiết bị đo dao động ký bị lệch tín hiệu chuẩn sau học kỳ thực hành.', NULL),
    (N'CAPA-26-005', N'Tổ chức khảo sát doanh nghiệp về nhu cầu nhân lực logistics', N'Khoa Quản trị kinh doanh', N'Phòng ngừa', 'closed', '2026-01-05', '2026-03-01', '2026-02-28', N'Đạt', N'Thu thập ý kiến đóng góp từ 30 doanh nghiệp đối tác để hiệu chỉnh chương trình đào tạo logistics.', NULL),
    (N'CAPA-26-006', N'Bổ sung giáo trình tiếng Anh chuyên ngành cho thư viện số', N'Viện Đào tạo Quốc tế', N'Khắc phục', 'processing', '2026-05-20', '2026-06-30', NULL, N'Đang đánh giá', N'Mua bản quyền số cho 15 đầu sách giáo trình chuyên ngành Công nghệ thông tin phiên bản mới nhất.', NULL),
    (N'CAPA-26-007', N'Nâng cấp phần mềm đồ họa phòng máy thực hành đa phương tiện', N'Khoa Đa phương tiện', N'Khắc phục', 'processing', '2026-04-10', '2026-05-30', NULL, N'Đang đánh giá', N'Cài đặt và cấu hình bộ công cụ Adobe Creative Cloud bản quyền cho 45 máy tính phòng máy số 3.', N'Thiếu minh chứng bản quyền PDF được Học viện phê duyệt.'),
    (N'CAPA-26-008', N'Hoàn thiện quy trình giải quyết phản hồi trực tuyến của sinh viên', N'Phòng Công tác Chính trị & CTSV', N'Phòng ngừa', 'closed', '2026-02-01', '2026-03-15', '2026-03-10', N'Chưa đạt', N'Xây dựng biểu mẫu số tự động hóa việc tiếp nhận phản hồi từ app sinh viên, tuy nhiên thời gian phản hồi thực tế vẫn trễ.', NULL),
    (N'CAPA-26-009', N'Cập nhật vá lỗ hổng bảo mật trên cổng thông tin sinh viên', N'Khoa An toàn thông tin', N'Khắc phục', 'processing', '2026-05-25', '2026-06-25', NULL, N'Đang đánh giá', N'Sửa lỗi SQL Injection được phát hiện trong đợt đánh giá an ninh mạng nội bộ tháng 5.', NULL),
    (N'CAPA-26-010', N'Tối ưu hóa thời khóa biểu học kỳ hè giảm xung đột phòng học', N'Phòng Đào tạo', N'Phòng ngừa', 'processing', '2026-05-01', '2026-06-15', NULL, N'Đang đánh giá', N'Áp dụng thuật toán phân chia phòng học động để tránh trùng lặp khung giờ thực hành của các khóa.', NULL);

    PRINT 'Initial seed data inserted into dbo.capa.';
END
GO
