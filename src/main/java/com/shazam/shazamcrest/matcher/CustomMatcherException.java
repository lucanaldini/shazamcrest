package com.shazam.shazamcrest.matcher;

import org.hamcrest.Matcher;

class CustomMatcherException extends RuntimeException {
    private final Object object;
    private final Matcher<?> matcher;
    private final String classSimpleName;
    private final String jsonSnippet;

    CustomMatcherException(Object object, Matcher<?> matcher, String classSimpleName, String jsonSnippet) {
        this.object = object;
        this.matcher = matcher;
        this.classSimpleName = classSimpleName;
        this.jsonSnippet = jsonSnippet;
    }

    public Matcher<?> getMatcher() {
        return matcher;
    }

    public Object getObject() {
        return object;
    }

    public String getClassSimpleName() {
        return classSimpleName;
    }

    public String getJsonSnippet() {
        return jsonSnippet;
    }
}
