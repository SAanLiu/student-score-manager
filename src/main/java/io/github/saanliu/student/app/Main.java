package io.github.saanliu.student.app;

import io.github.saanliu.student.exception.StudentException;
import io.github.saanliu.student.model.ScoreStatistics;
import io.github.saanliu.student.model.Student;
import io.github.saanliu.student.repository.InMemoryStudentRepository;
import io.github.saanliu.student.service.StudentService;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;

public class Main {
    private final StudentService service;
    private final Scanner scanner;
    private final PrintStream output;

    // 将输入输出传入，测试时就能模拟键盘输入并检查显示结果。
    public Main(StudentService service, Scanner scanner, PrintStream output) {
        this.service = service;
        this.scanner = scanner;
        this.output = output;
    }

    public static void main(String[] args) {
        StudentService service = new StudentService(new InMemoryStudentRepository());
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            new Main(service, scanner, System.out).run();
        }
    }

    public void run() {
        boolean running = true;
        while (running) {
            printMenu();
            try {
                int choice = readMenuChoice();
                switch (choice) {
                    case 1 -> addStudent();
                    case 2 -> deleteStudent();
                    case 3 -> updateScore();
                    case 4 -> findStudent();
                    case 5 -> listStudents();
                    case 6 -> showStatistics();
                    case 0 -> {
                        output.println("Goodbye.");
                        running = false;
                    }
                    default -> output.println("Invalid choice. Please enter 0 to 6.");
                }
            } catch (StudentException exception) {
                // 业务失败只影响当前操作，菜单继续运行。
                output.println("Error: " + exception.getMessage());
            } catch (NoSuchElementException exception) {
                // 输入流结束时退出，避免无限循环或显示异常堆栈。
                output.println();
                output.println("Input ended. Goodbye.");
                running = false;
            }
        }
    }

    private void printMenu() {
        output.println();
        output.println("=== Student Score Manager ===");
        output.println("1. Add student");
        output.println("2. Delete student");
        output.println("3. Update score");
        output.println("4. Find student by ID");
        output.println("5. List all students by score (highest first)");
        output.println("6. Show statistics");
        output.println("0. Exit");
    }

    private void addStudent() {
        String id = readLine("ID: ");
        String name = readLine("Name: ");
        String className = readLine("Class name: ");
        double score = readScore();
        Student student = service.addStudent(id, name, className, score);
        output.println("Student added successfully.");
        printStudent(student);
    }

    private void deleteStudent() {
        String id = readLine("ID: ");
        service.deleteStudent(id);
        output.println("Student deleted successfully.");
    }

    private void updateScore() {
        String id = readLine("ID: ");
        double score = readScore();
        Student student = service.updateScore(id, score);
        output.println("Score updated successfully.");
        printStudent(student);
    }

    private void findStudent() {
        printStudent(service.getStudent(readLine("ID: ")));
    }

    private void listStudents() {
        List<Student> students = service.getAllStudents();
        if (students.isEmpty()) {
            output.println("No students found.");
            return;
        }
        for (Student student : students) {
            printStudent(student);
        }
    }

    private void showStatistics() {
        Optional<ScoreStatistics> result = service.getStatistics();
        if (result.isEmpty()) {
            output.println("No students available for statistics.");
            return;
        }

        ScoreStatistics statistics = result.get();
        output.println("=== Score Statistics ===");
        output.println("Student count: " + statistics.studentCount());
        output.printf(Locale.ROOT, "Average score: %.2f%n", statistics.averageScore());
        output.printf(Locale.ROOT, "Highest score: %.2f%n", statistics.highestScore());
        output.printf(Locale.ROOT, "Lowest score: %.2f%n", statistics.lowestScore());
        output.printf(Locale.ROOT, "Pass rate: %.2f%%%n", statistics.passRate());
        output.println("Class averages:");
        statistics.classAverages().forEach((className, average) ->
                output.printf(Locale.ROOT, "%s: %.2f%n", className, average));
    }

    private void printStudent(Student student) {
        output.printf(Locale.ROOT, "ID: %s | Name: %s | Class: %s | Score: %.2f%n",
                student.id(), student.name(), student.className(), student.score());
    }

    private int readMenuChoice() {
        while (true) {
            String value = readLine("Choose an option: ").strip();
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                output.println("Invalid input. Please enter a whole number.");
            }
        }
    }

    private double readScore() {
        while (true) {
            String value = readLine("Score: ").strip();
            try {
                // 此处只转换数字，成绩范围等业务规则仍由 service 校验。
                return Double.parseDouble(value);
            } catch (NumberFormatException exception) {
                output.println("Invalid input. Please enter a number, such as 85.5.");
            }
        }
    }

    private String readLine(String prompt) {
        output.print(prompt);
        output.flush();
        return scanner.nextLine();
    }
}
