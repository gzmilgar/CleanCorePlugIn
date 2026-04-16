package com.rap.generator.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EntityDefinition {

    public enum SourceType {
        CDS, TABLE
    }

    private String id;
    private String name;
    private String objectName;
    private String projectionName;
    private String metadataExtName;
    private String underlyingSource;
    private SourceType sourceType = SourceType.TABLE;
    private boolean root;
    private boolean draftEnabled;
    private List<FieldDefinition> fields = new ArrayList<>();
    private String parentId;
    private String parentKeyField;
    private List<String> childIds = new ArrayList<>();
    private String facetLabel;

    public EntityDefinition() {
        this.id = UUID.randomUUID().toString();
    }

    public EntityDefinition(String name) {
        this();
        this.name = name;
    }

    // Auto-generate object names from prefix and entity name
    public void generateObjectNames(String prefix) {
        String upper = name.toUpperCase().replace(" ", "_");
        this.objectName = prefix + "I_" + upper;
        this.projectionName = prefix + "C_" + upper;
        this.metadataExtName = prefix + "C_" + upper; // metadata ext annotates the projection
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getObjectName() { return objectName; }
    public void setObjectName(String objectName) { this.objectName = objectName; }

    public String getProjectionName() { return projectionName; }
    public void setProjectionName(String projectionName) { this.projectionName = projectionName; }

    public String getMetadataExtName() { return metadataExtName; }
    public void setMetadataExtName(String metadataExtName) { this.metadataExtName = metadataExtName; }

    public String getUnderlyingSource() { return underlyingSource; }
    public void setUnderlyingSource(String underlyingSource) { this.underlyingSource = underlyingSource; }

    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }

    public boolean isRoot() { return root; }
    public void setRoot(boolean root) { this.root = root; }

    public boolean isDraftEnabled() { return draftEnabled; }
    public void setDraftEnabled(boolean draftEnabled) { this.draftEnabled = draftEnabled; }

    public List<FieldDefinition> getFields() { return fields; }
    public void setFields(List<FieldDefinition> fields) { this.fields = fields; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getParentKeyField() { return parentKeyField; }
    public void setParentKeyField(String parentKeyField) { this.parentKeyField = parentKeyField; }

    public List<String> getChildIds() { return childIds; }
    public void setChildIds(List<String> childIds) { this.childIds = childIds; }

    public String getFacetLabel() { return facetLabel; }
    public void setFacetLabel(String facetLabel) { this.facetLabel = facetLabel; }

    public void addField(FieldDefinition field) {
        fields.add(field);
    }

    public void removeField(String fieldName) {
        fields.removeIf(f -> f.getName().equals(fieldName));
    }

    public List<FieldDefinition> getKeyFields() {
        List<FieldDefinition> keys = new ArrayList<>();
        for (FieldDefinition f : fields) {
            if (f.isKey()) keys.add(f);
        }
        return keys;
    }

    public void addChild(String childId) {
        if (!childIds.contains(childId)) {
            childIds.add(childId);
        }
    }

    public String getImplClassName() {
        return "ZBP_" + name.toUpperCase().replace(" ", "_");
    }

    public String getDraftTableName() {
        return objectName.replace("ZI_", "ZD_");
    }

    @Override
    public String toString() {
        return name + (root ? " (root)" : "");
    }
}
