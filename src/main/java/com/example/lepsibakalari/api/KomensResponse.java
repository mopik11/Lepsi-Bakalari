package com.example.lepsibakalari.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class KomensResponse {

    @SerializedName("Messages")
    private List<KomensMessage> messages;

    public List<KomensMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<KomensMessage> messages) {
        this.messages = messages;
    }

    public static class KomensMessage {
        @SerializedName("Id")
        private String id;

        @SerializedName("Title")
        private String title;

        @SerializedName("Text")
        private String text;

        @SerializedName("SentDate")
        private String sentDate;

        @SerializedName("Sender")
        private Sender sender;

        @SerializedName("Read")
        private boolean read;

        @SerializedName("Type")
        private String type;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getSentDate() { return sentDate; }
        public void setSentDate(String sentDate) { this.sentDate = sentDate; }
        public Sender getSender() { return sender; }
        public void setSender(Sender sender) { this.sender = sender; }
        public boolean isRead() { return read; }
        public void setRead(boolean read) { this.read = read; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    public static class Sender {
        @SerializedName("Name")
        private String name;

        @SerializedName("Type")
        private String type;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}
