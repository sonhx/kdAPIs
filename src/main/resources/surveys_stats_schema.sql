-- ==========================================
-- DDL & PROCEDURES FOR SURVEY STATISTICS (ULTRA-FAST ACTIVE ANSWERS DESIGN)
-- ==========================================

-- 1. DROP existing tables if they exist (clean setup)
IF OBJECT_ID('dbo.survey_question_option_stats', 'U') IS NOT NULL DROP TABLE dbo.survey_question_option_stats;
IF OBJECT_ID('dbo.survey_question_stats', 'U') IS NOT NULL DROP TABLE dbo.survey_question_stats;
IF OBJECT_ID('dbo.survey_block_stats', 'U') IS NOT NULL DROP TABLE dbo.survey_block_stats;
IF OBJECT_ID('dbo.survey_overall_stats', 'U') IS NOT NULL DROP TABLE dbo.survey_overall_stats;

-- 2. DROP existing views
IF OBJECT_ID('dbo.vw_responses_with_campaign', 'V') IS NOT NULL DROP VIEW dbo.vw_responses_with_campaign;
IF OBJECT_ID('dbo.vw_question_option_map', 'V') IS NOT NULL DROP VIEW dbo.vw_question_option_map;

-- 3. DROP existing procedures
IF OBJECT_ID('dbo.sp_compute_question_option_stats', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_compute_question_option_stats;
IF OBJECT_ID('dbo.sp_compute_question_numeric_stats', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_compute_question_numeric_stats;
IF OBJECT_ID('dbo.sp_compute_block_stats', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_compute_block_stats;
IF OBJECT_ID('dbo.sp_compute_survey_overall_stats', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_compute_survey_overall_stats;
IF OBJECT_ID('dbo.sp_recompute_campaign', 'P') IS NOT NULL DROP PROCEDURE dbo.sp_recompute_campaign;

-- 4. CREATE TABLES
-- Option level stats (Multiple choice / Grid column distribution)
CREATE TABLE dbo.survey_question_option_stats (
  id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL PRIMARY KEY,
  survey_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
  campaign_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
  block_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
  question_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
  option_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
  option_text nvarchar(500) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
  count int NOT NULL DEFAULT 0,
  percentage decimal(6,2) NULL,
  total_responses int NOT NULL DEFAULT 0,
  computed_at datetime2(7) NOT NULL DEFAULT SYSUTCDATETIME()
);

-- Numeric/Likert question aggregates
CREATE TABLE dbo.survey_question_stats (
  id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL PRIMARY KEY,
  survey_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
  campaign_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
  block_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
  question_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
  total_responses int NOT NULL DEFAULT 0,
  mean decimal(9,4) NULL,
  std_dev decimal(9,4) NULL,
  min_value decimal(9,4) NULL,
  max_value decimal(9,4) NULL,
  text_count int NULL,
  computed_at datetime2(7) NOT NULL DEFAULT SYSUTCDATETIME()
);

-- Block level aggregates
CREATE TABLE dbo.survey_block_stats (
  id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL PRIMARY KEY,
  survey_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
  campaign_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
  block_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
  total_responses int NOT NULL DEFAULT 0,
  mean decimal(9,4) NULL,
  std_dev decimal(9,4) NULL,
  computed_at datetime2(7) NOT NULL DEFAULT SYSUTCDATETIME()
);

-- Survey overall aggregates
CREATE TABLE dbo.survey_overall_stats (
  id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL PRIMARY KEY,
  survey_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL,
  campaign_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NULL,
  total_responses int NOT NULL DEFAULT 0,
  computed_at datetime2(7) NOT NULL DEFAULT SYSUTCDATETIME()
);

-- 5. CREATE INDEXES
CREATE INDEX IX_qos_question_campaign_option ON dbo.survey_question_option_stats(question_id, campaign_id, option_id);
CREATE INDEX IX_qos_survey_campaign ON dbo.survey_question_option_stats(survey_id, campaign_id);
CREATE INDEX IX_qs_question_campaign ON dbo.survey_question_stats(question_id, campaign_id);
CREATE INDEX IX_block_stats_survey_campaign ON dbo.survey_block_stats(survey_id, campaign_id);
CREATE INDEX IX_overall_stats_survey_campaign ON dbo.survey_overall_stats(survey_id, campaign_id);
GO

-- 6. CREATE VIEWS
CREATE VIEW dbo.vw_responses_with_campaign AS
SELECT r.id AS response_id, r.survey_id, r.created_at, c.id AS campaign_id, c.start_time, c.end_time
FROM dbo.survey_responses r
LEFT JOIN dbo.survey_campaigns c ON r.survey_id = c.survey_id
  AND (r.created_at BETWEEN c.start_time AND c.end_time);
GO

CREATE VIEW dbo.vw_question_option_map AS
SELECT qo.question_id, qo.id AS option_id, qo.content AS option_text,
       TRY_CAST(qo.content AS decimal(9,4)) AS scale_value
FROM dbo.question_options qo;
GO

-- 7. CREATE STORED PROCEDURES
CREATE PROCEDURE dbo.sp_compute_question_option_stats 
  @survey_id varchar(24), 
  @campaign_id varchar(24) = NULL, 
  @campaign_start datetime2 = NULL, 
  @campaign_end datetime2 = NULL
AS
BEGIN
  SET NOCOUNT ON;
  SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
  
  -- Clean up existing option stats
  DELETE FROM dbo.survey_question_option_stats
  WHERE survey_id = @survey_id 
    AND (
      (@campaign_id IS NULL AND campaign_id IS NULL)
      OR (@campaign_id IS NOT NULL AND campaign_id = @campaign_id)
    );

  -- Use index-seek on a temp table to filter active responses
  IF OBJECT_ID('tempdb..#temp_responses') IS NOT NULL DROP TABLE #temp_responses;
  
  SELECT r.id AS response_id
  INTO #temp_responses
  FROM dbo.survey_responses r
  WHERE r.survey_id = @survey_id
    AND (@campaign_id IS NULL OR r.created_at BETWEEN @campaign_start AND @campaign_end);

  CREATE CLUSTERED INDEX IX_temp_responses ON #temp_responses(response_id);

  -- Pull active answers into local memory-resident temp table (guarantees zero full scans on 4.9M table)
  IF OBJECT_ID('tempdb..#temp_active_answers') IS NOT NULL DROP TABLE #temp_active_answers;

  SELECT a.question_id, a.choices, a.response_id
  INTO #temp_active_answers
  FROM dbo.survey_response_answers a WITH (NOLOCK, INDEX(IX_answers_response_id))
  JOIN #temp_responses fa ON a.response_id = fa.response_id;

  CREATE CLUSTERED INDEX IX_temp_active_answers ON #temp_active_answers(question_id);

  -- Create a tiny temp table for pre-computed original-to-hashed ID mapping
  IF OBJECT_ID('tempdb..#id_map') IS NOT NULL DROP TABLE #id_map;
  
  CREATE TABLE #id_map (
    original_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL PRIMARY KEY,
    hashed_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL
  );

  -- Extract distinct standard option IDs
  INSERT INTO #id_map (original_id, hashed_id)
  SELECT DISTINCT 
    opt.value AS original_id,
    LOWER(SUBSTRING(CONVERT(VARCHAR(32), HASHBYTES('MD5', CONCAT(@survey_id, '_', opt.value)), 2), 1, 24)) AS hashed_id
  FROM #temp_active_answers a
  JOIN dbo.survey_questions q WITH (NOLOCK) ON a.question_id = q.id
  CROSS APPLY OPENJSON(a.choices) opt
  WHERE q.question_type NOT IN ('GridSingleChoice', 'GridMultipleChoice')
    AND a.choices IS NOT NULL AND ISJSON(a.choices) = 1;

  -- Extract distinct grid row IDs (idHang)
  INSERT INTO #id_map (original_id, hashed_id)
  SELECT DISTINCT 
    opt.idHang AS original_id,
    LOWER(SUBSTRING(CONVERT(VARCHAR(32), HASHBYTES('MD5', CONCAT(@survey_id, '_', opt.idHang)), 2), 1, 24)) AS hashed_id
  FROM #temp_active_answers a
  JOIN dbo.survey_questions q WITH (NOLOCK) ON a.question_id = q.id
  CROSS APPLY OPENJSON(a.choices) WITH (
    idHang varchar(24) '$.idHang'
  ) opt
  WHERE q.question_type IN ('GridSingleChoice', 'GridMultipleChoice')
    AND a.choices IS NOT NULL AND ISJSON(a.choices) = 1
    AND opt.idHang IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM #id_map WHERE original_id = opt.idHang);

  -- Extract distinct grid column IDs (idCot)
  INSERT INTO #id_map (original_id, hashed_id)
  SELECT DISTINCT 
    opt.idCot AS original_id,
    LOWER(SUBSTRING(CONVERT(VARCHAR(32), HASHBYTES('MD5', CONCAT(@survey_id, '_', opt.idCot)), 2), 1, 24)) AS hashed_id
  FROM #temp_active_answers a
  JOIN dbo.survey_questions q WITH (NOLOCK) ON a.question_id = q.id
  CROSS APPLY OPENJSON(a.choices) WITH (
    idCot varchar(24) '$.idCot'
  ) opt
  WHERE q.question_type IN ('GridSingleChoice', 'GridMultipleChoice')
    AND a.choices IS NOT NULL AND ISJSON(a.choices) = 1
    AND opt.idCot IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM #id_map WHERE original_id = opt.idCot);

  -- Temporary table to hold parsed and hashed choices (runs once, then indexed)
  IF OBJECT_ID('tempdb..#temp_exploded') IS NOT NULL DROP TABLE #temp_exploded;
  
  CREATE TABLE #temp_exploded (
    target_question_id varchar(24) NOT NULL,
    option_id varchar(24) NOT NULL,
    response_id varchar(24) NOT NULL
  );

  -- standard choice answers (join with tiny #id_map)
  INSERT INTO #temp_exploded (target_question_id, option_id, response_id)
  SELECT 
    q.id AS target_question_id,
    m.hashed_id AS option_id,
    a.response_id
  FROM #temp_active_answers a
  JOIN dbo.survey_questions q WITH (NOLOCK) ON a.question_id = q.id
  CROSS APPLY OPENJSON(a.choices) opt
  JOIN #id_map m ON opt.value = m.original_id
  WHERE q.question_type NOT IN ('GridSingleChoice', 'GridMultipleChoice')
    AND a.choices IS NOT NULL AND ISJSON(a.choices) = 1;

  -- grid choice answers (join with tiny #id_map)
  INSERT INTO #temp_exploded (target_question_id, option_id, response_id)
  SELECT 
    mHang.hashed_id AS target_question_id,
    mCot.hashed_id AS option_id,
    a.response_id
  FROM #temp_active_answers a
  JOIN dbo.survey_questions q WITH (NOLOCK) ON a.question_id = q.id
  CROSS APPLY OPENJSON(a.choices) WITH (
    idHang varchar(24) '$.idHang',
    idCot varchar(24) '$.idCot'
  ) opt
  JOIN #id_map mHang ON opt.idHang = mHang.original_id
  JOIN #id_map mCot ON opt.idCot = mCot.original_id
  WHERE q.question_type IN ('GridSingleChoice', 'GridMultipleChoice')
    AND a.choices IS NOT NULL AND ISJSON(a.choices) = 1;

  CREATE CLUSTERED INDEX IX_temp_exploded ON #temp_exploded(target_question_id, option_id);

  -- Perform fast aggregations and joins using #temp_exploded
  WITH option_counts AS (
    SELECT 
      target_question_id,
      option_id,
      COUNT(1) AS opt_count
    FROM #temp_exploded
    GROUP BY target_question_id, option_id
  ),
  question_totals AS (
    SELECT 
      target_question_id,
      COUNT(DISTINCT response_id) AS total_responses
    FROM #temp_exploded
    GROUP BY target_question_id
  ),
  all_question_options AS (
    -- Standard Options
    SELECT 
      q.id AS target_question_id,
      q.block_id,
      qo.id AS option_id,
      qo.content AS option_text
    FROM dbo.survey_questions q
    JOIN dbo.question_options qo ON q.id = qo.question_id
    WHERE q.block_id IN (SELECT sb.id FROM dbo.survey_blocks sb WHERE sb.survey_id = @survey_id)

    UNION ALL

    -- Grid Options (Row ID x Column ID)
    SELECT 
      qmr.id AS target_question_id,
      q.block_id,
      qmc.id AS option_id,
      qmc.content AS option_text
    FROM dbo.survey_questions q
    JOIN dbo.question_matrix_rows qmr ON q.id = qmr.question_id
    JOIN dbo.question_matrix_cols qmc ON q.id = qmc.question_id
    WHERE q.block_id IN (SELECT sb.id FROM dbo.survey_blocks sb WHERE sb.survey_id = @survey_id)
  )
  INSERT INTO dbo.survey_question_option_stats (
    id, survey_id, campaign_id, block_id, question_id, option_id, option_text, count, percentage, total_responses, computed_at
  )
  SELECT 
    LOWER(SUBSTRING(CONVERT(VARCHAR(32), HASHBYTES('MD5', CONCAT(@survey_id, '_', COALESCE(@campaign_id, 'ALL'), '_', aqo.target_question_id, '_', COALESCE(aqo.option_id, ''))), 2), 1, 24)) AS id,
    @survey_id AS survey_id,
    @campaign_id AS campaign_id,
    aqo.block_id,
    aqo.target_question_id,
    aqo.option_id,
    aqo.option_text,
    COALESCE(oc.opt_count, 0) AS count,
    CAST(COALESCE(oc.opt_count, 0) * 100.0 / NULLIF(qt.total_responses, 0) AS decimal(6,2)) AS percentage,
    COALESCE(qt.total_responses, 0) AS total_responses,
    SYSUTCDATETIME()
  FROM all_question_options aqo
  LEFT JOIN option_counts oc ON aqo.target_question_id = oc.target_question_id AND aqo.option_id = oc.option_id
  LEFT JOIN question_totals qt ON aqo.target_question_id = qt.target_question_id;

  DROP TABLE #temp_exploded;
  DROP TABLE #id_map;
  DROP TABLE #temp_active_answers;
  DROP TABLE #temp_responses;
END;
GO

CREATE PROCEDURE dbo.sp_compute_question_numeric_stats 
  @survey_id varchar(24), 
  @campaign_id varchar(24) = NULL, 
  @campaign_start datetime2 = NULL, 
  @campaign_end datetime2 = NULL
AS
BEGIN
  SET NOCOUNT ON;
  SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
  
  DELETE FROM dbo.survey_question_stats
  WHERE survey_id = @survey_id 
    AND (
      (@campaign_id IS NULL AND campaign_id IS NULL)
      OR (@campaign_id IS NOT NULL AND campaign_id = @campaign_id)
    );

  -- Use index-seek on a temp table to filter active responses
  IF OBJECT_ID('tempdb..#temp_responses') IS NOT NULL DROP TABLE #temp_responses;
  
  SELECT r.id AS response_id
  INTO #temp_responses
  FROM dbo.survey_responses r
  WHERE r.survey_id = @survey_id
    AND (@campaign_id IS NULL OR r.created_at BETWEEN @campaign_start AND @campaign_end);

  CREATE CLUSTERED INDEX IX_temp_responses ON #temp_responses(response_id);

  -- Pull active answers into local memory-resident temp table (guarantees zero full scans on 4.9M table)
  IF OBJECT_ID('tempdb..#temp_active_answers') IS NOT NULL DROP TABLE #temp_active_answers;

  SELECT a.question_id, a.choices, a.response_id, a.other_answer
  INTO #temp_active_answers
  FROM dbo.survey_response_answers a WITH (NOLOCK, INDEX(IX_answers_response_id))
  JOIN #temp_responses fa ON a.response_id = fa.response_id;

  CREATE CLUSTERED INDEX IX_temp_active_answers ON #temp_active_answers(question_id);

  -- Create a tiny temp table for pre-computed original-to-hashed ID mapping
  IF OBJECT_ID('tempdb..#id_map') IS NOT NULL DROP TABLE #id_map;
  
  CREATE TABLE #id_map (
    original_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL PRIMARY KEY,
    hashed_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL
  );

  -- Extract distinct standard option IDs
  INSERT INTO #id_map (original_id, hashed_id)
  SELECT DISTINCT 
    opt.value AS original_id,
    LOWER(SUBSTRING(CONVERT(VARCHAR(32), HASHBYTES('MD5', CONCAT(@survey_id, '_', opt.value)), 2), 1, 24)) AS hashed_id
  FROM #temp_active_answers a
  JOIN dbo.survey_questions q WITH (NOLOCK) ON a.question_id = q.id
  CROSS APPLY OPENJSON(a.choices) opt
  WHERE q.question_type NOT IN ('GridSingleChoice', 'GridMultipleChoice')
    AND a.choices IS NOT NULL AND ISJSON(a.choices) = 1;

  -- Extract distinct grid row IDs (idHang)
  INSERT INTO #id_map (original_id, hashed_id)
  SELECT DISTINCT 
    opt.idHang AS original_id,
    LOWER(SUBSTRING(CONVERT(VARCHAR(32), HASHBYTES('MD5', CONCAT(@survey_id, '_', opt.idHang)), 2), 1, 24)) AS hashed_id
  FROM #temp_active_answers a
  JOIN dbo.survey_questions q WITH (NOLOCK) ON a.question_id = q.id
  CROSS APPLY OPENJSON(a.choices) WITH (
    idHang varchar(24) '$.idHang'
  ) opt
  WHERE q.question_type IN ('GridSingleChoice', 'GridMultipleChoice')
    AND a.choices IS NOT NULL AND ISJSON(a.choices) = 1
    AND opt.idHang IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM #id_map WHERE original_id = opt.idHang);

  -- Extract distinct grid column IDs (idCot)
  INSERT INTO #id_map (original_id, hashed_id)
  SELECT DISTINCT 
    opt.idCot AS original_id,
    LOWER(SUBSTRING(CONVERT(VARCHAR(32), HASHBYTES('MD5', CONCAT(@survey_id, '_', opt.idCot)), 2), 1, 24)) AS hashed_id
  FROM #temp_active_answers a
  JOIN dbo.survey_questions q WITH (NOLOCK) ON a.question_id = q.id
  CROSS APPLY OPENJSON(a.choices) WITH (
    idCot varchar(24) '$.idCot'
  ) opt
  WHERE q.question_type IN ('GridSingleChoice', 'GridMultipleChoice')
    AND a.choices IS NOT NULL AND ISJSON(a.choices) = 1
    AND opt.idCot IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM #id_map WHERE original_id = opt.idCot);

  -- Temporary table to hold parsed and hashed choices (runs once, then indexed)
  IF OBJECT_ID('tempdb..#temp_exploded') IS NOT NULL DROP TABLE #temp_exploded;
  
  CREATE TABLE #temp_exploded (
    original_question_id varchar(24) NOT NULL,
    target_question_id varchar(24) NOT NULL,
    block_id varchar(24) NOT NULL,
    response_id varchar(24) NOT NULL,
    option_id varchar(24) NULL,
    other_answer nvarchar(max) NULL,
    question_type varchar(50) NOT NULL
  );

  -- standard choice/text answers
  INSERT INTO #temp_exploded (original_question_id, target_question_id, block_id, response_id, option_id, other_answer, question_type)
  SELECT 
    q.id AS original_question_id,
    q.id AS target_question_id,
    q.block_id,
    a.response_id,
    m.hashed_id AS option_id,
    a.other_answer,
    q.question_type
  FROM #temp_active_answers a
  JOIN dbo.survey_questions q WITH (NOLOCK) ON a.question_id = q.id
  OUTER APPLY OPENJSON(a.choices) opt
  LEFT JOIN #id_map m ON opt.value = m.original_id
  WHERE q.question_type NOT IN ('GridSingleChoice', 'GridMultipleChoice')
    AND ((a.choices IS NOT NULL AND ISJSON(a.choices) = 1) OR a.other_answer IS NOT NULL);

  -- grid single/multiple choice answers
  INSERT INTO #temp_exploded (original_question_id, target_question_id, block_id, response_id, option_id, other_answer, question_type)
  SELECT 
    q.id AS original_question_id,
    mHang.hashed_id AS target_question_id,
    q.block_id,
    a.response_id,
    mCot.hashed_id AS option_id,
    NULL AS other_answer,
    q.question_type
  FROM #temp_active_answers a
  JOIN dbo.survey_questions q WITH (NOLOCK) ON a.question_id = q.id
  CROSS APPLY OPENJSON(a.choices) WITH (
    idHang varchar(24) '$.idHang',
    idCot varchar(24) '$.idCot'
  ) opt
  JOIN #id_map mHang ON opt.idHang = mHang.original_id
  JOIN #id_map mCot ON opt.idCot = mCot.original_id
  WHERE q.question_type IN ('GridSingleChoice', 'GridMultipleChoice')
    AND a.choices IS NOT NULL AND ISJSON(a.choices) = 1;

  CREATE CLUSTERED INDEX IX_temp_exploded ON #temp_exploded(target_question_id, option_id);

  WITH answer_scores AS (
    SELECT 
      ea.original_question_id,
      ea.target_question_id,
      ea.block_id,
      ea.response_id,
      COALESCE(
        CASE 
          WHEN COALESCE(qo.content, qmc.content) LIKE '5 =%' OR COALESCE(qo.content, qmc.content) LIKE '5=%' THEN 5.0
          WHEN COALESCE(qo.content, qmc.content) LIKE '4 =%' OR COALESCE(qo.content, qmc.content) LIKE '4=%' THEN 4.0
          WHEN COALESCE(qo.content, qmc.content) LIKE '3 =%' OR COALESCE(qo.content, qmc.content) LIKE '3=%' THEN 3.0
          WHEN COALESCE(qo.content, qmc.content) LIKE '2 =%' OR COALESCE(qo.content, qmc.content) LIKE '2=%' THEN 2.0
          WHEN COALESCE(qo.content, qmc.content) LIKE '1 =%' OR COALESCE(qo.content, qmc.content) LIKE '1=%' THEN 1.0
          WHEN COALESCE(qo.content, qmc.content) IN (N'Rất không hài lòng', N'Rất kém', N'Hoàn toàn không', N'Rất yếu', N'Hoàn toàn không đồng ý') THEN 1.0
          WHEN COALESCE(qo.content, qmc.content) IN (N'Không hài lòng', N'Kém', N'Ít sẵn sàng', N'Yếu', N'Ít phù hợp', N'Không đồng ý') THEN 2.0
          WHEN COALESCE(qo.content, qmc.content) IN (N'Bình thường', N'Trung bình', N'Tương đối', N'Phân vân') THEN 3.0
          WHEN COALESCE(qo.content, qmc.content) IN (N'Hài lòng', N'Tốt', N'Sẵn sàng', N'Phù hợp', N'Đồng ý') THEN 4.0
          WHEN COALESCE(qo.content, qmc.content) IN (N'Rất hài lòng', N'Rất tốt', N'Xuất sắc', N'Rất sẵn sàng', N'Rất phù hợp', N'Hoàn toàn đồng ý') THEN 5.0
          ELSE NULL
        END,
        TRY_CAST(COALESCE(qo.content, qmc.content) AS decimal(9,4))
      ) AS score_val,
      CASE WHEN ea.other_answer IS NOT NULL AND RTRIM(LTRIM(ea.other_answer)) <> '' THEN 1 ELSE 0 END AS is_text
    FROM #temp_exploded ea
    LEFT JOIN dbo.question_options qo ON ea.option_id = qo.id AND ea.question_type NOT IN ('GridSingleChoice', 'GridMultipleChoice')
    LEFT JOIN dbo.question_matrix_cols qmc ON ea.option_id = qmc.id AND ea.question_type IN ('GridSingleChoice', 'GridMultipleChoice')
  ),
  question_aggregates AS (
    SELECT 
      target_question_id,
      COUNT(DISTINCT response_id) AS total_responses,
      AVG(score_val) AS mean,
      STDEV(score_val) AS std_dev,
      MIN(score_val) AS min_value,
      MAX(score_val) AS max_value,
      SUM(is_text) AS text_count
    FROM answer_scores
    GROUP BY target_question_id
  ),
  all_target_questions AS (
    -- Standard Questions
    SELECT 
      q.id AS target_question_id,
      q.block_id
    FROM dbo.survey_questions q
    WHERE q.question_type NOT IN ('GridSingleChoice', 'GridMultipleChoice')
      AND q.block_id IN (SELECT sb.id FROM dbo.survey_blocks sb WHERE sb.survey_id = @survey_id)

    UNION ALL

    -- Grid Rows (Separate questions)
    SELECT 
      qmr.id AS target_question_id,
      q.block_id
    FROM dbo.survey_questions q
    JOIN dbo.question_matrix_rows qmr ON q.id = qmr.question_id
    WHERE q.block_id IN (SELECT sb.id FROM dbo.survey_blocks sb WHERE sb.survey_id = @survey_id)
  )
  INSERT INTO dbo.survey_question_stats (
    id, survey_id, campaign_id, block_id, question_id, total_responses, mean, std_dev, min_value, max_value, text_count, computed_at
  )
  SELECT 
    LOWER(SUBSTRING(CONVERT(VARCHAR(32), HASHBYTES('MD5', CONCAT(@survey_id, '_', COALESCE(@campaign_id, 'ALL'), '_', atq.target_question_id)), 2), 1, 24)) AS id,
    @survey_id AS survey_id,
    @campaign_id AS campaign_id,
    atq.block_id,
    atq.target_question_id,
    COALESCE(qa.total_responses, 0) AS total_responses,
    qa.mean,
    qa.std_dev,
    qa.min_value,
    qa.max_value,
    qa.text_count,
    SYSUTCDATETIME()
  FROM all_target_questions atq
  LEFT JOIN question_aggregates qa ON atq.target_question_id = qa.target_question_id;

  DROP TABLE #temp_exploded;
  DROP TABLE #id_map;
  DROP TABLE #temp_active_answers;
  DROP TABLE #temp_responses;
END;
GO

CREATE PROCEDURE dbo.sp_compute_block_stats 
  @survey_id varchar(24), 
  @campaign_id varchar(24) = NULL
AS
BEGIN
  SET NOCOUNT ON;
  SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
  
  DELETE FROM dbo.survey_block_stats
  WHERE survey_id = @survey_id 
    AND (
      (@campaign_id IS NULL AND campaign_id IS NULL)
      OR (@campaign_id IS NOT NULL AND campaign_id = @campaign_id)
    );

  DECLARE @campaign_start datetime2 = NULL;
  DECLARE @campaign_end datetime2 = NULL;
  
  IF @campaign_id IS NOT NULL
  BEGIN
    SELECT @campaign_start = start_time, @campaign_end = end_time 
    FROM dbo.survey_campaigns 
    WHERE id = @campaign_id;
  END;

  -- Use index-seek on a temp table to filter active responses
  IF OBJECT_ID('tempdb..#temp_responses') IS NOT NULL DROP TABLE #temp_responses;
  
  SELECT r.id AS response_id
  INTO #temp_responses
  FROM dbo.survey_responses r
  WHERE r.survey_id = @survey_id
    AND (@campaign_id IS NULL OR r.created_at BETWEEN @campaign_start AND @campaign_end);

  CREATE CLUSTERED INDEX IX_temp_responses ON #temp_responses(response_id);

  -- Pull active answers into local memory-resident temp table (guarantees zero full scans on 4.9M table)
  IF OBJECT_ID('tempdb..#temp_active_answers') IS NOT NULL DROP TABLE #temp_active_answers;

  SELECT a.question_id, a.choices, a.response_id
  INTO #temp_active_answers
  FROM dbo.survey_response_answers a WITH (NOLOCK, INDEX(IX_answers_response_id))
  JOIN #temp_responses fa ON a.response_id = fa.response_id;

  CREATE CLUSTERED INDEX IX_temp_active_answers ON #temp_active_answers(question_id);

  -- Create a tiny temp table for pre-computed original-to-hashed ID mapping
  IF OBJECT_ID('tempdb..#id_map') IS NOT NULL DROP TABLE #id_map;
  
  CREATE TABLE #id_map (
    original_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL PRIMARY KEY,
    hashed_id varchar(24) COLLATE SQL_Latin1_General_CP1_CI_AS NOT NULL
  );

  -- Extract distinct standard option IDs
  INSERT INTO #id_map (original_id, hashed_id)
  SELECT DISTINCT 
    opt.value AS original_id,
    LOWER(SUBSTRING(CONVERT(VARCHAR(32), HASHBYTES('MD5', CONCAT(@survey_id, '_', opt.value)), 2), 1, 24)) AS hashed_id
  FROM #temp_active_answers a
  JOIN dbo.survey_questions q WITH (NOLOCK) ON a.question_id = q.id
  CROSS APPLY OPENJSON(a.choices) opt
  WHERE q.question_type NOT IN ('GridSingleChoice', 'GridMultipleChoice')
    AND a.choices IS NOT NULL AND ISJSON(a.choices) = 1;

  -- Extract distinct grid column IDs (idCot)
  INSERT INTO #id_map (original_id, hashed_id)
  SELECT DISTINCT 
    opt.idCot AS original_id,
    LOWER(SUBSTRING(CONVERT(VARCHAR(32), HASHBYTES('MD5', CONCAT(@survey_id, '_', opt.idCot)), 2), 1, 24)) AS hashed_id
  FROM #temp_active_answers a
  JOIN dbo.survey_questions q WITH (NOLOCK) ON a.question_id = q.id
  CROSS APPLY OPENJSON(a.choices) WITH (
    idCot varchar(24) '$.idCot'
  ) opt
  WHERE q.question_type IN ('GridSingleChoice', 'GridMultipleChoice')
    AND a.choices IS NOT NULL AND ISJSON(a.choices) = 1
    AND opt.idCot IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM #id_map WHERE original_id = opt.idCot);

  -- Temporary table to hold parsed and hashed choices (runs once, then indexed)
  IF OBJECT_ID('tempdb..#temp_exploded') IS NOT NULL DROP TABLE #temp_exploded;
  
  CREATE TABLE #temp_exploded (
    block_id varchar(24) NOT NULL,
    option_id varchar(24) NOT NULL,
    question_type varchar(50) NOT NULL,
    response_id varchar(24) NOT NULL
  );

  -- standard choice answers
  INSERT INTO #temp_exploded (block_id, option_id, question_type, response_id)
  SELECT 
    q.block_id,
    m.hashed_id AS option_id,
    q.question_type,
    a.response_id
  FROM #temp_active_answers a
  JOIN dbo.survey_questions q WITH (NOLOCK) ON a.question_id = q.id
  CROSS APPLY OPENJSON(a.choices) opt
  JOIN #id_map m ON opt.value = m.original_id
  WHERE q.question_type NOT IN ('GridSingleChoice', 'GridMultipleChoice')
    AND a.choices IS NOT NULL AND ISJSON(a.choices) = 1;

  -- grid choice answers
  INSERT INTO #temp_exploded (block_id, option_id, question_type, response_id)
  SELECT 
    q.block_id,
    mCot.hashed_id AS option_id,
    q.question_type,
    a.response_id
  FROM #temp_active_answers a
  JOIN dbo.survey_questions q WITH (NOLOCK) ON a.question_id = q.id
  CROSS APPLY OPENJSON(a.choices) WITH (
    idCot varchar(24) '$.idCot'
  ) opt
  JOIN #id_map mCot ON opt.idCot = mCot.original_id
  WHERE q.question_type IN ('GridSingleChoice', 'GridMultipleChoice')
    AND a.choices IS NOT NULL AND ISJSON(a.choices) = 1;

  CREATE CLUSTERED INDEX IX_temp_exploded ON #temp_exploded(block_id, option_id);

  WITH answer_scores AS (
    SELECT 
      ea.block_id,
      ea.response_id,
      COALESCE(
        CASE 
          WHEN COALESCE(qo.content, qmc.content) LIKE '5 =%' OR COALESCE(qo.content, qmc.content) LIKE '5=%' THEN 5.0
          WHEN COALESCE(qo.content, qmc.content) LIKE '4 =%' OR COALESCE(qo.content, qmc.content) LIKE '4=%' THEN 4.0
          WHEN COALESCE(qo.content, qmc.content) LIKE '3 =%' OR COALESCE(qo.content, qmc.content) LIKE '3=%' THEN 3.0
          WHEN COALESCE(qo.content, qmc.content) LIKE '2 =%' OR COALESCE(qo.content, qmc.content) LIKE '2=%' THEN 2.0
          WHEN COALESCE(qo.content, qmc.content) LIKE '1 =%' OR COALESCE(qo.content, qmc.content) LIKE '1=%' THEN 1.0
          WHEN COALESCE(qo.content, qmc.content) IN (N'Rất không hài lòng', N'Rất kém', N'Hoàn toàn không', N'Rất yếu', N'Hoàn toàn không đồng ý') THEN 1.0
          WHEN COALESCE(qo.content, qmc.content) IN (N'Không hài lòng', N'Kém', N'Ít sẵn sàng', N'Yếu', N'Ít phù hợp', N'Không đồng ý') THEN 2.0
          WHEN COALESCE(qo.content, qmc.content) IN (N'Bình thường', N'Trung bình', N'Tương đối', N'Phân vân') THEN 3.0
          WHEN COALESCE(qo.content, qmc.content) IN (N'Hài lòng', N'Tốt', N'Sẵn sàng', N'Phù hợp', N'Đồng ý') THEN 4.0
          WHEN COALESCE(qo.content, qmc.content) IN (N'Rất hài lòng', N'Rất tốt', N'Xuất sắc', N'Rất sẵn sàng', N'Rất phù hợp', N'Hoàn toàn đồng ý') THEN 5.0
          ELSE NULL
        END,
        TRY_CAST(COALESCE(qo.content, qmc.content) AS decimal(9,4))
      ) AS score_val
    FROM #temp_exploded ea
    LEFT JOIN dbo.question_options qo ON ea.option_id = qo.id AND ea.question_type NOT IN ('GridSingleChoice', 'GridMultipleChoice')
    LEFT JOIN dbo.question_matrix_cols qmc ON ea.option_id = qmc.id AND ea.question_type IN ('GridSingleChoice', 'GridMultipleChoice')
  ),
  block_aggregates AS (
    SELECT 
      block_id,
      COUNT(DISTINCT response_id) AS total_responses,
      AVG(score_val) AS mean,
      STDEV(score_val) AS std_dev
    FROM answer_scores
    GROUP BY block_id
  )
  INSERT INTO dbo.survey_block_stats (
    id, survey_id, campaign_id, block_id, total_responses, mean, std_dev, computed_at
  )
  SELECT 
    LOWER(SUBSTRING(CONVERT(VARCHAR(32), HASHBYTES('MD5', CONCAT(@survey_id, '_', COALESCE(@campaign_id, 'ALL'), '_', sb.id)), 2), 1, 24)) AS id,
    @survey_id AS survey_id,
    @campaign_id AS campaign_id,
    sb.id AS block_id,
    COALESCE(ba.total_responses, 0) AS total_responses,
    ba.mean,
    ba.std_dev,
    SYSUTCDATETIME()
  FROM dbo.survey_blocks sb
  LEFT JOIN block_aggregates ba ON sb.id = ba.block_id
  WHERE sb.survey_id = @survey_id;

  DROP TABLE #temp_exploded;
  DROP TABLE #id_map;
  DROP TABLE #temp_active_answers;
  DROP TABLE #temp_responses;
