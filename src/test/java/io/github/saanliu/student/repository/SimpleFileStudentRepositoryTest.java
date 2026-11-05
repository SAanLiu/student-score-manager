package io.github.saanliu.student.repository;

import io.github.saanliu.student.exception.InvalidStudentException;
import io.github.saanliu.student.exception.StudentStorageException;
import io.github.saanliu.student.model.Student;
import io.github.saanliu.student.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimpleFileStudentRepositoryTest {
    @TempDir
    Path directory;

    private Path file;

    @BeforeEach
    void setUp() {
        file = directory.resolve("data/students.csv");
    }

    @Test
    void startsEmptyWhenFileDoesNotExist() {
        StudentRepository repository = new SimpleFileStudentRepository(file);

        assertTrue(repository.findAll().isEmpty());
        assertTrue(repository.findById("001").isEmpty());
        assertFalse(Files.exists(file.getParent()));
    }

    @Test
    void savesFourFieldsAndLoadsThemOnRestart() throws IOException {
        StudentRepository repository = new SimpleFileStudentRepository(file);
        Student student = new Student("001", "Alice", "Class A", 85.5);

        repository.save(student);

        assertEquals(List.of("001,Alice,Class A,85.5"), Files.readAllLines(file, StandardCharsets.UTF_8));
        StudentRepository reloaded = new SimpleFileStudentRepository(file);
        assertEquals(List.of(student), reloaded.findAll());
        assertEquals(student, reloaded.findById("001").orElseThrow());
    }

    @Test
    void updatesAndDeletesPersistentlyIncludingLastStudent() throws IOException {
        StudentRepository repository = new SimpleFileStudentRepository(file);
        repository.save(new Student("001", "Alice", "Class A", 85));
        Student bob = new Student("002", "Bob", "Class B", 60);
        repository.save(bob);
        Student updated = new Student("001", "Alice", "Class A", 92.123456789);
        repository.save(updated);

        StudentRepository reloaded = new SimpleFileStudentRepository(file);
        assertEquals(2, reloaded.findAll().size());
        assertEquals(updated, reloaded.findById("001").orElseThrow());
        reloaded.deleteById("001");
        assertEquals(List.of(bob), new SimpleFileStudentRepository(file).findAll());
        reloaded.deleteById("002");
        assertTrue(Files.readAllLines(file).isEmpty());
        assertTrue(new SimpleFileStudentRepository(file).findAll().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"002,Bob,60", "002,Bob,Class B,60,extra", ""})
    void rejectsWrongFieldCountWithLineNumber(String invalidLine) throws IOException {
        Files.createDirectories(file.getParent());
        List<String> lines = List.of("001,Alice,Class A,85", invalidLine);
        Files.write(file, lines, StandardCharsets.UTF_8);

        StudentStorageException exception = assertThrows(StudentStorageException.class,
                () -> new SimpleFileStudentRepository(file));

        assertTrue(exception.getMessage().contains("line 2"));
        assertTrue(exception.getMessage().contains("expected 4 fields"));
        assertEquals(lines, Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", " "})
    void rejectsNonnumericScoreIncludingTrailingEmptyField(String score) throws IOException {
        Files.createDirectories(file.getParent());
        List<String> lines = List.of("001,Alice,Class A,85", "002,Bob,Class B," + score);
        Files.write(file, lines, StandardCharsets.UTF_8);

        StudentStorageException exception = assertThrows(StudentStorageException.class,
                () -> new SimpleFileStudentRepository(file));

        assertTrue(exception.getMessage().contains("line 2"));
        assertTrue(exception.getMessage().contains("score must be a number"));
        assertInstanceOf(NumberFormatException.class, exception.getCause());
        assertEquals(lines, Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    @Test
    void preservesUtf8Data() {
        Student expected = new Student("001", "\u5f20\u4e09", "\u4e00\u73ed", 90);
        StudentRepository repository = new SimpleFileStudentRepository(file);

        repository.save(expected);

        assertEquals(expected, new SimpleFileStudentRepository(file).findById("001").orElseThrow());
    }

    @Test
    void worksWithExistingServiceForOperationsSortingAndStatistics() {
        StudentService service = new StudentService(new SimpleFileStudentRepository(file));
        service.addStudent("001", "Alice", "Class A", 80);
        Student bob = service.addStudent("002", "Bob", "Class B", 60);
        service.updateScore("001", 90);

        StudentService reloaded = new StudentService(new SimpleFileStudentRepository(file));
        assertEquals(List.of(reloaded.getStudent("001"), bob), reloaded.getAllStudents());
        assertEquals(75, reloaded.getStatistics().orElseThrow().averageScore());
        reloaded.deleteStudent("002");
        assertEquals(1, new StudentService(new SimpleFileStudentRepository(file)).getAllStudents().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"001,,Class A,80", "001,Alice,Class A,101", "001,Alice,Class A,NaN"})
    void leavesBusinessValidationToService(String line) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, List.of(line), StandardCharsets.UTF_8);
        StudentRepository repository = new SimpleFileStudentRepository(file);
        assertEquals(1, repository.findAll().size());

        assertThrows(InvalidStudentException.class, () -> new StudentService(repository));
        assertEquals(List.of(line), Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    @Test
    void preservesDuplicateRowsForServiceValidation() throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, List.of("001,Alice,Class A,80", "001,Bob,Class B,60"));
        StudentRepository repository = new SimpleFileStudentRepository(file);

        assertEquals(2, repository.findAll().size());
        assertThrows(InvalidStudentException.class, () -> new StudentService(repository));
    }

    @Test
    void reportsReadFailureRatherThanTreatingDirectoryAsEmpty() throws IOException {
        Files.createDirectories(file);

        assertThrows(StudentStorageException.class, () -> new SimpleFileStudentRepository(file));
    }

    @Test
    void failedSaveKeepsExistingStudentsInMemory() throws IOException {
        StudentRepository repository = new SimpleFileStudentRepository(file);
        Student original = new Student("001", "Alice", "Class A", 80);
        repository.save(original);
        Files.delete(file);
        Files.createDirectory(file);

        assertThrows(StudentStorageException.class,
                () -> repository.save(new Student("001", "Alice", "Class A", 90)));
        assertThrows(StudentStorageException.class, () -> repository.deleteById("001"));
        assertEquals(List.of(original), repository.findAll());
        assertThrows(UnsupportedOperationException.class, () -> repository.findAll().clear());
    }

    @Test
    void rejectsCommaInNameBeforeCreatingFile() {
        StudentService service = new StudentService(new SimpleFileStudentRepository(file));

        StudentStorageException exception = assertThrows(StudentStorageException.class,
                () -> service.addStudent("001", "Bob, Jr.", "Class A", 90));

        assertTrue(exception.getMessage().contains("Name cannot contain commas or line breaks"));
        assertTrue(service.getAllStudents().isEmpty());
        assertFalse(Files.exists(file.getParent()));
        assertTrue(new StudentService(new SimpleFileStudentRepository(file)).getAllStudents().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {",", "\n", "\r", "\r\n"})
    void rejectsUnrepresentableFieldsWithoutChangingExistingData(String separator) throws IOException {
        StudentRepository repository = new SimpleFileStudentRepository(file);
        Student original = new Student("001", "Alice", "Class A", 80);
        repository.save(original);
        byte[] originalBytes = Files.readAllBytes(file);
        List<Student> invalidStudents = List.of(
                new Student("00" + separator + "2", "Bob", "Class B", 90),
                new Student("001", "Bob" + separator + "Jr.", "Class A", 90),
                new Student("002", "Bob", "Class" + separator + "B", 90));

        for (Student invalid : invalidStudents) {
            assertThrows(StudentStorageException.class, () -> repository.save(invalid));
            assertEquals(List.of(original), repository.findAll());
            assertArrayEquals(originalBytes, Files.readAllBytes(file));
            assertEquals(List.of(original), new StudentService(new SimpleFileStudentRepository(file))
                    .getAllStudents());
        }
    }

    @Test
    void allowsCommaInNameWithOtherRepositoryImplementations() {
        StudentService inMemory = new StudentService(new InMemoryStudentRepository());
        StudentService csv = new StudentService(new FileStudentRepository(file));

        Student expected = inMemory.addStudent("001", "Bob, Jr.", "Class A", 90);
        assertEquals(expected, csv.addStudent("001", "Bob, Jr.", "Class A", 90));
        assertEquals(expected, new StudentService(new FileStudentRepository(file)).getStudent("001"));
    }

    @Test
    void acceptsOrdinaryPunctuationAndCanRestartAfterRejectedInput() {
        StudentService service = new StudentService(new SimpleFileStudentRepository(file));
        assertThrows(StudentStorageException.class,
                () -> service.addStudent("001", "Bob, Jr.", "Class A", 90));

        Student expected = service.addStudent("001", "Bob Jr. (Ace)", "Class A-1", 90);

        assertEquals(expected, new StudentService(new SimpleFileStudentRepository(file)).getStudent("001"));
    }
}
