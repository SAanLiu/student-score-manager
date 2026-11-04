package io.github.saanliu.student.app;

import io.github.saanliu.student.exception.StudentNotFoundException;
import io.github.saanliu.student.model.Student;
import io.github.saanliu.student.repository.InMemoryStudentRepository;
import io.github.saanliu.student.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    private StudentService service;

    @BeforeEach
    void setUp() {
        service = new StudentService(new InMemoryStudentRepository());
    }

    @Test
    void completesAddFindUpdateListAndDeleteInOneSession() {
        String output = runMenu("""
                1
                001
                Alice
                Class A
                85.5
                4
                001
                3
                001
                92
                5
                2
                001
                5
                0
                """);

        assertTrue(output.contains("Student added successfully."));
        assertTrue(output.contains("ID: 001 | Name: Alice | Class: Class A | Score: 85.50"));
        assertTrue(output.contains("Score updated successfully."));
        assertTrue(output.contains("Score: 92.00"));
        assertTrue(output.contains("Student deleted successfully."));
        assertTrue(output.contains("No students found."));
        assertTrue(output.endsWith("Goodbye." + System.lineSeparator()));
        assertTrue(service.getAllStudents().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "", "999999999999999999999"})
    void retriesNonIntegerMenuInputAndThenRunsNextChoice(String input) {
        String output = runMenu(input + "\n5\n0\n");

        assertTrue(output.contains("Please enter a whole number."));
        assertTrue(output.contains("No students found."));
        assertTrue(output.contains("Goodbye."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "7", "99"})
    void rejectsUnknownMenuChoiceAndContinues(String choice) {
        String output = runMenu(choice + "\n5\n0\n");

        assertTrue(output.contains("Invalid choice."));
        assertTrue(output.contains("No students found."));
    }

    @Test
    void retriesInvalidScoreWithoutLosingOtherFields() {
        String output = runMenu("1\n001\nAlice\nClass A\nabc\n\n85.5\n0\n");

        assertTrue(output.contains("Please enter a number, such as 85.5."));
        assertEquals(new Student("001", "Alice", "Class A", 85.5), service.getStudent("001"));
    }

    @Test
    void handlesMissingIdForFindUpdateAndDeleteAndContinues() {
        String output = runMenu("4\nmissing\n3\nmissing\n90\n2\nmissing\n5\n0\n");

        assertEquals(3, output.lines().filter(line -> line.contains("Student not found: missing")).count());
        assertTrue(output.contains("No students found."));
        assertFalse(output.contains("successfully"));
    }

    @Test
    void reportsDuplicateIdWithoutOverwritingStudent() {
        Student original = service.addStudent("001", "Alice", "Class A", 85.5);

        String output = runMenu("1\n001\nBob\nClass B\n60\n4\n001\n0\n");

        assertTrue(output.contains("Error: Student already exists: 001"));
        assertTrue(output.contains("Name: Alice"));
        assertEquals(original, service.getStudent("001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "101", "NaN", "Infinity", "-Infinity", "1e309"})
    void reportsInvalidScoreAndContinuesWithoutAddingStudent(String score) {
        String output = runMenu("1\n001\nAlice\nClass A\n" + score + "\n5\n0\n");

        assertTrue(output.contains("Error: Score must be a finite number between 0 and 100."));
        assertTrue(output.contains("No students found."));
        assertTrue(service.getAllStudents().isEmpty());
    }

    @Test
    void rejectsInvalidUpdateAndKeepsExistingScore() {
        Student original = service.addStudent("001", "Alice", "Class A", 85.5);

        String output = runMenu("3\n001\n101\n4\n001\n0\n");

        assertTrue(output.contains("Error: Score must"));
        assertTrue(output.contains("Score: 85.50"));
        assertEquals(original, service.getStudent("001"));
    }

    @Test
    void reportsBlankNameAndContinues() {
        String output = runMenu("1\n001\n   \nClass A\n85\n5\n0\n");

        assertTrue(output.contains("Error: Name must not be blank."));
        assertTrue(output.contains("No students found."));
    }

    @Test
    void acceptsSurroundingSpacesWithoutSkippingNameInput() {
        runMenu(" 1 \n 001 \n Alice Smith \n Class A \n 85.5 \n 0 \n");

        assertEquals(new Student("001", "Alice Smith", "Class A", 85.5), service.getStudent("001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc\n", "1\n", "1\n001\n", "1\n001\nAlice\nClass A\n", "3\n001\n"})
    void exitsOnEndOfInputWithoutPartialChangesOrInfiniteLoop(String input) {
        String output = assertTimeoutPreemptively(Duration.ofSeconds(3), () -> runMenu(input));

        assertTrue(output.contains("Input ended. Goodbye."));
        assertTrue(service.getAllStudents().isEmpty());
    }

    @Test
    void stopsImmediatelyAfterExitChoice() {
        String output = runMenu("0\n1\n001\nAlice\nClass A\n85\n");

        assertTrue(output.contains("Goodbye."));
        assertFalse(output.contains("Student added successfully."));
        assertThrows(StudentNotFoundException.class, () -> service.getStudent("001"));
    }

    @Test
    void displaysStatisticsAndSortedStudents() {
        service.addStudent("003", "Carol", "Class B", 30);
        service.addStudent("001", "Alice", "Class A", 90);
        service.addStudent("004", "Dave", "Class B", 100);
        service.addStudent("002", "Bob", "Class A", 60);

        String output = runMenu("5\n6\n0\n");

        assertTrue(output.indexOf("ID: 004") < output.indexOf("ID: 001"));
        assertTrue(output.indexOf("ID: 001") < output.indexOf("ID: 002"));
        assertTrue(output.indexOf("ID: 002") < output.indexOf("ID: 003"));
        assertTrue(output.contains("Student count: 4"));
        assertTrue(output.contains("Average score: 70.00"));
        assertTrue(output.contains("Highest score: 100.00"));
        assertTrue(output.contains("Lowest score: 30.00"));
        assertTrue(output.contains("Pass rate: 75.00%"));
        assertTrue(output.contains("Class A: 75.00"));
        assertTrue(output.contains("Class B: 65.00"));
        assertTrue(output.indexOf("Class A: 75.00") < output.indexOf("Class B: 65.00"));
    }

    @Test
    void displaysNoStatisticsForEmptyDataAndContinues() {
        String output = runMenu("6\n5\n0\n");

        assertTrue(output.contains("No students available for statistics."));
        assertTrue(output.contains("No students found."));
        assertFalse(output.contains("Average score:"));
        assertFalse(output.contains("NaN"));
        assertTrue(output.contains("Goodbye."));
    }

    @Test
    void roundsStatisticsOnlyForDisplay() {
        service.addStudent("001", "Alice", "Class A", 100);
        service.addStudent("002", "Bob", "Class A", 0);
        service.addStudent("003", "Carol", "Class A", 0);

        String output = runMenu("6\n0\n");

        assertTrue(output.contains("Average score: 33.33"));
        assertTrue(output.contains("Pass rate: 33.33%"));
        assertTrue(output.contains("Class A: 33.33"));
        assertEquals(100.0 / 3, service.getStatistics().orElseThrow().averageScore(), 1e-10);
    }

    private String runMenu(String input) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Scanner scanner = new Scanner(input);
             PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            new Main(service, scanner, output).run();
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
