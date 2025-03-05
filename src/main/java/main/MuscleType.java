package main;

public enum MuscleType {
    CHEST("Грудь", "chest"),
    BACK("Спина", "back"),
    LEGS("Ноги", "legs"),
    ARMS("Руки", "arms"),
    SHOULDERS("Плечи", "shoulders"),
    ABS("Пресс", "abs");

    private final String name;
    private final String tableName;

    MuscleType(String name, String tableName) {
        this.name = name;
        this.tableName = tableName;
    }

    public String getRussianName() {
        return name;
    }

    public String getTableName() {
        return tableName;
    }

    public static MuscleType fromRussianName(String russianName) {
        for (MuscleType type : MuscleType.values()) {
            if (type.getRussianName().equalsIgnoreCase(russianName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Неизвестная группа мышц: " + russianName);
    }
}
