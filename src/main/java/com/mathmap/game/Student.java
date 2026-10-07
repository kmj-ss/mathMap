package com.mathmap.game;

import java.util.HashMap;
import java.util.Map;

public class Student {
    private final String id;
    private final String classNo;
    private final String name;
    private int score;
    private boolean connected;
    /** 문제 id → 제출 기록 */
    private final Map<Long, Submission> submissions = new HashMap<>();

    public Student(String id, String classNo, String name) {
        this.id = id;
        this.classNo = classNo;
        this.name = name;
    }

    public String getId() { return id; }
    public String getClassNo() { return classNo; }
    public String getName() { return name; }
    public int getScore() { return score; }
    public void addScore(int points) { this.score += points; }
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }
    public Map<Long, Submission> getSubmissions() { return submissions; }

    public String key() {
        return GameService.studentKey(classNo, name);
    }

    public record Submission(String answer, boolean correct) {}
}
