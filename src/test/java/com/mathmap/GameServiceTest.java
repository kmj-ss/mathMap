package com.mathmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mathmap.game.GameException;
import com.mathmap.game.GameService;
import com.mathmap.game.GameService.QuestionInput;
import com.mathmap.game.Question;
import com.mathmap.game.QuestionKind;
import com.mathmap.game.Student;

class GameServiceTest {

    private GameService game;

    @BeforeEach
    void setUp() {
        game = new GameService();
        game.setEntryCode("3반수학");
    }

    private Question subjective(String answer, Integer points) {
        return game.addQuestion(new QuestionInput("문제", null, QuestionKind.SUBJECTIVE, null, null, List.of(answer), points, null));
    }

    @Test
    void wrongEntryCodeIsRejected() {
        assertThrows(GameException.class, () -> game.join("다른코드", "1", "김철수"));
    }

    @Test
    void correctAnswerGetsDefaultThreePointsOnlyOnce() {
        Student s = game.join("3반수학", "1", "김철수");
        Question q = subjective("12", null);
        game.next(null);
        assertTrue(game.submit(s.getId(), q.getId(), " 1 2 ").correct());
        assertThrows(GameException.class, () -> game.submit(s.getId(), q.getId(), "12"));
        assertEquals(3, s.getScore());
    }

    @Test
    void wrongAnswerShowsTeacherMessage() {
        Student s = game.join("3반수학", "1", "김철수");
        Question q = game.addQuestion(new QuestionInput("문제", null, QuestionKind.MULTIPLE,
                List.of("1", "7"), 1, null, 5, "아쉬워요"));
        game.next(null);
        assertFalse(game.submit(s.getId(), q.getId(), "0").correct());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) game.studentView(s.getId()).get("myResult");
        assertEquals("아쉬워요", result.get("message"));
        assertEquals(0, s.getScore());
    }

    @Test
    void answerIsHiddenUntilClosed() {
        Student s = game.join("3반수학", "1", "김철수");
        subjective("12", null);
        game.next(null);
        assertFalse(game.studentView(s.getId()).containsKey("correctAnswer"));
        game.closeCurrent();
        assertEquals("12", game.studentView(s.getId()).get("correctAnswer"));
    }

    @Test
    void rejoinKeepsScoreAndKickBlocksRejoin() {
        Student s = game.join("3반수학", "1", "김철수");
        assertEquals(s.getId(), game.join("3반수학", "1", "김철수").getId());
        game.kick(s.getId());
        assertThrows(GameException.class, () -> game.join("3반수학", "1", "김철수"));
    }

    @Test
    void cannotAnswerAfterClose() {
        Student s = game.join("3반수학", "1", "김철수");
        Question q = subjective("12", null);
        game.next(null);
        game.closeCurrent();
        assertThrows(GameException.class, () -> game.submit(s.getId(), q.getId(), "12"));
    }
}
