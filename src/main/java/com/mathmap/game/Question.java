package com.mathmap.game;

import java.util.List;

/**
 * 선생님이 등록한 문제. 정답(answers, correctChoice)은 서버 밖으로 나갈 때
 * 선생님 화면용 데이터에만 포함되고, 학생에게는 마감 후에만 공개된다.
 */
public class Question {
    private final long id;
    private String text;
    private String imageId;
    private QuestionKind kind;
    private List<String> choices;
    /** 주관식 정답(여러 개 허용) */
    private List<String> answers;
    /** 객관식 정답 번호(0부터) */
    private int correctChoice;
    private int points;
    private String wrongMessage;
    private QuestionStatus status = QuestionStatus.PENDING;

    public Question(long id) {
        this.id = id;
    }

    public long getId() { return id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getImageId() { return imageId; }
    public void setImageId(String imageId) { this.imageId = imageId; }
    public QuestionKind getKind() { return kind; }
    public void setKind(QuestionKind kind) { this.kind = kind; }
    public List<String> getChoices() { return choices; }
    public void setChoices(List<String> choices) { this.choices = choices; }
    public List<String> getAnswers() { return answers; }
    public void setAnswers(List<String> answers) { this.answers = answers; }
    public int getCorrectChoice() { return correctChoice; }
    public void setCorrectChoice(int correctChoice) { this.correctChoice = correctChoice; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public String getWrongMessage() { return wrongMessage; }
    public void setWrongMessage(String wrongMessage) { this.wrongMessage = wrongMessage; }
    public QuestionStatus getStatus() { return status; }
    public void setStatus(QuestionStatus status) { this.status = status; }

    /** 학생 화면에 보여줄 정답 문자열 */
    public String answerText() {
        if (kind == QuestionKind.MULTIPLE) {
            return (correctChoice + 1) + "번. " + choices.get(correctChoice);
        }
        return String.join(" 또는 ", answers);
    }

    /** 제출한 답이 맞는지 서버에서 판정 */
    public boolean isCorrect(String submitted) {
        if (submitted == null) {
            return false;
        }
        if (kind == QuestionKind.MULTIPLE) {
            try {
                return Integer.parseInt(submitted.trim()) == correctChoice;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        String normalized = normalize(submitted);
        return answers.stream().anyMatch(a -> normalize(a).equals(normalized));
    }

    /** 공백과 대소문자 차이는 무시하고 비교 */
    static String normalize(String s) {
        return s.replaceAll("\\s+", "").toLowerCase();
    }
}
