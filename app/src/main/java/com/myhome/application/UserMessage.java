package com.myhome.application;

public class UserMessage {
    String sender;
    String message;

    public UserMessage(String sender, String message){
        this.sender = sender;
        this.message = message;
    }

    public String getSender() {
        return sender;
    }

    public String getMessage() {
        return message;
    }
}
