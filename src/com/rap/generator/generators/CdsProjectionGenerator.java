package com.rap.generator.generators;

import com.rap.generator.model.*;
import java.util.List;

public class CdsProjectionGenerator {

    public String generate(RapApplication app, EntityDefinition entity) {
        AbapCodeFormatter f = new AbapCodeFormatter();

        f.annotation("@AccessControl.authorizationCheck: #NOT_REQUIRED");
        f.annotation("@EndUserText.label: '" + entity.getName() + "'");

        // Search annotations
        boolean hasSearchFields = entity.getFields().stream().anyMatch(FieldDefinition::isSearchField);
        if (hasSearchFields) {
            f.annotation("@Search.searchable: true");
        }

        if (entity.isRoot()) {
            f.line("define root view entity " + entity.getProjectionName());
        } else {
            f.line("define view entity " + entity.getProjectionName());
        }

        f.indent();
        f.line("provider contract transactional_query");
        f.line("as projection on " + entity.getObjectName());
        f.dedent();

        // Field list
        f.line("{");
        f.indent();

        List<FieldDefinition> fields = entity.getFields();
        List<EntityDefinition> children = app.getChildEntities(entity.getId());

        for (int i = 0; i < fields.size(); i++) {
            FieldDefinition field = fields.get(i);
            StringBuilder line = new StringBuilder();

            // Search annotation inline
            if (field.isSearchField()) {
                f.annotation("@Search.defaultSearchElement: true");
            }

            if (field.isKey()) {
                line.append("key ");
            } else {
                line.append("    ");
            }

            line.append(field.getName());

            if (i < fields.size() - 1 || !children.isEmpty() || entity.getParentId() != null) {
                line.append(",");
            }

            f.line(line.toString());
        }

        // Redirected associations
        if (!children.isEmpty() || entity.getParentId() != null) {
            f.line();
        }

        for (int i = 0; i < children.size(); i++) {
            EntityDefinition child = children.get(i);
            String line = "_" + child.getName() + " : redirected to composition child " + child.getProjectionName();
            if (i < children.size() - 1 || entity.getParentId() != null) {
                line += ",";
            }
            f.line(line);
        }

        if (entity.getParentId() != null) {
            app.getEntityById(entity.getParentId()).ifPresent(parent -> {
                f.line("_" + parent.getName() + " : redirected to parent " + parent.getProjectionName());
            });
        }

        f.dedent();
        f.line("}");

        return f.toString();
    }
}
