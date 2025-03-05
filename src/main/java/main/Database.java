package main;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {

    private static final String DB_URL = "jdbc:sqlite:gym_bot.db";
    private Connection connection;

    public Database() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            initializeDatabase();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void initializeDatabase() {
        try (Statement statement = connection.createStatement()) {
            String createExercisesTable = "CREATE TABLE IF NOT EXISTS exercises ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT NOT NULL UNIQUE,"
                    + "muscle_type TEXT NOT NULL,"
                    + "created_at TEXT DEFAULT CURRENT_TIMESTAMP)";
            statement.execute(createExercisesTable);

            String createWorkoutsTable = "CREATE TABLE IF NOT EXISTS workouts ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "chat_id INTEGER NOT NULL,"
                    + "exercise_id INTEGER NOT NULL,"
                    + "reps INTEGER NOT NULL,"
                    + "weight REAL NOT NULL,"
                    + "date TEXT DEFAULT CURRENT_TIMESTAMP,"
                    + "FOREIGN KEY (exercise_id) REFERENCES exercises(id))";
            statement.execute(createWorkoutsTable);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveWorkout(long chatId, String exerciseName, int reps, double weight) {
        int exerciseId = getExerciseIdByName(exerciseName);
        if (exerciseId == -1) {
            System.out.println("Упражнение не найдено: " + exerciseName);
            return;
        }
        String sql = "INSERT INTO workouts (chat_id, exercise_id, reps, weight) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatId);
            statement.setInt(2, exerciseId);
            statement.setInt(3, reps);
            statement.setDouble(4, weight);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private int getExerciseIdByName(String name) {
        String sql = "SELECT id FROM exercises WHERE name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private String getExerciseNameById(int exerciseId) {
        String sql = "SELECT name FROM exercises WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, exerciseId);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getWorkoutHistory(long chatId) {
        StringBuilder history = new StringBuilder();
        String sql = "SELECT exercise_id, reps, weight, date FROM workouts WHERE chat_id = ? ORDER BY date DESC";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatId);
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                int exerciseId = resultSet.getInt("exercise_id");
                String exerciseName = getExerciseNameById(exerciseId);
                if (exerciseName.equals(null)) {
                    System.out.println("Упражнение не найдено: " + exerciseName);
                }
                int reps = resultSet.getInt("reps");
                double weight = resultSet.getDouble("weight");
                String date = resultSet.getString("date");
                history.append(String.format(
                        "Упражнение: %s, Повторения: %d, Вес: %.2f, Дата: %s\n",
                        exerciseName,
                        reps,
                        weight,
                        date
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history.toString();
    }


    public List<String> getExerciseNamesByType(MuscleType muscleType) {
        List<String> exercises = new ArrayList<>();
        String sql = "SELECT name FROM exercises WHERE muscle_type = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, muscleType.getTableName());
            ResultSet resultSet = statement.executeQuery();
            if (!resultSet.next()) {
                return exercises;
            }
            while (resultSet.next()) {
                String exercise = resultSet.getString("name");
                exercises.add(exercise);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return exercises;
    }

    public void addExercise(String name, String muscleType) {
        String sql = "INSERT INTO exercises (name, muscle_type) VALUES (?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, muscleType);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
