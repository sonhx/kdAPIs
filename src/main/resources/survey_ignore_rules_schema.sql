IF OBJECT_ID('dbo.survey_auto_ignore_rules', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.survey_auto_ignore_rules (
        id INT IDENTITY(1,1) PRIMARY KEY,
        rule_code VARCHAR(50) NOT NULL UNIQUE,
        rule_name NVARCHAR(200) NOT NULL,
        rule_type VARCHAR(50) NOT NULL, -- 'keyword', 'min_responses', 'empty_survey'
        pattern_value NVARCHAR(MAX) NULL, -- comma separated keywords or threshold number
        is_enabled BIT NOT NULL DEFAULT 1,
        description NVARCHAR(500) NULL,
        created_at DATETIME2 DEFAULT GETDATE(),
        updated_at DATETIME2 DEFAULT GETDATE()
    );

    INSERT INTO dbo.survey_auto_ignore_rules (rule_code, rule_name, rule_type, pattern_value, is_enabled, description)
    VALUES 
    ('RULE_KEYWORDS', N'Tự động ẩn theo Từ khóa Tiêu đề', 'keyword', N'test,demo,nháp,draft,(copy),kiểm tra hệ thống,kstn,mẫu khảo sát,bài tập quiz,quiz', 1, N'Tự động ẩn các khảo sát có tiêu đề chứa từ khóa thử nghiệm/nháp/quiz/rác.'),
    ('RULE_EMPTY_QUESTIONS', N'Tự động ẩn Khảo sát Rỗng (0 câu hỏi)', 'empty_survey', N'0', 1, N'Tự động ẩn các khảo sát tạo ra nhưng chưa có khối câu hỏi hoặc câu hỏi nào.'),
    ('RULE_MIN_RESPONSES', N'Tự động ẩn Khảo sát ít phản hồi', 'min_responses', N'10', 1, N'Tự động ẩn các khảo sát có dưới 10 phản hồi (áp dụng cho khảo sát đã đóng hoặc tạo > 3 ngày).');
END
