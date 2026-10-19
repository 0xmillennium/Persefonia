package dev.persefonia.webpublic;

/** Language of the resolved HTML representation, independent of client preference. */
public interface PublicDocument {
    default String htmlLanguage() {
        return "en";
    }
}
