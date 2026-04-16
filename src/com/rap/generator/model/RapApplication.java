package com.rap.generator.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RapApplication {

    private String prefix = "Z";
    private List<EntityDefinition> entities = new ArrayList<>();
    private List<ActionDefinition> actions = new ArrayList<>();
    private ServiceConfig service = new ServiceConfig();
    private boolean draftEnabled = false;

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }

    public List<EntityDefinition> getEntities() { return entities; }
    public void setEntities(List<EntityDefinition> entities) { this.entities = entities; }

    public List<ActionDefinition> getActions() { return actions; }
    public void setActions(List<ActionDefinition> actions) { this.actions = actions; }

    public ServiceConfig getService() { return service; }
    public void setService(ServiceConfig service) { this.service = service; }

    public boolean isDraftEnabled() { return draftEnabled; }
    public void setDraftEnabled(boolean draftEnabled) { this.draftEnabled = draftEnabled; }

    public void addEntity(EntityDefinition entity) {
        entities.add(entity);
    }

    public void removeEntity(String entityId) {
        entities.removeIf(e -> e.getId().equals(entityId));
        actions.removeIf(a -> a.getEntityId().equals(entityId));
        // Remove parent references
        for (EntityDefinition e : entities) {
            e.getChildIds().remove(entityId);
            if (entityId.equals(e.getParentId())) {
                e.setParentId(null);
                e.setParentKeyField(null);
            }
        }
    }

    public Optional<EntityDefinition> getEntityById(String id) {
        return entities.stream().filter(e -> e.getId().equals(id)).findFirst();
    }

    public Optional<EntityDefinition> getRootEntity() {
        return entities.stream().filter(EntityDefinition::isRoot).findFirst();
    }

    public List<EntityDefinition> getChildEntities(String parentId) {
        List<EntityDefinition> children = new ArrayList<>();
        for (EntityDefinition e : entities) {
            if (parentId.equals(e.getParentId())) {
                children.add(e);
            }
        }
        return children;
    }

    public List<ActionDefinition> getActionsForEntity(String entityId) {
        List<ActionDefinition> result = new ArrayList<>();
        for (ActionDefinition a : actions) {
            if (a.getEntityId().equals(entityId)) {
                result.add(a);
            }
        }
        return result;
    }

    public void addAction(ActionDefinition action) {
        actions.add(action);
    }

    public void removeAction(String actionId) {
        actions.removeIf(a -> a.getId().equals(actionId));
    }
}
