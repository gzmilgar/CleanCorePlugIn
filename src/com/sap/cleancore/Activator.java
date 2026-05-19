package com.sap.cleancore;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "com.sap.cleancore";
    private static Activator plugin;

    public Activator() {
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        // Kick off background load of SAP Cloudification + api.sap.com data so
        // the user does not need to click "Sync" on every plug-in launch.
        try {
            com.sap.cleancore.analyzer.CleanCoreBootstrapper.runOnce();
        } catch (Throwable ignored) {
            // bootstrap must never block the UI
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    public static Activator getDefault() {
        return plugin;
    }

    public static ImageDescriptor getImageDescriptor(String path) {
        return imageDescriptorFromPlugin(PLUGIN_ID, path);
    }
}
