package com.sap.cleancore.analyzer.handlers;

import com.sap.cleancore.analyzer.analyzers.ObsoleteApiDetector;
import com.sap.cleancore.analyzer.analyzers.StaticAbapAnalyzer;
import com.sap.cleancore.analyzer.mapping.MappingRepository;
import com.sap.cleancore.analyzer.model.Finding;
import com.sap.cleancore.analyzer.ui.CleanCoreAnalyzerView;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for "Clean Core → Analyze Current File" (Ctrl+Shift+K).
 *
 * Reads the active ABAP source editor, runs StaticAbapAnalyzer +
 * ObsoleteApiDetector on its content, and pushes the resulting Findings into
 * the Clean Core Analyzer view's "Current File" tab.
 *
 * Editor extraction uses a 5-tier fallback chain so it works with ADT's
 * AbapSourceEditor as well as the generic Eclipse ITextEditor.
 */
public class AnalyzeCurrentFileHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IEditorPart editor = HandlerUtil.getActiveEditor(event);
        if (editor == null) {
            MessageDialog.openInformation(HandlerUtil.getActiveShell(event),
                    "Clean Core — Analyze Current File",
                    "Open an ABAP source file in the editor first, then run this command.");
            return null;
        }

        String source = extractSource(editor);
        if (source == null || source.trim().isEmpty()) {
            MessageDialog.openWarning(HandlerUtil.getActiveShell(event),
                    "Clean Core — Analyze Current File",
                    "Could not read the editor contents. The active editor may not be a text editor.");
            return null;
        }

        // Make sure mappings are loaded (CleanCoreBootstrapper usually has by now).
        MappingRepository.getInstance().loadIfNeeded();

        // Run analyzers
        List<Finding> findings = new ArrayList<>();
        findings.addAll(new StaticAbapAnalyzer().analyze(source));
        findings.addAll(new ObsoleteApiDetector().analyze(source).findings);

        // Push to Clean Core Analyzer view
        String fileName = editorTitle(editor);
        try {
            IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
            CleanCoreAnalyzerView view = (CleanCoreAnalyzerView)
                    page.showView(CleanCoreAnalyzerView.ID);
            view.showCurrentFileFindings(fileName, findings);
        } catch (Exception e) {
            MessageDialog.openError(HandlerUtil.getActiveShell(event),
                    "Clean Core — Analyze Current File",
                    "Found " + findings.size() + " issue(s) but could not show the view:\n"
                            + e.getMessage());
        }
        return null;
    }

    private String editorTitle(IEditorPart editor) {
        IEditorInput in = editor.getEditorInput();
        if (in != null && in.getName() != null) return in.getName();
        return editor.getTitle();
    }

    /**
     * Five-tier fallback for getting an IDocument out of an arbitrary editor.
     * Adapted from the reference plug-in's AnalyzeHandler.
     */
    private String extractSource(IEditorPart editor) {
        // Tier 1: ITextEditor + DocumentProvider
        try {
            if (editor instanceof ITextEditor) {
                ITextEditor te = (ITextEditor) editor;
                IDocumentProvider dp = te.getDocumentProvider();
                if (dp != null) {
                    IDocument doc = dp.getDocument(te.getEditorInput());
                    if (doc != null) return doc.get();
                }
            }
        } catch (Throwable ignored) {}

        // Tier 2: getAdapter(ITextEditor.class)
        try {
            Object adapter = editor.getAdapter(ITextEditor.class);
            if (adapter instanceof ITextEditor) {
                ITextEditor te = (ITextEditor) adapter;
                IDocumentProvider dp = te.getDocumentProvider();
                if (dp != null) {
                    IDocument doc = dp.getDocument(te.getEditorInput());
                    if (doc != null) return doc.get();
                }
            }
        } catch (Throwable ignored) {}

        // Tier 3: getAdapter(IDocument.class)
        try {
            Object adapter = editor.getAdapter(IDocument.class);
            if (adapter instanceof IDocument) return ((IDocument) adapter).get();
        } catch (Throwable ignored) {}

        // Tier 4: reflection getDocumentProvider() on custom editors (ADT AbapSourceEditor)
        try {
            Method m = editor.getClass().getMethod("getDocumentProvider");
            Object dpObj = m.invoke(editor);
            if (dpObj instanceof IDocumentProvider) {
                IDocumentProvider dp = (IDocumentProvider) dpObj;
                IDocument doc = dp.getDocument(editor.getEditorInput());
                if (doc != null) return doc.get();
            }
        } catch (Throwable ignored) {}

        // Tier 5: reflection getSourceViewer().getDocument()
        try {
            Method viewerMethod = null;
            for (Method m : editor.getClass().getMethods()) {
                if (m.getName().equalsIgnoreCase("getSourceViewer") && m.getParameterCount() == 0) {
                    viewerMethod = m; break;
                }
            }
            if (viewerMethod != null) {
                Object viewer = viewerMethod.invoke(editor);
                if (viewer != null) {
                    Method getDoc = viewer.getClass().getMethod("getDocument");
                    Object doc = getDoc.invoke(viewer);
                    if (doc instanceof IDocument) return ((IDocument) doc).get();
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }
}
