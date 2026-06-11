import re

path = r'e:\eclipse-workspace\kdAPIs\src\main\java\com\daotao\DaotaoService.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Pattern 1: jdbcTemplate.query(sql, params.toArray(), (rs, rowNum) -> {
# Replacing (sql, args, callback) with (sql, callback, args)
content = re.sub(r'jdbcTemplate\.query\s*\(\s*([^,]+),\s*([^,]+?\.toArray\(\))\s*,\s*(\(rs,\s*rowNum\)\s*->\s*\{)', 
                 r'jdbcTemplate.query(\1, \3, \2)', content)

# Pattern 2: jdbcTemplate.query(sql, params.toArray(), (rs) -> {
content = re.sub(r'jdbcTemplate\.query\s*\(\s*([^,]+),\s*([^,]+?\.toArray\(\))\s*,\s*(\(rs\)\s*->\s*\{)', 
                 r'jdbcTemplate.query(\1, \3, \2)', content)

# Pattern 3: jdbcTemplate.queryForObject(sql, params.toArray(), Class)
content = re.sub(r'jdbcTemplate\.queryForObject\s*\(\s*([^,]+),\s*([^,]+?\.toArray\(\))\s*,\s*([^,]+?\.class)\s*\)', 
                 r'jdbcTemplate.queryForObject(\1, \3, \2)', content)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")
