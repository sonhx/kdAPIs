-- 1. Drop Foreign Key constraints referencing TBL_USER
DECLARE @fkSql NVARCHAR(MAX) = '';
SELECT @fkSql = @fkSql + 'ALTER TABLE [' + OBJECT_SCHEMA_NAME(parent_object_id) + '].[' + OBJECT_NAME(parent_object_id) + '] DROP CONSTRAINT [' + name + ']; '
FROM sys.foreign_keys
WHERE referenced_object_id = OBJECT_ID('TBL_USER');
IF @fkSql <> '' EXEC(@fkSql);
GO

-- 2. Drop Primary Key constraint on TBL_USER if any
DECLARE @pkName NVARCHAR(200);
SELECT @pkName = name FROM sys.key_constraints WHERE type = 'PK' AND parent_object_id = OBJECT_ID('TBL_USER');
IF @pkName IS NOT NULL
    EXEC('ALTER TABLE TBL_USER DROP CONSTRAINT ' + @pkName);
GO

-- 3. Add temporary column ID_new if not exists
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'TBL_USER' AND COLUMN_NAME = 'ID_new')
BEGIN
    ALTER TABLE TBL_USER ADD ID_new VARCHAR(100) NULL;
END
GO

-- 4. Populate ID_new with personnel.id by matching email
EXEC('
    UPDATE u
    SET u.ID_new = COALESCE(p.id, CAST(u.ID AS VARCHAR(100)))
    FROM TBL_USER u
    LEFT JOIN personnel p ON (p.emailCanBo = u.Email OR p.email = u.Email) AND p.isDeleted = 0;
');
GO

-- 5. Fallback for any NULL/empty ID_new
EXEC('
    UPDATE TBL_USER SET ID_new = CAST(ID AS VARCHAR(100)) WHERE ID_new IS NULL OR ID_new = '''';
');
GO

-- 6. Drop original IDENTITY column ID
ALTER TABLE TBL_USER DROP COLUMN ID;
GO

-- 7. Rename ID_new to ID
EXEC sp_rename 'TBL_USER.ID_new', 'ID', 'COLUMN';
GO

-- 8. Set ID NOT NULL
ALTER TABLE TBL_USER ALTER COLUMN ID VARCHAR(100) NOT NULL;
GO
