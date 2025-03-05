package main;

public class EnvListener {

    private final String TOKEN = System.getenv("TOKEN");
    private final String TG_IDS = System.getenv("TG_IDS");
    private final String DB = System.getenv("DB");

    public String getToken() {
        return TOKEN;
    }

    public String getTgIds() {
        return TG_IDS;
    }

    public String getDB() {
        return DB;
    }
}
