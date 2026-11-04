package io.github.saanliu.student.model;

// record 只承载数据，业务校验统一交给 service。
public record Student(String id, String name, String className, double score) {
}
