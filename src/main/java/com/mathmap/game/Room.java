package com.mathmap.game;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 방(수업) 한 개의 모든 상태(문제, 학생, 점수)를 서버 메모리에 보관한다.
 * 점수 계산과 채점은 전부 여기서만 이뤄지고, 브라우저가 보낸 점수 같은 값은 받지 않는다.
 * 모든 public 메서드는 synchronized 로 한 번에 하나씩만 실행된다.
 */
public class Room {

    public static final int DEFAULT_POINTS = 3;
    public static final String DEFAULT_WRONG_MESSAGE = "틀렸습니다";

    private static final Pattern CLASS_NO = Pattern.compile("^[0-9]{1,10}$");
    private static final Pattern NAME = Pattern.compile("^[가-힣A-Za-z][가-힣A-Za-z ]{0,19}$");
    private static final int MAX_QUESTIONS = 300;
    private static final int MAX_STUDENTS = 300;

    private final SecureRandom random = new SecureRandom();

    private final String id;
    private final String name;
    private final Instant createdAt = Instant.now();
    private volatile Instant lastActivity = Instant.now();
    private long nextQuestionId = 1;
    private final List<Question> questions = new ArrayList<>();
    private Question current;
    /** studentId → Student */
    private final Map<String, Student> students = new LinkedHashMap<>();
    /** 강퇴된 학생(학급번호|이름) */
    private final Set<String> kicked = new LinkedHashSet<>();

