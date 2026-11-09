package com.surveycomments.redact;

/**
 * Immutable result of applying all redaction rules to a single text.
 * Counts how many instances of each type were replaced.
 */
public final class RedactionResult {

    public final String text;
    public final int emails;
    public final int phones;
    public final int mssv;
    public final int names;

    public RedactionResult(String text, int emails, int phones, int mssv, int names) {
        this.text   = text;
        this.emails = emails;
        this.phones = phones;
        this.mssv   = mssv;
        this.names  = names;
    }

    public int totalRedacted() { return emails + phones + mssv + names; }
}

