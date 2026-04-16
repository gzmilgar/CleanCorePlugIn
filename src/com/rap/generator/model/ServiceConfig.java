package com.rap.generator.model;

public class ServiceConfig {

    public enum BindingType {
        ODATA_V2("OData V2 - UI"),
        ODATA_V4("OData V4 - UI");

        private final String label;

        BindingType(String label) {
            this.label = label;
        }

        public String getLabel() { return label; }
    }

    private String serviceName;
    private String serviceBindingName;
    private String description = "RAP Service";
    private BindingType bindingType = BindingType.ODATA_V4;

    public ServiceConfig() {}

    public void generateNames(String prefix, String entityName) {
        String upper = entityName.toUpperCase().replace(" ", "_");
        this.serviceName = prefix + "UI_" + upper;
        String suffix = bindingType == BindingType.ODATA_V4 ? "_O4" : "_O2";
        this.serviceBindingName = prefix + "UI_" + upper + suffix;
    }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getServiceBindingName() { return serviceBindingName; }
    public void setServiceBindingName(String serviceBindingName) { this.serviceBindingName = serviceBindingName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BindingType getBindingType() { return bindingType; }
    public void setBindingType(BindingType bindingType) { this.bindingType = bindingType; }
}
