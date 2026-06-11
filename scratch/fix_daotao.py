import sys

path = r'e:\eclipse-workspace\kdAPIs\src\main\java\com\daotao\DaotaoService.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace block 1
old1 = """\t\t} catch (JSONException e) {
\t\t\te.printStackTrace();
\t\t\treturn "{\\"code\\":" + 800 + ", \\"description\\":\\"" + "JSON parse error" + "\\"}";
\t\t} catch (SQLException e) {
\t\t\te.printStackTrace();
\t\t\treturn "{\\"code\\":" + 801 + ", \\"description\\":\\"" + "DB connect error" + "\\"}";
\t\t}"""

new1 = """\t\t} catch (Exception e) {
\t\t\te.printStackTrace();
\t\t\treturn "{\\"code\\": 801, \\"description\\": \\"Error\\"}";
\t\t}"""

# Replace block 2
old2 = """\t\t} catch (JSONException e) {
\t\t\te.printStackTrace();
\t\t\treturn "{\\"code\\":" + 800 + ", \\"description\\":\\"" + "JSON parse error" + "\\"}";
\t\t} catch (SQLException e) {
\t\t\te.printStackTrace();
\t\t\treturn "{\\"code\\":" + 801 + ", \\"description\\":\\"" + "DB connect error" + "\\"}";
\t\t}"""

# Since old1 and old2 are identical, we should probably just do a global replace or do it twice.
content = content.replace(old1, new1)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Done")
