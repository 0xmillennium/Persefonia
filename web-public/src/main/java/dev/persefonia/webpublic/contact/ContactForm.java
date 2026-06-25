package dev.persefonia.webpublic.contact;

public final class ContactForm {
    private String senderName = "";
    private String senderEmail = "";
    private String subject = "";
    private String body = "";

    public String senderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName == null ? "" : senderName;
    }

    public String senderEmail() {
        return senderEmail;
    }

    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail == null ? "" : senderEmail;
    }

    public String subject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject == null ? "" : subject;
    }

    public String body() {
        return body;
    }

    public void setBody(String body) {
        this.body = body == null ? "" : body;
    }
}
