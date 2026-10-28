package com.ocr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class OcrExtend {

    public static JSONArray occurrences(String content, String search_key) {
        JSONArray jsaOutputFiles = new JSONArray();
        if (content == null || search_key == null || search_key.trim().isEmpty()) {
            return jsaOutputFiles;
        }
        try {
            String trimmedKey = search_key.trim();
            Pattern p = Pattern.compile(Pattern.quote(trimmedKey), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher m = p.matcher(content);
            int previousline_end = 0;
            int count = 0;
            while (m.find() && count < 20) {
                JSONObject joOccurence = new JSONObject();
                int start = m.start();
                int end = m.end();
                joOccurence.put("start", start);
                joOccurence.put("end", end);
                
                if (start < previousline_end) continue;
                int newline_pos_start = content.substring(0, start).lastIndexOf("\n");
                if (newline_pos_start < 0) {
                    if (start > 120) {
                        newline_pos_start = start - 120;
                    } else {
                        newline_pos_start = 0;
                    }
                } else {
                    newline_pos_start += 1;
                }
                
                int relEnd = content.substring(end).indexOf("\n");
                int newline_pos_end = (relEnd >= 0) ? (end + relEnd) : Math.min(end + 120, content.length());
                previousline_end = newline_pos_end;

                String snippet = content.substring(newline_pos_start, newline_pos_end).trim();
                Matcher sm = p.matcher(snippet);
                String occurence = sm.replaceAll("<b>$0</b>");
                joOccurence.put("text", occurence);
                jsaOutputFiles.put(joOccurence);
                count++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONArray();
        }
        return jsaOutputFiles;
    }
}