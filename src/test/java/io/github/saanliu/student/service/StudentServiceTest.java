package io.github.saanliu.student.service;

import io.github.saanliu.student.exception.DuplicateStudentException;
import io.github.saanliu.student.exception.InvalidStudentException;
import io.github.saanliu.student.exception.StudentNotFoundException;
import io.github.saanliu.student.model.ScoreStatistics;
import io.github.saanliu.student.model.Student;
import io.github.saanliu.student.repository.InMemoryStudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

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
    void sortsByDescendingScoreThenIdWithoutChangingStorageOrder() {
        InMemoryStudentRepository repository = new InMemoryStudentRepository();
        service = new StudentService(repository);
        Student bob = service.addStudent("002", "Bob", "Class A", 90);
        Student carol = service.addStudent("003", "Carol", "Class B", 0);
        Student alice = service.addStudent("001", "Alice", "Class A", 90);
        Student dave = service.addStudent("004", "Dave", "Class B", 100);

        assertEquals(List.of(dave, alice, bob, carol), service.getAllStudents());
        assertEquals(List.of(bob, carol, alice, dave), repository.findAll());
    }

    @Test
    void hasNoStatisticsWhenThereAreNoStudents() {
        assertTrue(service.getStatistics().isEmpty());
    }

    @Test
    void calculatesStatisticsForClassesWithDifferentSizes() {
        service.addStudent("001", "Alice", "Class A", 100);
        service.addStudent("002", "Bob", "Class A", 60);
        service.addStudent("003", "Carol", "Class B", 59.5);
        service.addStudent("004", "Dave", "Class B", 0);
        service.addStudent("005", "Eve", "Class B", 80.5);

        ScoreStatistics statistics = service.getStatistics().orElseThrow();

        assertEquals(5, statistics.studentCount());
        assertEquals(60.0, statistics.averageScore(), 1e-10);
        assertEquals(100.0, statistics.highestScore());
        assertEquals(0.0, statistics.lowestScore());
        assertEquals(60.0, statistics.passRate(), 1e-10);
        assertEquals(80.0, statistics.classAverages().get("Class A"), 1e-10);
        assertEquals(140.0 / 3, statistics.classAverages().get("Class B"), 1e-10);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "59.99, 0", "60, 100", "100, 100"})
    void calculatesSingleStudentStatisticsAtPassingBoundary(double score, double expectedPassRate) {
        service.addStudent("001", "Alice", "Class A", score);

        ScoreStatistics statistics = service.getStatistics().orElseThrow();

        assertEquals(1, statistics.studentCount());
        assertEquals(score, statistics.averageScore());
        assertEquals(score, statistics.highestScore());
        assertEquals(score, statistics.lowestScore());
        assertEquals(expectedPassRate, statistics.passRate());
        assertEquals(Map.of("Class A", score), statistics.classAverages());
    }

    @Test
    void refreshesStatisticsAfterUpdatesAndDeletes() {
        service.addStudent("001", "Alice", "Class A", 0);
        service.addStudent("002", "Bob", "Class B", 100);
        ScoreStatistics initial = service.getStatistics().orElseThrow();
        assertEquals(50.0, initial.averageScore());
        assertEquals(50.0, initial.passRate());

        service.updateScore("001", 60);
        ScoreStatistics updated = service.getStatistics().orElseThrow();
        assertEquals(80.0, updated.averageScore());
        assertEquals(100.0, updated.passRate());

        service.deleteStudent("002");
        ScoreStatistics remaining = service.getStatistics().orElseThrow();
        assertEquals(60.0, remaining.averageScore());
        assertEquals(Map.of("Class A", 60.0), remaining.classAverages());

        service.deleteStudent("001");
        assertTrue(service.getStatistics().isEmpty());
    }

    @Test
    void keepsClassAveragesSortedAndProtectsStatisticsSnapshot() {
        service.addStudent("002", "Bob", "Class B", 60);
        service.addStudent("001", "Alice", " Class A ", 80);
        ScoreStatistics statistics = service.getStatistics().orElseThrow();

        assertEquals(List.of("Class A", "Class B"), List.copyOf(statistics.classAverages().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> statistics.classAverages().clear());
        service.updateScore("001", 100);
        assertEquals(80.0, statistics.classAverages().get("Class A"));
        assertEquals(70.0, statistics.averageScore());
    }

    @Test
    void startsWithNoStudents() {
        assertTrue(service.getAllStudents().isEmpty());
    }
}
