package com.rap.generator.model;

/**
 * Additional UI annotation configuration for complex scenarios.
 * Used when field-level annotations in FieldDefinition are not sufficient.
 */
public class UiAnnotationConfig {

    // Header info
    private String typeName;
    private String typeNamePlural;
    private String titleField;
    private String descriptionField;

    // Object page header facet
    private String headerImageUrl;
    private boolean showHeaderDataPoints;

    public UiAnnotationConfig() {}

    public UiAnnotationConfig(String typeName) {
        this.typeName = typeName;
        this.typeNamePlural = typeName + "s";
    }

    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }

    public String getTypeNamePlural() { return typeNamePlural; }
    public void setTypeNamePlural(String typeNamePlural) { this.typeNamePlural = typeNamePlural; }

    public String getTitleField() { return titleField; }
    public void setTitleField(String titleField) { this.titleField = titleField; }

    public String getDescriptionField() { return descriptionField; }
    public void setDescriptionField(String descriptionField) { this.descriptionField = descriptionField; }

    public String getHeaderImageUrl() { return headerImageUrl; }
    public void setHeaderImageUrl(String headerImageUrl) { this.headerImageUrl = headerImageUrl; }

    public boolean isShowHeaderDataPoints() { return showHeaderDataPoints; }
    public void setShowHeaderDataPoints(boolean showHeaderDataPoints) { this.showHeaderDataPoints = showHeaderDataPoints; }
}
