package io.github.saanliu.student.app;

import io.github.saanliu.student.exception.StudentException;
import io.github.saanliu.student.repository.InMemoryStudentRepository;
import io.github.saanliu.student.service.StudentService;

public class Main {
    public static void main(String[] args) {
        StudentService service = new StudentService(new InMemoryStudentRepository());

        // 第一步用固定数据演示，第二步再替换为交互菜单。
        System.out.println("=== Student Management Core Demo ===");
        System.out.println("Added: " + service.addStudent("001", "Alice", "Class A", 85.5));
        System.out.println("Added: " + service.addStudent("002", "Bob", "Class B", 60));
        System.out.println("Found: " + service.getStudent("001"));
        System.out.println("Updated: " + service.updateScore("001", 92));
        service.deleteStudent("002");
        System.out.println("Deleted student: 002");
        System.out.println("Remaining students: " + service.getAllStudents());

        showExpectedFailure(() -> service.addStudent("001", "Charlie", "Class A", 80));
        showExpectedFailure(() -> service.addStudent("003", "   ", "Class A", 80));
        showExpectedFailure(() -> service.addStudent("003", "Charlie", "Class A", 101));
        showExpectedFailure(() -> service.updateScore("001", Double.NaN));
        showExpectedFailure(() -> service.getStudent("002"));
        System.out.println("Demo completed.");
    }

    private static void showExpectedFailure(Runnable action) {
        try {
            action.run();
        } catch (StudentException exception) {
            System.out.println("Expected error: " + exception.getMessage());
            return;
        }
        throw new AssertionError("Expected a student operation to fail.");
    }
}
