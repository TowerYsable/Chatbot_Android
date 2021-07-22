package com.myhome.application;

import java.util.List;

public class BotResponse {
    String recipient_id;
    String text;
    List buttons;

    public BotResponse(String recipient_id, String text) {
        this.recipient_id = recipient_id;
        this.text = text;
        this.buttons = buttons;
    }

    public String getRecipient_id() {
        return recipient_id;
    }

    public String getText() {
        return text;
    }

    public List getButtons() {
        return buttons;
    }
}
