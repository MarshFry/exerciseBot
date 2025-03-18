package main;

public enum State {

    START_EXERCISE(1),
    CHOOSE_MUSCLE(2),
    ENTER_EXERCISE(3),
    ENTER_REPS_AND_WEIGHT(4),
    ACTION(0);

    private final int id;

    State(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
