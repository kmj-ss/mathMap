package com.mathmap.game;

/** 사용자에게 그대로 보여줘도 되는 오류 */
public class GameException extends RuntimeException {
    public GameException(String message) {
        super(message);
    }
}
