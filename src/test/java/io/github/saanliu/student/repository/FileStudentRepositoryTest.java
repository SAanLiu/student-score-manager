package io.github.saanliu.student.repository;

import io.github.saanliu.student.app.Main;
import io.github.saanliu.student.exception.InvalidStudentException;
import io.github.saanliu.student.exception.StudentStorageException;
import io.github.saanliu.student.model.Student;
import io.github.saanliu.student.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class FileStudentRepositoryTest {
    @TempDir
    Path directory;

    private Path file;

    @BeforeEach
    void setUp() {
        file = directory.resolve("students.csv");
    }

    @Test
    void startsEmptyWithoutCreatingMissingFileOrDirectories() {
        Path nested = directory.resolve("data/students.csv");

        StudentService service = new StudentService(new FileStudentRepository(nested));

        assertTrue(service.getAllStudents().isEmpty());
        assertFalse(Files.exists(nested.getParent()));
    }

    @Test
    void createsDirectoriesAndSavesFourFieldsWithoutHeader() throws IOException {
        file = directory.resolve("data/students.csv");
        StudentService service = new StudentService(new FileStudentRepository(file));

        Student student = service.addStudent("001", "Alice", "Class A", 85.5);

        assertEquals("001,Alice,Class A,85.5\n", Files.readString(file, StandardCharsets.UTF_8));
        assertEquals(List.of(student), new FileStudentRepository(file).findAll());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Alice, Jr.", "Alice \"Ace\"", "Alice\nSmith", "Alice\r\nSmith"})
    void roundTripsCsvCommasQuotesAndNewlines(String name) {
        StudentService service = new StudentService(new FileStudentRepository(file));
        Student expected = service.addStudent("id,\"001\"", name, "Class, \"A\"", 85.123456789);

        StudentService reloaded = new StudentService(new FileStudentRepository(file));

        assertEquals(expected, reloaded.getStudent("id,\"001\""));
    }

    @Test
    void preservesUtf8StudentData() throws IOException {
        String name = "\u5f20\u4e09";
        String className = "\u4e00\u73ed";
        StudentService service = new StudentService(new FileStudentRepository(file));
        Student expected = service.addStudent("001", name, className, 90);

        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains(name + "," + className));
        assertEquals(expected, new StudentService(new FileStudentRepository(file)).getStudent("001"));
    }

    @Test
    void acceptsUtf8BomAndWindowsLineEndings() throws IOException {
        Files.writeString(file, "\uFEFF001,Alice,Class A,0\r\n002,Bob,Class B,100\r\n",
                StandardCharsets.UTF_8);

        StudentService service = new StudentService(new FileStudentRepository(file));

        assertEquals(0, service.getStudent("001").score());
        assertEquals(100, service.getStudent("002").score());
    }

    @Test
    void persistsAddUpdateAndDeleteAcrossRestartsIncludingLastStudent() throws IOException {
        StudentService first = new StudentService(new FileStudentRepository(file));
        first.addStudent("001", "Alice", "Class A", 85.5);
        first.addStudent("002", "Bob", "Class B", 60);

        StudentService second = new StudentService(new FileStudentRepository(file));
        second.updateScore("001", 92.123456789);
        second.deleteStudent("002");

        StudentService third = new StudentService(new FileStudentRepository(file));
        assertEquals(List.of(new Student("001", "Alice", "Class A", 92.123456789)),
                third.getAllStudents());
        third.deleteStudent("001");
        assertEquals("", Files.readString(file));
        assertTrue(new StudentService(new FileStudentRepository(file)).getAllStudents().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"001,Alice,80\n", "001,Alice,Class A,80,extra\n", "\n",
            "001,Alice,Class A,not-a-number\n", "001,\"Alice,Class A,80\n",
            "001,\"Alice\"extra,Class A,80\n"})
    void rejectsMalformedCsvWithoutChangingFile(String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);

        assertThrows(StudentStorageException.class, () -> new FileStudentRepository(file));
        assertEquals(content, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void identifiesRecordNumberForInvalidFieldCount() throws IOException {
        Files.writeString(file, "001,Alice,Class A,80\n002,Bob,60\n");

        StudentStorageException exception = assertThrows(StudentStorageException.class,
                () -> new FileStudentRepository(file));

        assertTrue(exception.getMessage().contains("record 2"));
    }

    @ParameterizedTest
    @ValueSource(strings = {",Alice,Class A,80\n", "001,,Class A,80\n", "001,Alice,,80\n",
            "001,Alice,Class A,-1\n", "001,Alice,Class A,101\n", "001,Alice,Class A,NaN\n",
            "001,Alice,Class A,Infinity\n", " 001,Alice,Class A,80\n"})
    void serviceRejectsInvalidStoredStudentsWithoutChangingFile(String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);

        InvalidStudentException exception = assertThrows(InvalidStudentException.class,
                () -> new StudentService(new FileStudentRepository(file)));

        assertTrue(exception.getMessage().contains("record 1"));
        assertEquals(content, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void serviceRejectsDuplicateStoredIdsInsteadOfSilentlyOverwriting() throws IOException {
        String content = "001,Alice,Class A,80\n001,Bob,Class B,60\n";
        Files.writeString(file, content);
        FileStudentRepository repository = new FileStudentRepository(file);
        assertEquals(2, repository.findAll().size());

        InvalidStudentException exception = assertThrows(InvalidStudentException.class,
                () -> new StudentService(repository));

        assertTrue(exception.getMessage().contains("record 2"));
        assertTrue(exception.getMessage().contains("Student already exists: 001"));
        assertEquals(content, Files.readString(file));
    }

    @Test
    void rejectsInvalidUtf8InsteadOfTreatingFileAsEmpty() throws IOException {
        byte[] content = {(byte) 0xC3, (byte) 0x28};
        Files.write(file, content);

        assertThrows(StudentStorageException.class, () -> new FileStudentRepository(file));
        assertArrayEquals(content, Files.readAllBytes(file));
    }

    @Test
    void reportsReadFailureWhenDataPathIsDirectory() throws IOException {
        Files.createDirectory(file);

        assertThrows(StudentStorageException.class, () -> new FileStudentRepository(file));
    }

    @Test
    void failedDirectoryCreationDoesNotAddStudentInMemory() throws IOException {
        Path parent = directory.resolve("data");
        file = parent.resolve("students.csv");
        StudentService service = new StudentService(new FileStudentRepository(file));
        Files.writeString(parent, "Directory creation is blocked.");

        assertThrows(StudentStorageException.class, () -> service.addStudent("001", "Alice", "Class A", 80));
        assertTrue(service.getAllStudents().isEmpty());
        assertEquals("Directory creation is blocked.", Files.readString(parent));
    }

    @ParameterizedTest
    @ValueSource(strings = {"add", "update", "delete"})
    void failedReplacementPreservesMemoryAndCleansTemporaryFile(String operation) throws IOException {
        StudentService service = new StudentService(new FileStudentRepository(file));
        Student original = service.addStudent("001", "Alice", "Class A", 80);
        Path backup = directory.resolve("original.csv");
        Files.move(file, backup);
        Files.createDirectory(file);
        Files.writeString(file.resolve("blocker.txt"), "Replacement is blocked.");

        assertThrows(StudentStorageException.class, () -> {
            switch (operation) {
                case "add" -> service.addStudent("002", "Bob", "Class B", 60);
                case "update" -> service.updateScore("001", 90);
                case "delete" -> service.deleteStudent("001");
                default -> fail("Unexpected test operation.");
            }
        });

        assertEquals(List.of(original), service.getAllStudents());
        assertEquals("001,Alice,Class A,80.0\n", Files.readString(backup));
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
        // 恢复可写路径后，应当可以继续保存。
        Files.delete(file.resolve("blocker.txt"));
        Files.delete(file);
        Files.move(backup, file);
        service.updateScore("001", 95);
        assertEquals(95, new StudentService(new FileStudentRepository(file)).getStudent("001").score());
    }

    @Test
    void menuWritesDataThatCanBeLoadedByNextSession() {
        StudentService first = new StudentService(new FileStudentRepository(file));
        String firstOutput = runMenu(first, "1\n001\nAlice\nClass A\n85.5\n0\n");
        assertTrue(firstOutput.contains("Student added successfully."));

        StudentService second = new StudentService(new FileStudentRepository(file));
        String secondOutput = runMenu(second, "4\n001\n3\n001\n92\n6\n0\n");
        assertTrue(secondOutput.contains("Score: 85.50"));
        assertTrue(secondOutput.contains("Average score: 92.00"));
        assertEquals(92, new StudentService(new FileStudentRepository(file)).getStudent("001").score());
    }

    @Test
    void menuReportsStorageFailureWithoutClaimingSuccessAndContinues() throws IOException {
        StudentService service = new StudentService(new FileStudentRepository(file));
        Files.createDirectory(file);
        Files.writeString(file.resolve("blocker.txt"), "Replacement is blocked.");

        String output = runMenu(service, "1\n001\nAlice\nClass A\n85.5\n5\n0\n");

        assertTrue(output.contains("Error: Could not save student data"));
        assertTrue(output.contains("No students found."));
        assertFalse(output.contains("Student added successfully."));
        assertTrue(output.contains("Goodbye."));
    }

    private String runMenu(StudentService service, String input) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Scanner scanner = new Scanner(input);
             PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            new Main(service, scanner, output).run();
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
