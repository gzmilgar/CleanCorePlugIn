package com.rap.generator.generators;

import com.rap.generator.model.*;

public class ServiceBindingGenerator {

    public String generate(RapApplication app) {
        AbapCodeFormatter f = new AbapCodeFormatter();

        ServiceConfig service = app.getService();
        if (service == null) return "// Service configuration not set";

        f.line("Service Binding Configuration");
        f.line("=============================");
        f.line();
        f.line("Name:               " + service.getServiceBindingName());
        f.line("Service Definition:  " + service.getServiceName());
        f.line("Binding Type:        " + getBindingType(service));
        f.line("Description:         " + service.getDescription());
        f.line();
        f.line("Note: Service Binding is created via the ADT wizard.");
        f.line("Steps:");
        f.line("  1. Right-click the package -> New -> Other ABAP Repository Object");
        f.line("  2. Search for 'Service Binding'");
        f.line("  3. Enter the name: " + service.getServiceBindingName());
        f.line("  4. Select Service Definition: " + service.getServiceName());
        f.line("  5. Choose Binding Type: " + getBindingType(service));
        f.line("  6. Click 'Finish' and then 'Activate'");
        f.line("  7. Click 'Publish' to make the service available");

        return f.toString();
    }

    private String getBindingType(ServiceConfig service) {
        switch (service.getBindingType()) {
            case ODATA_V2: return "OData V2 - UI";
            case ODATA_V4: return "OData V4 - UI";
            default: return "OData V4 - UI";
        }
    }
}
