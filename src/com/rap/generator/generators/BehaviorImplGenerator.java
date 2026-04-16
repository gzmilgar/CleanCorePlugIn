package com.rap.generator.generators;

import com.rap.generator.model.*;
import java.util.List;

public class BehaviorImplGenerator {

    public String generate(RapApplication app) {
        AbapCodeFormatter f = new AbapCodeFormatter();

        EntityDefinition root = app.getRootEntity().orElse(null);
        if (root == null) return "\" No root entity defined";

        String className = root.getImplClassName();

        // Class definition
        f.line("CLASS " + className + " DEFINITION PUBLIC ABSTRACT FINAL");
        f.line("  FOR BEHAVIOR OF " + root.getObjectName() + ".");
        f.line("ENDCLASS.");
        f.line();
        f.line("CLASS " + className + " IMPLEMENTATION.");
        f.line("ENDCLASS.");

        // Generate local handler classes for each entity with actions
        f.line();
        f.line("*----------------------------------------------------------------------*");
        f.line("* Local handler classes");
        f.line("*----------------------------------------------------------------------*");

        for (EntityDefinition entity : app.getEntities()) {
            List<ActionDefinition> actions = app.getActionsForEntity(entity.getId());
            boolean hasFeatureControl = actions.stream().anyMatch(ActionDefinition::isHasFeatureControl);

            if (!actions.isEmpty() || hasFeatureControl) {
                f.line();
                generateHandlerClass(f, app, entity, actions, hasFeatureControl);
            }
        }

        return f.toString();
    }

    private void generateHandlerClass(AbapCodeFormatter f, RapApplication app,
                                       EntityDefinition entity, List<ActionDefinition> actions,
                                       boolean hasFeatureControl) {
        String handlerName = "lhc_" + entity.getName().toLowerCase();
        EntityDefinition root = app.getRootEntity().orElse(entity);

        f.line("CLASS " + handlerName + " DEFINITION INHERITING FROM cl_abap_behavior_handler.");
        f.line("  PRIVATE SECTION.");

        // Action method declarations
        for (ActionDefinition action : actions) {
            f.line("    METHODS " + action.getName() + " FOR MODIFY");
            f.line("      IMPORTING keys FOR ACTION " + entity.getName() + "~" + action.getName() + " RESULT result.");
        }

        // Feature control method
        if (hasFeatureControl) {
            f.line("    METHODS get_instance_features FOR INSTANCE FEATURES");
            f.line("      IMPORTING keys REQUEST requested_features FOR " + entity.getName() + " RESULT result.");
        }

        f.line("ENDCLASS.");
        f.line();
        f.line("CLASS " + handlerName + " IMPLEMENTATION.");

        // Action method implementations
        for (ActionDefinition action : actions) {
            f.line();
            generateActionMethod(f, root, entity, action);
        }

        // Feature control implementation
        if (hasFeatureControl) {
            f.line();
            generateFeatureControlMethod(f, root, entity, actions);
        }

        f.line("ENDCLASS.");
    }

    private void generateActionMethod(AbapCodeFormatter f, EntityDefinition root,
                                       EntityDefinition entity, ActionDefinition action) {
        f.line("  METHOD " + action.getName() + ".");
        f.line();
        f.line("    \" Read relevant " + entity.getName() + " instances");
        f.line("    READ ENTITIES OF " + root.getObjectName() + " IN LOCAL MODE");
        f.line("      ENTITY " + entity.getName());
        f.line("        ALL FIELDS WITH CORRESPONDING #( keys )");
        f.line("      RESULT DATA(lt_" + entity.getName().toLowerCase() + ").");
        f.line();
        f.line("    \" TODO: Implement business logic for action '" + action.getName() + "'");
        f.line("    LOOP AT lt_" + entity.getName().toLowerCase() + " ASSIGNING FIELD-SYMBOL(<entity>).");
        f.line("      \" Example: <entity>-Status = 'NEW_STATUS'.");
        f.line("    ENDLOOP.");
        f.line();
        f.line("    \" Modify entities with updated data");
        f.line("    MODIFY ENTITIES OF " + root.getObjectName() + " IN LOCAL MODE");
        f.line("      ENTITY " + entity.getName());
        f.line("        UPDATE FIELDS ( " + getUpdateFieldsPlaceholder(entity) + " )");
        f.line("        WITH VALUE #( FOR entity IN lt_" + entity.getName().toLowerCase() + "");
        f.line("          ( %tky = entity-%tky");
        f.line("            \" TODO: Set field values here");
        f.line("          ) )");
        f.line("      FAILED failed");
        f.line("      REPORTED reported.");
        f.line();
        f.line("    \" Fill the result");
        f.line("    result = VALUE #( FOR entity IN lt_" + entity.getName().toLowerCase() + "");
        f.line("      ( %tky = entity-%tky");
        f.line("        %param = entity ) ).");
        f.line();
        f.line("  ENDMETHOD.");
    }

    private void generateFeatureControlMethod(AbapCodeFormatter f, EntityDefinition root,
                                                EntityDefinition entity, List<ActionDefinition> actions) {
        f.line("  METHOD get_instance_features.");
        f.line();
        f.line("    \" Read relevant " + entity.getName() + " instances");
        f.line("    READ ENTITIES OF " + root.getObjectName() + " IN LOCAL MODE");
        f.line("      ENTITY " + entity.getName());
        f.line("        ALL FIELDS WITH CORRESPONDING #( keys )");
        f.line("      RESULT DATA(lt_" + entity.getName().toLowerCase() + ").");
        f.line();
        f.line("    result = VALUE #( FOR entity IN lt_" + entity.getName().toLowerCase() + "");
        f.line("      ( %tky = entity-%tky");

        for (ActionDefinition action : actions) {
            if (action.isHasFeatureControl() && action.getFeatureControlField() != null) {
                f.line("        %action-" + action.getName() + " = COND #(");
                f.line("          WHEN entity-" + action.getFeatureControlField()
                     + " " + action.getFeatureControlOperator() + " '"
                     + (action.getFeatureControlValue() != null ? action.getFeatureControlValue() : "")
                     + "'");
                f.line("          THEN if_abap_behv=>fc-o-enabled");
                f.line("          ELSE if_abap_behv=>fc-o-disabled )");
            }
        }

        f.line("      ) ).");
        f.line();
        f.line("  ENDMETHOD.");
    }

    private String getUpdateFieldsPlaceholder(EntityDefinition entity) {
        // Return first non-key, non-admin field as placeholder
        for (FieldDefinition field : entity.getFields()) {
            if (!field.isKey() && !field.isAdminField()) {
                return field.getName();
            }
        }
        return "Status";
    }
}
