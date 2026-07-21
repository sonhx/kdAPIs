package com.ocr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class OcrExtend {

    public static JSONArray occurrences(String content, String search_key) {
        JSONArray jsaOutputFiles = new JSONArray();
        try {
            Pattern p = Pattern.compile(search_key);
            Matcher m = p.matcher(content);
            int previousline_end = 0;
            while (m.find()) {
                JSONObject joOccurence = new JSONObject();
                int start = m.start();
                int end = m.end();
                joOccurence.put("start", start);
                joOccurence.put("end", end);
                
                if (start < previousline_end) continue;
                int newline_pos_start = content.substring(0, start).lastIndexOf("\n");
                if (newline_pos_start < 0) {
                    if (start > 100) {
                        newline_pos_start = start - 100;
                    } else {
                        newline_pos_start = 0;
                    }
                }
                int newline_pos_end = end + content.substring(end).indexOf("\n");
                previousline_end = newline_pos_end;
                if (newline_pos_end < 0) newline_pos_end = end;
                String occurence = content.substring(newline_pos_start, newline_pos_end)
                        .replaceAll(search_key, "<b>" + search_key + "</b>");
                joOccurence.put("text", occurence);
                jsaOutputFiles.put(joOccurence);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return jsaOutputFiles;
    }
}
