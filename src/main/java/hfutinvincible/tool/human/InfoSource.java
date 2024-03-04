package hfutinvincible.tool.human;


public enum InfoSource {
    WITNESS,
    FROM_RADIO,
    FROM_VOICE,
    MY_SELF;

    public boolean isFromMessage() {
        String name = name();
        return name.equals("FROM_RADIO") || name.equals("FROM_VOICE");
    }
}