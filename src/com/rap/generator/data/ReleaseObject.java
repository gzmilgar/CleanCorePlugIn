package com.rap.generator.data;

import java.util.List;

public class ReleaseObject {

    private String tadirObject;
    private String tadirObjName;
    private String objectType;
    private String objectKey;
    private String softwareComponent;
    private String applicationComponent;
    private String state;
    private String successorClassification;
    private String successorConceptName;
    private List<Successor> successors;

    public String getTadirObject() { return tadirObject; }
    public void setTadirObject(String tadirObject) { this.tadirObject = tadirObject; }

    public String getTadirObjName() { return tadirObjName; }
    public void setTadirObjName(String tadirObjName) { this.tadirObjName = tadirObjName; }

    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }

    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }

    public String getSoftwareComponent() { return softwareComponent; }
    public void setSoftwareComponent(String softwareComponent) { this.softwareComponent = softwareComponent; }

    public String getApplicationComponent() { return applicationComponent; }
    public void setApplicationComponent(String applicationComponent) { this.applicationComponent = applicationComponent; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getSuccessorClassification() { return successorClassification; }
    public void setSuccessorClassification(String successorClassification) { this.successorClassification = successorClassification; }

    public String getSuccessorConceptName() { return successorConceptName; }
    public void setSuccessorConceptName(String successorConceptName) { this.successorConceptName = successorConceptName; }

    public List<Successor> getSuccessors() { return successors; }
    public void setSuccessors(List<Successor> successors) { this.successors = successors; }

    public static class Successor {
        private String tadirObject;
        private String tadirObjName;
        private String objectType;
        private String objectKey;

        public String getTadirObject() { return tadirObject; }
        public void setTadirObject(String tadirObject) { this.tadirObject = tadirObject; }

        public String getTadirObjName() { return tadirObjName; }
        public void setTadirObjName(String tadirObjName) { this.tadirObjName = tadirObjName; }

        public String getObjectType() { return objectType; }
        public void setObjectType(String objectType) { this.objectType = objectType; }

        public String getObjectKey() { return objectKey; }
        public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    }
}
