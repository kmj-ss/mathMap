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
import com.mathmap.game.Room;
import com.mathmap.game.Room.QuestionInput;
import com.mathmap.game.RoomService;
import com.mathmap.game.Question;
import com.mathmap.game.QuestionKind;
import com.mathmap.game.Student;

class RoomTest {

    private Room game;

    @BeforeEach
    void setUp() {
        game = new Room("abcdefghjk", "3반 수학");
    }

    private Question subjective(String answer, Integer points) {
        return game.addQuestion(new QuestionInput("문제", null, QuestionKind.SUBJECTIVE, null, null, List.of(answer), points));
    }

    @Test
    void roomsAreSeparateAndIdsAreUnguessable() {
        RoomService rooms = new RoomService();
        Room a = rooms.create("1반");
        Room b = rooms.create("2반");
        assertTrue(RoomService.ROOM_ID.matcher(a.getId()).matches());
        assertFalse(a.getId().equals(b.getId()));
        a.join("1", "김철수");
        assertEquals(1, a.studentCount());
        assertEquals(0, b.studentCount());
        assertTrue(rooms.find("../etc").isEmpty());
        rooms.delete(a.getId());
        assertTrue(rooms.find(a.getId()).isEmpty());
    }

    @Test
    void correctAnswerGetsDefaultThreePointsOnlyOnce() {
        Student s = game.join("1", "김철수");
        Question q = subjective("12", null);
        game.next(null);
        assertTrue(game.submit(s.getId(), q.getId(), " 1 2 ").correct());
        assertThrows(GameException.class, () -> game.submit(s.getId(), q.getId(), "12"));
        assertEquals(3, s.getScore());
    }

    @Test
    void wrongAnswerShowsTeacherMessage() {
        Student s = game.join("1", "김철수");
        Question q = game.addQuestion(new QuestionInput("문제", null, QuestionKind.MULTIPLE,
                List.of("1", "7"), 1, null, 5));
        game.setWrongMessage("아쉬워요");
        game.next(null);
        assertFalse(game.submit(s.getId(), q.getId(), "0").correct());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) game.studentView(s.getId()).get("myResult");
        assertEquals("아쉬워요", result.get("message"));
        assertEquals(0, s.getScore());
    }

    @Test
    void answerIsHiddenUntilClosed() {
        Student s = game.join("1", "김철수");
        subjective("12", null);
        game.next(null);
        assertFalse(game.studentView(s.getId()).containsKey("correctAnswer"));
        game.closeCurrent();
        assertEquals("12", game.studentView(s.getId()).get("correctAnswer"));
    }

    @Test
    void rejoinKeepsScoreAndKickBlocksRejoin() {
        Student s = game.join("1", "김철수");
        assertEquals(s.getId(), game.join("1", "김철수").getId());
        game.kick(s.getId());
        assertThrows(GameException.class, () -> game.join("1", "김철수"));
    }

    @Test
    void historyShowsOnlyClosedQuestionsWithMyAnswer() {
        Student s = game.join("1", "김철수");
        Question q1 = subjective("12", null);
        subjective("7", null);
        game.next(null);
        game.submit(s.getId(), q1.getId(), "13");
        assertTrue(((List<?>) game.studentView(s.getId()).get("history")).isEmpty());
        game.next(null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) game.studentView(s.getId()).get("history");
        assertEquals(1, history.size());
        assertEquals("13", history.get(0).get("myAnswer"));
        assertEquals("12", history.get(0).get("correctAnswer"));
        assertEquals(false, history.get(0).get("correct"));
    }

    @Test
    void fractionAnswersIgnoreSpaces() {
        Student s = game.join("1", "김철수");
        Question q = subjective("1 2/3", null);
        game.next(null);
        assertTrue(game.submit(s.getId(), q.getId(), "1 2/3").correct());
    }

    @Test
    void cannotAnswerAfterClose() {
        Student s = game.join("1", "김철수");
        Question q = subjective("12", null);
        game.next(null);
        game.closeCurrent();
        assertThrows(GameException.class, () -> game.submit(s.getId(), q.getId(), "12"));
    }
}
