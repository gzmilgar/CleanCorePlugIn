package com.rap.generator.utils;

import java.util.regex.Pattern;

public class AbapNamingValidator {

    private static final int MAX_LENGTH = 30;
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z_/][A-Za-z0-9_/]*$");
    private static final Pattern CUSTOMER_PREFIX = Pattern.compile("^[ZY]", Pattern.CASE_INSENSITIVE);

    public static ValidationResult validateObjectName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return ValidationResult.error("Name cannot be empty");
        }
        name = name.trim();
        if (name.length() > MAX_LENGTH) {
            return ValidationResult.error("Name exceeds " + MAX_LENGTH + " characters (current: " + name.length() + ")");
        }
        if (!VALID_NAME.matcher(name).matches()) {
            return ValidationResult.error("Name contains invalid characters. Only letters, digits, underscores, and '/' are allowed");
        }
        if (!CUSTOMER_PREFIX.matcher(name).matches() && !name.startsWith("/")) {
            return ValidationResult.warning("Name should start with Z or Y (customer namespace) or / (partner namespace)");
        }
        return ValidationResult.ok();
    }

    public static ValidationResult validateFieldName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return ValidationResult.error("Field name cannot be empty");
        }
        name = name.trim();
        if (name.length() > MAX_LENGTH) {
            return ValidationResult.error("Field name exceeds " + MAX_LENGTH + " characters");
        }
        if (!VALID_NAME.matcher(name).matches()) {
            return ValidationResult.error("Field name contains invalid characters");
        }
        return ValidationResult.ok();
    }

    public static ValidationResult validateActionName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return ValidationResult.error("Action name cannot be empty");
        }
        name = name.trim();
        if (!Pattern.matches("^[a-zA-Z][a-zA-Z0-9]*$", name)) {
            return ValidationResult.error("Action name must be camelCase (letters and digits only, starting with a letter)");
        }
        if (name.length() > MAX_LENGTH) {
            return ValidationResult.error("Action name exceeds " + MAX_LENGTH + " characters");
        }
        return ValidationResult.ok();
    }

    public static String toAbapObjectName(String prefix, String type, String name) {
        String upper = name.toUpperCase().replace(" ", "_");
        String result = prefix + type + "_" + upper;
        if (result.length() > MAX_LENGTH) {
            result = result.substring(0, MAX_LENGTH);
        }
        return result;
    }

    public static class ValidationResult {
        public enum Severity { OK, WARNING, ERROR }

        private final Severity severity;
        private final String message;

        private ValidationResult(Severity severity, String message) {
            this.severity = severity;
            this.message = message;
        }

        public static ValidationResult ok() { return new ValidationResult(Severity.OK, null); }
        public static ValidationResult warning(String msg) { return new ValidationResult(Severity.WARNING, msg); }
        public static ValidationResult error(String msg) { return new ValidationResult(Severity.ERROR, msg); }

        public boolean isOk() { return severity == Severity.OK; }
        public boolean isError() { return severity == Severity.ERROR; }
        public Severity getSeverity() { return severity; }
        public String getMessage() { return message; }
    }
}
