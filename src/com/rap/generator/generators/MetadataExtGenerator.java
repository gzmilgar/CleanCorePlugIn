package com.rap.generator.generators;

import com.rap.generator.model.*;
import com.rap.generator.model.ActionDefinition.ActionPlacement;
import java.util.List;
import java.util.stream.Collectors;

public class MetadataExtGenerator {

    public String generate(RapApplication app, EntityDefinition entity) {
        AbapCodeFormatter f = new AbapCodeFormatter();

        f.annotation("@Metadata.layer: #CUSTOMER");
        f.annotation("@UI: {");
        f.line("  headerInfo: {");
        f.line("    typeName: '" + entity.getName() + "',");

        String titleField = findTitleField(entity);
        if (titleField != null) {
            f.line("    typeNamePlural: '" + entity.getName() + "s',");
            f.line("    title: { type: #STANDARD, value: '" + titleField + "' }");
        } else {
            f.line("    typeNamePlural: '" + entity.getName() + "s'");
        }

        f.line("  }");
        f.line("}");

        f.line("annotate entity " + entity.getProjectionName() + " with");
        f.line("{");
        f.indent();

        // Facets
        generateFacets(f, app, entity);
        f.line();

        // Field annotations
        List<ActionDefinition> actions = app.getActionsForEntity(entity.getId());
        boolean firstField = true;

        for (FieldDefinition field : entity.getFields()) {
            if (field.isAdminField()) continue; // Skip admin fields in UI annotations

            boolean hasAnnotation = false;
            StringBuilder annotations = new StringBuilder();

            // @UI.lineItem
            if (field.getLineItemPosition() != null) {
                annotations.append("  @UI.lineItem: [");

                // Add action buttons on the first field
                if (firstField && !actions.isEmpty()) {
                    annotations.append("\n");
                    for (ActionDefinition action : actions) {
                        if (action.getPlacement() == ActionPlacement.LIST || action.getPlacement() == ActionPlacement.BOTH) {
                            annotations.append("    { type: #FOR_ACTION, dataAction: '")
                                       .append(action.getName())
                                       .append("', label: '")
                                       .append(action.getLabel())
                                       .append("' },\n");
                        }
                    }
                    annotations.append("    { position: ")
                               .append(field.getLineItemPosition());
                } else {
                    annotations.append("{ position: ").append(field.getLineItemPosition());
                }

                if (field.getLineItemImportance() != null) {
                    annotations.append(", importance: #").append(field.getLineItemImportance());
                }
                if (field.getLabel() != null) {
                    annotations.append(", label: '").append(field.getLabel()).append("'");
                }
                annotations.append(" }]\n");
                hasAnnotation = true;
            }

            // @UI.selectionField
            if (field.getSelectionFieldPosition() != null) {
                annotations.append("  @UI.selectionField: [{ position: ")
                           .append(field.getSelectionFieldPosition())
                           .append(" }]\n");
                hasAnnotation = true;
            }

            // @UI.identification
            if (field.getIdentificationPosition() != null) {
                annotations.append("  @UI.identification: [");

                // Add action buttons on identification for first field
                if (firstField && !actions.isEmpty()) {
                    annotations.append("\n");
                    for (ActionDefinition action : actions) {
                        if (action.getPlacement() == ActionPlacement.OBJECT_PAGE || action.getPlacement() == ActionPlacement.BOTH) {
                            annotations.append("    { type: #FOR_ACTION, dataAction: '")
                                       .append(action.getName())
                                       .append("', label: '")
                                       .append(action.getLabel())
                                       .append("' },\n");
                        }
                    }
                    annotations.append("    { position: ").append(field.getIdentificationPosition()).append(" }]\n");
                } else {
                    annotations.append("{ position: ").append(field.getIdentificationPosition()).append(" }]\n");
                }
                hasAnnotation = true;
            }

            // @UI.fieldGroup
            if (field.getFieldGroupQualifier() != null && field.getFieldGroupPosition() != null) {
                annotations.append("  @UI.fieldGroup: [{ qualifier: '")
                           .append(field.getFieldGroupQualifier())
                           .append("', position: ")
                           .append(field.getFieldGroupPosition())
                           .append(" }]\n");
                hasAnnotation = true;
            }

            if (hasAnnotation) {
                f.append(annotations.toString());
                f.line(field.getName() + ";");
                f.line();
                firstField = false;
            }
        }

        f.dedent();
        f.line("}");

        return f.toString();
    }

    private void generateFacets(AbapCodeFormatter f, RapApplication app, EntityDefinition entity) {
        f.line("@UI.facet: [");
        f.indent();

        // Header facet - identification
        f.line("{ id: '" + entity.getName() + "',");
        f.line("  purpose: #STANDARD,");
        f.line("  type: #IDENTIFICATION_REFERENCE,");
        f.line("  label: '" + entity.getName() + " Details',");
        f.line("  position: 10 },");

        // Field group facets
        List<String> fieldGroups = entity.getFields().stream()
            .filter(fd -> fd.getFieldGroupQualifier() != null)
            .map(FieldDefinition::getFieldGroupQualifier)
            .distinct()
            .collect(Collectors.toList());

        int position = 20;
        for (String group : fieldGroups) {
            f.line("{ id: '" + group + "',");
            f.line("  purpose: #STANDARD,");
            f.line("  type: #FIELDGROUP_REFERENCE,");
            f.line("  targetQualifier: '" + group + "',");
            f.line("  label: '" + group + "',");
            f.line("  position: " + position + " },");
            position += 10;
        }

        // Child entity facets (lineItem reference)
        List<EntityDefinition> children = app.getChildEntities(entity.getId());
        for (int i = 0; i < children.size(); i++) {
            EntityDefinition child = children.get(i);
            String label = child.getFacetLabel() != null ? child.getFacetLabel() : child.getName() + "s";
            f.line("{ id: '" + child.getName() + "',");
            f.line("  purpose: #STANDARD,");
            f.line("  type: #LINEITEM_REFERENCE,");
            f.line("  label: '" + label + "',");
            f.line("  position: " + position + ",");
            f.line("  targetElement: '_" + child.getName() + "' }");
            if (i < children.size() - 1) {
                f.append(",\n");
            }
            position += 10;
        }

        // Remove trailing comma from last facet if no children
        f.dedent();
        f.line("]");
    }

    private String findTitleField(EntityDefinition entity) {
        // Find a good title field - first non-key, non-admin field
        for (FieldDefinition field : entity.getFields()) {
            if (!field.isKey() && !field.isAdminField() && field.getIdentificationPosition() != null) {
                return field.getName();
            }
        }
        // Fallback to first key field
        for (FieldDefinition field : entity.getFields()) {
            if (field.isKey()) return field.getName();
        }
        return null;
    }
}
