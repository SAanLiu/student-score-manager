package io.github.saanliu.student.service;

import io.github.saanliu.student.exception.DuplicateStudentException;
import io.github.saanliu.student.exception.InvalidStudentException;
import io.github.saanliu.student.exception.StudentNotFoundException;
import io.github.saanliu.student.model.Student;
import io.github.saanliu.student.repository.InMemoryStudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StudentServiceTest {
    private StudentService service;

    @BeforeEach
    void setUp() {
        service = new StudentService(new InMemoryStudentRepository());
    }

    @Test
    void addsAndFindsStudentWithNormalizedText() {
        Student added = service.addStudent(" 001 ", " Alice ", " Class A ", 85.5);

        assertEquals(new Student("001", "Alice", "Class A", 85.5), added);
        assertEquals(added, service.getStudent(" 001 "));
        assertEquals(List.of(added), service.getAllStudents());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 60, 89.5, 100})
    void acceptsValidScoresForAddingAndUpdating(double score) {
        service.addStudent("001", "Alice", "Class A", score);
        assertEquals(score, service.getStudent("001").score());

        service.updateScore("001", score);
        assertEquals(score, service.getStudent("001").score());
    }

    @Test
    void rejectsDuplicateIdWithoutOverwritingOriginal() {
        Student original = service.addStudent("001", "Alice", "Class A", 85.5);

        assertThrows(DuplicateStudentException.class,
                () -> service.addStudent(" 001 ", "Bob", "Class B", 60));

        assertEquals(List.of(original), service.getAllStudents());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n", "\u3000"})
    void rejectsMissingStudentFields(String value) {
        assertThrows(InvalidStudentException.class,
                () -> service.addStudent(value, "Alice", "Class A", 85));
        assertThrows(InvalidStudentException.class,
                () -> service.addStudent("001", value, "Class A", 85));
        assertThrows(InvalidStudentException.class,
                () -> service.addStudent("001", "Alice", value, 85));
        assertTrue(service.getAllStudents().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 100.01, Double.NaN,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidScoresWithoutAddingStudent(double score) {
        assertThrows(InvalidStudentException.class,
                () -> service.addStudent("001", "Alice", "Class A", score));
        assertTrue(service.getAllStudents().isEmpty());
    }

    @Test
    void updatesOnlyScoreAndPreservesOriginalRecord() {
        Student original = service.addStudent("001", "Alice", "Class A", 85.5);

        Student updated = service.updateScore(" 001 ", 92);

        assertEquals(new Student("001", "Alice", "Class A", 92), updated);
        assertEquals(updated, service.getStudent("001"));
        assertEquals(85.5, original.score());
        assertEquals(1, service.getAllStudents().size());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 100.01, Double.NaN,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidScoreUpdateWithoutChangingStudent(double score) {
        Student original = service.addStudent("001", "Alice", "Class A", 85.5);

        assertThrows(InvalidStudentException.class, () -> service.updateScore("001", score));
        assertEquals(original, service.getStudent("001"));
    }

    @Test
    void deletesOnlyRequestedStudentAndAllowsIdReuse() {
        service.addStudent("001", "Alice", "Class A", 85.5);
        Student remaining = service.addStudent("002", "Bob", "Class B", 60);

        service.deleteStudent(" 001 ");

        assertEquals(List.of(remaining), service.getAllStudents());
        assertThrows(StudentNotFoundException.class, () -> service.getStudent("001"));
        Student replacement = service.addStudent("001", "Charlie", "Class B", 90);
        assertEquals(replacement, service.getStudent("001"));
    }

    @Test
    void rejectsMissingIdForQueryUpdateAndDeleteWithoutChangingOtherStudents() {
        Student original = service.addStudent("001", "Alice", "Class A", 85.5);

        assertThrows(StudentNotFoundException.class, () -> service.getStudent("missing"));
        assertThrows(StudentNotFoundException.class, () -> service.updateScore("missing", 90));
        assertThrows(StudentNotFoundException.class, () -> service.deleteStudent("missing"));
        assertEquals(List.of(original), service.getAllStudents());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n", "\u3000"})
    void rejectsBlankIdForQueryUpdateAndDelete(String id) {
        Student original = service.addStudent("001", "Alice", "Class A", 85.5);

        assertThrows(InvalidStudentException.class, () -> service.getStudent(id));
        assertThrows(InvalidStudentException.class, () -> service.updateScore(id, 90));
        assertThrows(InvalidStudentException.class, () -> service.deleteStudent(id));
        assertEquals(List.of(original), service.getAllStudents());
    }

    @Test
    void returnsReadOnlySnapshotThatCannotChangeStoredStudents() {
        Student original = service.addStudent("001", "Alice", "Class A", 85.5);
        List<Student> snapshot = service.getAllStudents();

        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        service.addStudent("002", "Bob", "Class B", 60);

        assertEquals(List.of(original), snapshot);
        assertEquals(2, service.getAllStudents().size());
    }

    @Test
    void startsWithNoStudents() {
        assertTrue(service.getAllStudents().isEmpty());
    }
}
