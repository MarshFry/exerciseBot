package main;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.lang3.tuple.Pair;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

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
                    double weight = resultSet.getDouble("weight");
                    int reps = resultSet.getInt("reps");
                    workoutDatas.add(weight + " " + reps);
                }
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при выполнении SQL-запроса: " + e.getMessage());
        }
        return workoutDatas;
    }

    public String getExerciseHistory(String exerciseName, int limit) {
        StringBuilder history = new StringBuilder();
        int exerciseId = getExerciseIdByName(exerciseName);

        if (exerciseId == -1) {
            return "Упражнение не найдено";
        }
        String sql = "SELECT reps, weight, date " +
                "FROM workouts " +
                "WHERE exercise_id = ? " +
                "ORDER BY date DESC " +
                "LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, exerciseId);
            statement.setInt(2, limit * 10);

            ResultSet rs = statement.executeQuery();
            Map<String, List<String>> workoutsByDate = new LinkedHashMap<>();

            while (rs.next()) {
                int reps = rs.getInt("reps");
                double weight = rs.getDouble("weight");
                String fullDate = rs.getString("date");
                String dateOnly = fullDate.split(" ")[0];

                workoutsByDate.computeIfAbsent(dateOnly, k -> new ArrayList<>())
                        .add(0, String.format("Повторения: %d, Вес: %.1f", reps, weight));
            }
            int workoutNum = 1;
            for (Map.Entry<String, List<String>> entry : workoutsByDate.entrySet()) {
                if (workoutNum > limit) break;

                try {
                    SimpleDateFormat fromDB = new SimpleDateFormat("yyyy-MM-dd");
                    Date dateObj = fromDB.parse(entry.getKey());
                    SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", new Locale("ru"));
                    SimpleDateFormat dateFormat = new SimpleDateFormat("d MMMM yyyy", new Locale("ru"));

                    String dayOfWeek = dayFormat.format(dateObj);
                    dayOfWeek = dayOfWeek.substring(0, 1).toUpperCase() + dayOfWeek.substring(1);
                    String formattedDate = dateFormat.format(dateObj);

                    history.append(String.format("%d) %s %s:\n",
                            workoutNum++, dayOfWeek, formattedDate));
                    for (String set : entry.getValue()) {
                        history.append("       ").append(set).append("\n");
                    }
                } catch (Exception e) {
                    history.append(String.format("%d) %s:\n", workoutNum++, entry.getKey()));
                    for (String set : entry.getValue()) {
                        history.append("       ").append(set).append("\n");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Ошибка при загрузке истории";
        }
        return history.toString();
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

    public String getWorkoutHistoryText(int offset) {
        StringBuilder history = new StringBuilder();
        List<String> dates = new ArrayList<>();
        Map<String, Map<String, List<String>>> dateExercises = new LinkedHashMap<>();

        String dateSql = "SELECT DISTINCT DATE(date) as workout_date FROM workouts " +
                "ORDER BY workout_date DESC LIMIT 3 OFFSET ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(dateSql)) {

            stmt.setInt(1, offset);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String date = rs.getString("workout_date");
                dates.add(date);
                dateExercises.put(date, new LinkedHashMap<>());
            }

            String exerciseSql = "SELECT exercise_id, reps, weight, date " +
                    "FROM workouts WHERE DATE(date) = ? " +
                    "ORDER BY date DESC";

            try (PreparedStatement exStmt = conn.prepareStatement(exerciseSql)) {
                for (String date : dates) {
                    exStmt.setString(1, date);
                    ResultSet exRs = exStmt.executeQuery();

                    Map<String, List<String>> exercises = new LinkedHashMap<>();

                    while (exRs.next()) {
                        int exerciseId = exRs.getInt("exercise_id");
                        String exerciseName = getExerciseNameById(exerciseId);
                        if (exerciseName == null) continue;

                        int reps = exRs.getInt("reps");
                        double weight = exRs.getDouble("weight");

                        if (!exercises.containsKey(exerciseName)) {
                            exercises.put(exerciseName, new ArrayList<>());
                        }
                        exercises.get(exerciseName).add(0, String.format("Повторения: %d, Вес: %.1f", reps, weight));
                    }

                    SimpleDateFormat fromDB = new SimpleDateFormat("yyyy-MM-dd");
                    Date dateObj = fromDB.parse(date);
                    SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", new Locale("ru"));
                    SimpleDateFormat dateFormat = new SimpleDateFormat("d MMMM yyyy", new Locale("ru"));

                    String dayOfWeek = dayFormat.format(dateObj);
                    dayOfWeek = dayOfWeek.substring(0, 1).toUpperCase() + dayOfWeek.substring(1);
                    String formattedDate = dateFormat.format(dateObj);

                    history.append(String.format("<b>%s %s</b> 💪\n", dayOfWeek, formattedDate));

                    int exerciseNum = 1;
                    for (Map.Entry<String, List<String>> entry : exercises.entrySet()) {
                        history.append(String.format("%d) %s:\n", exerciseNum++, entry.getKey()));
                        for (String set : entry.getValue()) {
                            history.append("       ").append(set).append("\n");
                        }
                    }
                    history.append("\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Ошибка при загрузке истории";
        }

        return history.length() > 0 ? history.toString() :
                (offset == 0 ? "История тренировок пуста" : "Это все тренировки");
    }

    public boolean hasMoreWorkouts(int offset) {
        String sql = "SELECT 1 FROM workouts GROUP BY DATE(date) " +
                "ORDER BY DATE(date) DESC LIMIT 1 OFFSET ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, offset + 3);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
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
