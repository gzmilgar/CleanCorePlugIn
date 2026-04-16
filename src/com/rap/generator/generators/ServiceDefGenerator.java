package com.rap.generator.generators;

import com.rap.generator.model.*;

public class ServiceDefGenerator {

    public String generate(RapApplication app) {
        AbapCodeFormatter f = new AbapCodeFormatter();

        ServiceConfig service = app.getService();
        if (service == null || service.getServiceName() == null) {
            return "// Service configuration not set";
        }

        f.annotation("@EndUserText.label: '" + service.getDescription() + "'");
        f.line("define service " + service.getServiceName() + " {");
        f.indent();

        // Expose all projection views
        for (EntityDefinition entity : app.getEntities()) {
            f.line("expose " + entity.getProjectionName() + " as " + entity.getName() + ";");
        }

        f.dedent();
        f.line("}");

        return f.toString();
    }
}
