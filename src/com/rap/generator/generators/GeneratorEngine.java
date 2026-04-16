package com.rap.generator.generators;

import com.rap.generator.model.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Orchestrator that runs all generators and returns a map of
 * artifact name -> generated source code.
 */
public class GeneratorEngine {

    private final CdsDataModelGenerator cdsDataModel = new CdsDataModelGenerator();
    private final CdsProjectionGenerator cdsProjection = new CdsProjectionGenerator();
    private final MetadataExtGenerator metadataExt = new MetadataExtGenerator();
    private final BehaviorDefGenerator behaviorDef = new BehaviorDefGenerator();
    private final BehaviorImplGenerator behaviorImpl = new BehaviorImplGenerator();
    private final ServiceDefGenerator serviceDef = new ServiceDefGenerator();
    private final ServiceBindingGenerator serviceBinding = new ServiceBindingGenerator();

    /**
     * Generate all RAP artifacts.
     * @return Map of artifact display name -> generated source code
     */
    public Map<String, GeneratedArtifact> generateAll(RapApplication app) {
        Map<String, GeneratedArtifact> artifacts = new LinkedHashMap<>();

        // CDS Data Model views (one per entity)
        for (EntityDefinition entity : app.getEntities()) {
            String code = cdsDataModel.generate(app, entity);
            artifacts.put(entity.getObjectName() + " (Data Model)",
                new GeneratedArtifact(entity.getObjectName(), "DDLS", code,
                    entity.getObjectName() + ".ddls.asddls"));
        }

        // CDS Projection views (one per entity)
        for (EntityDefinition entity : app.getEntities()) {
            String code = cdsProjection.generate(app, entity);
            artifacts.put(entity.getProjectionName() + " (Projection)",
                new GeneratedArtifact(entity.getProjectionName(), "DDLS", code,
                    entity.getProjectionName() + ".ddls.asddls"));
        }

        // Metadata Extensions (one per entity)
        for (EntityDefinition entity : app.getEntities()) {
            String code = metadataExt.generate(app, entity);
            artifacts.put(entity.getMetadataExtName() + " (Metadata Ext)",
                new GeneratedArtifact("ME_" + entity.getProjectionName(), "DDLX", code,
                    "ME_" + entity.getProjectionName() + ".ddlx.asddlxs"));
        }

        // Behavior Definition (one for the whole tree)
        String bdefCode = behaviorDef.generate(app);
        EntityDefinition root = app.getRootEntity().orElse(null);
        if (root != null) {
            artifacts.put(root.getObjectName() + " (Behavior Def)",
                new GeneratedArtifact(root.getObjectName(), "BDEF", bdefCode,
                    root.getObjectName() + ".bdef.asbdef"));
        }

        // Behavior Implementation (one class)
        String implCode = behaviorImpl.generate(app);
        if (root != null) {
            artifacts.put(root.getImplClassName() + " (Behavior Impl)",
                new GeneratedArtifact(root.getImplClassName(), "CLAS", implCode,
                    root.getImplClassName() + ".clas.abap"));
        }

        // Service Definition
        String svcDefCode = serviceDef.generate(app);
        if (app.getService() != null && app.getService().getServiceName() != null) {
            artifacts.put(app.getService().getServiceName() + " (Service Def)",
                new GeneratedArtifact(app.getService().getServiceName(), "SRVD", svcDefCode,
                    app.getService().getServiceName() + ".srvd.srvdsrv"));
        }

        // Service Binding (instructions)
        String svcBindCode = serviceBinding.generate(app);
        if (app.getService() != null && app.getService().getServiceBindingName() != null) {
            artifacts.put(app.getService().getServiceBindingName() + " (Service Binding)",
                new GeneratedArtifact(app.getService().getServiceBindingName(), "SRVB", svcBindCode,
                    app.getService().getServiceBindingName() + ".txt"));
        }

        return artifacts;
    }

    /**
     * Represents a single generated artifact with metadata.
     */
    public static class GeneratedArtifact {
        private final String objectName;
        private final String objectType;
        private final String sourceCode;
        private final String fileName;

        public GeneratedArtifact(String objectName, String objectType, String sourceCode, String fileName) {
            this.objectName = objectName;
            this.objectType = objectType;
            this.sourceCode = sourceCode;
            this.fileName = fileName;
        }

        public String getObjectName() { return objectName; }
        public String getObjectType() { return objectType; }
        public String getSourceCode() { return sourceCode; }
        public String getFileName() { return fileName; }
    }
}
