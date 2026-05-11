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
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for "Clean Core → Analyze All Open Editors".
 *
 * Iterates every open editor across all workbench windows/pages, extracts
 * each one's source via the same 5-tier fallback as AnalyzeCurrentFileHandler,
 * runs StaticAbapAnalyzer + ObsoleteApiDetector, and pushes the combined
 * findings into Clean Core Analyzer's "Current File" tab — labelled with the
 * editor count so the analyst can scan a batch of files in one shot.
 *
 * No HTTP traffic: only Eclipse-cached editor content is read. This is the
 * recommended workflow behind SAProuter / VPN-less networks.
 */
public class AnalyzeAllOpenEditorsHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        List<EditorSnapshot> snapshots = collectOpenEditorSources();
        if (snapshots.isEmpty()) {
            MessageDialog.openInformation(HandlerUtil.getActiveShell(event),
                    "Clean Core — Analyze All Open Editors",
                    "No editor sources to analyse. Open one or more ABAP source files in ADT, "
                  + "then re-run this command.");
            return null;
        }

        MappingRepository.getInstance().loadIfNeeded();

        List<Finding> combined = new ArrayList<>();
        StaticAbapAnalyzer staticAnalyzer = new StaticAbapAnalyzer();
        ObsoleteApiDetector apiDetector = new ObsoleteApiDetector();

        for (EditorSnapshot snap : snapshots) {
            if (snap.source == null || snap.source.isEmpty()) continue;
            combined.addAll(staticAnalyzer.analyze(snap.source));
            combined.addAll(apiDetector.analyze(snap.source).findings);
        }

        try {
            IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
            CleanCoreAnalyzerView view = (CleanCoreAnalyzerView)
                    page.showView(CleanCoreAnalyzerView.ID);
            view.showCurrentFileFindings(
                    snapshots.size() + " editor(s) analysed", combined);
        } catch (Exception e) {
            MessageDialog.openError(HandlerUtil.getActiveShell(event),
                    "Clean Core — Analyze All Open Editors",
                    "Collected " + combined.size() + " finding(s) across "
                  + snapshots.size() + " editor(s), but the results view could not be opened:\n"
                  + e.getMessage());
        }
        return null;
    }

    private List<EditorSnapshot> collectOpenEditorSources() {
        List<EditorSnapshot> out = new ArrayList<>();
        try {
            for (IWorkbenchWindow win : PlatformUI.getWorkbench().getWorkbenchWindows()) {
                if (win == null) continue;
                for (IWorkbenchPage page : win.getPages()) {
                    if (page == null) continue;
                    for (IEditorReference ref : page.getEditorReferences()) {
                        if (ref == null) continue;
                        IEditorPart editor = ref.getEditor(false);
                        if (editor == null) continue;
                        String source = extractSource(editor);
                        if (source != null && !source.isEmpty()) {
                            EditorSnapshot s = new EditorSnapshot();
                            s.name = ref.getName();
                            s.source = source;
                            out.add(s);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private String extractSource(IEditorPart editor) {
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
        try {
            Object a = editor.getAdapter(ITextEditor.class);
            if (a instanceof ITextEditor) {
                ITextEditor te = (ITextEditor) a;
                IDocumentProvider dp = te.getDocumentProvider();
                if (dp != null) {
                    IDocument doc = dp.getDocument(te.getEditorInput());
                    if (doc != null) return doc.get();
                }
            }
        } catch (Throwable ignored) {}
        try {
            Object a = editor.getAdapter(IDocument.class);
            if (a instanceof IDocument) return ((IDocument) a).get();
        } catch (Throwable ignored) {}
        try {
            Method m = editor.getClass().getMethod("getDocumentProvider");
            Object dpObj = m.invoke(editor);
            if (dpObj instanceof IDocumentProvider) {
                IDocumentProvider dp = (IDocumentProvider) dpObj;
                IDocument doc = dp.getDocument(editor.getEditorInput());
                if (doc != null) return doc.get();
            }
        } catch (Throwable ignored) {}
        try {
            for (Method m : editor.getClass().getMethods()) {
                if (m.getName().equalsIgnoreCase("getSourceViewer") && m.getParameterCount() == 0) {
                    Object viewer = m.invoke(editor);
                    if (viewer != null) {
                        Method getDoc = viewer.getClass().getMethod("getDocument");
                        Object doc = getDoc.invoke(viewer);
                        if (doc instanceof IDocument) return ((IDocument) doc).get();
                    }
                    break;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static class EditorSnapshot {
        String name;
        String source;
    }
}
