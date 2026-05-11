package com.sap.cleancore.analyzer.collectors;

import com.sap.cleancore.analyzer.data.AdtConnectionService;
import com.sap.cleancore.analyzer.model.ZObject;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;

/**
 * Reads the ABAP source of a ZObject WITHOUT going through HTTP.
 *
 * Strategy (in order, first hit wins):
 *   1. Active workbench editors — match by editor name stem (works for any
 *      ABAP file the user has open in ADT).
 *   2. ITextFileBufferManager  → IDocument (workspace-cached source).
 *   3. IFile.getContents()     (resource-cached InputStream).
 *   4. IAdtObjectReference.getSource() via reflection.
 *   5. Last-resort HTTP fallback via SourceFetcher.
 *
 * For NS4/SAProuter customers steps 1–4 should always cover what the user
 * needs to analyse: they will have opened those files in ADT manually before
 * running 'Run Analysis'.
 */
public class AdtResourceSourceFetcher {

    public String fetch(ZObject obj) {
        if (obj == null || obj.getName() == null) return null;

        // 1) Open editor match — most reliable for ADT projects
        String s = readFromOpenEditor(obj);
        if (s != null && !s.isEmpty()) return s;

        IProject project = AdtConnectionService.getInstance().getAdtProject();
        if (project == null) return tryHttp(obj);

        IResource match = findResource(project, obj.getName());
        if (match != null) {
            try {
                s = readViaTextFileBuffer(match);
                if (s != null && !s.isEmpty()) return s;
            } catch (Throwable ignored) {}
            try {
                if (match instanceof IFile) {
                    s = readViaIFileStream((IFile) match);
                    if (s != null && !s.isEmpty()) return s;
                }
            } catch (Throwable ignored) {}
            try {
                Object adt = match.getAdapter(Class.forName("com.sap.adt.tools.core.model.IAdtObjectReference"));
                if (adt != null) {
                    Method m = adt.getClass().getMethod("getSource");
                    Object body = m.invoke(adt);
                    if (body instanceof String) return (String) body;
                }
            } catch (Throwable ignored) {}
        }

        return tryHttp(obj);
    }

    /**
     * Searches every open editor for one whose name stem equals the ZObject's
     * name and reads its IDocument. Uses the same 5-tier IDocument extraction
     * as AnalyzeCurrentFileHandler.
     */
    private String readFromOpenEditor(ZObject obj) {
        try {
            String target = obj.getName().toUpperCase(Locale.ROOT);
            for (IWorkbenchWindow win : PlatformUI.getWorkbench().getWorkbenchWindows()) {
                if (win == null) continue;
                for (IWorkbenchPage page : win.getPages()) {
                    if (page == null) continue;
                    for (IEditorReference ref : page.getEditorReferences()) {
                        if (ref == null) continue;
                        String n = ref.getName();
                        if (n == null) continue;
                        int dot = n.lastIndexOf('.');
                        String stem = (dot > 0 ? n.substring(0, dot) : n).toUpperCase(Locale.ROOT);
                        if (!stem.equals(target)) continue;
                        IEditorPart editor = ref.getEditor(false);
                        if (editor == null) continue;
                        String src = extractSource(editor);
                        if (src != null && !src.isEmpty()) return src;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** 5-tier IDocument extraction shared with AnalyzeCurrentFileHandler. */
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

    private IResource findResource(IProject project, String name) {
        if (name == null || name.isEmpty()) return null;
        String target = name.toUpperCase(Locale.ROOT);
        Queue<IResource> q = new LinkedList<>();
        try { for (IResource r : project.members()) q.add(r); } catch (Throwable ignored) {}
        while (!q.isEmpty()) {
            IResource r = q.poll();
            if (r == null) continue;
            String rn = r.getName();
            if (rn != null && rn.toUpperCase(Locale.ROOT).startsWith(target)) return r;
            try {
                Method members = r.getClass().getMethod("members");
                Object kids = members.invoke(r);
                if (kids instanceof IResource[]) {
                    for (IResource k : (IResource[]) kids) q.add(k);
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private String readViaTextFileBuffer(IResource res) throws Exception {
        if (res == null) return null;
        IPath path = res.getFullPath();
        if (path == null) return null;
        ITextFileBufferManager mgr = FileBuffers.getTextFileBufferManager();
        mgr.connect(path, LocationKind.IFILE, new NullProgressMonitor());
        try {
            ITextFileBuffer buf = mgr.getTextFileBuffer(path, LocationKind.IFILE);
            if (buf != null) {
                IDocument doc = buf.getDocument();
                if (doc != null) return doc.get();
            }
        } finally {
            try { mgr.disconnect(path, LocationKind.IFILE, new NullProgressMonitor()); } catch (Throwable ignored) {}
        }
        return null;
    }

    private String readViaIFileStream(IFile file) throws Exception {
        try (InputStream is = file.getContents();
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString();
        }
    }

    private String tryHttp(ZObject obj) {
        try { return new SourceFetcher().fetch(obj); }
        catch (Throwable t) { return null; }
    }
}
