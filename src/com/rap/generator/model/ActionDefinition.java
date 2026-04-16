package com.rap.generator.model;

import java.util.UUID;

public class ActionDefinition {

    public enum ActionPlacement {
        LIST, OBJECT_PAGE, BOTH
    }

    private String id;
    private String name;
    private String label;
    private String entityId;
    private ActionPlacement placement = ActionPlacement.BOTH;
    private boolean hasFeatureControl;
    private String featureControlField;
    private String featureControlOperator = "NE";
    private String featureControlValue;
    private boolean isStatic;

    public ActionDefinition() {
        this.id = UUID.randomUUID().toString();
    }

    public ActionDefinition(String name, String label, String entityId) {
        this();
        this.name = name;
        this.label = label;
        this.entityId = entityId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public ActionPlacement getPlacement() { return placement; }
    public void setPlacement(ActionPlacement placement) { this.placement = placement; }

    public boolean isHasFeatureControl() { return hasFeatureControl; }
    public void setHasFeatureControl(boolean hasFeatureControl) { this.hasFeatureControl = hasFeatureControl; }

    public String getFeatureControlField() { return featureControlField; }
    public void setFeatureControlField(String featureControlField) { this.featureControlField = featureControlField; }

    public String getFeatureControlOperator() { return featureControlOperator; }
    public void setFeatureControlOperator(String featureControlOperator) { this.featureControlOperator = featureControlOperator; }

    public String getFeatureControlValue() { return featureControlValue; }
    public void setFeatureControlValue(String featureControlValue) { this.featureControlValue = featureControlValue; }

    public boolean isStatic() { return isStatic; }
    public void setStatic(boolean isStatic) { this.isStatic = isStatic; }
}
