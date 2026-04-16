package com.rap.generator.generators;

import com.rap.generator.model.*;
import java.util.List;

public class BehaviorDefGenerator {

    public String generate(RapApplication app) {
        AbapCodeFormatter f = new AbapCodeFormatter();

        EntityDefinition root = app.getRootEntity().orElse(null);
        if (root == null) return "// No root entity defined";

        f.line("managed implementation in class " + root.getImplClassName() + " unique;");
        f.line("strict ( 2 );");

        if (app.isDraftEnabled()) {
            f.line("with draft;");
        }
        f.line();

        // Generate behavior for each entity
        generateEntityBehavior(f, app, root, true);

        for (EntityDefinition child : app.getChildEntities(root.getId())) {
            f.line();
            generateEntityBehavior(f, app, child, false);

            // Recursively handle grandchildren
            for (EntityDefinition grandchild : app.getChildEntities(child.getId())) {
                f.line();
                generateEntityBehavior(f, app, grandchild, false);
            }
        }

        return f.toString();
    }

    private void generateEntityBehavior(AbapCodeFormatter f, RapApplication app,
                                         EntityDefinition entity, boolean isRoot) {
        f.line("define behavior for " + entity.getObjectName() + " alias " + entity.getName());
        f.line("persistent table " + getTableName(entity));

        if (app.isDraftEnabled() && entity.isDraftEnabled()) {
            f.line("draft table " + entity.getDraftTableName());
        }

        if (isRoot) {
            if (app.isDraftEnabled()) {
                f.line("etag master LocalLastChangedAt");
                f.line("lock master total etag LastChangedAt");
            } else {
                // Use LocalLastChangedAt if available, otherwise skip etag
                if (hasField(entity, "LocalLastChangedAt")) {
                    f.line("etag master LocalLastChangedAt");
                }
                f.line("lock master");
            }
            f.line("authorization master ( global )");
        } else {
            if (app.isDraftEnabled()) {
                f.line("etag master LocalLastChangedAt");
            }
            app.getEntityById(entity.getParentId()).ifPresent(parent -> {
                f.line("lock dependent by _" + parent.getName());
            });
            f.line("authorization dependent by _" + getParentName(app, entity));
        }

        f.line("{");
        f.indent();

        // Read-only and mandatory fields
        List<FieldDefinition> readOnlyFields = getFieldsByAttribute(entity, true, false);
        List<FieldDefinition> mandatoryFields = getFieldsByAttribute(entity, false, true);

        if (!readOnlyFields.isEmpty()) {
            StringBuilder ro = new StringBuilder("field ( readonly ) ");
            for (int i = 0; i < readOnlyFields.size(); i++) {
                if (i > 0) ro.append(", ");
                ro.append(readOnlyFields.get(i).getName());
            }
            ro.append(";");
            f.line(ro.toString());
        }

        if (!mandatoryFields.isEmpty()) {
            StringBuilder m = new StringBuilder("field ( mandatory ) ");
            for (int i = 0; i < mandatoryFields.size(); i++) {
                if (i > 0) m.append(", ");
                m.append(mandatoryFields.get(i).getName());
            }
            m.append(";");
            f.line(m.toString());
        }

        f.line();

        // CRUD operations
        f.line("create;");
        f.line("update;");
        f.line("delete;");

        // Draft operations
        if (app.isDraftEnabled() && isRoot) {
            f.line();
            f.line("draft action Edit;");
            f.line("draft action Activate optimized;");
            f.line("draft action Discard;");
            f.line("draft action Resume;");
            f.line("draft determine action Prepare;");
        }

        // Actions
        List<ActionDefinition> actions = app.getActionsForEntity(entity.getId());
        if (!actions.isEmpty()) {
            f.line();
            for (ActionDefinition action : actions) {
                StringBuilder actionLine = new StringBuilder();
                if (action.isStatic()) {
                    actionLine.append("static action ");
                } else if (action.isHasFeatureControl()) {
                    actionLine.append("action ( features : instance ) ");
                } else {
                    actionLine.append("action ");
                }
                actionLine.append(action.getName());
                actionLine.append(" result [1] $self;");
                f.line(actionLine.toString());
            }
        }

        // Associations to children
        List<EntityDefinition> children = app.getChildEntities(entity.getId());
        if (!children.isEmpty()) {
            f.line();
            for (EntityDefinition child : children) {
                StringBuilder assoc = new StringBuilder("association _" + child.getName());
                assoc.append(" { create;");
                if (app.isDraftEnabled()) {
                    assoc.append(" with draft;");
                }
                assoc.append(" }");
                f.line(assoc.toString());
            }
        }

        f.dedent();
        f.line("}");
    }

    private String getTableName(EntityDefinition entity) {
        // Convention: ZI_TRAVEL -> ZTRAVEL or the underlying source
        if (entity.getSourceType() == EntityDefinition.SourceType.TABLE) {
            return entity.getUnderlyingSource();
        }
        return entity.getUnderlyingSource();
    }

    private String getParentName(RapApplication app, EntityDefinition entity) {
        return app.getEntityById(entity.getParentId())
                  .map(EntityDefinition::getName)
                  .orElse("Parent");
    }

    private boolean hasField(EntityDefinition entity, String fieldName) {
        return entity.getFields().stream().anyMatch(f -> f.getName().equals(fieldName));
    }

    private List<FieldDefinition> getFieldsByAttribute(EntityDefinition entity,
                                                        boolean readOnly, boolean mandatory) {
        return entity.getFields().stream()
            .filter(f -> readOnly ? f.isReadOnly() : f.isMandatory())
            .collect(java.util.stream.Collectors.toList());
    }
}
