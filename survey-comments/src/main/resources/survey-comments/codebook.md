# Codebook (STARTER DRAFT v0.2, owner must review and edit before v1)

version: v0.2-draft
status: DRAFT. Written for Vietnamese university student feedback on the quality of the
school's services (course content, lecturers, infrastructure, support, etc.).
Replace/merge/split themes after codebook discovery (task T3) on real past surveys.
Trim themes that never appear and split themes that are too broad.

## Rules for the classifier
- Comments are in Vietnamese (sometimes without diacritics, with abbreviations or teencode).
  Do NOT translate. `evidence` must be copied exactly from the comment as written.
- Assign a theme only if clearly expressed. Max 3 themes per comment; pick the most
  important if more are present and set `needs_human_review` = true.
- Every theme needs an evidence quote and its own sentiment (positive / neutral / negative).
- Use `other` only when the comment has substantive content that fits no theme.
- If a comment expresses a feeling but no specific topic (e.g. "Tốt.", "Cũng được."),
  return no themes and set overall sentiment.
- `overall_sentiment` = `mixed` only when clearly positive AND clearly negative parts exist.
- Set `contains_suggestion` = true when the student proposes a change
  (e.g. "Mong nhà trường...", "Em đề nghị...", "nên...").

## Themes
| id | Name (vi) | Definition | Not this theme |
|---|---|---|---|
| course_content | Nội dung môn học / chương trình | Relevance, difficulty, depth, up-to-dateness of subjects, curriculum, textbooks and learning materials | How it is taught -> teaching; exams -> assessment |
| teaching | Giảng viên & giảng dạy | Lecturer's clarity, method, attitude, punctuality, engagement, willingness to answer | Subject matter/materials -> course_content; grading -> assessment |
| assessment | Thi cử, kiểm tra, đánh giá | Exams, grading fairness, grading speed, feedback on results, match between taught and tested | Quality of teaching -> teaching |
| schedule | Lịch học, thời khóa biểu | Timetable, weekly workload, session length, schedule changes and notice | Rooms/equipment -> facilities |
| facilities | Cơ sở vật chất học tập | Classrooms, projectors, air-conditioning, labs, library, wifi, learning equipment | Canteen/dorm/parking -> campus_life; online systems -> online_systems |
| campus_life | Tiện ích & môi trường khuôn viên | Canteen, dormitory (ký túc xá), parking, restrooms, safety, cleanliness, green space | Classroom/lab equipment -> facilities |
| online_systems | Hệ thống trực tuyến | Student portal, LMS, online course registration, school website/app, e-mail system | Staff help -> student_support |
| student_support | Hỗ trợ sinh viên | Responsiveness and attitude of staff (phòng đào tạo, công tác sinh viên...), academic advisors, handling of requests and complaints | Fee/paperwork procedure itself -> admin_fees |
| admin_fees | Thủ tục hành chính & học phí | Administrative procedures, tuition and fee amounts/payment process, paperwork, announcements | Personal help from staff -> student_support |
| career_activities | Hướng nghiệp & hoạt động | Internship/job support, career events, clubs, extracurricular and exchange activities | Curriculum -> course_content |
| other | Khác | Substantive content that fits no theme above | Empty/vague praise or complaint -> no theme |

## Vietnamese language notes (for the classifier)
- Abbreviations: gv = giảng viên, sv = sinh viên, pđt/pdt = phòng đào tạo, ktx = ký túc xá,
  mssv = mã số sinh viên, cs = cơ sở, thầy/cô = lecturer (honorifics), tl = tài liệu.
- No-diacritics text (e.g. "giang vien day rat de hieu") and teencode (ko, k, đc, dc, j, vs, bt, ok)
  are normal; interpret by meaning.
- Neutral/lukewarm: "cũng được", "tạm ổn", "bình thường", "tàm tạm", "tạm được" -> neutral.
- Mild positive: "khá ổn", "ok", "không có gì để chê", "hài lòng". Mild negative: "chưa tốt",
  "cần cải thiện", "hơi thất vọng".
- Polite softening is common: "em nghĩ nhà trường nên xem xét..." is usually a complaint plus a suggestion.
- Sarcasm: positive words next to a complaint (e.g. "Tuyệt vời lắm, lịch cứ đổi liên tục")
  mean negative. Judge intended meaning.
- Rhetorical questions ("Sao lại như vậy?") usually signal dissatisfaction.

## Examples
- "Thầy dạy rất dễ hiểu" -> teaching (positive)
- "Phòng học quá nóng, máy lạnh hỏng" -> facilities (negative)
- "Web đăng ký môn học sập mỗi lần mở đăng ký" -> online_systems (negative)
- "Căng tin đắt mà dở" -> campus_life (negative)
- "Tài liệu không khớp với đề thi" -> assessment (negative)
- "Bình thường." -> no themes, overall neutral