END;
GO

CREATE PROCEDURE dbo.sp_compute_survey_overall_stats 
  @survey_id varchar(24), 
  @campaign_id varchar(24) = NULL
AS
BEGIN
  SET NOCOUNT ON;
  SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
  
  DELETE FROM dbo.survey_overall_stats
  WHERE survey_id = @survey_id 
    AND (
      (@campaign_id IS NULL AND campaign_id IS NULL)
      OR (@campaign_id IS NOT NULL AND campaign_id = @campaign_id)
    );

  DECLARE @campaign_start datetime2 = NULL;
  DECLARE @campaign_end datetime2 = NULL;
  
  IF @campaign_id IS NOT NULL
  BEGIN
    SELECT @campaign_start = start_time, @campaign_end = end_time 
    FROM dbo.survey_campaigns 
    WHERE id = @campaign_id;
  END;

  INSERT INTO dbo.survey_overall_stats (
    id, survey_id, campaign_id, total_responses, computed_at
  )
  SELECT 
    LOWER(SUBSTRING(CONVERT(VARCHAR(32), HASHBYTES('MD5', CONCAT(@survey_id, '_', COALESCE(@campaign_id, 'ALL'))), 2), 1, 24)) AS id,
    @survey_id,
    @campaign_id,
    COUNT(1) AS total_responses,
    SYSUTCDATETIME()
  FROM dbo.survey_responses r
  WHERE r.survey_id = @survey_id
    AND (@campaign_id IS NULL OR r.created_at BETWEEN @campaign_start AND @campaign_end);
END;
GO

CREATE PROCEDURE dbo.sp_recompute_campaign 
  @survey_id varchar(24), 
  @campaign_id varchar(24) = NULL
AS
BEGIN
  SET NOCOUNT ON;
  
  DECLARE @campaign_start datetime2 = NULL;
  DECLARE @campaign_end datetime2 = NULL;
  
  IF @campaign_id IS NOT NULL AND @campaign_id <> 'all'
  BEGIN
    SELECT @campaign_start = start_time, @campaign_end = end_time 
    FROM dbo.survey_campaigns 
    WHERE id = @campaign_id;
  END
  ELSE
  BEGIN
    SET @campaign_id = NULL;
  END;

  -- Execute individual aggregations
  EXEC dbo.sp_compute_question_option_stats @survey_id, @campaign_id, @campaign_start, @campaign_end;
  EXEC dbo.sp_compute_question_numeric_stats @survey_id, @campaign_id, @campaign_start, @campaign_end;
  EXEC dbo.sp_compute_block_stats @survey_id, @campaign_id;
  EXEC dbo.sp_compute_survey_overall_stats @survey_id, @campaign_id;
END;
GO
