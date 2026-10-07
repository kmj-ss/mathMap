package com.mathmap.game;

public enum QuestionStatus {
    /** 등록만 되고 아직 출제되지 않음 */
    PENDING,
    /** 출제되어 학생이 답을 낼 수 있음 */
    OPEN,
    /** 마감되어 정답이 공개됨 */
    CLOSED
}
