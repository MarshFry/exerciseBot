package main;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {

    private static final String DB_URL = "jdbc:sqlite:gym_bot.db";
    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(5000);
        dataSource = new HikariDataSource(config);
    }

    public Database() {
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
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
        String sql = "INSERT INTO workouts " +
                "(chat_id, exercise_id, reps, weight)" +
                " VALUES (?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatId);
            statement.setInt(2, exerciseId);
            statement.setInt(3, reps);
            statement.setDouble(4, weight);
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<String> getLastWorkoutDataByName(String exerciseName, int sizeOfList) {
        List<String> workoutDatas = new ArrayList<>();
        int exerciseId = getExerciseIdByName(exerciseName);
        if (exerciseId == -1) {
            System.out.println("Упражнение не найдено: " + exerciseName);
            return workoutDatas;
        }
        String sql = "SELECT DISTINCT reps, weight " +
                "FROM workouts " +
                "WHERE exercise_id = ? " +
                "ORDER BY date DESC " +
                "LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, exerciseId);
            statement.setInt(2, sizeOfList);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int reps = resultSet.getInt("reps");
                    double weight = resultSet.getDouble("weight");
                    workoutDatas.add(reps + " " + weight);
                }
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при выполнении SQL-запроса: " + e.getMessage());
        }
        return workoutDatas;
    }

    private int getExerciseIdByName(String name) {
        String sql = "SELECT id FROM exercises WHERE name = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
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

    public String getWorkoutHistory() {
        StringBuilder history = new StringBuilder();
        String sql = "SELECT exercise_id, reps, weight, date " +
                "FROM workouts " +
                "ORDER BY date DESC";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                int exerciseId = resultSet.getInt("exercise_id");
                String exerciseName = getExerciseNameById(exerciseId);
                if (exerciseName == null) {
                    System.out.println("Упражнение не найдено: " + exerciseId);
                    continue;
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
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, muscleType.getTableName());
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                String exercise = resultSet.getString("name");
                exercises.add(exercise);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return exercises;
    }

    public void addExercise(String name, String muscleType) throws SQLException {
        String sql = "INSERT INTO exercises (name, muscle_type) VALUES (?, ?)";
        Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, name);
        statement.setString(2, muscleType);
        statement.executeUpdate();
    }
}
