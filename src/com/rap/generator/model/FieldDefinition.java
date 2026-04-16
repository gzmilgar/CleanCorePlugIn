package com.rap.generator.model;

public class FieldDefinition {

    private String name;
    private String abapName;
    private String abapType;
    private String label;
    private boolean key;
    private boolean mandatory;
    private boolean readOnly;

    // UI Annotations
    private Integer lineItemPosition;
    private String lineItemImportance; // HIGH, MEDIUM, LOW
    private Integer selectionFieldPosition;
    private Integer identificationPosition;
    private String fieldGroupQualifier;
    private Integer fieldGroupPosition;
    private boolean searchField;

    // Draft administrative fields
    private boolean adminField;

    public FieldDefinition() {}

    public FieldDefinition(String name, String abapType, String label, boolean key) {
        this.name = name;
        this.abapName = toSnakeCase(name);
        this.abapType = abapType;
        this.label = label;
        this.key = key;
    }

    private static String toSnakeCase(String camelCase) {
        if (camelCase == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    // Standard draft admin fields factory
    public static FieldDefinition createdBy() {
        FieldDefinition f = new FieldDefinition("CreatedBy", "abap.char(12)", "Created By", false);
        f.setReadOnly(true);
        f.setAdminField(true);
        return f;
    }

    public static FieldDefinition createdAt() {
        FieldDefinition f = new FieldDefinition("CreatedAt", "abap.utclong", "Created At", false);
        f.setReadOnly(true);
        f.setAdminField(true);
        return f;
    }

    public static FieldDefinition lastChangedBy() {
        FieldDefinition f = new FieldDefinition("LastChangedBy", "abap.char(12)", "Last Changed By", false);
        f.setReadOnly(true);
        f.setAdminField(true);
        return f;
    }

    public static FieldDefinition lastChangedAt() {
        FieldDefinition f = new FieldDefinition("LastChangedAt", "abap.utclong", "Last Changed At", false);
        f.setReadOnly(true);
        f.setAdminField(true);
        return f;
    }

    public static FieldDefinition localLastChangedAt() {
        FieldDefinition f = new FieldDefinition("LocalLastChangedAt", "abap.utclong", "Local Last Changed At", false);
        f.setReadOnly(true);
        f.setAdminField(true);
        return f;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAbapName() { return abapName; }
    public void setAbapName(String abapName) { this.abapName = abapName; }

    public String getAbapType() { return abapType; }
    public void setAbapType(String abapType) { this.abapType = abapType; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public boolean isKey() { return key; }
    public void setKey(boolean key) { this.key = key; }

    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }

    public boolean isReadOnly() { return readOnly; }
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }

    public Integer getLineItemPosition() { return lineItemPosition; }
    public void setLineItemPosition(Integer lineItemPosition) { this.lineItemPosition = lineItemPosition; }

    public String getLineItemImportance() { return lineItemImportance; }
    public void setLineItemImportance(String lineItemImportance) { this.lineItemImportance = lineItemImportance; }

    public Integer getSelectionFieldPosition() { return selectionFieldPosition; }
    public void setSelectionFieldPosition(Integer selectionFieldPosition) { this.selectionFieldPosition = selectionFieldPosition; }

    public Integer getIdentificationPosition() { return identificationPosition; }
    public void setIdentificationPosition(Integer identificationPosition) { this.identificationPosition = identificationPosition; }

    public String getFieldGroupQualifier() { return fieldGroupQualifier; }
    public void setFieldGroupQualifier(String fieldGroupQualifier) { this.fieldGroupQualifier = fieldGroupQualifier; }

    public Integer getFieldGroupPosition() { return fieldGroupPosition; }
    public void setFieldGroupPosition(Integer fieldGroupPosition) { this.fieldGroupPosition = fieldGroupPosition; }

    public boolean isSearchField() { return searchField; }
    public void setSearchField(boolean searchField) { this.searchField = searchField; }

    public boolean isAdminField() { return adminField; }
    public void setAdminField(boolean adminField) { this.adminField = adminField; }
}
