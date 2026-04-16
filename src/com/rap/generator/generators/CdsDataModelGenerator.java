package com.rap.generator.generators;

import com.rap.generator.model.*;
import java.util.List;

public class CdsDataModelGenerator {

    public String generate(RapApplication app, EntityDefinition entity) {
        AbapCodeFormatter f = new AbapCodeFormatter();

        f.annotation("@AccessControl.authorizationCheck: #NOT_REQUIRED");
        f.annotation("@EndUserText.label: '" + entity.getName() + "'");

        if (entity.isRoot()) {
            f.line("define root view entity " + entity.getObjectName());
        } else {
            f.line("define view entity " + entity.getObjectName());
        }

        f.indent();
        f.line("as select from " + entity.getUnderlyingSource());

        // Compositions to children
        List<EntityDefinition> children = app.getChildEntities(entity.getId());
        for (EntityDefinition child : children) {
            f.line("composition [0..*] of " + child.getObjectName() + " as _" + child.getName());
        }

        // Association to parent (for child entities)
        if (entity.getParentId() != null) {
            app.getEntityById(entity.getParentId()).ifPresent(parent -> {
                f.line("association to parent " + parent.getObjectName() + " as _" + parent.getName());
                f.indent();
                f.line("on $projection." + entity.getParentKeyField() + " = _" + parent.getName() + "." + getFirstKeyFieldName(parent));
                f.dedent();
            });
        }

        f.dedent();

        // Field list
        f.line("{");
        f.indent();

        List<FieldDefinition> fields = entity.getFields();
        for (int i = 0; i < fields.size(); i++) {
            FieldDefinition field = fields.get(i);
            StringBuilder line = new StringBuilder();

            if (field.isKey()) {
                line.append("key ");
            } else {
                line.append("    ");
            }

            // If underlying source is a table, use abapName as source and Name as alias
            if (entity.getSourceType() == EntityDefinition.SourceType.TABLE) {
                line.append(field.getAbapName());
                if (!field.getAbapName().equals(field.getName())) {
                    line.append(" as ").append(field.getName());
                }
            } else {
                // CDS source - use Name directly
                line.append(field.getName());
            }

            if (i < fields.size() - 1 || !children.isEmpty() || entity.getParentId() != null) {
                line.append(",");
            }

            f.line(line.toString());
        }

        // Publish associations
        if (!children.isEmpty() || entity.getParentId() != null) {
            f.line();
        }
        for (int i = 0; i < children.size(); i++) {
            EntityDefinition child = children.get(i);
            String line = "_" + child.getName();
            if (i < children.size() - 1 || entity.getParentId() != null) {
                line += ",";
            }
            f.line(line);
        }

        if (entity.getParentId() != null) {
            app.getEntityById(entity.getParentId()).ifPresent(parent -> {
                f.line("_" + parent.getName());
            });
        }

        f.dedent();
        f.line("}");

        return f.toString();
    }

    private String getFirstKeyFieldName(EntityDefinition entity) {
        for (FieldDefinition field : entity.getFields()) {
            if (field.isKey()) {
                return field.getName();
            }
        }
        return "ID";
    }
}