    public Room(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActivity() { return lastActivity; }

    private void touch() {
        lastActivity = Instant.now();
    }

    // ───────────────────────── 수업 ─────────────────────────

    /** 새 수업: 학생, 점수, 강퇴 목록, 진행 상태를 비우고 문제는 모두 '대기'로 되돌린다. */
    public synchronized void resetClass() {
        students.clear();
        kicked.clear();
        current = null;
        questions.forEach(q -> q.setStatus(QuestionStatus.PENDING));
    }

    // ───────────────────────── 학생 ─────────────────────────

    public static String studentKey(String classNo, String name) {
        return classNo.trim() + "|" + name.trim().replaceAll("\\s+", " ");
    }

    /**
     * 학생 입장. 같은 학급번호+이름이 이미 있으면 같은 학생으로 재접속 처리한다.
     * @return 학생 id (서버 세션에 저장)
     */
    public synchronized Student join(String classNo, String name) {
        touch();
        String cn = classNo == null ? "" : classNo.trim();
        String nm = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (!CLASS_NO.matcher(cn).matches()) {
            throw new GameException("학급 번호는 숫자로 입력해 주세요.");
        }
        if (!NAME.matcher(nm).matches()) {
            throw new GameException("이름은 한글 또는 영문으로 20자까지 입력해 주세요.");
        }
        String key = studentKey(cn, nm);
        if (kicked.contains(key)) {
            throw new GameException("이 수업에 입장할 수 없어요.");
        }
        Optional<Student> existing = findByKey(key);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (students.size() >= MAX_STUDENTS) {
            throw new GameException("입장 인원이 가득 찼어요.");
        }
        Student s = new Student(newId(), cn, nm);
        students.put(s.getId(), s);
        return s;
    }

    public synchronized Optional<Student> findStudent(String studentId) {
        if (studentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(students.get(studentId));
    }

    private Optional<Student> findByKey(String key) {
        return students.values().stream().filter(s -> s.key().equals(key)).findFirst();
    }

    public synchronized int studentCount() {
        return students.size();
    }

    public synchronized void setConnected(String studentId, boolean connected) {
        Student s = students.get(studentId);
        if (s != null) {
            s.setConnected(connected);
        }
    }

    /** 강퇴: 목록에서 지우고 같은 학급번호+이름으로 다시 들어오지 못하게 한다. */
    public synchronized Optional<Student> kick(String studentId) {
        Student s = students.remove(studentId);
        if (s != null) {
            kicked.add(s.key());
        }
        return Optional.ofNullable(s);
    }

    public synchronized void unkick(String key) {
        kicked.remove(key);
    }

    /** 답 제출. 한 문제에 한 번만 채점한다. */
    public synchronized Student.Submission submit(String studentId, long questionId, String answer) {
        Student s = students.get(studentId);
        if (s == null) {
            throw new GameException("입장 정보가 없어요. 다시 입장해 주세요.");
        }
        if (current == null || current.getId() != questionId || current.getStatus() != QuestionStatus.OPEN) {
            throw new GameException("지금은 이 문제에 답할 수 없어요.");
        }
        if (s.getSubmissions().containsKey(questionId)) {
            throw new GameException("이미 답을 제출했어요.");
        }
        if (answer == null || answer.isBlank() || answer.length() > 200) {
            throw new GameException("답을 입력해 주세요.");
        }
        touch();
        boolean correct = current.isCorrect(answer);
        Student.Submission sub = new Student.Submission(answer.trim(), correct);
        s.getSubmissions().put(questionId, sub);
        if (correct) {
            s.addScore(current.getPoints());
        }
        return sub;
    }

    // ───────────────────────── 문제 ─────────────────────────

    public synchronized Question addQuestion(QuestionInput in) {
        if (questions.size() >= MAX_QUESTIONS) {
            throw new GameException("문제는 최대 " + MAX_QUESTIONS + "개까지 등록할 수 있어요.");
        }
        touch();
        Question q = new Question(nextQuestionId++);
        apply(q, in);
        questions.add(q);
        return q;
    }

    public synchronized void updateQuestion(long id, QuestionInput in) {
        Question q = get(id);
        if (q.getStatus() != QuestionStatus.PENDING) {
            throw new GameException("이미 출제한 문제는 수정할 수 없어요.");
        }
        apply(q, in);
    }

    public synchronized void deleteQuestion(long id) {
        Question q = get(id);
        if (q == current) {
            throw new GameException("지금 출제 중인 문제는 삭제할 수 없어요.");
        }
        questions.remove(q);
    }

    /**
     * 다음 문제 출제. id 를 주면 그 문제를, 없으면 대기 중인 첫 문제를 출제한다.
     * 진행 중인 문제는 자동으로 마감된다. 남은 문제가 없으면 '대기' 상태가 된다.
     */
    public synchronized Optional<Question> next(Long id) {
        touch();
        if (current != null) {
            current.setStatus(QuestionStatus.CLOSED);
        }
        Question target;
        if (id != null) {
            target = get(id);
            if (target.getStatus() != QuestionStatus.PENDING) {
                throw new GameException("이미 출제한 문제예요.");
            }
        } else {
            target = questions.stream()
                    .filter(q -> q.getStatus() == QuestionStatus.PENDING)
                    .findFirst().orElse(null);
        }
        current = target;
        if (target != null) {
            target.setStatus(QuestionStatus.OPEN);
        }
        return Optional.ofNullable(target);
    }

    /** 문제 마감 + 정답 공개 */
    public synchronized void closeCurrent() {
        if (current == null || current.getStatus() != QuestionStatus.OPEN) {
            throw new GameException("진행 중인 문제가 없어요.");
        }
        current.setStatus(QuestionStatus.CLOSED);
    }

    private Question get(long id) {
        return questions.stream().filter(q -> q.getId() == id).findFirst()
                .orElseThrow(() -> new GameException("문제를 찾을 수 없어요."));
    }

    private void apply(Question q, QuestionInput in) {
        String text = in.text() == null ? "" : in.text().trim();
        String imageId = in.imageId() == null || in.imageId().isBlank() ? null : in.imageId().trim();
        if (text.isEmpty() && imageId == null) {
            throw new GameException("문제 내용을 입력하거나 이미지를 등록해 주세요.");
        }
        if (text.length() > 2000) {
            throw new GameException("문제 내용은 2000자까지 입력할 수 있어요.");
        }
        if (imageId != null && !imageId.matches("^[0-9a-f]{32}$")) {
            throw new GameException("이미지 정보가 올바르지 않아요.");
        }
        if (in.kind() == null) {
            throw new GameException("주관식/객관식을 선택해 주세요.");
        }
        int points = in.points() == null ? DEFAULT_POINTS : in.points();
        if (points < 0 || points > 100) {
            throw new GameException("배점은 0~100점 사이로 입력해 주세요.");
        }
        String wrong = in.wrongMessage() == null || in.wrongMessage().isBlank()
                ? DEFAULT_WRONG_MESSAGE : in.wrongMessage().trim();
        if (wrong.length() > 100) {
            throw new GameException("오답 문구는 100자까지 입력할 수 있어요.");
        }
        if (in.kind() == QuestionKind.MULTIPLE) {
            List<String> choices = clean(in.choices(), 100);
            if (choices.size() < 2 || choices.size() > 10) {
                throw new GameException("객관식 보기는 2~10개 입력해 주세요.");
            }
            if (in.correctChoice() == null || in.correctChoice() < 0 || in.correctChoice() >= choices.size()) {
                throw new GameException("객관식 정답 번호를 선택해 주세요.");
            }
            q.setChoices(choices);
            q.setCorrectChoice(in.correctChoice());
            q.setAnswers(List.of());
        } else {
            List<String> answers = clean(in.answers(), 200);
            if (answers.isEmpty() || answers.size() > 10) {
                throw new GameException("주관식 정답을 1~10개 입력해 주세요.");
            }
            q.setAnswers(answers);
            q.setChoices(List.of());
            q.setCorrectChoice(-1);
        }
        q.setText(text);
        q.setImageId(imageId);
        q.setKind(in.kind());
        q.setPoints(points);
        q.setWrongMessage(wrong);
    }

    private static List<String> clean(List<String> list, int maxLen) {
        if (list == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : list) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.length() > maxLen) {
                throw new GameException("보기/정답은 " + maxLen + "자까지 입력할 수 있어요.");
            }
            out.add(t);
        }
        return out;
    }

