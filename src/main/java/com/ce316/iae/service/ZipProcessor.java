package com.ce316.iae.service;

import com.ce316.iae.model.StudentSubmission;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Lists and extracts student ZIP files (no reliance on system unzip).
 */
public class ZipProcessor {

    public List<File> listZips(File directory) {
        return Collections.emptyList();
    }

    public File extractOne(File zip, File targetParent) {
        return null;
    }

    public List<StudentSubmission> extractAll(File submissionsDirectory) {
        return Collections.emptyList();
    }
}
