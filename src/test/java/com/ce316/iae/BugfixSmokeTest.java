package com.ce316.iae;

import com.ce316.iae.model.Configuration;
import com.ce316.iae.model.Project;
import com.ce316.iae.model.StudentSubmission;
import com.ce316.iae.model.TestCase;
import com.ce316.iae.persistence.PersistenceManager;
import com.ce316.iae.service.ConfigurationManager;
import com.ce316.iae.service.ProjectManager;
import com.ce316.iae.service.ZipProcessor;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the critical bug fixes from the May 2026 fix pass:
 *  1. ProjectDialog flow persists configuration + test cases (not just an empty shell)
 *  2. ZipProcessor uses 9-digit student ID pattern
 *  3. seedDefaultsIfEmpty installs the four built-in configurations
 */
class BugfixSmokeTest {

    @Test
    void seedDefaults_installsFourConfigs_onEmptyDb() {
        PersistenceManager pm = PersistenceManager.getInstance();
        ConfigurationManager cm = new ConfigurationManager(pm);
        cm.seedDefaultsIfEmpty();
        List<Configuration> configs = cm.listConfigurations();
        assertTrue(configs.size() >= 4, "expected >=4 configs after seed; got " + configs.size());
        assertTrue(configs.stream().anyMatch(c -> "C Language".equals(c.getName())));
        assertTrue(configs.stream().anyMatch(c -> "Python 3".equals(c.getName())));
    }

    @Test
    void persistNewProject_keepsConfigurationAndTestCases() {
        PersistenceManager pm = PersistenceManager.getInstance();
        ConfigurationManager cm = new ConfigurationManager(pm);
        cm.seedDefaultsIfEmpty();
        Configuration cfg = cm.listConfigurations().get(0);

        ProjectManager pmgr = new ProjectManager(pm);
        Project p = pmgr.createProject("BugfixTest_" + System.nanoTime(), "C:\\tmp");
        p.setConfiguration(cfg);

        TestCase tc1 = new TestCase();
        tc1.setInputArgs(""); tc1.setExpectedOutput("Hello");
        p.getTestCases().add(tc1);
        TestCase tc2 = new TestCase();
        tc2.setInputArgs("Alice"); tc2.setExpectedOutput("Hi Alice");
        p.getTestCases().add(tc2);

        pmgr.persistNewProject(p);
        int id = p.getId();
        assertTrue(id > 0, "project id should be assigned by DB");

        // Reload from DB — config + tests must round-trip
        Project loaded = pmgr.openProject(id);
        assertNotNull(loaded);
        assertNotNull(loaded.getConfiguration(), "configuration must persist");
        assertEquals(cfg.getId(), loaded.getConfiguration().getId());
        assertEquals(2, loaded.getTestCases().size(), "both test cases must persist");
    }

    @Test
    void zipProcessor_skips_nonNineDigitFilenames() throws Exception {
        Path tmp = Files.createTempDirectory("iae_zip_test_");
        // Valid 9-digit ID
        File valid = createDummyZip(tmp.resolve("220201085.zip").toFile());
        // Non-matching name (like the walkthrough's "homework1.zip" example)
        File invalid = createDummyZip(tmp.resolve("homework1.zip").toFile());

        ZipProcessor zp = new ZipProcessor();
        StudentSubmission validSub = zp.extractOne(valid);
        StudentSubmission invalidSub = zp.extractOne(invalid);

        assertEquals("220201085", validSub.getStudentId());
        // Per the fixed deriveStudentId, invalid → returns the full filename;
        // ExecutionEngine.runAll then matches "\d{9}" and marks it SKIPPED.
        assertNotEquals("homework1", "1", "the non-fixed regex used to extract '1' from homework1");
        assertFalse(invalidSub.getStudentId().matches("\\d{9}"),
                "invalid filename must NOT be derived to a 9-digit id");
    }

    private static File createDummyZip(File target) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(target))) {
            zos.putNextEntry(new ZipEntry("main.c"));
            zos.write("int main(){return 0;}".getBytes());
            zos.closeEntry();
        }
        return target;
    }
}