    // ───────────────────────── 화면용 데이터 ─────────────────────────

    /** 학생 한 명에게 보낼 화면 상태. 마감 전에는 정답을 절대 포함하지 않는다. */
    public synchronized Map<String, Object> studentView(String studentId) {
        Student s = students.get(studentId);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("type", "state");
        view.put("roomName", name);
        if (s == null) {
            view.put("status", "GONE");
            return view;
        }
        view.put("me", Map.of("classNo", s.getClassNo(), "name", s.getName(), "score", s.getScore()));
        if (current == null) {
            view.put("status", "WAITING");
            return view;
        }
        view.put("status", current.getStatus().name());
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("id", current.getId());
        q.put("number", questions.indexOf(current) + 1);
        q.put("text", current.getText());
        q.put("imageId", current.getImageId());
        q.put("kind", current.getKind().name());
        q.put("choices", current.getChoices());
        q.put("points", current.getPoints());
        view.put("question", q);
        Student.Submission sub = s.getSubmissions().get(current.getId());
        if (sub != null) {
            view.put("myResult", Map.of(
                    "answer", sub.answer(),
                    "correct", sub.correct(),
                    "message", sub.correct() ? "정답입니다! +" + current.getPoints() + "점" : current.getWrongMessage()));
        }
        if (current.getStatus() == QuestionStatus.CLOSED) {
            view.put("correctAnswer", current.answerText());
        }
        return view;
    }

    /** 선생님 화면용 전체 상태(정답 포함) */
    public synchronized Map<String, Object> teacherView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("type", "dashboard");
        view.put("roomId", id);
        view.put("roomName", name);
        view.put("currentId", current == null ? null : current.getId());
        view.put("status", current == null ? "WAITING" : current.getStatus().name());

        List<Map<String, Object>> qs = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", q.getId());
            m.put("number", i + 1);
            m.put("text", q.getText());
            m.put("imageId", q.getImageId());
            m.put("kind", q.getKind().name());
            m.put("choices", q.getChoices());
            m.put("answers", q.getAnswers());
            m.put("correctChoice", q.getCorrectChoice());
            m.put("points", q.getPoints());
            m.put("wrongMessage", q.getWrongMessage());
            m.put("status", q.getStatus().name());
            m.put("answerText", q.answerText());
            qs.add(m);
        }
        view.put("questions", qs);

        List<Map<String, Object>> ss = new ArrayList<>();
        int answered = 0;
        int correctCount = 0;
        List<Student> sorted = new ArrayList<>(students.values());
        sorted.sort(Comparator.comparing((Student st) -> padNo(st.getClassNo())).thenComparing(Student::getName));
        for (Student st : sorted) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", st.getId());
            m.put("classNo", st.getClassNo());
            m.put("name", st.getName());
            m.put("score", st.getScore());
            m.put("connected", st.isConnected());
            Student.Submission sub = current == null ? null : st.getSubmissions().get(current.getId());
            m.put("answered", sub != null);
            m.put("correct", sub != null && sub.correct());
            if (sub != null) {
                answered++;
                if (sub.correct()) {
                    correctCount++;
                }
            }
            ss.add(m);
        }
        view.put("students", ss);
        view.put("answeredCount", answered);
        view.put("correctCount", correctCount);
        view.put("kicked", List.copyOf(kicked));
        return view;
    }

    /** 엑셀 내보내기용 스냅샷 */
    public synchronized ScoreSheet scoreSheet() {
        List<Question> asked = questions.stream()
                .filter(q -> q.getStatus() != QuestionStatus.PENDING).toList();
        List<ScoreSheet.Row> rows = new ArrayList<>();
        List<Student> sorted = new ArrayList<>(students.values());
        sorted.sort(Comparator.comparing((Student st) -> padNo(st.getClassNo())).thenComparing(Student::getName));
        for (Student st : sorted) {
            List<String> marks = new ArrayList<>();
            for (Question q : asked) {
                Student.Submission sub = st.getSubmissions().get(q.getId());
                marks.add(sub == null ? "-" : (sub.correct() ? "O" : "X"));
            }
            rows.add(new ScoreSheet.Row(st.getClassNo(), st.getName(), st.getScore(), marks));
        }
        List<String> headers = new ArrayList<>();
        for (Question q : asked) {
            headers.add((questions.indexOf(q) + 1) + "번");
        }
        return new ScoreSheet(headers, rows);
    }

    private static String padNo(String no) {
        return String.format("%10s", no);
    }

    private String newId() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    public record QuestionInput(
            String text,
            String imageId,
            QuestionKind kind,
            List<String> choices,
            Integer correctChoice,
            List<String> answers,
            Integer points,
            String wrongMessage) {}

    public record ScoreSheet(List<String> questionHeaders, List<Row> rows) {
        public record Row(String classNo, String name, int score, List<String> marks) {}
    }
}
