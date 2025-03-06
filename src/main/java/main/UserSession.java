package main;

public class UserSession {

    private State state = State.ACTION;
    private MuscleType muscleType;
    private String exercise;
    private int reps;
    private double weight;
    private StringBuilder history = new StringBuilder();

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public MuscleType getMuscleType() {
        return muscleType;
    }

    public void setMuscleType(MuscleType muscleType) {
        this.muscleType = muscleType;
    }

    public String getExercise() {
        return exercise;
    }

    public void setExercise(String exercise) {
        this.exercise = exercise;
    }

    public int getReps() {
        return reps;
    }

    public void setReps(int reps) {
        this.reps = reps;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public String getHistory() {
        return history.toString();
    }

    public void addToHistory(String entry) {
        history.append(entry).append("\n");
    }
}

enum State {
    START_EXERCISE, CHOOSE_MUSCLE, ENTER_EXERCISE, ENTER_REPS_AND_WEIGHT, ACTION
}
