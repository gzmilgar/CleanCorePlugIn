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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;

/**
 * Reads the ABAP source of a workspace-resolved ZObject WITHOUT going through
 * HTTP. The resource was discovered by WorkspaceAdtCollector by walking the
 * IProject tree; here we ask ADT to hand us its document text — exactly the
 * same code the user already sees in the editor.
 *
 * Fallback chain:
 *   1. ITextFileBufferManager → ITextFileBuffer → IDocument  (cached source)
 *   2. IFile.getContents()  (workspace-cached InputStream)
 *   3. AdtConnectionService.get() HTTP — last resort (will likely fail behind
 *      SAProuter, in which case the source is left null and the analyzer
 *      simply produces no findings for that object).
 */
public class AdtResourceSourceFetcher {

    public String fetch(ZObject obj) {
        if (obj == null || obj.getName() == null) return null;

        IProject project = AdtConnectionService.getInstance().getAdtProject();
        if (project == null) return tryHttp(obj);

        IResource match = findResource(project, obj.getName());
        if (match == null) return tryHttp(obj);

        // 1) ITextFileBuffer
        try {
            String s = readViaTextFileBuffer(match);
            if (s != null && !s.isEmpty()) return s;
        } catch (Throwable ignored) {}

        // 2) IFile.getContents
        try {
            if (match instanceof IFile) {
                String s = readViaIFileStream((IFile) match);
                if (s != null && !s.isEmpty()) return s;
            }
        } catch (Throwable ignored) {}

        // 3) ADT reflection — some ADT versions expose getSource() on the
        //    resource adapter directly
        try {
            Object adt = match.getAdapter(Class.forName("com.sap.adt.tools.core.model.IAdtObjectReference"));
            if (adt != null) {
                Method m = adt.getClass().getMethod("getSource");
                Object body = m.invoke(adt);
                if (body instanceof String) return (String) body;
            }
        } catch (Throwable ignored) {}

        return tryHttp(obj);
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
            if (rn != null && rn.toUpperCase(Locale.ROOT).startsWith(target)) {
                return r;
            }
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

    /** Last-resort HTTP path. Returns null on failure (timeout / SAProuter). */
    private String tryHttp(ZObject obj) {
        try { return new SourceFetcher().fetch(obj); }
        catch (Throwable t) { return null; }
    }
}
