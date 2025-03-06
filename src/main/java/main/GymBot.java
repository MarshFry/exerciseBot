package main;

import org.sqlite.SQLiteException;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GymBot extends TelegramLongPollingBot {

    private final Map<Long, UserSession> userSessions = new HashMap<>();
    private final Database database = new Database();
    EnvListener envListener = new EnvListener();

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();
            UserSession session = userSessions.getOrDefault(chatId, new UserSession());

            if (messageText.equals("/start")) {
                userSessions.remove(chatId);
                session = new UserSession();
                session.setState(State.ACTION);
                userSessions.put(chatId, session);
            }

            switch (session.getState()) {
                case START_EXERCISE:
                    sendMessage(chatId, "Выбирите тип мышц:", createMuscleTypeKeyboard());
                    session.setState(State.CHOOSE_MUSCLE);
                    break;
                case CHOOSE_MUSCLE:
                    session.setMuscleType(MuscleType.fromRussianName(messageText));
                    sendMessage(chatId, "Выберите упражнение или введите название нового:",
                            createExerciseKeyboard(session.getMuscleType()));
                    session.setState(State.ENTER_EXERCISE);
                    break;
                case ENTER_EXERCISE:
                    try {
                        if (isExerciseFromKeyboard(session.getMuscleType(), messageText)) {
                            database.addExercise(messageText, session.getMuscleType().getTableName());
                        }
                        session.setExercise(messageText);
                        sendMessage(chatId, "Введите количество повторений и вес (через пробел):");
                        session.setState(State.ENTER_REPS_AND_WEIGHT);
                    } catch (SQLException e) {
                        sendMessage(chatId, "Некорректный ввод. Пожалуйста, введите название упражнения или выберите из имеющихся");
                    }
                    break;
                case ENTER_REPS_AND_WEIGHT:
                    try {
                        String cleanedInput = cleanInput(messageText);
                        String[] parts = cleanedInput.split(" ");
                        if (parts.length != 2) {
                            sendMessage(chatId, "Пожалуйста, введите два числа через пробел (например, '10 80.5').");
                            return;
                        }
                        if (!isNumber(parts[0]) || !isNumber(parts[1])) {
                            sendMessage(chatId, "Некорректный ввод. Оба значения должны быть числами (например, '10 80.5').");
                            return;
                        }
                        int reps = Integer.parseInt(parts[0]);
                        double weight = Double.parseDouble(parts[1]);
                        session.setReps(reps);
                        session.setWeight(weight);
                        database.saveWorkout(chatId, session.getExercise(), session.getReps(), session.getWeight());
                        sendMessage(chatId, "Данные сохранены. Выберите действие:", createActionKeyboard());
                        session.setState(State.ACTION);
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Некорректный ввод. Пожалуйста, введите два числа через пробел (например, '10 80.5').");
                    }
                    break;
                case ACTION:
                    if (messageText.equals("История")) {
                        String history = database.getWorkoutHistory(chatId);
                        sendMessage(chatId, "История занятий:\n" + history);
                    } else if (messageText.equals("Новое упражнение")) {
                        sendMessage(chatId, "Выберите тип мышц:", createMuscleTypeKeyboard());
                        session.setState(State.CHOOSE_MUSCLE);
                    } else {
                        sendMessage(chatId, "главная", createActionKeyboard());
                    }
                    break;
            }
            userSessions.put(chatId, session);
        }
    }

    private boolean isNumber(String str) {
        return str.matches("-?\\d+(\\.\\d+)?");
    }

    private String cleanInput(String input) {
        input = input.replace(',', '.');
        input = input.replaceAll("[^0-9. ]", "");
        input = input.trim().replaceAll(" +", " ");
        return input;
    }

    private boolean isExerciseFromKeyboard(MuscleType muscleType, String exerciseName) {
        List<String> exercises = database.getExerciseNamesByType(muscleType);
        return !exercises.contains(exerciseName);
    }

    private ReplyKeyboardMarkup createMuscleTypeKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        for (MuscleType muscleType : MuscleType.values()) {
            row.add(muscleType.getRussianName());
            if (row.size() >= 3) {
                keyboard.add(row);
                row = new KeyboardRow();
            }
        }
        if (!row.isEmpty()) {
            keyboard.add(row);
        }
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private ReplyKeyboardMarkup createExerciseKeyboard(MuscleType muscleType) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        List<String> exercises = database.getExerciseNamesByType(muscleType);
        if (!exercises.isEmpty()) {
            for (String exercise : exercises) {
                row.add(exercise);
                if (row.size() >= 3) {
                   keyboard.add(row);
                 row = new KeyboardRow();
                }
            }
        } else {
            row.add("Добавь с клавиатуры название упражнения");
        }
        if (!row.isEmpty()) {
            keyboard.add(row);
        }
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private ReplyKeyboardMarkup createActionKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("История");
        row.add("Новое упражнение");
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private void sendMessage(long chatId, String text, ReplyKeyboardMarkup keyboardMarkup) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setReplyMarkup(keyboardMarkup);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println(e.getMessage());
        }
    }

    private void sendMessage(long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    @Override
    public String getBotUsername() {
        return "ExerciseBot_marshFry";
    }

    @Override
    public String getBotToken() {
        return envListener.getToken();
    }
}
