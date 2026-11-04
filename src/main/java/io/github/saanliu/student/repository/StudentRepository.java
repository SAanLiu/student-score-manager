package io.github.saanliu.student.repository;

import io.github.saanliu.student.model.Student;

import java.util.List;
import java.util.Optional;

public interface StudentRepository {
    // 按 id 保存：不存在时新增，存在时替换。
    void save(Student student);

    Optional<Student> findById(String id);

    void deleteById(String id);

    // 返回独立的只读快照，避免调用方直接修改存储。
    List<Student> findAll();
}
