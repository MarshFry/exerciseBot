package main;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GymBot extends TelegramLongPollingBot {

    private final Map<Long, UserSession> userSessions = new HashMap<>();
    private final Database database = new Database();
    EnvListener envListener = new EnvListener();
    List<String> lastWorkouts = new ArrayList<>();
    private final Map<Long, Integer> historyOffsets = new HashMap<>();

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();
            UserSession session = userSessions.getOrDefault(chatId, new UserSession());

            if (messageText.equals("/start") || messageText.equals("Назад")) {
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
                        session.setState(State.ENTER_REPS_AND_WEIGHT);
                        String exerciseHistory = database.getExerciseHistory(messageText, 5);
                        sendHtmlMessage(chatId, "История по упражнению: " + messageText + "\n" + exerciseHistory, null);
                        lastWorkouts = database.getLastWorkoutDataByName(session.getExercise(), 5);
                        if (!lastWorkouts.isEmpty()) {
                            sendMessage(chatId, "Выберите последние значения или введите новые:",
                                    createLastWorkoutsKeyboard(lastWorkouts));
                        } else {
                            sendMessageAndRemoveKeyboard(chatId, "Введите вес и количество повторений (через пробел):");
                        }
                    } catch (SQLException e) {
                        sendMessage(chatId, "Некорректный ввод. Пожалуйста, введите название упражнения или выберите из имеющихся");
                    }
                    break;
                case ENTER_REPS_AND_WEIGHT:
                    try {
                        String cleanedInput = cleanInput(messageText);
                        String[] parts = cleanedInput.split(" ");
                        if (parts.length != 2) {
                            sendMessage(chatId, "Пожалуйста, введите два числа через пробел (например, '80.5 10').");
                            return;
                        }
                        if (isNotNumber(parts[0]) || isNotNumber(parts[1])) {
                            sendMessage(chatId, "Некорректный ввод. Оба значения должны быть числами (например, '80.5 10').");
                            return;
                        }
                        double weight = Double.parseDouble(parts[0]);
                        int reps = Integer.parseInt(parts[1]);
                        session.setWeight(weight);
                        session.setReps(reps);
                        database.saveWorkout(chatId, session.getExercise(), session.getReps(), session.getWeight());
                        sendMessage(chatId, "Данные сохранены. Выберите действие:", createActionKeyboard(true));
                        session.setState(State.ACTION);
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Некорректный ввод. Пожалуйста, введите два числа через пробел (например, '80.5 10').");
                    }
                    break;
                case ACTION:
                    switch (messageText) {
                        case "История":
                        case "Показать ещё":
                            int currentOffset = messageText.equals("История") ? 0 : historyOffsets.getOrDefault(chatId, 0) + 3;
                            historyOffsets.put(chatId, currentOffset);
                            String historyText = database.getWorkoutHistoryText(currentOffset);
                            boolean hasMore = database.hasMoreWorkouts(currentOffset);
                            ReplyKeyboardMarkup keyboard = createActionKeyboard(
                                    session.getExercise() != null,
                                    hasMore
                            );
                            sendHtmlMessage(chatId, historyText, keyboard);
                            break;

                        case "Новое упражнение":
                            sendMessage(chatId, "Выберите тип мышц:", createMuscleTypeKeyboard());
                            session.setState(State.CHOOSE_MUSCLE);
                            break;

                        case "Добавить подход (повторить предыдущее упражнение)":
                            String exerciseHistory = database.getExerciseHistory(session.getExercise(), 5);
                            sendHtmlMessage(chatId, "История по упражнению: " + session.getExercise() + "\n" + exerciseHistory, null);
                            lastWorkouts = database.getLastWorkoutDataByName(session.getExercise(), 5) ;
                            if (!lastWorkouts.isEmpty()) {
                                sendMessage(chatId, "Выберите последние значения или введите новые:",
                                        createLastWorkoutsKeyboard(lastWorkouts));
                            } else {
                                sendMessageAndRemoveKeyboard(chatId, "Введите количество повторений и вес (через пробел):");
                            }
                            session.setState(State.ENTER_REPS_AND_WEIGHT);
                            break;

                        default:
                            sendMessage(chatId, "Главная", createActionKeyboard(false, false));
                            break;
                    }
                    break;
            }
            userSessions.put(chatId, session);
        }
    }

    private ReplyKeyboardMarkup createLastWorkoutsKeyboard(List<String> lastWorkouts) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        for (String workout : lastWorkouts) {
            String[] element = workout.split(" ");
            row.add(String.format("Вес: %s; Повторений: %s", element[0], element[1]));
            keyboard.add(row);
            row = new KeyboardRow();
        }
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private boolean isNotNumber(String str) {
        return !str.matches("-?\\d+(\\.\\d+)?");
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
        keyboard.add(addPreviousStateBtn());
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
                if (row.size() >= 2) {
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
        keyboard.add(addPreviousStateBtn());
        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private ReplyKeyboardMarkup createActionKeyboard(boolean isAfterSave, boolean showMoreInsteadHistory) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        if (isAfterSave) {
            row.add("Добавить подход (повторить предыдущее упражнение)");
            keyboard.add(row);
            row = new KeyboardRow();
        }
        row.add(showMoreInsteadHistory ? "Показать ещё" : "История");
        row.add("Новое упражнение");
        keyboard.add(row);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    private ReplyKeyboardMarkup createActionKeyboard(boolean isAfterSave) {
        return createActionKeyboard(isAfterSave, false);
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

    private void sendMessageAndRemoveKeyboard(long chatId, String text) {
        ReplyKeyboardRemove keyboardRemove = new ReplyKeyboardRemove();
        keyboardRemove.setRemoveKeyboard(true);
        keyboardRemove.setSelective(false);
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setReplyMarkup(keyboardRemove);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println(e.getMessage());
        }
    }

    private void sendHtmlMessage(long chatId, String text, ReplyKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("HTML");
        if (keyboard != null) {
            message.setReplyMarkup(keyboard);
        }
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private KeyboardRow addPreviousStateBtn() {
        KeyboardRow backRow = new KeyboardRow();
        backRow.add("Назад");
        return backRow;
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
